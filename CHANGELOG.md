# Changelog

All notable changes to Sitara are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Phase A: foundation (`v0.1.0-dev`).

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

[Unreleased]: https://github.com/muhib-karim/sitara/commits/main
