# SPEC: Sitara, a premium open-source YouTube Music client for Android

Working name **Sitara** (সিতারা / ستارہ, "star"). No existing music client uses it (checked 2026-10-17). Tagline: "Your music, beautifully."
Licence **GPL-3.0** (required by the extractor libraries; fits the plan to publish it as a showcase). Original code; no competitor code, names or assets. Package `app.sitara.music`. minSdk 26, target/compile 36 (37 only if a dependency forces it).
Target device first: Galaxy S23 Ultra (120 Hz, 1440p, Android 15/16, One UI).

## 1. Product principles
1. **BitChord-grade feel**: every transition is spring physics, chrome is glass, colour comes from the artwork, lyrics are the hero.
2. **Union of features** from BitChord, Metrolist, Meld and ReVanced (STUDY.md section 2), shipped in phases behind clean module boundaries.
3. **Gaps nobody fills** (section 5) are the differentiators.
4. **Reliability by design**: the stream source sits behind an interface with health checks and fallbacks, so a YouTube change is a one-module fix.
5. **Showcase-grade engineering**: tests first, CI green, generated screenshots, architecture docs, reproducible FOSS build.

## 2. Architecture
- Kotlin 2.x, Jetpack Compose + Material 3 (expressive), Navigation-Compose (type-safe routes), Hilt, Coroutines/Flow, Room, DataStore, Coil 3, Ktor client, kotlinx.serialization.
- **Media3**: ExoPlayer + MediaSessionService + MediaLibraryService (Android Auto browse tree), with a custom `AudioProcessor` chain for crossfade, normalisation (ReplayGain-style LUFS target), EQ, speed/pitch and skip silence. Cast via the media3-cast module.
- Modules:
  - `:core:model` (pure Kotlin: Track, Album, Artist, Playlist, Queue, LyricsLine/Word)
  - `:core:common` (Result types, dispatchers, time)
  - `:core:data` (Room DB, repositories, DataStore settings, backup/restore JSON)
  - `:core:player` (Media3 service, queue engine, crossfade/normalise processors, sleep timer)
  - `:core:designsystem` (theme, motion specs, glass surfaces, typography, components)
  - `:source:api` (interfaces `MusicSource`, `StreamResolver`, `LyricsProvider`, `HealthCheck`)
  - `:source:youtube` (NewPipeExtractor-backed search/browse/stream resolution; InnerTube JSON for home/library where needed; original code)
  - `:source:lyrics` (LRCLIB first, more providers pluggable; LRC + enhanced-LRC word timing parser)
  - `:source:local` (MediaStore + SAF folders)
  - `:feature:home`, `:feature:search`, `:feature:library`, `:feature:player`, `:feature:lyrics`, `:feature:settings`, `:feature:replay`
  - `:app` (navigation host, DI graph, widgets)
- Testing: JUnit 5 + Turbine + MockK for logic; Robolectric for Android units; Compose UI tests; **Roborazzi** screenshot tests (also generate the README screenshots in CI); a fake `MusicSource` so the UI and player run without the network in tests. Kover coverage, detekt + ktlint, Android Lint, gitleaks.
- CI (GitHub Actions): `ci.yml` (lint, unit, screenshot verify, assembleDebug, upload the debug APK artifact); `release.yml` (on tag `v*`: signed release APK + AAB when secrets `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` exist, else an unsigned build plus a warning; SHA256SUMS; GitHub Release); `pages.yml` (docs site from `/site`); `gitleaks.yml`.

