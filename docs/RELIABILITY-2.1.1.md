# Pocket AI 2.1.1 reliability audit

Continues the 2.1 implementation; see [the full platform report](IMPLEMENTATION-2.1.md) for architecture, nine model choices, image/video limitations, update security and the 16-item roadmap. Working runtimes and download architecture were retained.

## Confirmed issues fixed
- Global search previously inspected only the last two truncated messages. It now searches full transcripts off the main thread, retains only compact results and limits results to 15 in the UI. Search is a linear scan, not an indexed database; Library inline filtering still uses summaries.
- Startup interruption recovery previously materialized all full chats simultaneously. It now visits one transcript at a time.
- Model revision checks previously offered already-registered hashes and did not check architecture continuity. New selection excludes known hashes, deduplicates candidates and matches kind, architecture and source filename.
- Model update registration now returns the actual registered model, preventing a duplicate candidate ID from being sent to the download manager.
- The model-update screen now displays saved results without requiring a network check.
- Truncated chat context now starts on a user message instead of potentially starting with an assistant turn.

## Validation
Release/debug/test APK builds, unit tests and lint passed in 2m 50s. All 17 JVM tests and 10 emulator integration tests passed (20.275 s). Full Qwen3 0.6B offline generation passed (13 tokens, 1.2 tok/s, 10.6 s generation; 22.784 s total). These are emulator measurements, not phone performance. Lint reports 0 errors and 77 warnings. Release signature and 16 KB ZIP alignment passed. The baseline build process was interrupted by the host before a final success status; its log contains no reported compilation failure. This is not recorded as a successful fresh baseline.

## Remaining limits
No new image/video compatibility claims. No physical phone attached. Preview signing identity is retained. The unfinished Qwen2.5 full-download test and image qualification remain outstanding unless explicitly superseded by new results. No production certification is claimed.

Database API reference: https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase

Release APK install-over-existing and cold launch passed (1.963 s). Version code 4, 52,584,352 bytes; SHA-256: 00cbfb3c452fe8eec5b2026fc4662ae12e13f6c8a08d5e95bd8ff395eb3e15e2.
