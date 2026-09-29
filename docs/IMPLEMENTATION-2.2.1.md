# Pocket AI 2.2.1 preview — implementation and QA

## Project audit
Pocket AI uses Kotlin Material Views, an AndroidViewModel, SQLite JSON records with compact conversation summaries, SharedPreferences settings, a sequential foreground download service, pinned llama.cpp CPU text JNI and isolated stable-diffusion.cpp CPU JNI. Android 13+, SDK target 36; ARM64 target phone, x86_64 emulator QA. Prior audit and earlier fixes: [2.1 report](IMPLEMENTATION-2.1.md). This 2.2.1 pass started from a successful 2.2 release build and 19 unit tests.

## Confirmed bugs and changes
- Full-transcript search previously stopped after the first matching database IDs, so 15 old conversations could hide newer matches. It now scans all matches and keeps a bounded newest-first heap. A regression test inserts 24 matches and requests the newest five.
- Library search previously inspected only the compact last-two-message summaries. It now searches full transcripts on a background worker after a short typing delay. Search remains linear and caps visible full-text matches at the newest 100.
- Library and gallery previously built all cards at once. They now show 50 at a time with Show more controls. Gallery previews decode at one-quarter resolution rather than one-half resolution.
- Model-loading admission and the model browser now share one rule reserving 512 MB of available RAM for Android after estimated model/context use. This is an estimate, not a measured peak.
- The Device & runtime screen showed a stale hard-coded 2.1 version. It now reads the installed package version.

## Current platform
Home, Chat, Create, Models, Library, Settings, Downloads, assistant presets, prompt library, local backup, Hugging Face compatible single-file text GGUF discovery, source-pinned model catalog, optional app/model update checks, offline-only setting, storage and hardware details remain. No account or paid AI API is required. Video has no runnable backend.

## Supported chat catalog
RAM is a base estimate at 2,048 tokens; context length adds memory. Compatibility is estimated for the 12 GB iQOO until physical tests. Verified emulator inference is not phone qualification.

| Model | Download | Format | Purpose | Base RAM | Device status | Tested |
|---|---:|---|---|---:|---|---|
| Qwen3 0.6B | 0.64 GB | GGUF Q8_0 | Chat, Writing, Translation, Multilingual | 1.5 GB | Estimated compatible | Emulator offline inference |
| Qwen3 1.7B | 1.83 GB | GGUF Q8_0 | Chat, Study, Writing, Summarization, Translation | 2.8 GB | Estimated compatible | Metadata/policy only |
| Qwen3 4B | 2.50 GB | GGUF Q4_K_M | Chat, Coding, Reasoning, Writing, Multilingual | 3.5 GB | Estimated compatible | Metadata/policy only |
| Qwen2.5 0.5B Instruct | 0.49 GB | GGUF Q4_K_M | General, Small & Fast, Writing, Translation, Multilingual | 1.3 GB | Experimental | Metadata/policy only |
| Qwen2.5 1.5B Instruct | 1.12 GB | GGUF Q4_K_M | General, Writing, Study, Translation, Multilingual | 2.2 GB | Experimental | Metadata/policy only |
| Qwen2.5 Coder 1.5B | 1.12 GB | GGUF Q4_K_M | Coding, Debugging | 2.2 GB | Experimental | Metadata/policy only |
| Qwen3 8B — Advanced | 5.03 GB | GGUF Q4_K_M | General, Reasoning, Coding, Multilingual, Experimental | 7.5 GB | Experimental | Metadata/policy only |
| SmolLM2 360M Instruct | 0.39 GB | GGUF Q8_0 | Chat, English, Small & Fast, Writing | 1.2 GB | Experimental | Emulator offline inference |
| SmolLM2 1.7B Instruct | 1.06 GB | GGUF Q4_K_M | Chat, English, Writing, Summarization | 2.3 GB | Experimental | Metadata/policy only |
| Qwen2.5 Coder 0.5B | 0.49 GB | GGUF Q4_K_M | Coding, Debugging, Small & Fast | 1.4 GB | Experimental | Metadata/policy only |
| Qwen3 4B Q5 | 2.89 GB | GGUF Q5_K_M | Chat, Coding, Reasoning, Multilingual | 4.1 GB | Experimental | Metadata/policy only |
| Qwen3 4B Q6 | 3.31 GB | GGUF Q6_K | Chat, Coding, Reasoning, Multilingual | 4.6 GB | Experimental | Metadata/policy only |
| Qwen2.5 1.5B Instruct Q8 | 1.89 GB | GGUF Q8_0 | Chat, Writing, Translation, Multilingual | 3.0 GB | Experimental | Metadata/policy only |

