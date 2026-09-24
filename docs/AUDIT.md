# Pocket AI audit and implementation plan
Date: 2026-09-24. Target: iQOO Z10 Turbo Pro, 12 GB RAM.

## Original architecture
Kotlin app module with AppCompat/Material 3 Views built programmatically in a single Activity. One AndroidViewModel owns downloads, models, history and inference. A library module wraps llama.cpp through JNI/C++. AGP 8.13.2, Kotlin 2.3.0, compile/target 36, minimum 33, NDK 27, CMake 3.22.1, Java 17 bytecode. ARM64 and x86_64. GGUF Qwen3 0.6B Q8 and 4B Q4_K_M, revision- and SHA-pinned. HttpURLConnection HTTPS with Range resume. Models and one JSON conversation in noBackupFilesDir; small preferences in SharedPreferences. No database, background worker, service, navigation, or media runtime. INTERNET is the only requested user-facing permission. Android backup disabled. No analytics/accounts/cloud inference.

## Confirmed findings
- Download lifetime tied to ViewModel; no durable queue, foreground notification, network retry policy, or per-download state.
- A new chat clears the only stored transcript; no multiple-conversation history.
- History read errors silently become an empty conversation; users cannot distinguish corruption from no history.
- History is only saved after an operation. An interrupted generation can lose the current message/response.
- Native sample logs include formatted prompts and replies at INFO level.
- Native UTF-8 output goes through NewStringUTF (modified UTF-8), unsafe for supplementary Unicode such as emoji.
- Native cleanup uses runBlocking; the public API can block a caller. The singleton is not reset by destroy.
- Cancellation depends on a token being emitted; prompt evaluation is not promptly interruptible.
- Conversation context restoration concatenates old messages into the system prompt instead of preserving roles.
- Fixed generation parameters and no real performance settings.
- Native sample has context-position/shift complexity and failure paths needing explicit, recoverable errors.
- Unused sample MessageAdapter/layouts and arithmetic-only sample tests; no workflow tests.
- Interface rebuilds message views on structural changes; no navigation, onboarding, import, model compatibility gate, accessible generation actions, or selectable appearance.
- Storage and RAM checks are simplistic and there is no thermal guard.
- Build works as a debug artifact; no release signing configuration or physical-phone verification.
- No known evidence of a remote upload or hidden analytics.

## Prioritized implementation
1. Preserve model directory, app ID, signing identity and migrate legacy history. Record baseline build.
2. Separate SQLite conversation/media/download metadata, settings, model registry, compatibility and runtime control.
3. Fix JNI lifetime, cancellation, Unicode, prompt logging and role-aware context replay; add real sampling controls and thread profiles.
4. Durable foreground download queue with paused/interrupted recovery, HTTPS redirects, strict ranges, SHA verification and atomic completion.
5. Material navigation: Chat, Create, Models, Downloads, History, Settings. Add onboarding, accessible actions and appearance preferences.
6. Add curated Qwen 1.7B option, GGUF import validation, resource estimates, offline-only mode and storage tools.
7. Implement experimental SD 1.5 image backend using maintained stable-diffusion.cpp, bounded dimensions/batch size, cancellation, local gallery/export. Isolate native symbols. Do not claim phone-tested support.
8. Keep video unavailable: no validated mobile model/performance budget for this device. Include a capability-aware future extension path, with no misleading download/generate button.
9. Test pure logic, instrumented storage/download/UI flows where possible, build and inspect APK/native dependencies. Document physical-device gaps and release blockers.

## Architecture decisions
Keep Material Views and llama.cpp: replacing them with Compose or another text engine is unnecessary. Use Android SQLiteOpenHelper with transactions to avoid adding a Room compiler/toolchain dependency. Keep HTTP streaming instead of adding a network framework. A user-started dataSync foreground service owns a persisted sequential download queue; timeouts pause work, process restarts restore state, force-stop requires reopening. Run all file/database/native work off Main. Use a single native inference owner to prevent overlapping loads and unload text weights before image work. Resource estimates are conservative guidance, not measured device claims.

## Sources reviewed
- https://github.com/ggml-org/llama.cpp
- https://github.com/leejet/stable-diffusion.cpp
- https://developers.google.com/edge/mediapipe/solutions/vision/image_generator/android (deprecated; not chosen)
- https://developer.android.com/develop/background-work/services/fgs/timeout

Baseline result: assembleDebug --offline passed in 4m 26s. Warnings: SDK XML version mismatch; missing optional ccache. No baseline compile errors.
