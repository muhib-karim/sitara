# b5 report: Phase B slice 5, offline, sound, polish

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.6.0`, which completes Phase B; no tag or
release was made (the owner tags).

Songs, albums and playlists can be downloaded and play with no connection. Songs play at a similar volume, and can
crossfade while albums stay gapless. README and docs now describe the finished Phase B.

## What changed

1. **Downloads and offline play** (`:core:player`, `:feature:library`, `:feature:settings`).
   - Media3 `DownloadManager` + `SimpleCache` (`files/downloads`, no eviction). A download's id is the song id and
     its URI is `sitara://track/<id>`, the URI the player already loads, so downloads and playback share one cache key.
     The download data source resolves streams with the existing `StreamUriResolver` (the song is read back from the
     request), so a resumed download gets a fresh signed URL. No new dependency: downloads, the cache and its
     database ship with `media3-exoplayer`.
   - The player's data source is now a read-only `CacheDataSource` over that cache: a downloaded song is read from
     the device and never resolved; other songs stream as before and are not written to it.
   - Song details travel in the request's data (`DownloadedTrack`, versioned binary; unreadable entries are skipped),
     so the Downloads list needs no network and Room stays at version 2.
   - `SitaraDownloadService`: foreground (`dataSync`) with Media3's progress notification and Sitara's own download
     icon; no scheduler, so a download waiting for Wi-Fi keeps the service in the foreground (the only option on
     Android 12+). Opening the app resumes downloads left over from a killed process.
   - `SongDownloads` publishes `List<SongDownload>` (newest first) and maps Media3 states to `DownloadState`:
     waiting, waiting for Wi-Fi, waiting for a connection, downloading (percent), downloaded, failed. Progress is
     polled once a second only while something downloads.
   - UI: Download / Cancel download / Remove download in the song menu; Download all on album, playlist, Liked songs
     and own-playlist pages (shown while anything is missing; done or running songs are skipped); a downloaded badge
     on those pages' rows; a Downloads section at the end of the Library (count and size, per-row progress or state,
     cancel, delete, retry, tap to play the downloads in order); Settings › Downloads with "Download on Wi-Fi only"
     (on by default), storage used and Remove all downloads (asks first, inline).
2. **Sound** (`:core:player`, `:feature:settings`).
   - Normalise loudness (on by default). NewPipeExtractor v0.26.5 does not expose YouTube's loudness values (checked
     `AudioStream`, `ItagItem` and the stream extractor), so `ResolvedStream.loudnessLufs` stays empty and
     `LoudnessLeveller` measures as it plays: gated RMS (above −50 dBFS) over about 3 s as an approximation of LUFS,
     gain toward the target (−14 LUFS), at most +6 dB / −15 dB, at most 3 dB/s down and 1 dB/s up, ramped per buffer,
     never lifting a peak past −1 dBFS. Off is bit-exact. It runs in `LevellingAudioProcessor` (16-bit and float PCM)
     inside ExoPlayer's `DefaultAudioSink`.
   - Crossfade (Off, 1–12 s). `Crossfader` loads the playing song into a second ExoPlayer (no audio focus) 10 s before
     the fade; a `PlayerMessage` fires at the fade point; once the tail's output runs, both play the same audio for
     80 ms while the main player goes silent; the main player then seeks to the next item and the two fade with
     equal-power curves timed by the tail's position. The main player keeps the session, queue and saved position.
     No fade for same-album neighbours (gapless), repeat one, an end-of-song sleep stop, the end of the queue, or
     songs shorter than two fades. Pause, seek, skip, any other item change, an interruption or a settings change
     stops the tail and restores full volume; a tail not ready in time falls back to the gapless change; a stalled or
     failed tail still ends the fade on time.
   - `PlaybackDependencies.playbackSettings` feeds both levellers and the crossfader live.
   - Settings › Sound: Normalise loudness switch and a Crossfade slider (Off, 1–12 s) with a spoken state.
3. **Screenshots**: new `library_downloads` (dark, light), `album_page_downloads`, `settings_sound`,
   `settings_downloads`, `song_actions_downloaded`; changed: Library (Downloads empty state at the end), album and
   playlist pages (Download all), song menu (Download), settings.
4. **Docs**: README (status, screenshot strip from the recorded baselines, Features, How it works, Known limits,
   install from the Release APK, roadmap), CHANGELOG "Unreleased", PLAN (Phase B complete, carried-over items),
   ARCHITECTURE (module map, playback, a Downloads section, testing).

## Tests (JVM and Robolectric, no network), written before the code

