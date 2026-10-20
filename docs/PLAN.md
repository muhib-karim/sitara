# Sitara delivery plan

Phases follow SPEC section 4. Each phase ends with CI green on `main` and a tagged build.

## Status (2026-10-20)

| Phase | Scope | Status |
|---|---|---|
| A | Shell, design system, fake source, player UI, Media3 playback service, tests, CI, docs | Code complete; CI compiles and tests it; review findings fixed ([response](review/PHASE_A_REVIEW_RESPONSE.md)); on-device check pending |
| B | Real source (NewPipeExtractor), downloads, LRCLIB lyrics, crossfade/normalise processors, Room | **Complete.** Slice 1 released as 0.2.0: live YouTube Music search and playback, YouTube links pasted, opened or shared ([ADR 0004](adr/0004-youtube-source-newpipeextractor.md), [report](reports/REPORT_b1.md)). Slice 2 released as 0.3.0: artist, album and playlist pages, search filters, Play all / Shuffle, radio ([report](reports/REPORT_b2.md)). Slice 3 released as 0.4.0: Room library (liked songs, history, saved albums and artists, playlists), song menus, editable Up next ([report](reports/REPORT_b3.md)). Slice 4 released as 0.5.0: Home from YouTube Charts and the library, LRCLIB synced lyrics, the queue kept across restarts ([report](reports/REPORT_b4.md)). Slice 5 on `main` (for 0.6.0): downloads and offline play, loudness levelling, crossfade ([report](reports/REPORT_b5.md)). Carried over, not built: local files (MediaStore), import and backup, artwork images in the app, resolver health checks and the source health screen, Hilt |
| C | Accounts, scrobbling, widgets, Android Auto, Cast, Replay, remote control | Not started |
| D | Canvas, automix, translation/transliteration, Wear OS, TV | Not started |

### History: CI blocked while the repo was private
Until 2026-10-17 every Actions job died before its first step with a billing/spending-limit message. The repo is now
public and CI runs. The first real compile (run 39050111148) found two root causes, both fixed: `androidx.core` was
missing from `:core:player`, and Compose 1.12, material3 1.5.0-alpha28 and Haze 1.7.3 need compileSdk 37.
Details: [`plan/PHASE_A_FIX_PLAN.md`](plan/PHASE_A_FIX_PLAN.md).

## Phase A breakdown

| # | Task | Verification | State |
|---|---|---|---|
| A1 | Gradle multi-module scaffold, version catalog, AGP 9 built-in Kotlin | `gradle help` in CI | done, unverified |
| A2 | `:core:model` media + lyrics + settings types | compiles | done |
| A3 | Queue engine: add / addNext / move / remove / shuffle with restore / repeat | `QueueEngineTest` | done |
| A4 | Crossfade curves + fade window, LUFS gain with peak ceiling | `AudioMathTest` | done |
| A5 | Sleep timer (minutes + end of track) | `SleepTimerTest` (Turbine, virtual time) | done |
| A6 | LRC + enhanced word-timed LRC parser, active line/word lookup | `LrcParserTest`, `LyricsCursorTest` | done |
| A7 | Artwork palette to theme mapping with WCAG AA guarantees | `ArtworkPaletteTest` | done |
| A8 | Settings model + codec + DataStore repository | `SettingsCodecTest` | done |
| A9 | `:source:api` interfaces + fallback resolver; `:source:fake` with sample catalogue | `FallbackStreamResolverTest`, `FakeMusicSourceTest` | done |
| A10 | Design system: SitaraMotion, theme modes, glass (Haze 31+ / scrim), original icon set, generated covers | screenshots | done |
| A11 | Home / Search / Library screens | screenshots | done |
| A12 | Floating glass pill tab bar with spring selection pill | screenshots | done |
| A13 | Mini player + full player sheet: one critically damped spring, velocity hand-off, swipe down to hide, sideways to skip, predictive back | manual on device | done, needs device check |
| A14 | Now playing: artwork backdrop, scrubber, controls, Playing / Lyrics / Up next pager, karaoke lyrics | screenshots | done |
| A15 | Media3 `PlaybackService` owning a `PlaybackEngine`; whole queue as a playlist of lazily resolved demo streams; controller policy | `PlaybackEngineTest`, `EngineGateTest`, `ControllerPolicyTest`, `StreamPolicyTest`; manual on device | done, needs device check |
| A16 | Roborazzi screenshots (6 screens x light/dark), baselines recorded by `screenshots.yml` | CI `verifyRoborazziDebug` | done |
| A17 | CI: ktlint, unit tests, screenshots, assembleDebug artifact `sitara-debug-apk`; release, pages, gitleaks | green run | see the session report; Pages not yet deployed |
| A18 | README, ARCHITECTURE, DESIGN, CONTRIBUTING, COC, SECURITY, CHANGELOG, templates, Pages site | review | done |

## Simplifications taken in Phase A (recorded on purpose)
- **No Hilt yet**: dependencies are wired by hand in `SitaraApplication` (ADR 0001). Room and KSP landed in slice 3 without Hilt; `LibraryStore` keeps Room inside `:core:data`.
- **No Navigation library**: tabs are plain state, and detail pages are a saveable back stack of typed `DetailRoute`s (slice 2). That covers back, process death and per-page scroll state without a new dependency; Navigation 3 stays an option if pages need animated transitions or deep links.
- `:feature:lyrics` and `:feature:replay` from SPEC are folded into `:feature:player` / deferred to Phase C.
- JUnit 5 for pure-Kotlin modules, JUnit 4 for Android modules (Robolectric/Roborazzi need JUnit 4).
- ktlint runs as the CLI in CI with a relaxed `.editorconfig`; detekt is deferred.

## Next steps
1. Owner: on-device pass of the checklist in [REPORT_b5](reports/REPORT_b5.md), then tag `v0.6.0`.
2. Carried over from Phase B: artwork images in the app (an image loader), a bot-check message and PoToken provider, resolver health checks, local files, import and backup.
3. Phase C: accounts, scrobbling, widgets, Android Auto, Cast.
4. Still open from Phase A: a device check of the sheet spring and gestures on a 120 Hz phone, and enabling GitHub Pages (Settings, Pages, source "GitHub Actions") so `pages.yml` can deploy the site.
