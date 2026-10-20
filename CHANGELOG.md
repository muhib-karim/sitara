# Changelog

All notable changes to Sitara are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.7.0] - 2026-10-20

Phase C, slice 1: real artwork and a player that looks like the music.

### Added

- Real covers everywhere in the app: songs, albums, artists and playlists on Home, Search, the Library, artist, album and playlist pages, Up next, the mini player and Now Playing. Each cover is fetched in a size that fits where it is drawn and kept in memory and on disk. YouTube video thumbnails are cropped to the square of their picture, without the black bars.
- Now Playing takes its colours from the cover: the backdrop, the glow behind the sleeve, the scrubber, shuffle, repeat and like, and the mini player's tint. The highlight colour is checked to stay readable (at least 4.5:1) on the backdrop. Colours fade from song to song, or change at once with reduced motion. The AMOLED theme keeps saffron.
- Lyrics: scrolling them by hand stops the list from following the song, and a "Back to current line" button returns to it. Tapping a line still jumps there.

### Changed

- While a cover loads, or when it cannot (offline, removed, no image), the generated gradient cover shows instead, and the player keeps colours from the song's own palette.

### Fixed

- The device screenshot job waits longer on a slow emulator and dismisses a system "isn't responding" dialog instead of failing.

## [0.6.0] - 2026-10-20

Phase B, slice 5: offline listening and sound. This completes Phase B.

### Added

- Downloads: Download a song from its menu, or a whole album or playlist (yours, liked songs, or one from YouTube Music) with Download all. Downloaded songs play with no connection; songs that are not downloaded stream as before. A downloaded badge marks them on album and playlist pages.
- A Downloads section at the end of the Library lists every download with its progress, "Waiting for Wi-Fi" or "Failed", and lets you cancel, delete, retry, or play the downloads in order. Downloads carry on in the background with a progress notification after the app is closed.
- Settings, Downloads: "Download on Wi-Fi only" (on by default), storage used, and Remove all downloads (it asks first).
- Normalise loudness (on by default): songs play at a similar volume. YouTube's own loudness values are not available through NewPipeExtractor, so Sitara levels the sound gently as it plays: at most 6 dB louder, never past −1 dBFS peaks, never in sudden jumps. Off leaves the sound untouched.
- Crossfade (Off, or 1 to 12 seconds): the end of a song overlaps the start of the next. Songs from the same album, a song on repeat, and the song an end-of-song sleep timer stops after still play without a gap. Pausing, seeking, skipping or a phone call ends a fade at once.

### Changed

- Settings gain Sound and Downloads groups.

## [0.5.0] - 2026-10-20

Phase B, slice 4: a real Home, playback that survives restarts, and lyrics.

### Added

- Home shows the songs trending now on YouTube Charts for your country (the United Kingdom's chart where YouTube has none), with loading, error and Try again states, then Recently played, Jump back in (your playlists and saved albums) and From your likes (liked songs you have not played lately). A fresh install says how Home fills up.
- Lyrics from LRCLIB: synced lyrics highlight and follow the current line and seek on tap; plain lyrics show when nothing is synced; otherwise "No lyrics found". A failed lookup offers Try again. Lyrics are cached on the device (songs with none are asked again after a week), and the page credits LRCLIB. Requests name only the app and its repository.
- The queue, the current song and the position are saved as you listen and come back, paused, after the app is closed or killed. A headset or Bluetooth play button, or Android's resume card, resumes them even when the app is not running.
- The notification, lock screen and Bluetooth displays show the song's cover.

### Changed

- The built-in sample catalogue is gone from the app: Home's sample shelves and mood pills, the sample note and Search's sample browse are removed, and release builds no longer contain sample music. Songs from the sample catalogue saved in an earlier version can no longer play.
- The database moves to version 2 (new tables for the play queue and the lyrics cache; existing data is kept).

## [0.4.0] - 2026-10-20

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

[Unreleased]: https://github.com/muhib-karim/sitara/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/muhib-karim/sitara/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/muhib-karim/sitara/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/muhib-karim/sitara/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/muhib-karim/sitara/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/muhib-karim/sitara/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/muhib-karim/sitara/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/muhib-karim/sitara/releases/tag/v0.1.0
