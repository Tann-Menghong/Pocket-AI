# Pocket AI 2.2 preview

Private Android AI for the iQOO Z10 Turbo Pro (12 GB RAM). Android 13+, ARM64; x86_64 included for emulator QA. No account, API key, paid service, analytics, or cloud inference.

## Use
1. Install the preview APK over the existing version to preserve local data. Do not uninstall first.
2. Open Models and start with Qwen3 0.6B (639 MB). Downloads default to Wi-Fi, continue through a foreground service, and verify SHA-256 before installation.
3. Load the model in Chat. Internet is unnecessary after installation. Settings includes an offline-only mode.
4. Use Library to reopen conversations. New Chat preserves previous conversations.
5. Create → Image provides experimental local SD 1.5 generation. Download is about 1.75 GB; memory estimates are conservative and performance is unvalidated on the target phone. Video is unavailable because no validated mobile backend is enabled.

## New in 2.2
Six more free downloads (15 catalog choices), SmolLM2 English models, tiny coding model, size/RAM filters, sorting, better search and a free-model guide. [Models, changes and 10 next improvements](docs/UPDATE-2.2.md).

## Reliability patch 2.1.1
Full-transcript global search, bounded startup recovery, corrected model revision handling and cached update results. See [patch validation](docs/RELIABILITY-2.1.1.md).

## New in 2.1
Home dashboard, assistants, prompt templates, nine catalog options, Hugging Face discovery, model favorites, local backup/restore, archive, improved Markdown and secure opt-in update handling. See [the implementation report](docs/IMPLEMENTATION-2.1.md) for tested scope and limitations.

## Features
- Chat history, rename, pin, search, edit/resend, regenerate, stop, continue, copy, export and share.
- Curated, revision-pinned model catalog; compatibility estimates; GGUF import; integrity checks; deletion and storage details.
- Persistent download queue, pause/resume/cancel/retry, speed, ETA, HTTPS, strict range checks, SHA-256 and safe temporary files. Restarted downloads are paused for explicit resumption.
- Sampling controls, system prompts, editable presets, real CPU thread profiles, memory and thermal guards.
- Local image backend, bounded settings, private gallery, prompt reuse, favorites, save/share and delete.
- System/light/dark/AMOLED themes, accents, text size, spacing, Markdown emphasis/code highlighting, privacy/storage/device settings.

## Build
Clone with submodules: git clone --recurse-submodules <repository-url>

Open android in Android Studio, or run ./build.ps1 from PowerShell. Requires SDK 36, NDK 27.0.12077973, CMake 3.22.1 and Android Studio Java. Dependencies require internet on the first build. Use -Offline once cached, -Lint for Android lint. Models are never embedded in the APK.

Modules: app (UI/data/downloads/compatibility), lib (llama.cpp JNI text runtime), diffusion (isolated stable-diffusion.cpp JNI image runtime). Native revisions are pinned as Git submodules; KleidiAI is in vendor.

See docs/AUDIT.md, docs/IMPLEMENTATION-2.1.md and VALIDATION.md for evidence, limitations and pending phone tests. This is a release-variant preview signed with the existing debug identity, not a production-certified or Play Store release.

## Sources
- [llama.cpp](https://github.com/ggml-org/llama.cpp), MIT
- [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp), MIT
- [Qwen GGUF models](https://huggingface.co/Qwen), Apache-2.0
- [SD 1.5 GGUF](https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF), CreativeML Open RAIL-M; review bundled model license

Exact model revisions, sizes and checksums: android/app/src/main/assets/catalog.json. Licenses are bundled and accessible in Settings.
