#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "EnnamJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;
static llama_sampling_context *g_sampling = nullptr;
static std::string g_model_path;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeLoadModel(JNIEnv *env, jobject /*thiz*/,
                                                      jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    g_model_path = path;
    env->ReleaseStringUTFChars(model_path, path);

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    // Initialize backend
    llama_backend_init();

    // Load model
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only for now

    g_model = llama_load_model_from_file(g_model_path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model from %s", g_model_path.c_str());
        return JNI_FALSE;
    }

    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded: %s", g_model_path.c_str());
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeInference(JNIEnv *env, jobject /*thiz*/,
                                                      jstring prompt) {
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("{\"error\": \"Model not loaded\"}");
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // Build full prompt with Gemma chat template
    // Gemma 2B uses <start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n
    std::string full_prompt = "<start_of_turn>user\n" + prompt_str + "<end_of_turn>\n<start_of_turn>model\n";

    // Tokenize
    int n_tokens = llama_tokenize(g_model, full_prompt.c_str(), full_prompt.length(), nullptr, 0, false, false);
    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(g_model, full_prompt.c_str(), full_prompt.length(), tokens.data(), tokens.size(), false, false);

    // Clear KV cache
    llama_kv_cache_clear(g_ctx);

    // Eval prompt
    if (llama_eval(g_ctx, tokens.data(), tokens.size(), 0)) {
        LOGE("Failed to evaluate prompt");
        return env->NewStringUTF("{\"error\": \"Inference failed\"}");
    }

    // Generate tokens (max 256)
    std::string result;
    int n_pos = tokens.size();
    int n_generated = 0;
    const int max_tokens = 256;

    std::vector<llama_token_data> candidates;
    llama_token_data_array cur_p;

    while (n_generated < max_tokens) {
        // Sample next token
        llama_token new_token_id;
        {
            cur_p = llama_get_logits_ith(g_ctx, n_pos - 1);

            // Simple greedy sampling
            new_token_id = cur_p.data[0].id;
            for (int i = 1; i < cur_p.size; i++) {
                if (cur_p.data[i].logit > cur_p.data[new_token_id].logit) {
                    new_token_id = cur_p.data[i].id;
                }
            }
        }

        // Check for end token
        if (new_token_id == llama_token_eos(g_model)) {
            break;
        }

        // Convert to string
        std::string piece = llama_token_to_piece(g_ctx, new_token_id);
        result += piece;

        // Eval this token
        std::vector<llama_token> next_tokens = {new_token_id};
        if (llama_eval(g_ctx, next_tokens.data(), next_tokens.size(), n_pos)) {
            break;
        }
        n_pos++;
        n_generated++;
    }

    // Clean up
    llama_kv_cache_clear(g_ctx);

    LOGI("Generated %d tokens", n_generated);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_ennam_app_ml_LlamaEngine_nativeUnloadModel(JNIEnv *env, jobject /*thiz*/) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    LOGI("Model unloaded");
}

} // extern "C"