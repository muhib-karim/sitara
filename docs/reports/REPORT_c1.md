# c1 report: Phase C slice 1, real artwork and a player that looks like the music

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.7.0`; no tag or release was made (the owner tags).

Covers inside the app are real now, Now Playing takes its colours from the cover, and lyrics can be scrolled freely
with a way back to the current line. The device screenshot job no longer gives up on a slow emulator.

## What changed

1. **Device capture hardening** (`tools/capture-device-screens.py`, commit `ea05949`).
   - `wait_for_screen` waits on the clock (90 s for the first screen, 30 s after) instead of 20 polls.
   - While waiting it taps "Wait" on a system "isn't responding" dialog (`anr_wait_button`, curly or straight
     apostrophe), and `launch_and_wait` launches a screen once more if it misses its deadline. An adb timeout during
     a dump is retried, not fatal.
   - Tests: the matcher (dialog, both apostrophes, a lone "Wait" label is no dialog), the tap during a wait, the
     relaunch.

2. **Real covers everywhere** (`:core:designsystem`, `:app`, commit `a7d5d52`).
   - Coil 3.6.3 (`coil-compose`, `coil-network-okhttp` over the OkHttp the app already ships). `SitaraApplication`
     is the `SingletonImageLoader.Factory`; `sitaraImageLoader` keeps Coil's memory and disk caches and adds one
     mapper.
   - `ArtworkImage` is the single cover composable every screen already used (Home, Search, Library, detail pages,
     song sheets, Up next, mini player, Now Playing, the blurred stage backdrop), so all of them load real art. It
     draws the generated gradient first and the image on top, so loading, failed, offline and image-less covers
     show the gradient. Covers are decorative for TalkBack, as before.
   - Pure, unit-tested selection in `CoverUrls.kt`: `sizedCoverUrl` asks `googleusercontent`/`ggpht` servers for the
     smallest bucket covering the measured slot (120/240/544/1080 px; 544 when unknown), other hosts untouched;
     `needsSquareCrop` picks `i.ytimg.com/vi/` video thumbnails; `videoThumbnailSquare` centres a square on the 16:9
     picture, skipping hqdefault's letterbox bars; `coverUrlOf` treats a blank URL as none. The mapper runs after
     Coil measures the slot, so a 48 dp row and the sleeve fetch different sizes.
   - Fixtures: sample catalogue artworks carry `sample:cover/…` URLs that the debug `sampleImageLoader` draws locally
     from their colours (flat fields and an orbit ring, visibly unlike the gradient). Screenshot tests and the
     device capture use it; no test touches the network. It reports covers as memory-cache hits, so Coil never runs
     its wall-clock crossfade during a capture. Any other URL fails, which `player_cover_failed` records.

3. **Artwork-coloured player** (`:core:designsystem`, `:feature:player`, commit `f6e4265`).
   - `rememberArtworkTheme` loads a 128 px copy through the same loader (`allowHardware(false)`), runs
     `androidx.palette` 1.0.0 on the loader's decoder dispatcher (IO in the app, inline in tests), sorts swatches by
     population and feeds them to the existing `ArtworkPalette.themeFrom`. `ArtworkPalette.stageTheme` falls back to
     the artwork's own colours when there is no cover or it cannot load. Themes from loaded covers are kept in a
     32-entry LRU cache, so opening the player or revisiting a page starts in the right colours; a failed load is
     not cached and is retried on the next visit.
   - The accent now walks its lightness until it passes 4.5:1 against the backdrop as well as under its own text.
     A test sweeps 24 hues × 3 saturations × 3 lightnesses plus white, black, mid grey (#777777, which fails with
     both white and near-black text as it is), saturated yellow and blue, in both modes.
   - The theme drives the backdrop wash, the sleeve glow, the mini player tint and the stage's `primary`: scrubber,
     shuffle, repeat and the like heart. Colours fade over 400 ms between songs and snap under reduced motion. AMOLED
     keeps saffron. Detail page headers use the same backdrop, so they take the cover's palette too.

4. **Lyrics follow mode** (`:feature:player`, commit `b3b8ee9`).
   - A drag on synced lyrics (a `DragInteraction.Start` on the list, which programmatic scrolls never emit) stops
     the list following the playing line; a "Back to current line" pill scrolls to it and follows again. Tapping a
     line still seeks and leaves the mode as it is (resuming on tap would first jump to the stale line before the
     seek lands). Follow mode resets with each song.
   - `LyricsFollowTest` (Robolectric): no pill while following; a hand scroll keeps the list still while the song
     moves on; the pill returns to the last line; a tapped line seeks without moving the list.

5. **Baselines and docs** (commits listed below): every screen with a cover re-recorded through the Record
   screenshots workflow, a new `player_cover_failed` baseline, README screenshot strip and status, CHANGELOG
   Unreleased, PARITY rows for artwork and lyric follow marked v0.7.0, ARCHITECTURE module rows.

## Commits

| Commit | What |
| --- | --- |
| `ea05949` | ci(device): wait longer and dismiss launcher ANR |
| `a7d5d52` | feat(artwork): real covers everywhere, with the gradient as placeholder and fallback |
| `f6e4265` | feat(player): Now Playing takes its colours from the cover |
| `b3b8ee9` | feat(lyrics): scrolling by hand pauses follow mode until Back to current line |
| `cb3d295` | test(screenshots): record covers, artwork colours and the failed-cover baseline |
| (this commit) | docs: record real artwork, the artwork-coloured player and lyric follow (Phase C slice 1) |

## CI

- `ea05949`: CI 39456046931, Secret scan 39456046881, Device screens 39456046745, all green (the capture's new unit
  tests run in Device screens).
- `a7d5d52`: CI 39457637726 compiled, passed ktlint and every unit test, and failed only the screenshot comparison,
  as expected before re-recording; Device screens 39457638009 and Secret scan 39457637742 green.
- `f6e4265`: its runs were cancelled by the next push (CI concurrency on main); `b3b8ee9`'s run covers it.
- `b3b8ee9`: CI 39458762133 passed ktlint and every unit test (palette sweep, cover URLs, lyrics follow) and failed
  only the stale screenshot comparison; Secret scan 39458761948 green; its Device screens run was cancelled by the
  baselines push.
- Record screenshots 39458775762 green: 71 baselines (70 updated or unchanged, `player_cover_failed` new); home, player, failed-cover and blurred-stage shots inspected.
- `cb3d295`: CI 39460003902, Secret scan 39460003860, Device screens 39460003949, all green. The device capture on the API 34 emulator shows covers drawn by Coil, the blurred real-cover backdrop and the cover-coloured accent.

## Not verified on a real phone

- Real cover loading over Wi-Fi and mobile data, and how fast lists fill on a slow connection.
- Disk cache size on a device. Coil's default is 2% of free space, between 10 MB and 250 MB. On a nearly full
  phone, art for a long-unplayed downloaded song can be evicted, and offline it then shows the gradient. Pinning
  art to downloads belongs with portable downloads (c5).
- Palette extraction speed on a low-end phone (128 px, off the main thread).
- The release build under R8: Coil ships consumer keep rules, but CI builds debug only.
- Lyric follow with a real finger: fling feel, and that TalkBack scroll actions (which are not drags) keep
  following, by design.
- Seen in passing, not changed: shuffle on/off is shown on screen by colour alone (TalkBack gets the state);
  worth an icon change in the c10 accessibility pass.
