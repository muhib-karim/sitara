# Changelog

All notable changes to Sitara are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Phase B, slice 1: real music.

### Added

- Live song search on YouTube Music through NewPipeExtractor v0.26.5 (new `:source:youtube` module, OkHttp downloader).
- Search results play through the existing player and queue together; the best audio-only stream (opus or m4a, https) is picked per song.
- Search shows loading, empty and error states, with a "Try again" action.

### Changed

- Home and Library say that they show built-in sample music; search suggestions are now genre searches.
- Sample lyrics appear only on sample songs, never on real ones.

## [0.1.0] - 2026-10-18

Phase A: foundation, plus the first UI redesign.

### Changed

- Redesigned Home screen.
- Library with grid and list views, plus new album and artist pages.
- Redesigned Search and Settings screens.
- Slimmer scrubber in the full player.
- README with the split-star logo and current device screenshots.

### Fixed

- Play/Pause now responds correctly after seeking within a track.
- Shuffle no longer fails on an empty queue lookup.
- Track completion is synchronised with the player, and the stream loader keeps its state across reloads.

### Added

- Multi-module project: `:app`, `:core:model`, `:core:common`, `:core:data`, `:core:player`, `:core:designsystem`, `:source:api`, `:source:fake`, `:source:lyrics` and feature modules for home, search, library, player and settings.
- App shell with a floating glass pill tab bar (Haze blur on Android 12+, tinted scrim fallback on older versions).
- Home, Search and Library screens on a built-in sample source with generated gradient covers.
- Mini player docked above the tab bar: swipe up to open, swipe down to hide, swipe sideways to skip.
- Full player sheet on a single critically damped spring with drag-velocity hand-off.
- Artwork-tinted gradient backdrop, scrubber, and an Up next / Lyrics pager.
- Word-level karaoke lyrics, backed by an LRC and enhanced word-timed LRC parser.
- Queue engine with shuffle that restores the original order, and repeat off / all / one.
- Crossfade curve math and LUFS normalisation gain towards a −14 LUFS target.
- Sleep timer by minutes or at the end of the current track.
- Artwork palette to theme mapping with contrast-checked colour roles.
- Settings stored in DataStore: theme (dynamic, artwork or AMOLED black) and reduced motion.
- Demo playback of a royalty-free stream through a Media3 `MediaSessionService`.
- `SitaraMotion` design tokens: snappy, smooth and gentle springs plus 120, 220 and 360 ms fades.
- Unit tests (JUnit, Turbine, MockK) and Roborazzi screenshot tests on Robolectric.
- GitHub Actions workflows: CI (ktlint, unit tests, screenshot tests, debug APK artifact), release on `v*` tags with SHA256SUMS, GitHub Pages site, gitleaks scanning and a manual screenshot refresh workflow.
- Project documentation: README, architecture and design system guides, contributing guide, code of conduct, security policy and the project website.

[Unreleased]: https://github.com/muhib-karim/sitara/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/muhib-karim/sitara/releases/tag/v0.1.0
