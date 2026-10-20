# c2 report: Phase C slice 2, sound I (gapless, speed, pitch, skip silence, sleep timer)

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.8.0`; no tag or release was made (the owner tags).

Songs now join without a gap because the next stream is ready before it is needed. Speed and pitch can be set
independently for the session. Skip silence is a real setting. The sleep timer takes any number of minutes and can be
extended while it counts.

## What changed

1. **Gapless joins** (`:core:player`, commit `3f87869`).
   - `QueueState.followingEntry` is the entry the player moves to by itself: the next one, the first under repeat all,
     none under repeat one or at the end of the queue.
   - `PlaybackEngine` hands each new following entry to `prepareAhead` once playback is running (not for a paused,
     restored queue), and not when end-of-track sleep will stop after the current song. Each entry is handed over
     once; skips, shuffle, Up next edits and repeat changes re-evaluate it.
   - `PrefetchingStreamResolver` wraps the stream resolver with one prepared song: it resolves in the background, a
     load of that song awaits it, preparing another song cancels the old lookup, and a failed or 30-minute-old
     preparation is looked up again (stream addresses expire). Downloaded songs are skipped; they play from the cache.
   - Same-album songs with crossfade on already met through the main player's playlist (the crossfader does not arm
     for them); they get the same prefetch.
   - Tests: preparation after playback starts (not while paused), after skips and player transitions, shuffle on and
     off, play next / remove / move / append, repeat all / one / off at the last song, end-of-track sleep and its
     cancel; the resolver's hand-over, waiting on an in-flight lookup, replacement and cancellation, failure fallback
     and expiry.

2. **Speed and independent pitch** (`:core:model`, `:core:player`, `:feature:player`, commits `b71d49c`, `a75847d`).
   - `PlaybackTuning`: speed 0.5 to 2 in steps of 0.05 (snapped so presets compare equal), pitch in whole semitones
     within ±6 (ratio 2^(n/12)). Semitones, because a listener changes key in musical steps; ±6 covers half an
     octave either way without the artefacts of larger shifts.
   - Per session, as the plan says: the engine keeps it for the playback service's lifetime, through new queues,
     until Reset; nothing is saved. `PlayerPort.setTuning` sets ExoPlayer's playback parameters. The crossfade
     tail copies them, and the fade's safety deadline scales with speed.
   - Lyrics follow media time (ExoPlayer positions are media time), tested at 1.5x: each line appears when the song
     reaches it, two thirds of the way on the clock.
   - Now Playing: a compact glass button in the top bar shows the speed (`1×`, `1.25×`), in the cover accent when
     speed or pitch differ from normal, and opens a sheet with a speed slider, 0.75× to 2× presets, a pitch slider
     and Reset (shown only when something changed). All colours come from the stage theme (cover accent, AMOLED keeps
     saffron); targets are 48 dp; sliders and the button carry state descriptions for TalkBack.
   - `SitaraSheet` moved from `:feature:library` to `:core:designsystem` so the player can use it.
   - `a75847d` fixed two test compile errors CI found in `b71d49c` (see CI).

3. **Skip silence** (`:core:model`, `:core:data`, `:core:player`, `:feature:settings`, commit `163021e`).
   - `SitaraSettings.skipSilence`, off by default, stored under `skip_silence` (corrupt values fall back to off).
   - The service sets `skipSilenceEnabled` on the main player and the crossfade tail whenever settings change, which
     switches Media3's silence-skipping processor in each audio chain (after the leveller). Robolectric test against
     the real service and players.
   - Settings → Sound: "Skip silence", between Normalise loudness and Crossfade.

4. **Sleep timer** (`:core:player`, `:feature:player`, commit `c00038e`).
   - `SleepTimer` keeps its deadline as a monotonic `TimeMark` that the countdown re-reads every second; `extend`
     moves it while counting and does nothing otherwise. Cancel and end-of-track clear it. End of track is unchanged.
   - Tests on virtual time: a custom 37 minutes expires exactly then, +5 and +15 move expiry, extending in the last
     second keeps counting, no countdown means nothing to extend, cancel after extend never fires; the engine pauses
     later after an extension.
   - The top-bar timer button (accent while set) opens a sheet instead of cycling: time left with +5 min, +15 min
     and Turn off; 15/30/45 min and 1 h presets; End of this song; Custom from 1 minute to 3 hours (a continuous
     slider that rounds to minutes, with one-minute buttons for precision) and Start.

5. **Baselines and docs**: Record screenshots run for every changed screen (player and lyrics top bars, Settings) and
   three new shots (`speed_pitch`, `sleep_timer`, `player_timer_speed`); README screenshot strip, status table and
   limits; CHANGELOG Unreleased; PARITY rows for gapless, speed/pitch, skip silence and sleep timer marked v0.8.0;
   ARCHITECTURE.

## Commits

| Commit | What |
| --- | --- |
| `3f87869` | feat(playback): prepare the next song while the current one plays, for gapless joins |
| `b71d49c` | feat(player): playback speed and independent pitch for the session |
| `a75847d` | test(player): JUnit 5 for the tuning model, no setter clash in the fake player |
| `163021e` | feat(sound): skip silence, off by default |
| `c00038e` | feat(player): sleep timer sheet with custom minutes and extend |
| `3ecf0ec` | test(screenshots): record the speed and sleep timer controls and skip silence |
| (this commit) | docs: record gapless, speed and pitch, skip silence and the sleep timer (Phase C slice 2) |

## CI

- `3f87869`: CI 39465608643, Device screens 39465608545, Secret scan 39465608539, all green.
- `b71d49c`: CI 39466552780 failed to compile two tests: the new `:core:model` test used JUnit 4 (the module runs
  JUnit 5), and the fake player's `tuning` property generated a setter that clashed with `setTuning`. Secret scan
  39466552554 green; Device screens 39466552428 cancelled by the next push.
- `a75847d`: CI 39467430086 passed ktlint and every unit test (engine tuning, lyric timing at 1.5x, the speed and
  pitch sheet interactions) and failed only the screenshot comparison of the changed player screens, as expected
  before re-recording. Secret scan 39467430099 green; Device screens 39467430095 cancelled by the next push.
- `163021e`: CI 39468039281 passed ktlint and every unit test (settings round trip, the service applying skip
  silence, the Settings switch) and failed only the expected screenshot comparison (player, Settings). Secret scan
  39468039351 green; Device screens 39468039427 cancelled by the next push.
- `c00038e`: CI 39468877968 passed ktlint and every unit test (timer extend and expiry, sleep sheet interactions) and
  failed only the expected screenshot comparison plus the two new sheets without baselines. Device screens
  39468877961 and Secret scan 39468877962 green.
- Record screenshots 39468939585 green: 74 baselines, 25 updated, 3 new; speed and pitch sheet, sleep timer sheet,
  top bar with timer and 1.5x, and Sound settings inspected.
- `3ecf0ec`: CI 39469735716, Secret scan 39469736236, Device screens 39469736185, all green (no Device screens re-run needed).

## Not verified on a real phone

- Audible gaps on real albums. ExoPlayer trims encoder delay and padding where the container declares it (Opus
  pre-skip, MP4 edit lists); whether YouTube's opus and m4a streams of a live or classical album join without a click
  has not been heard. The prefetch only removes the risk of a slow stream lookup at the join.
- Speed and pitch artefacts: how Sonic's time-stretching sounds at 0.5x and 2x and at ±6 semitones on a phone
  speaker and on Bluetooth, and whether the crossfade hand-over stays clean at other speeds.
- Skip silence on real tracks: whether it trims quiet intros, fade-outs or classical rests too eagerly (Media3's
  default thresholds), and how it interacts with loudness levelling and crossfade timing.
- The sleep timer with the screen off and the phone in Doze: the countdown runs in the playback service on
  `elapsedRealtime`, but the pause landing on time after an hour in deep sleep has not been observed.
- Prefetch cost on mobile data: one extra stream lookup per song (the next one), more when skipping through a queue.
- The R8 release build with these changes (CI builds debug only).
