# b4 report: Phase B slice 4, real Home, background playback, lyrics

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.5.0`; no tag or release was made (the owner tags).

Home is real: what is trending on YouTube Charts for the listener's country, then their own recent plays, saves and
likes. Lyrics come from LRCLIB, synced where available. The queue survives the app being closed or killed, and a
headset button brings it back. The app no longer contains any sample music.

## What changed

1. **Home** (`:feature:home`, `ChartsSource` in `:source:api`).
   - Trending now: NewPipe's `trending_music` kiosk (YouTube Charts "Trending Music Videos") for the device's
     country; where YouTube Charts has no chart for it, the United Kingdom's. Shown as the paged quick-picks grid
     with "YouTube Charts · <country>". Loading, "The charts didn't load … try again" with Try again, and an empty
     chart message. The chart items go through the same song filter as radio (`songsFrom`, renamed from
     `radioTracks`): no live streams, clips or hour-long videos, no repeats.
   - From Room: Recently played, Jump back in (your playlists that have songs, then saved albums), From your likes
     (liked songs not already in Recently played). Each shelf has at most 20 items. A fresh install shows "Make Home
     yours"; the hint waits until the library has been read, so a full library never flashes it.
   - Removed: Home's mood pills and sample shelves, the "sample music" note, Search's sample browse (Search's idle
     screen keeps the suggestions), `Shelf`, `ListeningMood`, `MusicSource.home()`.
   - `:source:fake` is now `debugImplementation`: tests, screenshot fixtures and the debug capture screen only.
     Streams resolve through `YouTubeStreamResolver` alone.
2. **Room v2** (`:core:data`, auto-migration 1 → 2, new tables only): `queue_entries`, `queue_state` (one row) and
   `lyrics_cache`. `2.json` is Room's export from CI run 39416211005; `1.json` is unchanged.
3. **Playback that survives the process** (`:core:player`).
   - Pure `QueueState.toSaved` / `SavedQueue.toQueueState`: play order, unshuffled order as indices, current
     index, position, repeat; fresh entry ids on restore; a finished queue restarts its last song; a saved queue
     that does not add up restores nothing (or drops a broken shuffle order).
   - `PlaybackEngine.restore` puts it back paused at the same song and position, unless something is already
     queued. `keepSaving` saves once a change settles (1 s), on every pause, every 15 s while playing, and the
     service saves on destroy. An empty queue never overwrites the saved one. A save that only moves the position
     rewrites the one `queue_state` row, not the queue.
   - `onPlaybackResumption` plus Media3's `MediaButtonReceiver`: a headset or Bluetooth play button, or Android's
     resume card, after process death loads the saved queue and plays it from where it stopped.
   - Media items carry the cover URL, so the notification, lock screen and Bluetooth displays show the cover
     (Media3's bitmap loader fetches it).
   - Already in place and unchanged, now documented: ExoPlayer audio focus (duck, pause on transient loss, resume),
     pause on becoming noisy, previous/next/seek from system controls, and playing on after the app is swiped away.
4. **Lyrics** (`:source:lyrics`, `:feature:player`).
   - `LrclibLyricsProvider`: `GET /api/get` (cleaned title, artist, length in seconds), on 404 `GET /api/search`
     and the first same-length result (±2 s), synced before plain. Raw LRC and plain text are cached by song id:
     found lyrics for good, "none" for a week, errors never; a cache that cannot be written does not hide found
     lyrics. `User-Agent: Sitara (https://github.com/muhib-karim/sitara)`, nothing else.
   - `lyricsQuery` cleans video titles: drops bracketed video labels ("(Official Music Video)", "[Lyrics]",
     "(Visualizer)", "[4K]") and a " | …" tail, reads "Artist - Song" titles, keeps "Song - Remastered 2011", and
     drops "VEVO" from channel names.
   - JSON with nanojson, the parser NewPipeExtractor already ships (same commit), so no new runtime library.
   - `LyricsState` (Loading, Found, NotFound, Failed) replaces the sample lyrics. The Lyrics page says "Looking
     for lyrics…", shows the lyrics (synced lines still highlight, follow playback and seek on tap) with "Lyrics
     from LRCLIB" under the last line, "No lyrics found", or an error with Try again.
5. **Docs**: CHANGELOG "Unreleased", README (status, features, screenshots note, diagram), PLAN Phase B row,
   ARCHITECTURE (module map, source rules, playback persistence and resumption, a Lyrics section, testing table).

## Tests (JVM and Robolectric, no network), written before the code

| Test | Covers |
|---|---|
| `HomeFeedTest` | Pages of four; From your likes leaves out recent songs; Jump back in skips empty playlists, playlists before albums; shelf caps; fresh install; a built feed has read the library |
| `SongSelectionTest` | Radio filters (renamed); chart label "YouTube Charts · India" |
| `FakeMusicSourceTest` | The sample chart (fixture) |
| `PlaybackStoreTest` (in-memory Room) | Nothing saved on a fresh install; a queue with a repeated song round-trips; shuffle order kept; replace and clear; a position-only save; lyrics cache per song and replaced by a later lookup |
| `SavedQueueTest` | Empty saves nothing; songs, index, position, repeat kept; finished queue restarts; restored entries unique; shuffled queue unshuffles to the original order after a restart; bad data restores nothing or drops the shuffle |
| `PlaybackEngineTest` | Restore paused at song and position with repeat, nothing reported as started; restore never replaces music already started; the saved queue reads the player position; save after a change settles, on pause and while playing, nothing while paused, never an empty queue |
| `Media3PlayerPortTest` | Items carry title, artist, album and cover URL (none when there is no image) |
| `PlaybackServiceTest` (Robolectric) | The real service restores the saved queue paused at the saved position and saves it on destroy |
| `LrclibLyricsProviderTest` (OkHttp interceptor) | Request parameters and User-Agent; synced parse with line ends; plain fallback; search fallback picks synced of the same length; no same-length result finds nothing; "none" re-asked after a week, not before; cache hit makes no request; 503 throws and caches nothing; cache write failure still shows lyrics; instrumental has none |
| `LyricsQueryTest` | Clean titles kept; video labels dropped, other brackets kept; "Artist - Song"; versions after a dash kept; VEVO |
| `LrcParserTest` | An LRCLIB-shaped fixture file: negative offset, a line with two timestamps, a blank instrumental break, `[length:]` metadata |
| `BrowseInteractionTest` | Failed chart Try again; fresh install hint; Home shelves play from the tapped song and open your playlist; quick-pick paging on the chart; search suggestions; last rail clears the dock at 2x text |
| `PlayerInteractionTest` | Failed lyrics Try again |
| `ScreenshotTest` | New: `home_loading`, `home_chart_failed`, `home_fresh`, `search_idle` (dark, light), `lyrics_loading`, `lyrics_failed`; changed: Home, mini player, lyrics (credit, "No lyrics found"); removed: `home_focus`, `search_browse` |
| `NetworkSmokeTest` (live, non-blocking job) | The trending chart lists at least 10 songs; in CI run 39416780197 it returned the United Kingdom chart |

## Commits and CI

| Commit | Change | CI run | Result |
|---|---|---|---|
| 0fd6d61 | Home chart and shelves; sample catalogue out of the app | 39414742892 (CI), 39414742895 (Device screens) | CI red as expected on stale screenshots, plus one ktlint import order (fixed in 33c16a3); unit tests green; Device screens green |
| c95ab75 | Baselines from Record screenshots 39415198484 (10 changed, 5 new; Home, failed chart and fresh install inspected) | 39415669084, 39415669041 | build job green; ktlint red on the same import order |
| 33c16a3 | Room v2, queue save/restore, resumption, covers; import order fix | 39416211005, 39416210727 | red: `PlaybackServiceTest` read `sessions`, which stay empty until a controller connects (77 of 78 tests green); Device screens green |
| 9caf379 | Service test reads the engine; Room v2 schema from 39416211005 | 39416780197 (CI), 39416780037 (Device screens), 39416780143 (Secret scan) | green |
| 689ad81 | LRCLIB lyrics, Lyrics page states | 39417448922 (CI), 39417448941 (Device screens); Record 39417497764 | red: `CachedLyrics` comes from another module, so Kotlin would not smart-cast its properties |
| 2a4dc1d | Read the cached text into locals | 39417965535, 39417965233 | CI red only on the 5 expected lyrics screenshots (every unit test green); Device screens green |
| 360a64f | Lyrics baselines from Record screenshots 39418404851 (3 changed, 2 new; failed and synced-with-credit inspected) | 39418882643 (CI), 39418882580 (Device screens), 39418882567 (Secret scan) | green |

## Still missing or not verified

- **On a device:** real playback through the restored queue, the notification and lock screen with the cover,
  ducking and pausing for calls, pausing when headphones or Bluetooth disconnect, a headset play button after the
  app was killed (playback resumption), the resume card after a reboot, and LRCLIB lyrics for real songs.
- **Covers in the app** are still generated gradients; only system controls show the real image (an image loader
  is the next slice).
- **Chart artists** come from the video's channel, so some read like "TateMcRaeVEVO". Chart titles are YouTube's
  video titles ("(Official Music Video)"); lyric lookups clean both.
- **Songs from the sample catalogue** saved in 0.4.0 or earlier stay in the library but can no longer play.
- **Restore on app start** prepares the current song, which resolves its stream once even if the listener never
  presses play.
- The release build (R8) is not exercised by CI.

## Owner checklist before tagging 0.5.0

1. Home: the chart loads with your country's name; airplane mode shows the error, Try again recovers.
2. Play, like and save a few things: Recently played, Jump back in and From your likes fill in; tapping plays or
   opens the right page.
3. Lyrics: a well-known song shows synced lyrics that follow along and seek on tap; an obscure one says "No lyrics
   found"; airplane mode shows Try again.
4. Lock screen and notification: title, artist, cover; previous, next and seeking work.
5. A phone call or a navigation prompt ducks or pauses and resumes; unplugging headphones or turning off Bluetooth
   pauses.
6. Swipe the app away while playing: music continues. Pause, swipe away, force-stop: reopening shows the same song
   at the same position, paused, with the same Up next and shuffle.
7. With the app killed, press play on a Bluetooth headset: the last queue resumes.
8. Release build (`assembleRelease`) installs; lyrics and the restored queue work under R8.
