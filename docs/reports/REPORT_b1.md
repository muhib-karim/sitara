# b1 report: Phase B slice 1, real music

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. No tag or release was made.

Sitara now searches YouTube Music and plays the results. Search uses NewPipeExtractor v0.26.5 in a new
`:source:youtube` module. Home, Library and lyrics still come from the built-in sample catalogue, and the app says so.

## What changed

1. **`:source:youtube` module.** NewPipeExtractor `v0.26.5`, the latest tag on 2026-10-20, is pinned in
   `gradle/libs.versions.toml`. JitPack is declared with `exclusiveContent`, so only `com.github.TeamNewPipe`
   resolves from it. `OkHttpDownloader` (OkHttp 5.5.0) adds a browser user agent when none is set and keeps
   multi-value headers. A POST with no payload sends an empty body, and HTTP 429 becomes `ReCaptchaException`.
   `installNewPipe()` runs once in `SitaraApplication.onCreate`. The app enables core library desugaring
   (`desugar_jdk_libs_nio` 2.1.5), because the extractor calls `List.of` and `Map.of` (API 30+) and Sitara supports
   API 26. R8 keep rules for Rhino and the time-ago patterns are in `app/proguard-rules.pro`.
2. **`YouTubeStreamResolver`.** It rejects any track id that is not an 11-character video id before making a
   request. It loads `StreamInfo` (interruptible, on IO) and picks the stream with the pure function
   `selectAudioStream`:
   - progressive https only, in opus/webm, webm or m4a
   - original audio before dubbed or described tracks
   - highest bitrate, with opus ahead of m4a on a tie
   - reports the mime type and bitrate, and the expiry from `expire=`

   Errors propagate unchanged to `FallbackStreamResolver` and `StreamUriResolver`.
3. **`YouTubeMusicSource.search`.** Uses the extractor's `music_songs` filter. The pure mapper `StreamInfoItem.toTrack()`
   sets the video id, title, artist and duration. It takes the largest thumbnail, upgraded to `=w544-h544`, and
   stable generated cover colours. Duplicate songs are removed, because list rows are keyed by video id. An empty
   result list that comes with extractor errors is raised as a failure, not shown as "nothing found".
   `SearchSource` was split out of `MusicSource`, so a search-only source does not fake a home feed or library.
4. **Search UI and app wiring.**
   - `searchStates()` in `:feature:search` waits 300 ms after typing on the trimmed query and cancels a search that a
     newer query replaces. It goes idle at once when the query is cleared and re-runs on retry.
   - SearchScreen shows Searching…, results, a "nothing found" message, or a failure message with a "Try again"
     `PillButton`.
   - Tapping a result plays it and queues the other results after it.
   - The app's resolver is `FallbackStreamResolver(samples, YouTube)`. Samples come first because that check is local,
     so only YouTube tracks reach the network, and the sample catalogue still plays.
   - No Settings toggle was needed.
5. **Honest sample labels.** Home and Library have a one-line note saying they show built-in samples. The Search
   browse header reads "Browse the sample library", and the suggestions are genre searches instead of invented
   artist names. **Bug fixed:** `FakeMusicSource.lyrics` used to return demo lyrics for *any* track, so real songs
   would have shown fake lyrics. It now returns null for tracks outside the sample catalogue.
6. **Docs.** ADR 0004 (source choice), NOTICE and `docs/design/REFERENCES.md` (NewPipeExtractor, GPL-3.0-or-later),
   README (note, features, module graph), PLAN, ARCHITECTURE, and an "Unreleased" changelog section.

## Tests (all plain JVM, no network)

| Test | Covers |
|---|---|
| `OkHttpDownloaderTest` | GET headers and user agent, an explicit user agent kept, POST payload bytes, empty POST body, status/body/headers/final URL, 429 → `ReCaptchaException`. An OkHttp interceptor answers every call |
| `AudioStreamSelectionTest` | bitrate order, opus-over-m4a tie, m4a fallback, filtering of http/DASH/mp3/unknown/dubbed, dubbed-only fallback, unknown bitrate, empty input, bad `expire`, video-id validation |
| `SongMappingTest` | Hand-built `StreamInfoItem`s: field mapping, thumbnail choice and upgrade, missing thumbnail, stable colours, unknown duration/artist, `v=` anywhere in the query, items without a video id |
| `SearchStatesTest` | Virtual time: debounce and trim, failure then retry, a superseded search cancelled, instant idle on clear |
| `FakeMusicSourceTest` | Regression: no sample lyrics for real tracks |
| `BrowseInteractionTest` | "Try again" calls retry; stale results show Searching…; genre suggestions fill the query |
| `ScreenshotTest` | New `search_failed_dark`; Home/Library/Search baselines re-recorded |

## Commits and CI

| Commit | Change | CI run | Result |
|---|---|---|---|
| b29996d | `:source:youtube`, NewPipeExtractor, OkHttp downloader, desugaring | 39384821660 | green |
| b28b5f0 | `YouTubeStreamResolver` and `selectAudioStream` | 39385479801 | green |
| 94e6d61 | `YouTubeMusicSource` search and mapper | 39385867994 | green |
| 26c6265 | Search states, retry UI, wiring, sample labels, lyrics fix | 39386327072 | red: `flatMapLatest` took the idle branch's type (compile error) |
| 1d38e5f | Explicit `flatMapLatest<String, SearchState>` | 39386687663 | unit tests and ktlint green; screenshot step red on the 19 expected stale baselines |
| e144854 | Baselines from Record screenshots run 39386687456 | 39387182255 | green (verify and assemble ran fresh) |
| 675c06b + this report | Docs | see PROJECT_BRAIN session log | |

The first Record screenshots dispatch (39386334998) failed on the same compile error as 26c6265.

## Still sample or not done

- **Home and Library** use the built-in sample catalogue. A real home feed, album and artist pages, and a saved
  library are next.
- **Lyrics** are sample-only. Real songs show the empty lyrics state until LRCLIB lands.
- **Artwork.** Thumbnail URLs are mapped, but no image loader is bundled, so covers are generated gradients.
- **Album** is empty on search results: the extractor's song items carry no album.
- **Search** returns songs only (no albums, artists or videos).
- Search and stream regions use the extractor's default localisation (en-GB). Stream availability still follows the
  device's IP region.

## Verification and risks

- Nothing ran on this machine. CI compiled, ran ktlint and unit tests, verified screenshots and assembled the debug
  APK.
- **No live network test ran.** CI unit tests are offline by design. Search and playback against real YouTube need
  an on-device check: search for a song, play it, skip, seek, lock the screen, and turn on aeroplane mode to see the
  error and "Try again".
- **Release build not verified.** CI builds only debug. The R8 rules for Rhino and NewPipeExtractor apply first in
  `release.yml`, so check the release build (and play a song on it) before tagging.
- The extractor parses YouTube's responses. When YouTube changes them, search or playback fails with the error
  state; the fix is a version bump (ADR 0004).
- README screenshots in `docs/screenshots` predate the Home and Library notes. The Device screens workflow produces
  fresh captures.
