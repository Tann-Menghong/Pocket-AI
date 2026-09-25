# Pocket AI 2.1.0 preview — implementation report

This is a working incremental upgrade, not production certification or completion of every requested feature. Target-phone validation remains required. The baseline 2.0 audit and historical results remain in AUDIT.md and IMPLEMENTATION_REPORT.md.

## Audit and retained architecture
Baseline build and 11 existing unit tests passed before changes. Kotlin/Material Views, AppCompat and a lifecycle ViewModel drive the UI. SQLite stores private JSON records; SharedPreferences stores settings. A foreground service owns the persistent sequential model download queue. The lib module wraps pinned llama.cpp CPU inference; diffusion isolates pinned stable-diffusion.cpp CPU inference. Minimum Android 13/API 33, target 36, Java 17, ARM64 and x86_64. HTTPS model access, SHA-256 checks, private storage and no account/telemetry/cloud inference remain.

Confirmed issues addressed: image model selection was fixed to one model; deletion could release the inference mutex before deleting model files; export cache was not cleared; list browsing deserialized full conversations; navigation selection could disagree with the displayed page; Qwen3-specific prompt suffix was applied to other architectures; assistant metadata, discovery, backups and app updates were absent.

## Changes actually implemented
- Home dashboard with configurable sections, recent chats, favorite models, recommendations, storage and quick actions. Five main destinations plus Settings and Downloads.
- Custom assistant profiles (system prompt, sampling, description, icon and preferred model), including editable Khmer/English tutor presets. A persona does not guarantee model language proficiency.
- Prompt library with categories, favorites, duplication, editing and placeholder forms. Filling a prompt creates a draft, not an automatic request.
- Conversation archive, compact transactional summary index and full conversation retrieval on demand. Existing chat/edit/regenerate/stop/export capabilities retained.
- Markwon Markdown and table rendering for completed responses, basic code keyword highlighting; streaming stays lightweight.
- Seven pinned chat choices and two SD 1.5 quantizations. Favorites, filters, details, hardware estimates and selected image-model handling.
- Hugging Face API search/repository inspection with architecture/quantization/file checks. Only eligible single-file GGUF candidates can be registered; imported/discovered models remain experimental.
- Sequential download state filtering; final text GGUF architecture must match registered metadata. Existing resumable verified downloads retained.
- Opt-in scheduled app and text-model revision checks; safe manual app installation. Old model files are retained until explicit deletion.
- Bounded local JSON backup/restore for chats, settings, prompts and assistants. Restore merges with new IDs and validates types/limits; it cannot enable networking or restore transient update state.
- Theme density/radius/reset, organized Settings, image-setting persistence, measured memory/battery/thermal panel, native model-loading progress, image elapsed time and first-run onboarding.
- Model deletion holds the inference lock through unload and file deletion; export-cache cleanup corrected.

## Chat model support
Decimal GB; RAM is a conservative catalog base estimate, not a measured peak. Context and current available RAM also affect admission. All target-phone compatibility is estimated.

| Model | Download GB | Format / quantization | Purpose | Base RAM GB / recommended device GB | Device compatibility | Tested |
|---|---:|---|---|---:|---|---|
| Qwen3 0.6B | 0.639 | GGUF Q8_0 | Fast general chat | 1.5 / 4 | Estimated compatible | x86_64 offline generation passed |
| Qwen3 1.7B | 1.834 | GGUF Q8_0 | General chat | 2.8 / 6 | Estimated compatible | No full inference test |
| Qwen3 4B | 2.497 | GGUF Q4_K_M | General quality | 3.5 / 8 | Estimated compatible | No full inference test |
| Qwen2.5 0.5B Instruct | 0.491 | GGUF Q4_K_M | Small/fast | 1.3 / 3 | Experimental | See validation evidence |
| Qwen2.5 1.5B Instruct | 1.117 | GGUF Q4_K_M | Writing/multilingual | 2.2 / 5 | Experimental | Metadata verified only |
| Qwen2.5 Coder 1.5B | 1.117 | GGUF Q4_K_M | Coding | 2.2 / 5 | Experimental | Metadata verified only |
| Qwen3 8B | 5.028 | GGUF Q4_K_M | Advanced reasoning | 7.5 / 12 | Experimental; high memory/heat risk | Metadata verified only |

Exact immutable revisions, hashes, licenses and sizes: android/app/src/main/assets/catalog.json. Khmer quality is unmeasured.

## Image and video support
| Model | Download GB | Type | Resolution | Base RAM GB | Compatibility | Tested |
|---|---:|---|---|---:|---|---|
| SD 1.5 Q4_0 | 1.747 | GGUF diffusion, CPU | Start at 256; up to supported 512 | 5.5 | Experimental; minimum 10 GB device RAM | JNI build/load/error path only |
| SD 1.5 Q8_0 | 1.881 | Same checkpoint, higher quantization | Start at 256; up to supported 512 | 5.8 | Experimental; minimum 10 GB device RAM | JNI build/load/error path only |

These are two quantizations, not two distinct image architectures or styles. Successful image generation, cancellation under load and peak memory are not verified. No GPU/NPU acceleration claim is made. Video has an unavailable/experimental UI boundary and no runnable backend; desktop video models are not offered as working downloads.