## Image catalog
Both entries are quantizations of one SD 1.5 checkpoint, not independently validated architectures. The CPU runtime has not completed a successful generation test on the iQOO.

| Model | Download | Type | Resolution | Base RAM | Device status | Tested |
|---|---:|---|---|---:|---|---|
| Stable Diffusion 1.5 | 1.75 GB | SD 1.5 GGUF Q4_0 | 256–512 px | 5.5 GB | Experimental, 10 GB+ device RAM | JNI errors only |
| Stable Diffusion 1.5 Q8 | 1.88 GB | SD 1.5 GGUF Q8_0 | 256–512 px | 5.8 GB | Experimental, 10 GB+ device RAM | JNI errors only |

## App and model updates
App updates check GitHub releases from Tann-Menghong/Pocket-AI. Before launching the normal Android installer, the app validates HTTPS source, GitHub digest, complete file size, package identity, greater versionCode and matching signer. Installation requires the user and Android permissions. Text-model revision checks are separate and require explicit download; older weights remain until deleted.

## Testing and performance
- Baseline 2.2: release build and unit tests passed before edits.
- 2.2.1 release, debug, instrumentation builds and lint passed in 1m 46s. 20 JVM tests; 12 Android emulator integration tests in 30.206s.
- Qwen3 0.6B offline generation: 13 tokens, 2.2 tok/s, 5.8s generation. SmolLM2 360M: 2 tokens, 2.5 tok/s, 0.8s generation. These are short x86_64 emulator runs with previously installed verified weights and are not phone benchmarks.
- Release APK installed over an existing app and cold-launched in 1.425s in the emulator. Signature and 16 KB ZIP alignment passed.
- APK: PocketAI-2.2.1-preview.apk, 52,599,356 bytes; SHA-256 9eaf96c541f6c3d8f38fdaf227bd808d6f384b4a672ecdbf3e1d56ca9b72b1cf. Version code 6, version 2.2.1-preview. Lint: 0 errors, 77 warnings. No AAB.

## Known limitations and phone QA
No iQOO Z10 Turbo Pro is attached. The five other recently added models have metadata and compatibility checks but no complete inference test. Image generation is experimental and unqualified; video is unavailable. Full app update installation, prolonged heat/battery measurement, low-storage and reboot recovery, accessibility and sustained large-library performance require physical-device QA. Search is a linear JSON scan and visible results are limited to the newest 100. Backup is local plaintext and limited to 32 MB. The release variant retains the existing preview/debug signing identity for in-place upgrades; it is not a production certificate.

## Next recommended updates
These are proposals, not shipped features.

| Priority | Feature | User benefit | Difficulty | RAM/storage impact | Priority level | Version |
|---:|---|---|---|---|---|---|
| 1 | Physical iQOO benchmark wizard | Measure real speed and peak memory | Medium | Small logs; temporary model RAM | High | 2.3 |
| 2 | Indexed transcript search | Fast full history search | Medium | Small search index, lower transient RAM | High | 2.3 |
| 3 | Cancellation-aware search | Stop obsolete searches during typing | Low | Negligible | High | 2.3 |
| 4 | Model context ceilings | Prevent oversized KV caches | Medium | Can reduce RAM | High | 2.3 |
| 5 | Interrupted-download test suite | Catch resume and network regressions | Medium | Test scratch space only | High | 2.3 |
| 6 | Thermal-aware thread control | Reduce heat during sustained inference | Medium | Negligible storage; may reduce speed | High | 2.3 |
| 7 | Khmer quality evaluation | Choose multilingual models using evidence | Medium | Small text set | High | 2.3 |
| 8 | Download notification polish | Clearer queue, speed and errors | Low | Negligible | Medium | 2.3 |
| 9 | Encrypted local backups | Protect exported conversations | High | Backup-sized storage | Medium | 2.4 |
| 10 | Image backend qualification | Reliable local generation limits | High | Several GB; high RAM | Medium | 2.4 |
| 11 | Accessible gallery and TalkBack QA | Improve usability for large text and screen readers | Medium | Negligible | Medium | 2.4 |
| 12 | Production signing migration | Trusted stable upgrades | High | No runtime RAM | High | Stable |
