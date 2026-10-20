# Project brain

## Project
Independent GPL-3.0 Android music player; Kotlin, Compose and Media3. Module map: docs/ARCHITECTURE.md.

## Constraints
Original code and visual identity. References are technique studies only. No local Gradle, lint, builds, tests or screenshot recording. Push only via the owner's gated push script. No new runtime dependencies unless essential. Preserve CI screenshot comparison and recording workflows.

## Status
Goal: an original app with its own design and logo (not a reskin), shipped as a signed v0.1.0 release with real screenshots.
- ui1 DONE (pushed): original "split-star / orbit" identity, Inter + saffron tokens, new launcher icon, artwork atmosphere, glass dock, listening stage, API 34 device-screens workflow.
- fix: player compile errors from ui1 fixed (BoxWithConstraints scope, Slider colours API); CI acceptance pending.
- ui2 source review complete; CI acceptance pending: Home mood collections and paged picks; Library grid/list and detail navigation; Search browse/results; functional-only Settings; approved ui1 split-star retained; slim seek drawing and measured dock clearance. Reuse ui1 components and tokens.
- fix1 source review complete: N1/6 completion synchronization, N2 atomic lookup and 5 resolver cancellation fixes retained with regression coverage; interrupt-status assertion added. Item 16 is superseded by the current sheet, covered by Back/drag and screenshot cases. Review copy and REPORT_fix1.md ready. All execution and acceptance remain pending in CI.
- v0.1.0 RELEASED.
- b1 (Phase B slice 1, real music) IN PROGRESS: :source:youtube on NewPipeExtractor v0.26.5 (JitPack, exclusiveContent), OkHttp downloader, desugaring. Next: stream resolver, YT Music search, search UI states, docs.

## Next step
b1 goals 2-6 in order (resolver, search mapper, search UI + wiring, baselines, docs), each committed, pushed and CI-checked; then docs/reports/REPORT_b1.md.

## Session log
2026-10-17 22:21Z Codex / GPT-6 Astra: recorded original design and ui1 plan; next foundations and tests.
2026-10-17 22:24Z Codex / GPT-6 Astra: foundations and original mark authored; next glass and player.
2026-10-17 22:31Z Codex / GPT-6 Astra: glass and artwork primitives prepared; next player and CI review.
2026-10-17 22:32Z Codex / GPT-6 Astra: player and dock reviewed in source; next CI capture and final report.
2026-10-17 22:34Z Codex / GPT-6 Astra: API 34 capture workflow and fake-queue tests prepared; final review next.
2026-10-17 22:35Z Codex / GPT-6 Astra: review corrected lyric end spacing and stage system-bar contrast; tests await CI.
2026-10-17 22:36Z Codex / GPT-6 Astra: report and local bundle prepared; no builds, tests or pushes; next CI and visual acceptance.
2026-10-17 22:45Z maintainer: ui1 imported to main; player compile fix; added CLAUDE.md and AGENTS.md.
2026-10-17 23:13Z Codex: resumed ui2, retained the approved split-star, completed browse/settings and scrubber corrections with CI test cases; final source review and report next.
2026-10-17 23:15Z Codex: completed ui2 source review, regression/screenshot cases and report; whitespace check passed, all execution and visual acceptance deferred to CI.
2026-10-17 23:43Z Codex: resumed fix1, retained targeted playback fixes, strengthened cancellation coverage and completed source review/report; next CI and device verification, with all edits uncommitted.
