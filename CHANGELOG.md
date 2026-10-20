# Changelog

All notable changes to Sitara are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Phase B, slice 3: your own library, stored on the device.

### Added

- Like songs from the player or from any song's menu (the ⋮ button, or a long press on the row). Liked songs have their own page with Play all and Shuffle.
- Save albums and artists from their pages.
- Playlists of your own: create one from the Library or inline while adding a song, then rename it, delete it, reorder songs by dragging (or with TalkBack's Move up and Move down) and remove songs.
- Add to playlist from any song's menu and from the player.
- Play next and Add to queue from any song's menu. Up next can be reordered by dragging, and songs removed from it.
- Recently played: songs are recorded when they actually start playing (the latest 500 plays are kept).
- The Library tab now shows only what you saved: Liked songs, Playlists, Recently played, Albums and Artists, each with its own empty state.

### Changed

- The library is stored on the device with Room. The database schema is exported and committed, and CI fails if it changes without being committed.
- Search's sample browse no longer lists sample liked songs.

## [0.3.0] - 2026-10-20

Phase B, slice 2: real detail pages.

### Added

- Artist pages from YouTube Music: name, top songs (the artist's own songs, as YouTube Music song search ranks them) and an albums-and-singles shelf from the channel's Releases tab.
- Album and playlist pages from YouTube Music, with the full track list and durations; long playlists load up to 500 songs.
- Search filters: Songs, Albums, Artists and Playlists. Each result opens its page.
- Play all and Shuffle on albums, playlists and artist top songs; tapping a song plays from that song.
- Tap an album's artist, or the artist in Now Playing, to open the artist.
- Start radio on the Up next page, and a queue about to run out carries on with related songs (songs only, nothing already queued).
- Detail pages show loading, error with "Try again", and empty states; Back returns page by page, and the pages survive the app being closed in the background.

### Changed

- Sample albums, artists and playlists open the same pages as real ones.
- YouTube Music gives no release year, so real albums show "Album" without one.

## [0.2.0] - 2026-10-20

Phase B, first release: real music from YouTube Music.

### Added

- Live song search on YouTube Music through NewPipeExtractor v0.26.5 (new `:source:youtube` module, OkHttp downloader).
- Search results play through the existing player and queue together; the best audio-only stream (opus or m4a, https) is picked per song.
- Search shows loading, empty and error states, with a "Try again" action.
- Paste a YouTube Music, YouTube or youtu.be link into Search to get that song, ready to play.
- Open or share those links into Sitara: the song starts playing. Shared plain text becomes a search. Tapping a link needs Sitara allowed under Android's "Open by default"; sharing always works.

### Changed

- Home and Library say that they show built-in sample music; search suggestions are now genre searches.
- Sample lyrics appear only on sample songs, never on real ones.
- Search says "1 result" instead of "1 results".

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

[Unreleased]: https://github.com/muhib-karim/sitara/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/muhib-karim/sitara/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/muhib-karim/sitara/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/muhib-karim/sitara/releases/tag/v0.1.0
