# Validation — Pocket AI 2.2.1 preview

- Baseline 2.2 release build and 19 unit tests passed before edits.
- Release/debug/instrumentation builds and lint passed after edits (1m 46s).
- 20 JVM tests, 0 failures; 12 Android emulator integration tests passed in 30.206s. New search test verifies newest matches beyond the result limit.
- Qwen3 0.6B full offline generation passed: 13 tokens, 2.2 tok/s, 5.8s generation; 16.427s total.
- SmolLM2 360M full offline generation passed: 2 tokens, 2.5 tok/s, 0.8s generation; 9.227s total.
- Release APK installed over existing app and cold-launched in 1.425s.
- APK: PocketAI-2.2.1-preview.apk, 52,599,356 bytes. SHA-256: 9eaf96c541f6c3d8f38fdaf227bd808d6f384b4a672ecdbf3e1d56ca9b72b1cf.
- Version code 6; version 2.2.1-preview; release variant; existing preview/debug signer. APK signature and 16 KB ZIP alignment passed. No AAB.
- Lint: zero errors, 77 warnings. Two Kotlin compile warnings: unnecessary assertion and deprecated memory callback constant.
- No physical iQOO, successful image generation, video backend, full newer-APK installation or sustained heat/low-storage qualification.
- Full audit, model tables, update security and 12-item roadmap: docs/IMPLEMENTATION-2.2.1.md.

---
# Validation — Pocket AI 2.2.0 preview

- Build passed: release, debug, instrumentation APKs, JVM tests and lint (1m 39s). Initial missing import was fixed before this passing build.
- 19 JVM tests and 11 emulator integration tests passed; integration duration 23.98s.
- New SmolLM2 360M: complete 386,404,992-byte download, SHA-256 verification, offline inference and response persistence passed in 201.736s including download. Output: 2 tokens, 3.8 tok/s, 0.5s. This is a smoke test, not a quality or sustained-speed benchmark.
- Existing Qwen3 0.6B: offline inference passed; 13 tokens, 6.9 tok/s, 1.9s generation; 11.276s test.
- Release APK install-over-existing and cold launch passed in 1.631s. Model manager visually reviewed; screenshot docs/pocket-models-2.2.png.
- APK: PocketAI-2.2.0-preview.apk, 52,594,456 bytes; versionCode 5; versionName 2.2.0-preview.
- SHA-256: 8eb66ae118397502116e650b87f6133f2e92161f8e699ad599acea00c0ec713d
- Release variant, not debuggable; previous preview/debug signing identity retained. Signature verification and 16 KB ZIP alignment passed. Native code unchanged; no AAB.
- Lint: 0 errors, 77 warnings. SDK XML tool-version warning remains.
- Five other new model choices: source metadata and compatibility policy validated; full inference not tested. All six remain experimental for physical-device use.
- Image generation remains unqualified; no video backend. No physical iQOO, long-chat thermal, newer-APK installation or full low-storage/reboot certification added.
- See docs/UPDATE-2.2.md for source links, new models, implemented improvements and ten next priorities.

---
# Validation — Pocket AI 2.1.1 preview

- Release/debug/instrumentation APK builds, 17 JVM tests and lint passed (2m 50s).
- 10 Android integration tests passed in 20.275 s, including full-transcript Unicode search and existing navigation, backup and update policies.
- Real Qwen3 0.6B offline generation passed: 13 tokens, 1.2 tok/s, 10.6 s generation; 22.784 s total test. Emulator timing varies and is not a phone benchmark.
- Release APK install-over-existing and cold launch passed (1.963 s).
- Version code 4; version 2.1.1-preview; release variant, not debuggable; original preview signing identity retained.
- APK: PocketAI-2.1.1-preview.apk, 52,584,352 bytes.
- SHA-256: 00cbfb3c452fe8eec5b2026fc4662ae12e13f6c8a08d5e95bd8ff395eb3e15e2
- Signature verification and 16 KB ZIP alignment passed. Native code unchanged from 2.1.0.
- Lint: 0 errors, 77 warnings. No AAB produced.
- No physical-device, successful diffusion, newer-APK installer or Qwen2.5 full-inference certification added. The limitations and physical checklist below still apply.
- See docs/RELIABILITY-2.1.1.md for patch details and docs/IMPLEMENTATION-2.1.md for the full platform report and roadmap.

---
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