| Test | Covers |
|---|---|
| `DownloadsTest` (`:core:player`) | Song details survive the download codec (quotes, symbols, missing cover and artist id stay missing); truncated, unknown-version and empty data give no song; Media3 states map to Sitara's, a queued download says whether it waits for Wi-Fi or a connection; whole percent, unknown size |
| `DownloadsTest` (`:core:model`) | Download all skips downloaded and running songs, retries failed ones, downloads a repeated song once |
| `FormatBytesTest` | kB / MB / GB as Android counts them, no "1000 kB", negative counts |
| `SettingsCodecTest` | Wi-Fi only round trip, default on, corrupt value falls back |
| `LoudnessLevellerTest` | Loud song brought to −14, quiet song lifted by at most 6 dB, a song at the target left alone, off is bit-exact, gain moves gradually, silence and quiet gaps never raise the gain, a boost never pushes a peak past −1 dBFS, turning off returns smoothly to unity, a second player carries on at the same level |
| `CrossfadeTest` | Different songs fade, an album plays gapless, nothing fades when off / at the end / on repeat one / before a sleep stop, short songs are not cut into, the tail loads 10 s ahead, the 6 s curve falls and rises at constant power |
| `PlaybackServiceTest` (Robolectric) | The real service follows normalisation, target and crossfade settings as they change |
| `BrowseInteractionTest` | Song menu downloads, retries a failed one, cancels running ones and removes a finished one; Download all offers only what is missing and badges downloaded songs; Library downloads show progress, waiting and failed, and cancel, retry, delete and play; Settings show storage, toggle Wi-Fi only and ask before Remove all; Sound settings toggle normalisation and set crossfade |
| `ScreenshotTest` | The new and changed screens listed above |

## Commits and CI

| Commit | Change | CI run | Result |
|---|---|---|---|
| 14dfd28 | Downloads and offline play | 39422848265 (CI), 39422848339 (Device screens), 39422848419 (Secret scan) | CI red only on the 17 expected screenshot diffs (all unit and interaction tests green); Device screens and Secret scan green |
| a0ae37f | Loudness levelling and crossfade | 39423949645 (CI), 39423949473 (Secret scan); Record 39423969287; Device screens 39423949428 cancelled by the next push | red: the b2-era `settingsOnlyExposeImplementedControls` still asserted that no Crossfade control exists (121 of 122 app tests green; everything compiled); Secret scan green |
| d4106e5 | That test keeps only its no-placeholder check (the new Sound test covers the controls) | 39424550707 (CI), 39424550750 (Secret scan); Record 39424550720 | Record green with every test passing; CI red only on the expected screenshot diffs |
| f0634b0 | Baselines from Record 39424550720 (12 changed, 5 new; library downloads, settings sound and downloads, both song menus inspected); album page fixture moves downloaded songs above the dock | 39425092368 (CI), 39425092427 (Secret scan); Record 39425092099 | CI red only on `albumPageDownloads`, re-recorded by 39425092099 (only that file differed, so the other 17 reproduce exactly) |
| b402641 | `album_page_downloads` baseline, README, docs, screenshot strip, this report | 39425619405 (CI), 39425619518 (Secret scan), 39425619566 (Pages), 39425619380 (Device screens) | CI, Secret scan and Pages green. Device screens captured all ten screens, then failed saving logcat: `tools/capture-device-screens.py` decoded it as UTF-8 and a stray byte broke it (a latent bug, unrelated to the app); the following commit saves logcat as raw bytes |

One process slip: d4106e5 (a one-line test change) was pushed before its ponytail-review; the review ran right after
and found nothing. Every other push was reviewed first.

## Still missing or not verified

- **On a device:** downloading over Wi-Fi and its wait on mobile data; playing downloads in airplane mode; the
  download notification and finishing after the app is closed; resume after a force-stop; Remove all; how levelling
  sounds across loud and quiet songs; the crossfade hand-over (a few milliseconds of overlap of the same audio, which
  may be audible on some devices); that album playback stays gapless; crossfade with the screen off.
- **Bot checks**: downloads use the same stream extraction as playback, so a network that YouTube bot-checks cannot
  download either. CI cannot exercise real downloads or streams.
- **Crossfade loads the outgoing song twice**: the tail resolves the stream again (one extra extraction per fade,
  none for downloaded songs). The tail does not duck for navigation prompts during its few seconds.
- **No storage cap or eviction** for downloads, and no "download liked songs automatically".
- **Search results** show no downloaded badge yet (album, playlist and Library rows do).
- **Carried over from Phase B**: artwork images in the app, resolver health checks and the source health screen,
  local files, import and backup, Hilt.
- The release build (R8) is not exercised by CI.

## Owner checklist before tagging 0.6.0

1. Wi-Fi on: download a song from its menu and an album with Download all; the Library shows progress, then sizes;
   the album rows show the badge. Close the app mid-download: the notification keeps going and finishes.
2. Mobile data with "Download on Wi-Fi only" on: a new download says "Waiting for Wi-Fi"; turn the setting off and it
   starts.
3. Airplane mode: downloaded songs play, seek and skip between each other; a song that is not downloaded fails as
   before.
4. Delete one download from the Library, then Remove all in Settings: storage used returns to "Nothing downloaded".
5. Normalise loudness: alternate a quiet and a loud song; the jump is smaller with it on; turning it off changes
   nothing abruptly.
6. Crossfade at 6 s: a playlist of singles overlaps; an album plays gapless; pausing or skipping mid-fade stops the
   old song at once; a call during a fade pauses everything; the notification and lock screen show the new song.
7. Kill and reopen during or after a crossfade: the restored queue is at the right song and position.
8. Release build (`assembleRelease`) installs; downloads and crossfade work under R8.
