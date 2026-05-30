# Ennam (எண்ணம்) — On-Device SLM Thought Dump

Repo: https://github.com/tharunsuresh-code/ennam
License: Apache 2.0
Test device: Pixel 7 (Tensor G2)

## Phases

- **Phase 0 (current)** — llama.cpp Android prototype. Prove Gemma 2B runs on Pixel 7 with <6s latency.
- Phase 1 — Core dump → categorize → feed loop
- Phase 2 — Per-type cards + search + embeddings
- Phase 3 — Explore view (clusters, graph)
- Phase 4 — Cross-platform (Flutter + iOS)

## Architecture

- **SLM**: Gemma 2B Q4_K_M via llama.cpp JNI (CPU-only for now)
- **Embeddings**: all-MiniLM-L6-v2 via ONNX Runtime Mobile (~90MB separate model)
- **Storage**: SQLite (Room) + 384-dim vector embeddings
- **Voice**: Android SpeechRecognizer (on-device API, zero-download)
| **Model**: Downloaded from Hugging Face on first launch (~1.7 GB)

## Setup (Hermes Agent bootstrap — May 29 2026)

### Model note

The original project targeted `google/gemma-2b-it-GGUF` (gated repo). Switched to
`bartowski/gemma-2-2b-it-GGUF` (freely available) — Gemma 2 2B with same prompt template.
Model file: `gemma-2-2b-it-Q4_K_M.gguf`

```bash
# Pre-download on desktop:
curl -L -o models/gemma-2-2b-it-Q4_K_M.gguf \
  "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
```

## Setup (Hermes Agent bootstrap — May 29 2026)

### Clone

```bash
gh repo clone tharunsuresh-code/ennam
```

Missing `.gitmodules` for the llama.cpp submodule (was an orphaned gitlink). Fix:

```bash
cat > .gitmodules << 'EOF'
[submodule "android/app/src/main/cpp/llama.cpp"]
	path = android/app/src/main/cpp/llama.cpp
	url = https://github.com/ggerganov/llama.cpp.git
EOF
git submodule init
git submodule update --recursive
```

### SDK requirements

- Android SDK 35
- NDK 27.0.12077973
- CMake 3.31.6 (or system 3.28+)

```bash
export ANDROID_HOME=$HOME/android-sdk
sdkmanager "platforms;android-35" "ndk;27.0.12077973" "cmake;3.31.6"
echo "sdk.dir=$HOME/android-sdk" > android/local.properties
```

Add to `~/.bashrc`:
```bash
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

### Build fixes applied

1. **gradlew** — replaced broken single-line script (`$0` bug — resolved JAR path relative to script, not through `$0`) with proper dir-resolving POSIX wrapper
2. **build.gradle.kts** (root) — added `org.jetbrains.kotlin.plugin.compose` v2.1.0 (required for Kotlin 2.0+ Compose)
3. **app/build.gradle.kts** — added compose plugin + removed deprecated `composeOptions { kotlinCompilerExtensionVersion }` block
4. **CMakeLists.txt** — rewritten entirely for modern llama.cpp (commit 10f2e8180):
   - ggml sources now under `llama.cpp/ggml/src/` (was top-level)
   - ggml headers now under `llama.cpp/ggml/include/` (was top-level)
   - llama sources now under `llama.cpp/src/` (was top-level)
   - Added extra required files: `ggml-cpu.c`, `ggml-cpu.cpp`, `ggml-cpu-quants.c`, `ggml-cpu-aarch64.cpp`, `ggml-cpu-traits.cpp`, `sgemm.cpp`, `llama-arch.cpp`, `llama-impl.cpp`, `llama-model.cpp`, `llama-model-loader.cpp`, `llama-quant.cpp`, `llama-mmap.cpp`, `llama-kv-cache.cpp`, `llama-hparams.cpp`, `llama-batch.cpp`, `llama-adapter.cpp`, `llama-chat.cpp`, `llama-context.cpp`, `llama-cparams.cpp`, `unicode-data.cpp`, `ggml-opt.cpp`, `ggml-threading.cpp`, `gguf.cpp`
   - Added include path `llama.cpp/ggml/src/ggml-cpu` for internal headers
5. **jni_bridge.cpp** — rewritten for modern llama.cpp API:
   - `llama_free_model()` → `llama_model_free()`
   - `llama_load_model_from_file()` → modern signature
   - `llama_new_context_with_model()` → `llama_init_from_model()`
   - `llama_tokenize(model, ...)` → takes `llama_vocab*` from `llama_model_get_vocab()`
   - `llama_eval()` → `llama_decode()` with `llama_batch_get_one()`
   - `llama_token_eos(model)` → `llama_vocab_eos(vocab)`
   - `llama_token_to_piece(ctx, token)` → 6-arg version with `vocab`, explicit buffer
   - Added `#include <vector>` and type casts for modern API

### Build

```bash
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk` (~46MB)

### ADB & Pixel 7

Device ID: `33291FDH2004NP`. First connection shows `unauthorized` — must accept RSA fingerprint on phone screen.

Once authorized:
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Paths

- `android/app/src/main/cpp/llama.cpp/` — vendored llama.cpp (submodule, commit 10f2e8180)
- `android/app/src/main/cpp/jni_bridge.cpp` — JNI wrapper (rewritten for modern API)
- `android/app/.../ml/LlamaEngine.kt` — Kotlin native bridge
- `android/app/.../ml/Classifier.kt` — prompt template + JSON parser
- `models/download.sh` — download model for testing