## App update security
Trusted source: GitHub releases in Tann-Menghong/Pocket-AI. Checks require a newer numeric/ prerelease-aware version, an eligible APK asset and GitHub SHA-256 digest. Preview channel and background checking are configurable. Android DownloadManager transfers the APK; installation rechecks file size, checksum, package name, strictly higher versionCode and identical signing-certificate sets. Android's unknown-source permission and normal package installer remain mandatory. No silent installation.

Play-origin installs are directed to Play; Play Core integration is not included. Scheduled jobs resume scheduling on app launch after reboot. Notifications require Android notification permission. Network checks and transfers are blocked/cancelled by offline-only mode. App updates are distinct from manual text-model revision upgrades; models are never silently replaced.

## Performance and testing
See VALIDATION.md for final run results and artifact identity. Original Qwen3 0.6B emulator run: 13 output tokens, 4.8 tok/s, 2.7 seconds generation; total test 11.657 seconds with weights already installed. This is one short x86_64 emulator run, not a phone benchmark, quality evaluation or battery measurement.

Tested: baseline build; release/debug compilation; unit policy tests; Android navigation and recreation; SQLite/summary persistence; backup roundtrip and malformed input; real HF/GitHub metadata access; update checksum/same-version rejection; JNI invalid-file handling; original-model offline generation.

Partially tested: resumable downloads, memory policy, appearance, update workflow, model import and discovery. An eligible newer APK installer flow has not been completed end to end. No physical iQOO device was connected.

Required phone tests: upgrade without uninstalling; all model loads/switches; airplane-mode chat; long transcripts; background/network/process interruption; low RAM/storage; image generation/cancel; heat and battery; large fonts/TalkBack; unknown-source denial/installer cancellation; daily scheduling and soak testing.

## Known limitations
- Preview signing identity retained for existing-install upgrades; it is not a private production release key.
- Conversation summary browsing reduces payload memory, but full conversation editing still uses JSON records. Search covers titles/recent summary content, not a full-text index of every historical message.
- Backup is plaintext, maximum 32 MB, and excludes model weights and generated media.
- Repository discovery supports a conservative subset of single-file text GGUFs, not arbitrary Hugging Face models. Model revision checks currently cover text only.
- Image backend and both image options remain unvalidated for successful generation on the target phone. No local video engine.
- No chat branches/folders, biometric lock, encrypted backup, comprehensive syntax parser, speech, RAG or store-specific update API yet.
- Remaining lint warnings include localization, style, platform/dependency advice and existing refresh/storage patterns.
- “Release variant” does not imply production-ready hardware validation.

## Next recommended versions
| Feature | User benefit | Difficulty | RAM/storage impact | Priority | Version |
|---|---|---|---|---|---|
| Physical ARM64 benchmark/soak suite | Establish safe model defaults | High | Temporary model storage, measured RAM | P0 | 2.1 stable |
| Private production signing plan | Secure long-term distribution | Medium | Negligible | P0 | Before public stable |
| SD 1.5 phone qualification | Make image support reliable | High | 2 GB weights; several GB RAM | P0 | 2.1 stable |
| Installer end-to-end upgrade suite | Prevent broken updates | Medium | Two APKs temporarily | P0 | 2.1 stable |
| Encrypted backups | Protect exported private chats | Medium | Small crypto buffers | P1 | 2.2 |
| SQLite message tables/FTS paging | Search all chats and scale histories | High | Index storage, lower browse RAM | P1 | 2.2 |
| Accessibility/localization audit | Better large-font and Khmer usability | Medium | Small string resources | P1 | 2.2 |
| User-controlled model benchmarks | Personalize speed recommendations | Medium | Short CPU/battery workload | P1 | 2.2 |
| Durable reboot scheduling | More reliable opted-in updates | Low | Negligible | P1 | 2.2 |
| Image-model revision tracking | Safer diffusion upgrades | Medium | Metadata only until confirmed | P1 | 2.2 |
| Biometric app lock | Protect local library access | Medium | Negligible | P2 | 2.3 |
| Chat folders and branches | Organize and explore alternatives | Medium | Additional transcript storage | P2 | 2.3 |
| Verified GPU backend experiment | Potentially faster image/text inference | High | Backend buffers/binary growth | P2 | 2.3 experimental |
| Opt-in encrypted media backup | Preserve generated artwork | Medium | User-selected media size | P2 | 2.3 |
| Local document/RAG pipeline | Ask questions about private documents | High | Embedding model and index | P2 | 3.0 |
| Offline speech input/output | Hands-free private interaction | High | Additional speech weights/RAM | P2 | 3.0 |

## Primary references
- Hugging Face documented API: https://huggingface.co/docs/hub/api
- GitHub releases API and asset digest: https://docs.github.com/en/rest/releases/releases
- Android DownloadManager: https://developer.android.com/reference/android/app/DownloadManager
- Android package metadata/signers: https://developer.android.com/reference/android/content/pm/PackageManager
- Android installation: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Markwon: https://noties.io/Markwon/docs/v4/
