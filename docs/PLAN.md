# Sitara delivery plan

Phases follow SPEC section 4. Each phase ends with CI green on `main` and a tagged build.

## Status (2026-10-17)

| Phase | Scope | Status |
|---|---|---|
| A | Shell, design system, fake source, player UI, Media3 demo playback, tests, CI, docs | Code complete; **CI blocked** (see below) |
| B | Real source (NewPipeExtractor), downloads, local files, LRCLIB lyrics, crossfade/normalise processors, Hilt, Room | Not started |
| C | Accounts, scrobbling, widgets, Android Auto, Cast, Replay, remote control | Not started |
| D | Canvas, automix, translation/transliteration, Wear OS, TV | Not started |

### Blocker: GitHub Actions does not start on the private repo
The first CI run (`39049550077`) failed before any step ran: *"The job was not started because recent account
payments have failed or your spending limit needs to be increased."* Private repos use paid minutes; public repos run free.
The workflows are ready and run as soon as billing is fixed or the repo is made public (the owner's call).
Pages on a private repo also needs a paid plan, so `pages.yml` is in place but unverified.

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
| A15 | Media3 `MediaSessionService` + controller playing royalty-free SoundHelix demo streams | manual on device | done |
| A16 | Roborazzi screenshots (6 screens x light/dark) + `screenshots.yml` PR workflow | CI artifact | done, unverified |
| A17 | CI: ktlint, unit tests, screenshots, assembleDebug artifact `sitara-debug-apk`; release, pages, gitleaks | green run | **blocked by billing** |
| A18 | README, ARCHITECTURE, DESIGN, CONTRIBUTING, COC, SECURITY, CHANGELOG, templates, Pages site | review | done |

## Simplifications taken in Phase A (recorded on purpose)
- **No Hilt yet**: dependencies are wired by hand in `SitaraApplication` (ADR 0001). Hilt + KSP land with Room in Phase B.
- **No Navigation library yet**: three tabs and a settings overlay are plain state. Navigation 3 arrives with detail screens.
- `:feature:lyrics` and `:feature:replay` from SPEC are folded into `:feature:player` / deferred to Phase C.
- JUnit 5 for pure-Kotlin modules, JUnit 4 for Android modules (Robolectric/Roborazzi need JUnit 4).
- ktlint runs as the CLI in CI with a relaxed `.editorconfig`; detekt is deferred.

## Next steps
1. Unblock Actions (billing, or make the repo public), then iterate until `ci.yml` is green.
2. Run `screenshots.yml` to commit `docs/screenshots/*.png` for the README and the site.
3. Device check on a 120 Hz phone: sheet spring feel, swipe gestures, audio focus.
4. Phase B: NewPipeExtractor `StreamResolver`, health checks, LRCLIB `LyricsProvider`, Hilt, Room.
