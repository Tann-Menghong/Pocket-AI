# Pocket AI 2.0 preview implementation report

## Audit
The original Kotlin/Material Views app used one ViewModel, one JSON transcript and llama.cpp JNI. The baseline APK built before changes. Confirmed issues included foreground-only download lifetime, loss of previous chats on New Chat, incomplete interruption recovery, unsafe native Unicode conversion, prompt/output logging, blocking native cleanup, and fixed sampling controls. Full details and the original prioritized plan are in AUDIT.md.

## Implemented changes
- Preserved package identity, private model directory, original model choices and upstream llama.cpp. Added transactional migration of the original transcript to a private SQLite library.
- Separated registry/compatibility, persistent metadata/settings, foreground downloads, UI and native runtimes. App-owned text runtime serializes inference; image and text work share a mutual-exclusion guard.
- Added Chat, Create, Models, Downloads, History and Settings navigation; onboarding; useful empty/error/progress states; accessible text controls; appearance settings and light/dark/AMOLED themes.
- Multiple chats, rename/delete/pin/search, clear, edit/resend, retry/regenerate/continue/stop, copy, Markdown export/share, model selection and generation statistics.
- Role-aware chat templates with token-budget trimming, UTF-8 streaming, cancellation during prompt evaluation, local recovery snapshots, no prompt logging, sampling parameters, system prompts, stop sequences, seed and editable presets.
- Curated four-model catalog with pinned revisions and SHA-256; GGUF import header/architecture validation; explicit experimental status; estimated memory, storage and ABI checks. No arbitrary Hugging Face compatibility claims.
- Durable sequential foreground downloads, pause/resume/cancel/retry, speed/ETA, strict HTTPS redirects/range validation, duplicate prevention, low-space checks, SHA verification, atomic completion and restart recovery. Paused records cannot be overwritten by stale progress updates.
- CPU thread profiles, thermal checks, context-sensitive memory estimates, model unloading and recovery messages. No GPU/NPU claims.
- Real isolated stable-diffusion.cpp image JNI backend, SD 1.5 GGUF, 256–512 pixel bounds, 1–30 steps, negative prompts, CFG, seed, sequential batches up to three, progress/cancel, private PNG gallery and explicit sharing. Experimental until real generation is validated on the target phone.
- Video creation displays an honest unsupported state; no video weights or fake generation. Capability metadata separates text/image/video for future backend additions.
- Storage categories, model/media deletion, share-cache cleanup, Wi-Fi/offline-only controls, device/runtime details, bundled licenses, no analytics/account/cloud services.

## Model support
Sizes are decimal download sizes. RAM is an estimate of runtime working memory, not a measured peak. Text estimates below use the default 4,096-token context; Android and other apps require additional RAM.

| Model | Type | Size | Format | Estimated runtime RAM | iQOO 12 GB compatibility | Status |
|---|---|---:|---|---:|---|---|
| Qwen3 0.6B | Chat | 639 MB | GGUF Q8_0 | 1.83 GB | Estimated compatible | Curated; x86_64 inference tested, phone untested |
| Qwen3 1.7B | Chat | 1.83 GB | GGUF Q8_0 | 3.13 GB | Estimated compatible | Curated; phone inference untested |
| Qwen3 4B | Chat | 2.50 GB | GGUF Q4_K_M | 3.83 GB | Estimated compatible | Curated; phone inference untested |
| Stable Diffusion 1.5 | Image | 1.75 GB | GGUF Q4_0 | 5.5 GB | Experimental; at least 10 GB physical RAM gate | Backend compiled; complete generation untested |
| Video models | Video | — | — | — | Not recommended | Disabled; no validated backend |

The app checks actual free RAM before load. Estimates do not guarantee a particular speed or freedom from native allocation failures. Imported GGUF files remain experimental even when the header architecture is accepted. Catalog data is in app/src/main/assets/catalog.json.

## Known limitations and release blockers
- Debug-signed preview. Production signing, store delivery, device performance qualification and broader accessibility QA remain.
- No physical iQOO was attached. The starter model completed SHA verification and generated a saved response in the x86_64 emulator. ARM64 text responses, model switching under load, long-session memory/thermal behavior, larger-model SHA completion and full image generation still need device testing.
- Images use CPU only. Generation/loading may be slow; cancellation during image model loading takes effect after loading returns. Android/native memory exhaustion can terminate a process despite conservative admission checks.
- No video generation; speech, vision input, RAG and optional cloud backends remain future work.
- Model discovery is a curated compatible catalog, not arbitrary repository search. Recommendations use metadata/resource eligibility, not measured device benchmarks.
- Storage remains app-private. Uninstall removes local models/history/media. Shared model storage, batch conversation selection and a general large-file browser are not implemented.
- Markdown formatting covers emphasis/code and simple keyword highlighting, not a complete CommonMark renderer. UI is English; full localization and TalkBack audit remain.
- Download interruptions retry a bounded number of times. Process restarts recover to Paused and require Resume. Android foreground-service limits and force-stop remain platform constraints.
- History uses local SQLite JSON records; very large libraries need paging/indexed search in a later release. Image list thumbnails and file checks warrant profiling at large scale.

## Recommended next version
| Priority | Improvement | Value | Difficulty | Performance/storage impact | Timing |
|---|---|---|---|---|---|
| 1 | Physical-device benchmark and soak suite | Replace estimates with measured safe defaults and detect heat/OOM issues | Medium | Temporary model storage and battery use | Before production release |
| 2 | Release signing and reproducible CI builds | Safe upgrades and trustworthy distributable artifacts | Medium | No inference overhead | Before production release |
| 3 | User-controlled encrypted library backup/restore | Protect conversations from phone loss/uninstall | Medium | Explicit export storage; occasional CPU | Next version |
| 4 | Paginated history and bounded thumbnail cache | Smooth browsing with thousands of items | Medium | Small cache; reduced peak RAM | Next version |
| 5 | ABI-split delivery | Smaller phone download by omitting emulator libraries | Low | Smaller APK, same models | Release packaging |
| 6 | Validated GPU backend for images | Potentially improve latency and energy | High | Extra native libraries, driver QA, possibly extra RAM | After CPU baseline measurements |
| 7 | Accessibility/localization pass | Better TalkBack, large-font and multilingual usability | Medium | Negligible runtime overhead | Before wide release |

Build/test evidence is maintained in ../VALIDATION.md. Unsupported and untested work is deliberately distinguished from implemented behavior.
