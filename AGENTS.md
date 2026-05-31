# Ennam (எண்ணம்) — On-Device SLM Thought Dump

Repo: https://github.com/tharunsuresh-code/ennam
License: Apache 2.0
Test device: Pixel 7 (Tensor G2)

## Phases

- **Phase 0 ✅** — llama.cpp Android prototype. Qwen2.5-1.5B runs on Pixel 7 at 5-6s.
- **Phase 1 ✅ (current)** — Core dump → categorize → feed loop.
  - Text, voice, image input via bottom sheet
  - Room database (SQLite) for entries
  - Feed screen with chronological list + category tabs
  - SLM classification runs in background, pending cards shown during inference
- **Phase 2** — Per-type cards + search + embeddings
  - ✅ Week 1: Per-Type Adaptive Cards (26 May 2026)
  - ✅ Week 2: Search + Embeddings (30 May 2026)
    - Entry model: added `embedding` field (ByteArray?, 384 float32)
    - EntryFts: FTS4 virtual table with Room @Fts4 (rawText, summary, category, tags)
    - AppDatabase v3: EntryFts entity + seed callback for default categories
    - Embedder.kt: ONNX Runtime wrapper for all-MiniLM-L6-v2 (~90MB)
      - BERT WordPiece tokenizer (vocab.txt from HuggingFace)
      - Mean pooling + L2 normalization for 384-dim embeddings
      - Download model + vocab on first launch
    - SearchBar.kt: inline search composable at top of feed
    - SearchViewModel: FTS4 keyword search + semantic cosine similarity merge
    - FeedScreen: search bar above tabs, shows results in search mode
    - "On this day": section header with entries from 30 days / 1 year ago
    - FeedViewModel: queues embedding computation after entry creation
    - build.gradle.kts: added onnxruntime-android:1.21.0 dependency
    - Entry.kt: added isDone, isPinned, isLocked, answer fields
    - EntryCard.kt: complete rewrite with 7 distinct card layouts
    - Todo: checkbox + priority badge 🟢🟡🔴 → tap to archive
    - Idea: expandable notes + tags
    - Receipt: auto-extracted amount + store name, long-press to archive
    - Journal: first 3 lines + mood emoji, long-press to lock
    - Bookmark: URL extraction → tap opens in browser
    - Question: inline answer field → pressed Done marks resolved
    - Screenshot: pin to top via tap
    - FeedScreen: PullToRefreshBox, bottom sheet archive/delete
    - FeedViewModel: archiveEntry, toggleDone, togglePin, answerQuestion, toggleLocked
    - AppDatabase v2 (fallbackToDestructiveMigration)
    - **Fix**: mood emoji regex used `[...]` character class with supplementary Unicode — Android ICU can't handle surrogate pairs in char classes. Changed to `|` alternation.
- **Phase 2 Week 3** — Card tap bottom sheet + auto-scroll (31 May 2026)
    - All cards now open a unified bottom sheet on tap with Edit, Archive, Delete
    - Edit opens inline text editing in bottom sheet → saves to DB + recomputes embedding
    - Auto-scroll to newest entry on new entry creation (via LazyListState + LaunchedEffect)
    - Category-specific bottom sheet actions: Pin/Unpin (screenshot), Open Link (bookmark), Done/Undo (todo)
    - Removed per-card tap behaviors (Idea expand, Screenshot pin, Bookmark URL open) in favor of bottom sheet
    - `updateRawText` Room query + `FeedViewModel.updateEntryText()` for inline editing
    - Recategorization in edit mode: category picker chips in the edit bottom sheet
    - Dynamic categories: categories derived from active entries (no hardcoded list)
    - CategoryTabs accepts dynamic category list from FeedViewModel
    - Classifier prompt updated to allow suggesting new categories beyond the 7 predefined types
    - New categories automatically appear in tabs and in both InputSheet and edit bottom sheet pickers
    - `updateCategory`, `getDistinctCategories` Room queries + `updateEntryCategory` in ViewModel
    - Model lifecycle: unload on background (ON_STOP), reload on foreground (ON_START) via ProcessLifecycleOwner
    - Added `lifecycle-process` dependency for app-level foreground/background detection
    - `Embedder.unload()` fixed to reset `_loaded` flag so reload works after unload
    - `FeedViewModel.unloadAll()` unloads both LLM and embedder models
    - Background model reload on foreground: no blocking Loading screen, FAB stays visible
    - Classification queue: inputs submitted while model loads are queued and processed when ready
    - `LlamaEngine.isLoaded()` getter added for non-blocking model readiness check
    - Classifier prompt improved: removed grocery-ambiguous todo examples, added "groceries", "shopping" to new-category suggestions
- **Phase 3**
- Phase 4 — Cross-platform (Flutter + iOS)

## Architecture

- **SLM**: Gemma 2B Q4_K_M via llama.cpp JNI (CPU-only for now)
- **Embeddings**: all-MiniLM-L6-v2 via ONNX Runtime Mobile (~90MB separate model)
- **Storage**: SQLite (Room) + 384-dim vector embeddings
- **Voice**: Android SpeechRecognizer (on-device API, zero-download)
| **Model**: Downloaded from Hugging Face on first launch (~1.7 GB)

## Setup (Hermes Agent bootstrap — May 29 2026)

### Model note

Switching to Qwen2.5-1.5B-Instruct for faster inference on Pixel 7.
Gemma 2 2B tested at 15-20s — too slow for CPU-only on Tensor G2.
Qwen2.5-1.5B (~1GB, 1.5B params) expected ~6-10s on Pixel 7.

```bash
# Pre-download on desktop:
curl -L -o models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf \
  "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
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

### Phase 1 build fixes (new code)

Applied when pulling the Phase 1 code (Room DB + Feed + InputSheet):

1. **Room 2.6.1 → 2.7.0** — Kotlin 2.1.0 produces metadata v2.1.0; Room 2.6.1's bundled kotlinx-metadata-jvm only supports v2.0.0. Upgraded to Room 2.7.0 which handles v2.1.0 metadata.
2. **FTS5 MATCH → LIKE search in EntryDao** — The `search()` query used `WHERE entries MATCH :query` which requires an FTS5 virtual table that doesn't exist. Replaced with `LIKE '%' || :query || '%'` across rawText, summary, and category columns.
3. **Added `flatMapLatest` import** in FeedViewModel.kt.
4. **Added `material-icons-extended` dependency** — InputSheet uses `Icons.Default.Mic` and `Icons.Default.PhotoCamera` which live in the extended icons library (not in material-icons-core).

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