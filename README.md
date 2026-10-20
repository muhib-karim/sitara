<div align="center">

<img src="docs/brand/sitara-logo.svg" alt="Sitara split-star logo" width="112">

<h1>Sitara</h1>

<p><strong>Your music, beautifully.</strong></p>

<br>

[![CI](https://img.shields.io/github/actions/workflow/status/muhib-karim/sitara/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white&style=flat-square)](https://github.com/muhib-karim/sitara/actions/workflows/ci.yml)
[![Licence: GPL-3.0](https://img.shields.io/badge/licence-GPL--3.0-8B7CFF?style=flat-square)](LICENSE)
[![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.12-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Media3](https://img.shields.io/badge/Media3-1.10-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/media/media3)
[![minSdk 26](https://img.shields.io/badge/minSdk-26-FF6B9A?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)

**A free, open-source, unofficial YouTube Music client for Android, built around spring physics, glass, artwork colour and lyrics that sing along.**

[Nightly APK](https://github.com/muhib-karim/sitara/actions/workflows/ci.yml?query=branch%3Amain+is%3Asuccess) ·
[Architecture](docs/ARCHITECTURE.md) ·
[Design system](docs/DESIGN.md) ·
[Changelog](CHANGELOG.md)

</div>

---

> [!NOTE]
> Sitara is an early but complete-for-daily-listening preview: **Phase B is done**. Search YouTube Music for songs,
> albums, artists and playlists, or paste or share a YouTube link; play through a background service with a queue you
> can edit, radio and a sleep timer; keep liked songs, playlists and saved albums on the device; see what is trending
> on YouTube Charts; sing along with synced lyrics from LRCLIB; and, on `main` for `0.6.0`, download songs to play
> offline, with loudness levelling and crossfade. In the app, covers are still generated gradients (the notification
> and lock screen show the real cover). Releases are on the [Releases](https://github.com/muhib-karim/sitara/releases) page.

## Screenshots

<div align="center">

| Home | Player | Lyrics | Downloads | Sound |
| :---: | :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/home_dark.png" alt="Home with the trending chart, recently played and jump back in" width="160"> | <img src="docs/screenshots/player_dark.png" alt="Full player with artwork, slim scrubber and controls" width="160"> | <img src="docs/screenshots/lyrics_dark.png" alt="Synced karaoke lyrics with the LRCLIB credit" width="160"> | <img src="docs/screenshots/downloads_dark.png" alt="Library downloads with progress, waiting for Wi-Fi, failed and downloaded songs" width="160"> | <img src="docs/screenshots/sound_dark.png" alt="Sound settings with normalise loudness and the crossfade slider" width="160"> |

<sub>Recorded by the Record screenshots workflow (Roborazzi on Robolectric) from hand-built fixtures shaped like a listener's library; release builds contain no sample data. Light versions and more screens live in docs/screenshots.</sub>

</div>

## Why Sitara

Existing clients cover the basics well. Sitara aims for the things that make an app feel *finished*:

- **Motion you can feel.** Transitions ride springs. The cover travels from the unified dock to the player on a shared-element spring. Reduced motion uses instant transitions.
- **Colour from the music.** Artwork colors the listening stage while the chrome keeps its saffron accent. Material You changes only the accent; AMOLED removes the wash.
- **Lyrics as the hero.** Heavy Inter Display lyrics, a crisp active line, distance-softened context and tap-to-seek. Transliteration for Bangla, Hindi and Urdu is planned so everyone can sing along.
- **Playback that outlives the screen.** The queue, auto-advance and sleep timer live in the playback service, so the notification, Bluetooth and Android Auto controls keep working when the app is closed.
- **Sound that flows.** Songs play at a similar volume, and can crossfade into each other while albums stay gapless.
- **Offline when you need it.** Download songs, albums and playlists, on Wi-Fi only unless you allow mobile data.
- **Reliable by design.** Stream sources sit behind an interface with automatic fallback, and only the app and system surfaces may control the media session.
- **Accessible from the start.** TalkBack labels, a reduced-motion mode and contrast-checked artwork palettes are part of the design system.
- **Built in the open.** Tests, screenshot verification and lint run on every push.

Planned, not built yet: *Sitara Sync* (end-to-end encrypted sync through storage you own) and per-song sound profiles.

## Features

| Area | Feature | Status |
| --- | --- | :---: |
| **Shell** | Unified glass dock with four destinations and edge-to-edge layout | ✅ |
| | Home: trending songs from YouTube Charts for your country (loading, error and Try again), then Recently played, Jump back in and From your likes from your library | 🧪 |
| **Library** | Liked songs, saved albums and artists, recently played (latest 500 plays), stored on the device (Room) | 🧪 |
| | Your own playlists: create, rename, delete, drag to reorder, remove songs; Add to playlist from any song or the player | 🧪 |
| **Offline** | Download a song from its menu, or Download all on an album, a playlist or Liked songs; downloaded songs play with no connection and carry a badge | 🧪 |
| | Downloads in the Library: progress, waiting for Wi-Fi, failed with retry, cancel and delete; downloads finish in the background with a notification | 🧪 |
| | Settings: Download on Wi-Fi only (on by default), storage used, Remove all downloads | 🧪 |
| **Player** | Mini player in the navigation dock: swipe up to open, sideways to skip | 🧪 |
| | Shared artwork transition, white play disc and custom timeline | 🧪 |
| | Artwork backdrop, scrubber, Playing / Lyrics / Up next pager | ✅ |
| | Queue: unique entries, shuffle with order restore, repeat off / all / one | ✅ |
| | Play next and Add to queue from any song; reorder and remove in Up next | 🧪 |
| | Sleep timer (minutes or end of track) on a monotonic clock | ✅ |
| | Background playback service: the whole queue is a Media3 playlist; notification and lock screen show title, artist and cover with previous, next and seek; audio focus, pause when headphones disconnect, headset and Bluetooth buttons; keeps playing when the app is swiped away | 🧪 |
| | The queue, current song and position are saved and come back after the app is closed or killed; a headset play button resumes them | 🧪 |
| **Sound** | Normalise loudness (on by default): gentle levelling toward −14 LUFS, at most +6 dB, peaks kept under −1 dBFS | 🧪 |
| | Crossfade, Off or 1 to 12 s; songs from one album stay gapless | 🧪 |
| **Lyrics** | Synced lyrics from LRCLIB that follow the song and seek on tap; plain lyrics when nothing is synced; "No lyrics found" otherwise; cached on the device | 🧪 |
| | LRC and enhanced LRC parsing (offsets, repeated timestamps, word timing) | ✅ |
| **Settings** | Theme: dynamic, artwork or AMOLED black; light / dark; reduced motion | ✅ |
| **Source** | Live YouTube Music search (songs, albums, artists, playlists) with loading, empty, error and retry states (NewPipeExtractor) | 🧪 |
| | Playback: best audio-only stream (opus or m4a) | 🧪 |
| | Links: paste a YouTube Music, YouTube or youtu.be link into Search, or share it to Sitara, to play that song. Tapping a link opens Sitara once it is allowed under Android's "Open by default" settings | 🧪 |
| | Artist, album and playlist pages with Play all and Shuffle | 🧪 |
| | Radio: Start radio from Up next, and a queue about to run out continues with related songs | 🧪 |
| **Later** | Artwork images in the app, resolver health checks, local files, import, backup and restore | Not started |
| | Skip silence, speed and pitch, equaliser | Not started |
| **Accounts** | Account library sync, scrobbling, widgets, Android Auto, Cast | Phase C |
| | Sitara Sync, LAN remote control, smart offline | Phase C |
| **Premium** | Animated canvas, automix, lyric translation and transliteration, Wear OS remote, TV layout | Phase D |

✅ covered by unit or screenshot tests · 🧪 tested on the JVM, awaiting a real-device check

## How it works

- **Playback.** Search, pages, the chart and radio come from YouTube Music through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); no account or API key is involved. When a song starts, Sitara resolves its best audio-only stream (opus or m4a) on the fly, so links never go stale in the queue. Playback runs in a Media3 service with a media session: notification, lock screen, Bluetooth and headset controls work with the app closed.
- **Links.** Paste a `music.youtube.com`, `youtube.com/watch` or `youtu.be` link into Search, or share one to Sitara from another app; the song plays as soon as it resolves. Only the video id is read from the link.
- **Lyrics.** For the playing song Sitara asks [LRCLIB](https://lrclib.net) by title, artist and length (then a search), prefers synced lyrics, and keeps what it finds on the device. Requests carry only the app's name and repository URL.
- **Downloads.** A download stores the song's audio in an app-private cache through Media3's download manager, resolving a fresh stream URL whenever it starts or resumes. The player always looks in that cache first, so a downloaded song plays with no connection. Downloads wait for Wi-Fi unless you turn off "Download on Wi-Fi only".
- **Sound.** Loudness levelling runs inside the audio pipeline and adjusts gain slowly as a song plays. Crossfade hands the end of a song to a second player while the next one starts; the main player keeps the queue, so skipping, editing Up next and restoring after a restart behave as usual.

## Known limits

- No sign-in: there is no YouTube account, so no account library, history sync or personal recommendations.
- YouTube may answer with a bot check on some networks (it does on cloud servers); search and pages still load, but songs may not play there. Downloads need the same streams.
- YouTube passes on no loudness values through NewPipeExtractor, so levelling measures the sound as it plays rather than knowing a song's loudness in advance; the first seconds of a song can be a little louder or softer.
- Crossfade overlaps by switching to a second player; the hand-over may be audible on some devices.
- Covers inside the app are generated gradients; real covers show in the notification, lock screen and Bluetooth displays.
- An unofficial client can break when YouTube changes; fixes land in `:source:youtube`.

## Architecture

Sitara is a multi-module Gradle project. Features depend on core modules and on the `:source:api` contracts, never on a concrete source, so the stream backend can be swapped or repaired in one place.

```mermaid
graph TD
    app[":app"]

    subgraph Features
        home[":feature:home"]
        search[":feature:search"]
        library[":feature:library"]
        player[":feature:player"]
        settings[":feature:settings"]
    end

    subgraph Core
        designsystem[":core:designsystem"]
        coreplayer[":core:player"]
        data[":core:data"]
        common[":core:common"]
        model[":core:model"]
    end

    subgraph Sources
        api[":source:api"]
        fake[":source:fake"]
        lyrics[":source:lyrics"]
        youtube[":source:youtube"]
    end

    app --> home & search & library & player & settings
    app --> coreplayer & data & youtube & lyrics & common
    app -. debug and tests .-> fake
    home & search & library & player & settings --> designsystem
    settings --> data
    player --> lyrics
    coreplayer --> api
    fake --> api & lyrics
    youtube --> api
    lyrics --> api
    data --> model
    api --> model
    designsystem --> model
```

More detail, including data flow and the CI pipeline, is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The motion, colour and type system is documented in [docs/DESIGN.md](docs/DESIGN.md).

**Stack:** Kotlin 2.4 · Jetpack Compose 1.12 + Material 3 · Media3 1.10 (ExoPlayer, MediaSessionService, downloads) · Haze glass blur · DataStore · Room · NewPipeExtractor + OkHttp · JUnit 4/5, Turbine · Roborazzi on Robolectric.

## Install

**From a release (recommended).** Open the latest release on the [Releases](https://github.com/muhib-karim/sitara/releases)
page and download `sitara-vX.Y.Z.apk` and `SHA256SUMS`. Check the file with `sha256sum -c SHA256SUMS`, then open the
APK on your phone and allow installing from that source when Android asks. Releases can be tracked with Obtainium.

**Nightly builds.** Every green CI run on `main` uploads a debug APK as the `sitara-debug-apk` artifact. Open the latest
successful run on the [Actions](https://github.com/muhib-karim/sitara/actions/workflows/ci.yml?query=branch%3Amain+is%3Asuccess)
page (a GitHub login is required to download artifacts). A debug build has the same app id as a release but another
signature, so one must be uninstalled before the other installs, which deletes its library and downloads.

Requires Android 8.0 (API 26) or newer.

## Build from source

You need **JDK 21** and **Gradle 9.8** on your `PATH`, plus the Android SDK (compile SDK 37).

```sh
git clone https://github.com/muhib-karim/sitara.git
cd sitara
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

> [!IMPORTANT]
> No Gradle wrapper JAR is committed; the project relies on CI and a locally installed Gradle. Code style is checked with ktlint in CI. Useful tasks:
>
> | Task | What it does |
> | --- | --- |
> | `gradle test` | All unit tests (JVM modules and both Android variants) |
> | `gradle :app:verifyRoborazziDebug` | Compare screenshots with the committed baselines; fails on any mismatch |
> | `gradle :app:recordRoborazziDebug` | Re-record baselines after an intentional UI change (CI does this with the "Record screenshots" workflow) |

## Roadmap

- **Phase A: foundation** (`v0.1.0`). Design system, navigation, player sheet, service-owned queue, lyrics engine, settings, CI with screenshot verification.
- **Phase B: real source** (complete, `0.2.0` to `0.6.0`). Search and browse, links, radio, the on-device library and playlists, a real Home, background playback that survives restarts, synced lyrics, downloads and offline play, loudness levelling and crossfade. Carried over: artwork images in the app, health checks, local files, import and backup.
- **Phase C: accounts and connection.** Library sync, scrobbling, widgets, Android Auto, Cast, Sitara Sync, LAN remote control.
- **Phase D: premium touches.** Animated canvas, automix, lyric translation and transliteration, Wear OS remote, TV layout.

Follow progress in the [CHANGELOG](CHANGELOG.md) and on the [issue tracker](https://github.com/muhib-karim/sitara/issues).

## Contributing

Contributions of every size are welcome: bug reports, design feedback, translations and code. Read [CONTRIBUTING.md](CONTRIBUTING.md) to get started and please follow the [Code of Conduct](CODE_OF_CONDUCT.md). Security issues go through [private vulnerability reporting](SECURITY.md), never public issues.

## Disclaimer

Sitara is an unofficial client. It is not affiliated with, endorsed by, or connected to YouTube or Google. All trademarks belong to their respective owners. Sitara does not bundle copyrighted media, does not circumvent DRM and does not offer features that require it. You are responsible for using it in line with the terms that apply to you.

## Licence

Sitara is free software, released under the [GNU General Public License v3.0](LICENSE). You may use, study, share and modify it under the terms of that licence.

<div align="center">
<br>
<img src="docs/assets/icon.svg" alt="" width="56">
<br>
<sub>সিতারা · ستارہ · star</sub>
</div>
