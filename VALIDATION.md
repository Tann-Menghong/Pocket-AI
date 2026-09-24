# Validation — Pocket AI 2.0 preview

## Successful checks
- Original unmodified baseline: assembleDebug passed before implementation.
- Final preview: assembleDebug, testDebugUnitTest, lintDebug and assembleDebugAndroidTest passed (1m 25s incremental validation run).
- 11 JVM safety regression tests passed: matching/mismatched HTTP ranges and totals, HTTP failure, impossible memory, unsupported video/32-bit ABI, context memory growth, CPU profile limits, invalid generation budget and non-GGUF input.
- Opt-in full-model test passed on the same emulator (110.473 seconds including download): complete 639,446,688-byte Qwen3 0.6B download, SHA-256 verification, real text generation with offline-only mode enabled, nonempty complete response/statistics and persisted history.
- Four Android integration tests passed on API 35 x86_64 PocketAI_QA with 4 GB RAM (16.636 seconds): SQLite Unicode roundtrip/settings persistence; six-screen navigation and recreation; both JNI libraries link and reject missing model files; live pinned-host model download pause/resume/cancel with partial-file cleanup.
- An initial live download test on the default 2 GB emulator was correctly blocked by the compatibility policy. The emulator was resized to 4 GB for the transfer test; this was not a model-host failure.
- Upgrade over the baseline APK passed: both messages in a seeded legacy chat.json were migrated and displayed in the new library; original file retained.
- Clean startup and first-run onboarding verified. Light-theme screen reviewed visually; button/status-bar contrast corrected.
- APK signature verification passed; the signing certificate matches the original APK. APK ZIP alignment passed. All packaged native LOAD segments use at least 16 KB alignment; see docs/native-alignment.json.
- Final APK manifest: package com.offlinepocket.ai, versionCode 2, versionName 2.0-preview, min SDK 33, target SDK 36, ARM64 and x86_64. No broad storage/camera/microphone permission. No models embedded.
- Lint: 0 errors, 56 warnings. Remaining categories include English hardcoded strings/localization, newer tool/dependency versions, RecyclerView full refresh, conservative usable-space API, minor style and unused-resource recommendations. They remain visible; no blanket lint suppression was added.

## Artifact
- PocketAI-v2-preview.apk — debug variant, 59,380,280 bytes (59.38 MB).
- SHA-256: 0b973958f73dc4b2a0e2708018fbadf6723ac01a5f3ba9298d4b294d1ca50dc5
- Debug signing certificate SHA-256: 18a834d0d17f06731404aea4211f808407f130737effa6d6f6a3b4c896788bab
- AAB/release-signed build: not produced. Production keystore/Play delivery is not configured.

## Partially tested
- Download network path/range resume/state persistence: real bytes transferred and resumed; complete larger-model SHA verification, loss of connectivity, Android service timeout, reboot and low-space exhaustion still need end-to-end tests.
- Native backends: compiled for both ABIs; x86_64 loaded and checked for safe missing-file errors. Qwen3 0.6B successfully generated text with full weights on x86_64. ARM64 inference, larger text models and successful image generation remain unvalidated.
- Memory protection: policy boundaries unit-tested, and low-memory emulator admission block observed. Real peak RAM, native OOM behavior and phone thermal performance are unmeasured.
- Themes: light contrast reviewed; dark/AMOLED and maximum font scale need a complete visual/accessibility pass.

## Required physical iQOO Z10 Turbo Pro tests
1. Install over previous version without uninstalling; check existing models and history.
2. Download/verify starter model, pause/resume, interrupt Wi-Fi, background/foreground, force-stop/reopen, resume after restart.
3. Airplane-mode text generation, stop during load/prompt/response, long histories, Unicode, edit/regenerate/continue, export/share and rapid model switching.
4. Measure first-token latency, tokens/sec, peak RAM, battery/thermal behavior for all three text models and thread profiles.
5. Validate a real 256px SD 1.5 image before increasing steps/resolution; test cancellation, gallery/save/share and memory recovery. Do not claim this model is phone-qualified before this succeeds.
6. Exercise low-storage, corrupted files, import, model/media deletion, background service limits, rotation, permission denial, TalkBack and large fonts.
7. Run a multi-hour soak before production signing/release.

No physical phone was connected. This is a functional preview with test evidence, not a claim of production readiness. See docs/IMPLEMENTATION_REPORT.md for implemented scope and remaining limitations.
