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
- b1 (Phase B slice 1, real music) DONE on main: live YouTube Music song search + playback via :source:youtube (NewPipeExtractor v0.26.5); search states with retry; home, library, lyrics and covers still sample and labelled. Report: docs/reports/REPORT_b1.md.
- b2 (Phase B slice 2, real detail pages) on main, target v0.3.0 (owner tags): artist/album/playlist pages from YouTube Music, Songs/Albums/Artists/Playlists search chips, Play all/Shuffle/row taps, Start radio + auto-radio, typed DetailRoute back stack. Report: docs/reports/REPORT_b2.md.
- b3 (Phase B slice 3, saved library) DONE on main, target v0.4.0 (owner tags): Room store in :core:data behind LibraryStore (liked, capped history, saved albums/artists, playlists; schema committed, CI drift check), queue ops + play history, song menus, like/save, playlist sheets and pages, editable Up next, Room-only Library; baselines 08d9f88. Report: docs/reports/REPORT_b3.md.
- b4 (Phase B slice 4) DONE on main (CI, Device screens, Secret scan green at 360a64f), target v0.5.0 (owner tags): Home = YouTube Charts trending (ChartsSource, NewPipe trending_music kiosk) + Recently played / Jump back in / From your likes from Room; sample catalogue debug/test-only; Room v2 (saved queue, lyrics cache); queue restored after process death, saved on change/pause/15 s/destroy; onPlaybackResumption + MediaButtonReceiver; covers in system controls; LRCLIB lyrics (get -> search, cached, LyricsState with Try again). Report: docs/reports/REPORT_b4.md.
- b1a DONE on main: YouTube/YouTube Music/youtu.be links (paste into Search, VIEW + SEND intents, autoplay), live smoke test job (search finds x7kGuhlXmFI; player request bot-checked on CI runners). Tagged and released as v0.2.0.

- b5 (Phase B slice 5, target v0.6.0) IN PROGRESS: downloads/offline pushed (Media3 DownloadManager + SimpleCache keyed by sitara://track/<id>, read-only cache in playback); next sound (normaliser + crossfade), baselines, docs, REPORT_b5.

## Next step
Owner, before tagging v0.5.0: on-device pass of the checklist in REPORT_b4.md (chart, shelves, lyrics, lock screen and notification, focus and noisy, swipe away, restore after force-stop, headset resume, release build under R8). Next slice: image loader for in-app artwork, bot-check message + PoToken provider.

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
2026-10-20 07:37Z Claude Code / Opus 5.5: b1 shipped (6 commits b29996d..e144854 + docs), CI green through 39387647017 (docs + report); next device + release-build check, then artwork/browse/lyrics.
2026-10-20 07:45Z Claude Code / Opus 5.5: resumed after memory guard; nothing lost, recorded final CI run in report; b1 complete.
2026-10-20 08:08Z Claude Code / Opus 5.5: b1a shipped (links, intents, smoke job, 0.2.0 docs); CI green; next owner device + release check, then tag.
2026-10-20 08:50Z Claude Code / Opus 5.5: b2 shipped (1d57d6b..bb5578a + docs): detail pages, search filters, play all/shuffle, radio; CI, Device screens, Secret scan green; next owner device check and v0.3.0 tag.
2026-10-20 09:40Z Claude Code / Opus 5.5: b3 store, queue ops, history and library UI pushed (7c7f058..f07d348); next record baselines, docs, REPORT_b3, CI green.
2026-10-20 10:05Z Claude Code / Opus 5.5: b3 shipped (7c7f058..08d9f88 + docs); CI 39402430963, Device screens 39402430844 (re-run), Secret scan green; next owner device check and v0.4.0 tag.
2026-10-20 12:05Z Claude Code / Opus 5.5: b4 shipped (0fd6d61..360a64f + docs): real Home, queue kept across restarts + headset resume, LRCLIB lyrics; next owner device check and v0.5.0 tag.
