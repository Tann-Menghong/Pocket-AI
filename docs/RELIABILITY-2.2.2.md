# Pocket AI 2.2.2 preview — update and memory QA

## Audit and fix

The existing Kotlin/Material Views app uses SQLite for local conversations, SharedPreferences for settings, a foreground resumable download service, llama.cpp for local GGUF chat, and an experimental stable-diffusion.cpp image backend. The complete architecture, model tables, security review and 12-item roadmap are in [the 2.2.1 implementation report](IMPLEMENTATION-2.2.1.md). This patch keeps the working features and model catalog intact.

Android's ordinary `TRIM_MEMORY_UI_HIDDEN` callback has value 20. Pocket AI previously treated every value at or above the deprecated running-low value 10 as a memory emergency. Opening the Android update permission screen or simply sending the app Home then returning showed an incorrect memory-pressure dialog. The new policy accepts only running-low trim levels below `UI_HIDDEN`; the view model warns only while generation is active or a model is loaded. A unit boundary test and an Android activity test cover the normal UI-hidden callback. Android 14+ does not deliver the old running-low callbacks, so model-load RAM admission remains the main protection.

## App update workflow

In an isolated API 35 emulator, the published 2.2.0 preview checked GitHub, found 2.2.1, downloaded the full APK, verified its hash, package, newer version and signer, requested Android's source permission, and launched the normal package installer. The user-confirmed system update completed to versionCode 6; the existing onboarding state survived. This is an emulator end-to-end result, not a physical-phone result. Version 2.2.2 was then installed over 2.2.1 in the emulator for regression QA. The update source and verification policy are unchanged.

## Validation

- Release, debug and Android-test APKs built; lint passed with 0 errors and 77 existing warnings.
- 21 JVM tests passed. The Android suite ran 14 tests: 13 passed; the live Hugging Face metadata check failed because the emulator could not resolve `huggingface.co`. Both QA emulators independently reported unknown host. The 13 offline-capable tests then passed in a clean rerun, including the new UI-hidden regression.
- 2.2.2 release installed over 2.2.1, retained setup, opened Home, and did not show a memory warning after Home/background/resume. The 2.2.1 build reproduced the warning with the same sequence.
- Release APK: `PocketAI-2.2.2-preview.apk`, 52,600,280 bytes, SHA-256 `cac5d4ed67d86b45ee3314fe0dd809afbf331ea585d3da314d0b126d146c7f74`; versionCode 7. APK signature and 16 KB ZIP alignment passed. It uses the existing preview/debug signing identity to preserve upgrades; this is not a production certificate.

## Remaining limits

No iQOO Z10 Turbo Pro is attached. This patch did not add new models or qualify image generation. The 13 chat choices and two experimental SD 1.5 quantizations have the same status as the [model tables](IMPLEMENTATION-2.2.1.md#supported-chat-catalog). Only Qwen3 0.6B and SmolLM2 360M have complete short offline emulator inference runs. Video generation has no backend. Live model metadata, phone thermal/battery behavior, low storage, reboot recovery, image generation and sustained use need further tests. Stable release signing still needs a planned key migration.