## 3. UI/UX spec (the BitChord-grade bar, done our own way)
- **Navigation**: floating glass pill tab bar (Home, Search, Library, plus the mini player docked above it) with a sliding selection pill on a spring; it hides on scroll down and returns on scroll up. Large-title collapsing headers with a frosted top bar.
- **Mini player to full player**: a shared-element artwork morph on a critically damped spring (response ~0.42 s, damping 1.0), with velocity hand-off from the drag. **Swipe down on the mini player hides it, swipe up opens it, swipe horizontally to skip** (BitChord's top request). Predictive back gesture supported.
- **Now playing**: an artwork-tinted gradient backdrop (palette from the cover, animated cross-fade between tracks), large cover with soft shadow and subtle parallax on tilt (optional), scrubber with haptic ticks and a waveform-ish progress bar, a three-way page pager (Up next, Lyrics, Related), quick chips for sleep timer, speed and output device, and a "stats for nerds" sheet.
- **Lyrics**: word-level karaoke fill, line scale/blur focus effect, tap a line to seek, auto-scroll with manual override, translation toggle, and a share-as-card image.
- **Glass**: Haze blur for bars and sheets on API 31+; a tinted scrim fallback below. Our own AGSL "liquid" lens highlight on the tab pill (optional setting, off in battery saver).
- **Motion system**: one `SitaraMotion` object (springs: snappy, smooth, gentle; durations for fades), and 120 Hz-aware frame pacing. Reduced-motion setting respects the system "remove animations".
- **Colour and type**: Material You dynamic colour, or artwork colour, or 12 hand-tuned palettes; pure-black AMOLED mode. Type scale with a display face for headers (open-licence font, e.g. Inter / Manrope via OFL).
- **Long-press context menus** with a blurred preview card (iOS-style), haptics throughout.
- Accessibility: TalkBack labels on every control, 48 dp targets, font scaling to 200%, contrast checks in screenshot tests.

## 4. Feature union (phased)
**Phase A (first CI-green APK)**: app shell, design system, nav, home/search/library screens on a fake source with sample content, Media3 service playing a public-domain demo stream, mini and full player with spring motion, queue, sleep timer, settings with theme modes. The tests suite is green.
**Phase B (real source)**: NewPipeExtractor search, artist/album/playlist browse, stream resolution with quality per network (Wi-Fi vs mobile), background play, gapless, crossfade 0-12 s, normalisation, skip silence, speed/pitch, EQ (system + 10-band built-in), downloads (WorkManager, embedded tags), local files, synced lyrics (LRCLIB), history, local playlists, import (YouTube playlist URL, Spotify playlist via public link scrape, M3U/CSV), backup/restore JSON.
**Phase C (accounts and social)**: Google/YouTube Music account login (WebView cookies) with library sync, Last.fm + ListenBrainz scrobbling, Discord presence, listen-together, widgets (Glance), Android Auto, Cast, Replay (Wrapped-style stories), music recognition.
**Phase D (premium)**: animated canvas, automix (beat detection on-device), lyric translation, Wear OS remote, TV layout.

## 5. Gaps none of the four fills (the differentiators)
1. **Sitara Sync**: an optional end-to-end-encrypted sync of library, likes, history and settings across devices via the user's own storage (WebDAV, Google Drive app folder or a Git gist). No server of ours.
2. **Remote control (Connect-style)**: control phone playback from another device on the LAN (mDNS + a small WebSocket API), with a web remote page.
3. **Smart offline**: auto-download the next N songs of your top mixes on Wi-Fi + charging, and a "commute mode" that pre-caches today's likely queue.
4. **Exportable downloads**: optionally save as real files in Music/ with tags and lyrics (.lrc), shareable to other players.
5. **Per-song sound profile**: remember volume offset, EQ preset and start/end trim per track; loudness matched to −14 LUFS without pumping.
6. **Source health dashboard**: shows which resolver works and auto-fails over (NewPipe, InnerTube clients, local cache), with one-tap diagnostics export. It turns the #1 complaint into a visible feature.
7. **Accessibility-first and reduced-motion design**, plus a battery-aware animation governor (instead of BitChord's all-or-nothing High Performance mode).
8. **Reproducible, fully FOSS build** with a public test suite and screenshot tests (Metrolist's top complaint).
9. **Multilingual lyrics with transliteration** (Bangla, Hindi, Urdu to Latin) for singing along.

## 6. Non-goals and legal
- No ad-blocking claims; no Premium-only YouTube features that require circumventing DRM; no copyrighted assets bundled. A disclaimer says "Not affiliated with YouTube or Google".
- No secrets in the repo; signing keys only as GitHub Actions secrets; gitleaks in CI and before every push.

## 7. Definition of done for v0.1.0
- CI green on main (lint, unit, screenshot, assembleDebug).
- A debug APK artifact, plus a signed release APK on tag when the signing secrets exist.
- README homepage: banner, badges (CI, licence, release, Kotlin, Compose), 4+ screenshots generated by Roborazzi, a GIF of the player sheet motion (captured on CI emulator if feasible), feature list, architecture diagram (Mermaid), install section (APK, Obtainium), build-from-source, disclaimer.
- GitHub Pages site with the same story.
- docs/ARCHITECTURE.md, docs/DESIGN.md (motion and colour system), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CHANGELOG.
