#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <chrono>
#include <android/log.h>
#include "llama.h"
#include "ggml-cpu.h"

#define LOG_TAG "EnnamJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Internal ggml API for backend registration (declared in ggml-backend-impl.h)
extern "C" void ggml_backend_register(ggml_backend_reg_t reg);

static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;

extern "C" {

// Helper: convert a single token to text string using modern API
static std::string token_to_text(const struct llama_vocab *vocab, llama_token token) {
    std::vector<char> buf(16);
    int32_t n = llama_token_to_piece(vocab, token, buf.data(), (int32_t)buf.size(), 0, false);
    if (n < 0) {
        buf.resize(-n);
        n = llama_token_to_piece(vocab, token, buf.data(), (int32_t)buf.size(), 0, false);
    }
    return std::string(buf.data(), n);
}

JNIEXPORT jboolean JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeLoadModel(JNIEnv *env, jobject /*thiz*/,
                                                      jstring model_path) {
    auto t0 = std::chrono::steady_clock::now();

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    std::string model_path_str(path);
    env->ReleaseStringUTFChars(model_path, path);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    // Initialize backend
    llama_backend_init();

    // Register CPU backend (needed when compiled as a single shared library)
    ggml_backend_register(ggml_backend_cpu_reg());

    // Load model (CPU only)
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(model_path_str.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model from %s", model_path_str.c_str());
        return JNI_FALSE;
    }

    // Create context — keep it simple for speed
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 6;
    ctx_params.n_threads_batch = 6;
    ctx_params.flash_attn = false;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    auto t1 = std::chrono::steady_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
    LOGI("Model loaded in %lldms", (long long)ms);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeInference(JNIEnv *env, jobject /*thiz*/,
                                                      jstring prompt) {
    auto t_start = std::chrono::steady_clock::now();

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("{\"error\": \"Model not loaded\"}");
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // Prompt already formatted by Classifier.kt (ChatML), do not add wrapper
    const struct llama_vocab *vocab = llama_model_get_vocab(g_model);
    if (!vocab) {
        LOGE("Failed to get vocab");
        return env->NewStringUTF("{\"error\": \"Vocab not found\"}");
    }

    // Tokenize with parse_special=true so <|im_start|>, <|im_end|> are treated as special tokens
    // add_special=true adds BOS token (required for Qwen2.5)
    int32_t n_tokens = llama_tokenize(vocab, prompt_str.c_str(), (int32_t)prompt_str.length(),
                                      nullptr, 0, true, true);
    if (n_tokens <= 0) {
        n_tokens = -n_tokens;
    }
    if (n_tokens <= 0) {
        LOGE("Tokenize failed");
        return env->NewStringUTF("{\"error\": \"Tokenization failed\"}");
    }
    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(vocab, prompt_str.c_str(), (int32_t)prompt_str.length(),
                   tokens.data(), (int32_t)tokens.size(), true, true);

    // Clear KV cache
    llama_kv_cache_clear(g_ctx);

    // Eval prompt
    if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), (int32_t)tokens.size()))) {
        LOGE("Failed to evaluate prompt");
        return env->NewStringUTF("{\"error\": \"Inference failed\"}");
    }
    auto t_prompt = std::chrono::steady_clock::now();

    // Generate tokens (max 256)
    std::string result;
    int32_t n_generated = 0;
    const int32_t max_tokens = 256;
    int32_t n_vocab = llama_vocab_n_tokens(vocab);

    // Pre-allocate result buffer to avoid repeated reallocation
    result.reserve(1024);

    while (n_generated < max_tokens) {
        float *logits = llama_get_logits_ith(g_ctx, -1);
        if (!logits) {
            LOGE("Failed to get logits");
            break;
        }

        // Greedy sampling via max_element
        llama_token new_token_id = (llama_token)std::distance(
            logits, std::max_element(logits, logits + n_vocab));

        // Stop on EOS or EOT (end-of-turn) token
        if (new_token_id == llama_vocab_eos(vocab) ||
            new_token_id == llama_vocab_eot(vocab)) {
            break;
        }

        result += token_to_text(vocab, new_token_id);

        llama_token single = new_token_id;
        if (llama_decode(g_ctx, llama_batch_get_one(&single, 1))) {
            break;
        }

        n_generated++;
    }

    auto t_end = std::chrono::steady_clock::now();
    auto ms_prompt = std::chrono::duration_cast<std::chrono::milliseconds>(t_prompt - t_start).count();
    auto ms_total = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    int ms_per_token = n_generated > 0 ? (ms_total - ms_prompt) / n_generated : 0;

    LOGI("Inference: %lldms (%lldms prompt, %d tokens @ %dms/tok)",
         (long long)ms_total, (long long)ms_prompt, n_generated, ms_per_token);

    llama_kv_cache_clear(g_ctx);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeUnloadModel(JNIEnv *env, jobject /*thiz*/) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    LOGI("Model unloaded");
}

} // extern "C"