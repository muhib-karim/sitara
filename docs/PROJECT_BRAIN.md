# Project brain

## Project
Independent GPL-3.0 Android music player; Kotlin, Compose and Media3. Module map: docs/ARCHITECTURE.md.

## Constraints
Original code and visual identity. References are technique studies only. No local Gradle, lint, builds, tests or screenshot recording. No push in this session. No new runtime dependencies unless essential. Preserve CI screenshot comparison and recording workflows.

## Status
Goal: an original app with its own design and logo (not a reskin), shipped as a signed v0.1.0 release with real screenshots.
- ui1 DONE (pushed): original "split-star / orbit" identity, Inter + saffron tokens, new launcher icon, artwork atmosphere, glass dock, listening stage, API 34 device-screens workflow.
- fix: player compile errors from ui1 fixed (BoxWithConstraints scope, Slider colours API); CI acceptance pending.
- ui2: next (remaining screens polish on the new identity).
- fix1: after ui2 (Play/Pause after seek, empty-queue shuffle lookup, review items 5/6/16).
- v0.1.0: tag + release.yml, logo and 3-4 device screenshots in README and release notes.

## Next step
Get CI and device screens green on main, inspect the device screenshots, then run ui2.

## Session log
2026-10-17 22:21Z Codex / GPT-6 Astra: recorded original design and ui1 plan; next foundations and tests.
2026-10-17 22:24Z Codex / GPT-6 Astra: foundations and original mark authored; next glass and player.
2026-10-17 22:31Z Codex / GPT-6 Astra: glass and artwork primitives prepared; next player and CI review.
2026-10-17 22:32Z Codex / GPT-6 Astra: player and dock reviewed in source; next CI capture and final report.
2026-10-17 22:34Z Codex / GPT-6 Astra: API 34 capture workflow and fake-queue tests prepared; final review next.
2026-10-17 22:35Z Codex / GPT-6 Astra: review corrected lyric end spacing and stage system-bar contrast; tests await CI.
2026-10-17 22:36Z Codex / GPT-6 Astra: report and local bundle prepared; no builds, tests or pushes; next CI and visual acceptance.
2026-10-17 22:45Z maintainer: ui1 imported to main; player compile fix; added CLAUDE.md and AGENTS.md.
