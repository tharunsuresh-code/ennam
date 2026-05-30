# Ennam (எண்ணம்) — On-Device SLM Thought Dump

Repo: https://github.com/tharun/ennam
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
- **Model**: Downloaded from Hugging Face on first launch (~1.5 GB)

## Build

Requires Android SDK 35 + NDK 27.0.12077973.

```bash
cd android
./gradlew assembleDebug
```

The debug APK at `android/app/build/outputs/apk/debug/app-debug.apk`.
Install on Pixel 7: `adb install -r app-debug.apk`

## Paths

- `android/app/src/main/cpp/llama.cpp/` — vendored llama.cpp
- `android/app/src/main/cpp/jni_bridge.cpp` — JNI wrapper
- `android/app/.../ml/LlamaEngine.kt` — Kotlin native bridge
- `android/app/.../ml/Classifier.kt` — prompt template + JSON parser
- `models/download.sh` — download model for testing