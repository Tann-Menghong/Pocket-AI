# Validation — Pocket AI 2.1.0 preview

## Build and artifact
- Final assembleRelease, testDebugUnitTest and lintDebug: passed, 1 minute 16 seconds.
- Debug APK and Android test APK built successfully in the preceding QA build.
- Package com.offlinepocket.ai; versionCode 3; versionName 2.1.0-preview; minimum SDK 33; target SDK 36; ARM64 and x86_64.
- PocketAI-2.1.0-preview.apk: 52,579,348 bytes.
- SHA-256: 7945589c428498d2fa98a95608451b68086042b076c051067dea5a95d827b822
- Release variant, not debuggable. Signed with the existing preview/debug certificate to retain upgrade compatibility; NOT a private production signing key.
- Certificate SHA-256: 18a834d0d17f06731404aea4211f808407f130737effa6d6f6a3b4c896788bab
- APK signature and 16 KB ZIP alignment passed. All 23 packaged native libraries have 16 KB LOAD-segment alignment; see docs/native-alignment-2.1.json.
- No AAB produced.
- Lint: 0 errors, 82 warnings. Warnings include hardcoded/localization strings, existing refresh/storage patterns, platform/dependency advice and minor style. No blanket suppression added.

- Release APK install-over-existing and cold launch passed (1.579 seconds emulator launch).

## Tests passed
- 16 JVM tests: HTTP range/status safety, memory/context/ABI/video admission, CPU profiles, generation budget, invalid model file, update numeric/prerelease ordering, trusted asset URL, package/signature/version policy and prompt placeholders.
- 9 Android integration tests, API 35 x86_64 emulator with 4 GB RAM, 19.699 seconds: SQLite/settings, eleven-screen navigation and recreation, JNI safe errors, download controls, summary/full-chat consistency, backup roundtrip/validation, archive/assistant metadata, installed-APK checksum/version rejection and live repository metadata/offline gate.
- Real Qwen3 0.6B offline generation passed with the complete hash-verified model: 13 tokens, 4.8 tok/s, 2.7 seconds generation; 11.657 seconds total test. Existing downloaded weights reused.
- Home screen visually reviewed; missing quick actions found and corrected. Screenshot: docs/pocket-home-2.1.png.
- Historical 2.0 baseline also exercised fresh onboarding, legacy history migration and a complete Qwen3 download. These are historical results, not repeated phone certification.

## Partial or not tested
- Qwen2.5 0.5B full-model test FAILED its 600-second download deadline (603.358 seconds total). The transfer was progressing but incomplete; no inference was attempted. This is not a passed model test. Metadata and partial transfer only are verified.
- Larger chat models: metadata/format policy only; no full inference.
- Image generation: both CPU JNI builds load and reject invalid inputs; successful diffusion generation and cancellation under load not tested.
- Video: unavailable; no generation runtime implemented.
- App updates: live GitHub checks and rejection policies tested. Actual newer signed APK download plus Android user-confirmed installation not exercised end to end.
- Model updates: metadata checks implemented; actual changed upstream weight replacement not exercised.
- Backup restore bounded/validated in instrumentation; SAF chooser and reinstall/restore need physical-device QA.
- UI themes/accessibility, network loss, force-stop/reboot recovery, low storage, sustained memory pressure, long histories and thermal/battery behavior require broader tests.

## Physical iQOO Z10 Turbo Pro checklist
1. Install over the previous app without uninstalling; verify existing history and model files.
2. Pause/resume and interrupt model download; background/restart the app and reconnect Wi-Fi.
3. Run airplane-mode chat; test stop, retry, edit, regenerate, continue and model switching.
4. Measure first-token latency, output speed, peak RAM and battery/thermal behavior at several context sizes.
5. Validate SD 1.5 at 256 pixels before 512; test cancellation, gallery, save/share and memory recovery.
6. Exercise low storage, corrupt files, unsupported imports and permission denial.
7. Review light/dark/AMOLED, large fonts, TalkBack and rotation.
8. Test backup/export/restore, update installer cancellation and opt-in scheduled checks.
9. Complete a sustained-use soak before calling this production-ready.

See docs/IMPLEMENTATION-2.1.md for the audit, implemented features, model tables, limitations and 16-item roadmap.
