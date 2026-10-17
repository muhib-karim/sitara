# STUDY: Android YouTube Music clients (Oct 2026)

Apps studied (from MK's Galaxy S23 Ultra): **BitChord**, **Metrolist**, **Meld**, **Music ReVanced**.
Sources: GitHub repos (README, source tree, libs.versions.toml / build.gradle.kts, releases, top issues by comment count), Android Police review of BitChord (15 Oct 2026), ReVanced announcements and patch lists. Read 2026-10-17.

## 1. At a glance

| | BitChord | Metrolist | Meld | Music ReVanced |
|---|---|---|---|---|
| Repo | kushagrasinghx/BitChord | MetrolistGroup/Metrolist | FrancescoGrazioso/Meld | ReVanced/revanced-patches (GitHub returns HTTP 451, DMCA block since 2026-03-24) |
| What it is | Original client, Apple-Music-style | Original client (InnerTune/OuterTune lineage) | **Fork of Metrolist** + Spotify layer | Patched official YouTube Music APK |
| License | GPL-3.0 | GPL-3.0 | GPL-3.0 | GPL-3.0 patches; the patched app itself is Google's proprietary APK |
| Stars / forks | 3.4k / 328 | 13.6k / 1.2k | 1.75k / 128 | n/a |
| Latest | v1.8 (2026-10-05): Android, TV, Windows, macOS, Linux. Universal APK has ~162k downloads | v13.7.0 (2026-09-07) + nightlies; **maintenance mode** (no new features) | v0.9.2 (2026-09-28) | follows YTM versions |
| Platforms | Phone, Wear OS, Android TV, desktop (KMP shared + sharedUi), web stub | Phone (+ Android Auto, Cast) | Phone | Phone |
| Open issues | 105 | 388 | 92 | n/a |

## 2. Feature matrix
Y = yes, ~ = partial/experimental, - = no/not documented

| Feature | BitChord | Metrolist | Meld | ReVanced YTM |
|---|---|---|---|---|
| Ad-free | Y | Y | Y | Y |
| Background play | Y (foreground media session) | Y | Y | Y (patch) |
| Offline downloads | Y (embedded metadata, in-app only) | Y (download + cache) | Y | ~ (YTM's own Premium downloads stay locked) |
| Local library files | Y (folder picker) | - | - (top request) | - |
| Synced lyrics | Y **word/syllable**, 12+ providers, Apple-style animation | Y live synced + word-by-word (Better Lyrics) | Y | official YTM lyrics |
| Lyrics translation | Y | Y (AI translation) | ~ | - |
| Lyrics share card | Y | - | - | - |
| Gapless / crossfade | Y gapless + 0-12 s true crossfade | Y crossfade | Y | - |
| Automix (beat-matched DJ transitions) | ~ beta | - | - | - |
| Equaliser | system EQ + EqualizerScreen | Y | Y | system |
| Normalisation | - | Y | Y | - |
| Tempo / pitch | speed 0.5-2x | Y tempo + pitch | Y | - |
| Skip silence | Y | Y | Y | - |
| Sleep timer | Y presets + end of track | Y | Y | official |
| Per-network quality (Wi-Fi vs mobile) | Y | - | - | ~ |
| Hi-res / lossless | ~ FLAC/ALAC from pluggable module sources | - | ~ Qobuz via ISRC match, 3 fallback resolvers, opt-in | - |
| Stats for nerds (codec, bit depth) | Y | - | - | - |
| Queue reorder | Y | Y | Y | Y |
| Google account login + library sync | Y | Y two-way | Y two-way | Y (native) |
| Spotify import / integration | Y playlists + Liked | ~ import playlists | **Y deep**: Spotify home/search/recs, ISRC/fuzzy matching, manual match override | - |
| Listen together | Y (party server editor) | Y (metroserver) | Y (sync bugs) | - |
| Last.fm / ListenBrainz scrobbling | Y both | Last.fm | Last.fm | - |
| Discord Rich Presence | Y | Y | Y (Kizzy) | - |
| Music recognition (Shazam-style) | - | Y | ~ inherited | - |
| Recap / Wrapped | Y "Replay" with stories + poster share | - | - | YTM Recap |
| Animated album canvas | Y (Spotify Canvas) | - | - | - |
| Widgets | Y | Y | Y | official |
| Android Auto | Y (with lyrics) | Y (shuffle-by-artist request open) | inherited | Y |
| Chromecast | Y (added 1.8) | Y (crashes reported in 13.7) | inherited | Y |
| Wear OS / TV / desktop | Y / Y / Y | - | - | - |
| Theming | artwork-driven palette, Material 3, frosted glass (Haze), optional Liquid Glass, High-Performance animation mode | Light/Dark/Black/Dynamic + 19 presets | same as Metrolist | YTM |
| Backup / restore | Y JSON settings + history export | Y | Y | - |
| Cloud sync of app state | - | - | - | n/a |
| Translations | ~ | Weblate, many | inherited | YTM |

## 3. Why BitChord's UI wins (MK's favourite)
Read from source (`app/.../ui/`, `sharedUi/.../ui/player`) and from the review.
1. **Apple-Music look on Android.** Large artwork-first layouts, generous whitespace, SF-like type scale, and a home screen without YTM's Samples/podcast clutter. The reviewer called it "much lighter than YouTube Music".
2. **Physics, not tweens.** `PlayerSheetMotion.kt`: the mini player to full player sheet runs on a critically damped spring, ~0.45 s response, "the way Apple Music's player sheet moves". It leaves fast and settles slowly, and the artwork flies home. Velocity is carried from the drag, so the sheet follows your thumb.
3. **Glass chrome.** A floating pill tab bar (`FloatingBottomBar`, `GlassNavBar`, `GlassSelectionPill`), a frosted top bar (`FrostedTopBar`, `TopFadeBlur`, `BottomFadeScrim`), and an optional **Liquid Glass** lens/refraction surface built on a vendored backdrop library (Kyant0/backdrop, Apache-2.0 per its attribution) with runtime shaders. It sits on Haze blur, with a scrim fallback below API 31.
4. **Artwork drives everything.** `ArtworkBackdrop`: a palette extracted from the cover tints the whole player. The now-playing screen can play an animated canvas (`CanvasArtworkPlayer`).
5. **Lyrics as the hero.** Word/syllable highlighting with an Apple-style animation, a provider picker per song, translation, and shareable lyric cards (`LyricsShareCard`).
6. **Delight surfaces.** Replay (Wrapped-style stories and posters), held context menus (`HeldContextMenu`, iOS-style long-press preview), and a QR share.
7. **Refresh-rate aware.** `DisplayRefreshRates` plus a "High Performance Mode" that unlocks 120 Hz animation (the S23 Ultra is 120 Hz). The cost is battery and heat.
Weaknesses: animation is battery-heavy, the library page loads slowly (#issue), there's no swipe-down on the mini player (top issue, 8 comments), seeking drops the volume, automix pumps loudness, there's no cloud sync, and downloads stay locked in the app.

## 4. Best feature of each
- **BitChord**: UI and motion, word-synced lyrics, Replay, multi-platform (TV/Wear/desktop), per-network quality, stats for nerds, pluggable sources.
- **Metrolist**: maturity and breadth (listen together, music recognition, AI lyric translation, normalisation, tempo/pitch, 19 palettes, Weblate translations, Android Auto, Cast). It's the base that Meld and many others fork.
- **Meld**: the Spotify brain plus the YouTube body. Spotify taste drives home, search and queues (scored by artist affinity 30%, source 25%, genre 20%, recency 15%, popularity 10%, max 3 per artist), with ISRC-exact Qobuz lossless and a manual "change YouTube version" override.
- **Music ReVanced**: the official app's polish and ecosystem (account, Cast, Auto, Recap), with background play and no ads via patches. It needs re-patching whenever YouTube changes things, and the patches repo is DMCA-blocked on GitHub.

## 5. Known complaints (from issues and reviews)
- All: dependence on YouTube's internal API. Playback breaks when YouTube rotates client identities or PoToken/cipher (Meld's v0.9.x notes, ReVanced's "spoof video streams" fixes). Region locks need a VPN (Metrolist warning).
- BitChord: swipe-down on mini player, Spotify-Connect-style remote control requested, audio glitches (volume drop on seek, crossfade loop, BT disconnect), slow library, automix loudness, battery cost of high-performance mode, no cloud sync.
- Metrolist: maintenance mode (no new features). Proprietary components added in v13.0.0 broke reproducible builds (top issue, 58 comments). Cast crashes in 13.7. Sleep-timer UX requests.
- Meld: Spotify login and rate-limit failures (io_bad_http_status 2004), downloads stopping after ~31 Spotify songs, wrong match (MV audio instead of the official track), listen-together desync, no local folder.
- ReVanced: re-patching after updates, root-free install friction, playback stopping after ~1 min when the spoofing goes stale.

## 6. Technical approach
- **BitChord**: Kotlin, Jetpack Compose (BOM 2024.12 + foundation 1.10), Material 3, Media3/ExoPlayer, **InnerTubeX** AAR (YouTube Music InnerTube API, compileSdk 37), **NewPipeExtractor v0.26.3** (Utils class stripped to avoid a clash), QuickJS (signature/cipher JS), SMBJ (network shares), BouncyCastle, ONNX runtime (on-device ML, likely automix/beat analysis), Haze + vendored backdrop shaders. Kotlin Multiplatform modules `shared`/`sharedUi` reused by desktop, TV and Wear. A small backend (live stats API, party server).
- **Metrolist**: Kotlin 2.4, Compose 1.12, Material3 1.5-alpha, **Media3 1.10.1**, Hilt, Room, Ktor 3.6, Coil 3, DataStore, compose-reorderable, MaterialKolor (dynamic palettes), **innertubex v0.8.4** (MetrolistGroup/innertubex, GPL-3.0), zemer-cipher (cipher + PoToken), Cast framework, Robolectric tests, Blacksmith CI runners.
- **Meld**: the same stack as Metrolist (innertubex v0.5.2), plus a Spotify GraphQL/REST client (WebView cookie login + TOTP token), a fuzzy/ISRC matcher with a local cache, and Qobuz resolvers.
- **ReVanced**: bytecode patches (Smali/dexlib) applied by ReVanced Manager to the official APK. No own player.

## 7. Licences and reuse
- BitChord, Metrolist, Meld: **GPL-3.0**. Copying any of their code makes our app GPL-3.0 and obliges us to ship the corresponding source. Their names, logos, icons and screenshots are not reusable.
- **innertubex** (GPL-3.0) and **NewPipeExtractor** (GPL-3.0): the practical YouTube-side libraries. Linking either makes the app GPL-3.0. **Decision: our app is GPL-3.0 open source**, which fits the plan to go public as a showcase. We depend on NewPipeExtractor as a library (unmodified, with attribution) and write everything else originally.
- Kyant0/backdrop (Apache-2.0, per BitChord's attribution): reusable with a NOTICE. We prefer Haze (Apache-2.0) plus our own AGSL shaders for glass.
- ReVanced: nothing reusable (patches the proprietary app, DMCA-blocked). Its feature list is used as a checklist only.
- Policy for our repo: **original code only**. Competitors' code is read to learn approaches, never pasted. No branding or assets. Lyrics come from open providers with an API (LRCLIB first). A disclaimer says the app is not affiliated with YouTube/Google.

## 8. Five key findings
1. BitChord wins on **motion and materials**, not features: spring physics on the player sheet, glass chrome, artwork-driven colour, and Apple-style word-synced lyrics.
2. **Metrolist is the feature base everyone forks but is now frozen** (maintenance mode, reproducible-build controversy). The union of features exists, but nobody pairs it with BitChord-grade UI.
3. **Meld proves the "taste from one service, audio from another" idea**: Spotify-driven recommendations, ISRC matching, lossless fallback. It's fragile because it rides Spotify's private endpoints.
4. **The biggest shared risk is extraction breaking.** A client that isolates its stream resolver behind an interface, with health checks and fast hot-fix paths, wins on reliability.
5. **Gaps none of the four fill**: cross-device cloud sync of the library and history; Spotify-Connect-style remote control; exportable downloads; a smart offline queue (auto-download what you will play); a true per-song loudness/EQ profile; accessibility-first design; and reproducible, fully FOSS builds with a test suite.
