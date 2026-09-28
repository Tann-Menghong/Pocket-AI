# Pocket AI 2.2 — free-model expansion

## Implemented
- Six new curated downloads, for 15 total choices (13 text and 2 experimental image quantizations).
- Official publisher repositories; immutable revisions, exact sizes and SHA-256 integrity values. No weights in APK.
- Model sorting by download size, estimated RAM, name or installed status; sort preference persists.
- Under 1 GB filter and Fits available RAM filter, including current context and a 512 MB safety margin.
- Multi-word search across name, task, creator, quantization, architecture and license.
- Visible active filter, sort order and result count; current low-memory warnings.
- Reject unknown model types, unsupported text architectures and image architectures without a backend.
- In-app free-model guide with official publisher links and explanations of quantization and language limits.
- Fixed image selection from Downloads; fixed pending-update list disappearing after accepting one revision.
- About release notes use the installed version.

## New downloads
All six use Apache-2.0 according to publisher metadata retrieved 2026-09-28. Size is decimal GB. RAM is a conservative implementation estimate at 2,048 context tokens, not a measurement. Longer contexts add RAM. All entries remain experimental; no physical iQOO qualification.

| Choice | Download | Estimated runtime RAM | Suggested device RAM | Purpose | Source |
|---|---:|---:|---:|---|---|
| SmolLM2 360M Instruct Q8_0 | 0.39 GB | 1.2 GB | 3 GB | Chat, English, Small & Fast, Writing | [Publisher](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF) |
| SmolLM2 1.7B Instruct Q4_K_M | 1.06 GB | 2.3 GB | 5 GB | Chat, English, Writing, Summarization | [Publisher](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF) |
| Qwen2.5 Coder 0.5B Q4_K_M | 0.49 GB | 1.4 GB | 3 GB | Coding, Debugging, Small & Fast | [Publisher](https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF) |
| Qwen3 4B Q5 Q5_K_M | 2.89 GB | 4.1 GB | 9 GB | Chat, Coding, Reasoning, Multilingual | [Publisher](https://huggingface.co/Qwen/Qwen3-4B-GGUF) |
| Qwen3 4B Q6 Q6_K | 3.31 GB | 4.6 GB | 10 GB | Chat, Coding, Reasoning, Multilingual | [Publisher](https://huggingface.co/Qwen/Qwen3-4B-GGUF) |
| Qwen2.5 1.5B Instruct Q8 Q8_0 | 1.89 GB | 3.0 GB | 6 GB | Chat, Writing, Translation, Multilingual | [Publisher](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |

SmolLM2 adds a different model family focused on English. Qwen3 Q5/Q6 and Qwen2.5 Q8 are precision alternatives to existing models, not new architectures. More precision does not imply better reasoning than a larger model. Khmer capability is not certified. An inaccessible SmolLM2 135M repository was excluded. Qwen2.5 3B variants with custom licenses were researched but not added in this release.

Free cloud chat websites do not provide downloadable offline models by default. No third-party website automation, cloud prompt uploads or paid API dependency was added.

## Validation
Release, debug and instrumentation builds passed in 1m 39s after fixing a missing import. All 19 unit tests and 11 emulator integration tests passed (23.98s). Lint: zero errors, 77 warnings. The new catalog test checks unique IDs/files/hashes and revision/integrity formats. Signature and 16 KB ZIP alignment passed; native code is unchanged. Full SmolLM2 360M download, SHA-256 verification, offline generation and response persistence passed (201.736s including download; output 2 tokens in 0.5s, 3.8 tok/s). This short smoke test verifies operation, not answer quality or a reliable speed benchmark. The other five additions have metadata/policy validation only. No physical iQOO qualification. Previous validation remains in VALIDATION.md.

## Ten next recommended improvements
These are engineering recommendations, not shipped features.

| Priority | Improvement | Benefit | Difficulty | RAM/storage impact | Target |
|---|---|---|---|---|---|
| 1 | Physical-device benchmark wizard | Measures first-token time, speed and peak memory for model recommendations | Medium | Small logs; temporary model RAM | 2.3 |
| 2 | Automated interrupted-download tests | Protects resume, verification and restart behavior | Medium | Test device needs model scratch space | 2.3 |
| 3 | Per-model context limits | Prevents exceeding training context or memory budgets | Medium | Can lower KV-cache RAM | 2.3 |
| 4 | Search index and paged conversations | Keeps large libraries responsive | Medium | Small index; less transient RAM | 2.3 |
| 5 | Model comparison workspace | Compares selected models on the same prompt | Medium | Sequential loading; small result records | 2.3 |
| 6 | Khmer evaluation set and reviewed presets | Measures translation quality instead of guessing | Medium | Small text dataset | 2.3 |
| 7 | Thermal-aware thread adjustment | Reduces sustained overheating during long generation | Medium | Negligible storage; may reduce speed | 2.3 |
| 8 | Encrypted local backup | Protects exported conversations with user-held passphrase | High | Backup proportional to library | 2.4 |
| 9 | Qualified mobile image backend | Makes image generation useful with measured limits | High | Several GB; device-dependent | 2.4 |
| 10 | Production signing and end-to-end update QA | Improves release trust and upgrade reliability | High | Negligible runtime RAM; temporary APK storage | Stable release |

Video stays unavailable until a suitable runtime/model combination is demonstrated. Larger catalog size is not a compatibility guarantee. CPU-only acceleration remains unchanged.

Existing Qwen3 offline generation also passed (13 tokens, 1.9s; emulator only). Release install-over-existing and launch passed in 1.631s. Model manager visually reviewed; see [screenshot](pocket-models-2.2.png). APK: 52,594,456 bytes; SHA-256: 8eb66ae118397502116e650b87f6133f2e92161f8e699ad599acea00c0ec713d. Version code 5; original preview/debug signer retained.
