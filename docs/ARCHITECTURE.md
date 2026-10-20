# Sitara architecture

This document describes how Sitara is put together: the module map, how data moves from a music source to the screen, how playback is wired to Media3, how the stream source stays reliable, and how the code is tested and shipped.

> Status: Phase B, slice 4. Home's trending chart, search, artist/album/playlist pages, radio and streams are live (YouTube through NewPipeExtractor); lyrics come from LRCLIB; the library, the play queue and fetched lyrics are stored on the device with Room. The app contains no sample data; the sample catalogue is a test and debug fixture. Modules marked *planned* do not exist yet.

## Goals

1. **Swap the source, keep the app.** Features talk to interfaces in `:source:api`, never to a concrete backend. A change on the streaming side is a one-module fix.
2. **Pure logic stays pure.** Queue rules, crossfade curves, loudness gain, sleep timer and the LRC parser are plain Kotlin with no Android types, so they are fast to test.
3. **One way to do each thing.** One motion object, one theme, one player controller, one settings store.
4. **Everything visible is tested.** Screens are covered by Roborazzi screenshot tests that also produce the README images.

## Module map

| Module | Kind | Responsibility |
| --- | --- | --- |
| `:app` | Android application | Activity, app shell (glass tab bar, mini player dock, detail back stack of typed `DetailRoute`s), dependency wiring |
| `:core:model` | Pure Kotlin | Domain types: `Track`, `Album`, `Artist`, `Playlist`, `Queue`, `LyricsLine`, `LyricsWord` |
| `:core:common` | Pure Kotlin | Result types, coroutine dispatchers, time and formatting helpers |
| `:core:data` | Android library | DataStore-backed settings; the on-device store (`LibraryStore`): repositories for liked songs, play history, saved albums and artists, playlists, the saved play queue and the lyrics cache, implemented with Room, whose schema is exported to `core/data/schemas` |
| `:core:player` | Android library | `PlaybackService` (Media3 session + ExoPlayer), `PlaybackEngine`, `PlayerController` facade, queue engine and its save/restore, controller/stream policies, crossfade curves, LUFS gain, sleep timer |
| `:core:designsystem` | Android library | Theme, colour roles, artwork palette mapping, `SitaraMotion`, glass surfaces, typography, icons, shared components |
| `:source:api` | Pure Kotlin | Contracts: `SearchSource`, `ChartsSource`, `MusicSource`, `BrowseSource` (artist, album, playlist pages), `RadioSource`, `StreamResolver`, `LyricsProvider`, `HealthCheck` |
| `:source:fake` | Pure Kotlin | Deterministic sample catalogue with generated gradient covers: a fixture for tests, screenshots and the debug capture screen (`debugImplementation` only, never in a release build) |
| `:source:lyrics` | Pure Kotlin (JVM) | LRC and enhanced (word-timed) LRC parser; `LrclibLyricsProvider` (LRCLIB over OkHttp, cached through a `LyricsCache`) and the title clean-up for lyric searches |
| `:feature:home` | Android library | Home: the trending chart (loading, error, retry) and shelves from the library: Recently played, Jump back in, From your likes |
| `:feature:search` | Android library | Search field, suggestions, results; debounced search states with loading, error and retry |
| `:feature:library` | Android library | Library grid/list, and the artist, album and playlist detail pages with loading, error and empty states |
| `:feature:player` | Android library | Mini player, full player sheet, scrubber, Up next / Lyrics pager, karaoke lyrics |
| `:feature:settings` | Android library | Theme mode, reduced motion, playback preferences |
| `:source:youtube` | Pure Kotlin (JVM) | YouTube Music search (songs, albums, artists, playlists), artist/album/playlist pages, radio from related songs, the YouTube Charts trending list (NewPipe's `trending_music` kiosk), YouTube link parsing and audio stream resolution on NewPipeExtractor, with an OkHttp downloader ([ADR 0004](adr/0004-youtube-source-newpipeextractor.md)) |
| `:source:local` | *planned, Phase B* | MediaStore and folder-based local files |

## Dependency graph

Arrows point from a module to the modules it depends on (direct project dependencies only). Feature modules never depend on each other; anything shared moves down into a core module. `:core:common` is a leaf module with no project dependencies.

```mermaid
graph TD
    app[":app"]

    subgraph feature
        home[":feature:home"]
        search[":feature:search"]
        library[":feature:library"]
        fplayer[":feature:player"]
        settings[":feature:settings"]
    end

    subgraph core
        designsystem[":core:designsystem"]
        cplayer[":core:player"]
        data[":core:data"]
        common[":core:common"]
        model[":core:model"]
    end

    subgraph source
        api[":source:api"]
        fake[":source:fake"]
        lyrics[":source:lyrics"]
        youtube[":source:youtube"]
    end

    app --> home & search & library & fplayer & settings
    app --> cplayer & data & youtube & lyrics & common
    app -. debug and tests .-> fake
    home & search & library & fplayer & settings --> designsystem
    home & search & library & fplayer & settings --> model
    fplayer --> lyrics
    settings --> data
    cplayer --> api & model
    data --> model
    designsystem --> model
    fake --> api & lyrics
    youtube --> api
    lyrics --> api
    api --> model
```

Rules enforced in review:

- `:core:model`, `:core:common`, `:source:api`, `:source:fake`, `:source:lyrics` and `:source:youtube` stay free of Android dependencies.
- Only `:app` chooses concrete sources. Home's chart, search, detail pages, radio and streams use YouTube (`YouTubeMusicSource`, `YouTubeStreamResolver`); lyrics use `LrclibLyricsProvider`; the Library tab and Home's shelves read only `LibraryStore`. The sample source is a fixture: it reaches only debug builds and tests.
- UI modules depend on `:core:designsystem` for every colour, shape, spring and duration. Hard-coded values in feature code are a review comment.

## Data flow

Sitara follows unidirectional data flow. State flows down as immutable objects; user intent flows up as events.

```mermaid
flowchart LR
    src["MusicSource<br/>(:source:api)"] -->|suspend calls| vm["AppViewModel"]
    vm -->|StateFlow&lt;UiState&gt;| ui["Compose screens"]
    ui -->|events| vm
    vm -->|commands| pc["PlayerController facade<br/>(:core:player)"]
    pc -->|buffered until ready| eng["PlaybackEngine<br/>(in PlaybackService)"]
    eng -->|StateFlow&lt;PlaybackSnapshot&gt;| vm
    ds["DataStore settings<br/>(:core:data)"] -->|Flow| vm
    lib["LibraryStore (Room)<br/>(:core:data)"] -->|Flow| vm
    eng -->|song started| lib
```

1. **Source.** A `MusicSource` exposes suspending calls and flows for home shelves, search, albums, playlists and track metadata, returning `:core:model` types.
2. **ViewModel.** In Phase A one `AppViewModel` combines the fake source, settings and playback state into immutable UI state exposed as `StateFlow`. Repositories with caching arrive with the real source in Phase B.
3. **Errors.** `:core:common` provides an `Outcome` type for results that can fail.
4. **Compose.** Screens are stateless functions of `UiState` plus an event callback. This is what makes them easy to preview and screenshot-test with fake state.
5. **Settings.** Theme mode, reduced motion and playback preferences live in DataStore and are observed as flows, so changes apply instantly without restarts.

### Playback

```mermaid
sequenceDiagram
    participant UI as Player UI
    participant PC as PlayerController (facade)
    participant E as PlaybackEngine
    participant X as ExoPlayer
    participant R as StreamUriResolver
    UI->>PC: play(tracks, index)
    PC->>E: play (buffered by EngineGate until the service is up)
    E->>E: QueueState.setQueue (entries with unique ids)
    E->>X: setMediaItems(sitara://track/<id> for every entry)
    X->>R: open item (loader thread)
    R-->>X: https stream URL from the StreamResolver
    X-->>E: onMediaItemTransition / ENDED / pause at item end
    E-->>UI: PlaybackSnapshot flow (queue, position, playing)
```

- **The service owns playback.** `PlaybackService` creates ExoPlayer and a `PlaybackEngine` and keeps them for as
  long as the service lives. Screens only attach and detach, so auto-advance, system Next/Previous (notification,
  Bluetooth, Android Auto) and the sleep timer keep working with no UI.
- **`PlayerController` is a facade.** Connecting a `MediaController` binds the service. Commands go to the engine
  through `EngineGate`, which buffers anything sent before the engine exists and replays it once. Reconnecting never
  reloads the player.
- **The queue engine is pure Kotlin** (`QueueState`). Entries carry unique ids, so a track queued twice is
  unambiguous. Shuffle keeps the original order, so turning shuffle off restores it with the same entry selected.
  The engine mirrors the queue into ExoPlayer as a playlist: shuffling reorders around the playing item without
  reloading it, and repeat modes map onto the player's.
- **Streams are resolved lazily.** Items use `sitara://track/<id>` URIs; a `ResolvingDataSource` asks the
  `StreamResolver` for a URL when ExoPlayer opens the item. Only `sitara://` and https may be loaded (`StreamPolicy`).
- **The session is filtered.** `ControllerPolicy` gives the app (identified by binder uid) full access, gives system
  surfaces transport controls only, and rejects other apps. External media items are refused.
- **The queue survives the process.** `PlaybackService` asks the app for the saved queue when it starts and
  `PlaybackEngine.restore`s it paused at the same song and position, unless the listener already queued something.
  `keepSaving` hands the app the queue once a change settles (1 s), whenever playback pauses, every 15 s while
  playing and when the service is destroyed; an empty queue never overwrites the saved one. The conversion
  (`QueueState.toSaved` / `SavedQueue.toQueueState`) is pure: play order, the unshuffled order as indices, the
  current index, position and repeat mode, with fresh entry ids on the way back. Room keeps it in `queue_entries`
  and a one-row `queue_state`; a save that only moves the position rewrites that one row.
- **Resuming without the app.** `MediaButtonReceiver` starts the service for a headset or Bluetooth play button,
  and `onPlaybackResumption` restores the saved queue and hands its items to Media3 (also for Android's resume card).
- **System controls.** Media items carry title, artist, album and the cover URL; the session's bitmap loader fetches
  the cover for the notification, lock screen and Bluetooth displays. ExoPlayer handles audio focus (ducks, pauses
  for a call and resumes after it) and pauses when the output becomes noisy (headphones unplugged, Bluetooth gone).
  The service keeps running when the app is swiped away while music plays, and stops if it is paused or empty.
- **The sleep timer** counts minutes on `elapsedRealtime` (monotonic), or pauses at the end of the current item using
  ExoPlayer's pause-at-end.
- **Crossfade** and **normalisation** are pure functions (fade curves; gain toward −14 LUFS, capped by the true
  peak). They are tested but **not yet applied to playback**: the settings are disabled in the UI until a Media3
  `AudioProcessor` chain lands in Phase B.
- **Lyrics** are parsed into timed lines and words, keeping the original text and syllables. The UI derives the active
  line and the karaoke character sweep from the position; unsynced lyrics are shown as static text.

### Lyrics

```mermaid
flowchart LR
    vm["AppViewModel<br/>(current song changes)"] --> p["LrclibLyricsProvider"]
    p -->|"by song id"| c["LyricsCache<br/>(Room lyrics_cache)"]
    p -->|"miss or stale"| g["GET /api/get<br/>title, artist, length"]
    g -->|404| s["GET /api/search<br/>title, artist"]
    s --> m["bestMatch: same length ±2 s,<br/>synced first"]
    p --> lrc["LrcParser"] --> ui["LyricsState: Loading / Found / NotFound / Failed"]
```

- `LyricsProvider` (`:source:api`) returns parsed `Lyrics`, null for "none", and throws when the service fails.
- `LrclibLyricsProvider` cleans the video title first (`lyricsQuery`: drops "(Official Video)"-style labels and a
  " | …" tail, reads "Artist - Song" titles), asks `/api/get` and falls back to `/api/search`. It stores the raw
  LRC and plain text, so a parser change never needs a cache migration. Found lyrics stay cached; "none found" is
  asked again after a week; errors are not cached. Requests carry `User-Agent: Sitara (<repository URL>)` and
  nothing else about the device or listener.
- The view model looks lyrics up when the current song changes (a retry asks again for the same song); the
  Lyrics page shows loading, the lyrics with an "LRCLIB" credit, "No lyrics found", or an error with Try again.

## Stream source abstraction (Phase B)

The most common failure in streaming clients is the upstream service changing. Sitara treats that as a normal operating condition rather than an emergency.

```mermaid
flowchart TD
    req["resolve(trackId)"] --> chain["ResolverChain"]
    chain --> r1["Primary resolver"]
    chain --> r2["Secondary resolver"]
    chain --> r3["Local cache / downloads"]
    hc["HealthCheck (periodic + on failure)"] -.scores.-> chain
    chain --> out["StreamInfo or typed error"]
    chain --> dash["Source health dashboard"]
```

- `StreamResolver` turns a track id into playable stream info. Several resolvers are registered in priority order.
- Each resolver has a `HealthCheck`. Results are cached with a short time-to-live and refreshed after any failure.
- The chain picks the healthiest resolver, falls back on error, and records why. Errors are typed (network, region, parse, unavailable) so the UI can say something useful.
- A **source health dashboard** in settings shows each resolver's state and offers a one-tap diagnostics export for bug reports.
- Network-aware quality selection (Wi-Fi versus mobile) lives in the chain, not in the resolvers.

## Testing strategy

| Layer | Tools | What is covered |
| --- | --- | --- |
| Pure logic | JUnit 4/5, Turbine, coroutine test time | Queue engine (unique entries, shuffle restore, repeat), playback engine against a fake player (queue hand-off, transitions, end-of-queue restart, sleep timer, restore, save triggers), saved queue round trip (shuffle order, broken data), Home feed shelves, LRCLIB lookups against an interceptor (exact, search fallback, best match, cache freshness, errors, User-Agent), lyric title clean-up, an LRCLIB-shaped LRC fixture (offset, repeated timestamps, blank break), command gate, controller and stream policies, crossfade and LUFS maths, LRC and enhanced LRC parsing, karaoke sweep, sheet geometry, palette to theme mapping, settings codec, fake source, YouTube song mapping, audio stream selection, OkHttp downloader request/response mapping (interceptor, no network), search states |
| Library store | Robolectric, in-memory Room | Liked songs, capped history and its last-play order, saved albums and artists, playlist order, duplicates, renumbering, rename, delete cascading to entries, name validation, song details surviving storage; saved queue (repeats, shuffle order, replace, clear, position-only saves) and the lyrics cache |
| Playback service | Robolectric | The real `PlaybackService` restores a saved queue paused at its song, position and repeat mode, and saves it on destroy; media item titles and covers are covered by `Media3PlayerPortTest` |
| Screenshots | Roborazzi on Robolectric (debug variant) | Home, search, library, player, lyrics and settings in light and dark; CI fails on any pixel mismatch against the committed baselines in `app/src/test/screenshots` |

Principles:

- Tests run on the JVM; no emulator is needed for the main suite.
- The fake source is deterministic (stable ids, titles and generated covers), so screenshots are stable across runs.
- A bug fix starts with a failing test.
- Screenshot diffs are reviewed like code. An intentional UI change re-records the baselines with `screenshots.yml`, and they are reviewed and committed with that change.
- Not covered by automated tests yet: real stream playback, the player's audio attributes, audio focus and noisy-output behaviour on hardware, the notification's look, playback resumption from a media button, and gesture feel. Those need an on-device check.

## CI pipeline

All automation runs on GitHub Actions.

```mermaid
flowchart LR
    push["push / pull request"] --> ci
    subgraph ci["ci.yml"]
        k["ktlint"] --> u["unit tests"] --> s["screenshot tests"] --> b["assembleDebug"]
    end
    b --> art["artifact: sitara-debug-apk"]
    tag["tag v*"] --> rel["release.yml"]
    rel --> apk["signed APK (if signing secrets exist)"]
    rel --> sums["SHA256SUMS"]
    rel --> gh["GitHub Release"]
    main["push to main"] --> pages["pages.yml"] --> site["GitHub Pages site"]
    any["every push"] --> gl["gitleaks.yml"]
    manual["manual dispatch"] --> shots["screenshots.yml"] --> base["artifact: sitara-screenshot-baselines"]
```

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| `ci.yml` | Push and pull request | ktlint, unit tests, screenshot verification, `assembleDebug`, upload `sitara-debug-apk` |
| `release.yml` | Tags matching `v*` | Builds the release APK, signs it when the signing secrets are configured, writes `SHA256SUMS`, publishes a GitHub Release |
| `pages.yml` | Push to `main` | Publishes `site/` to GitHub Pages, copying in `docs/assets` and `docs/screenshots` |
| `gitleaks.yml` | Push and pull request | Scans for committed secrets |
| `screenshots.yml` | Manual | Records Roborazzi baselines on the CI runner image and uploads them as an artifact; a maintainer reviews and commits them |

Signing keys never live in the repository. They are provided only as encrypted GitHub Actions secrets; without them the release workflow still produces an unsigned build and says so.
