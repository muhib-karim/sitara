# c3 report: Phase C slice 3, sound II (equaliser, phone equaliser, per-song volume)

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.9.0`; no tag or release was made (the owner tags),
and `versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

Sitara now has its own 10-band equaliser in the player, with presets, a preamp and a limiter. It can hand the sound to
the phone's own equaliser instead, never both at once. Each song can carry its own volume trim.

## What changed

1. **Equaliser DSP** (`:core:model`, `:core:player`, commits `5f43a51`, `5a3375d`).
   - `Equaliser`: ten peaking biquads (RBJ cookbook, Q = 1.414 for one octave) at 31.25, 62.5, 125, 250, 500 Hz and
     1, 2, 4, 8, 16 kHz, ±12 dB, in direct form I with double-precision state, after a preamp (±12 dB).
   - No clicks: gains glide at 240 dB/s. Filters are re-designed every 32 frames, and the preamp is ramped per
     sample. A band back at 0 dB keeps running until its memory has caught up with its input, then drops out. CI
     caught the first version dropping it too early: a 0.008 step when switching off (`5a3375d`, reproduced first in
     a Python port of the DSP).
   - Clipping, my call: **a limiter, not automatic headroom**. Automatic headroom would cancel the preamp and make it
     an inert control. Instead, every preset carries a preamp equal to minus its largest boost, so presets add no level,
     and a peak limiter with instant attack and a 40 dB/s release holds every sample under 0.99. Boosts on a loud
     master squash rather than clip. The panel says so ("Lower the preamp if loud songs sound squashed").
   - Flat settings (off, or every gain 0) with no trim pass audio through bit for bit once the gains have glided back.
     Bands at or above 0.45 × the sample rate stay flat. A flush (seek, new stream) clears the filter memory.
   - It runs after the leveller in the existing PCM step, renamed `SoundAudioProcessor` (16-bit and float PCM), in
     both the main player and the crossfade tail.
   - Tests (`EqualiserTest`, pure DSP, no player): bit-identical pass-through for off, enabled-but-flat and
     off-with-bands. Each band at +12, −12 and +5 dB is measured at its centre within 0.5 dB, mono and stereo, 44.1 and
     48 kHz (the measured error is under 0.01 dB in the Python port). The preamp shifts by its gain. Stereo channels
     are separate. A full-scale 20 Hz–20 kHz stepped sweep and full-scale noise with every band and the preamp at +12
     never pass 1.0. Moving a band and moving the preamp mid-stream keep the largest sample-to-sample step within
     1.25× the settled one, and so does switching off, which then passes audio through bit for bit. Bands above a
     22.05 kHz stream's range are left out.

2. **Presets and custom bands** (`:core:model`, `:core:data`, `:core:player`, commit `2aada89`).
   - `EqualiserPreset`: Flat, Bass boost, Treble boost, Vocal, Rock, Pop, Classical, Electronic, Acoustic and Late night
     (less deep bass and sharp top, voices kept clear). The preset is recognised from the bands and preamp, so custom
     bands need no extra state.
   - `SitaraSettings.equaliser`, stored as `equaliser_on`, `equaliser_bands` (ten whole numbers), `equaliser_preamp`
     and `equaliser_system`. Corrupt values fall back to flat or clamp to ±12.
   - The service hands the settings to both players' equalisers as they change, so they apply while playing.
   - Tests: preset table (each adds no level, all distinct, recognised again), "moving a band or the preamp turns it
     on", range clamps, Reset; codec round trip of every field and every preset, corrupt input; Robolectric service
     test of live updates.

3. **Phone equaliser** (`:core:model`, `:core:player`, commit `f8f2676`).
   - `EqualiserSettings.builtIn()` / `systemActive()` route the sound. With "Use phone equaliser" on and an app
     answering `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` (queried in the manifest for Android 11+), the built-in
     equaliser is flat. Without such an app, the built-in one keeps the sound even if the setting is on.
   - `SystemEqualiserSession` announces the player's audio session (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, music
     content type) while the player is buffering or ready. It closes it (`ACTION_CLOSE_…`) on idle or ended, when the
     listener switches back, when the session changes and when the service ends.
   - ExoPlayer 1.10 creates its audio session in the background, so the crossfade tail adopts the main player's
     session from `onAudioSessionIdChanged`. One session means the phone's equaliser also shapes a fading-out tail.
     The session id is shared with the UI through `PlaybackEngineRegistry`, so Settings can open the phone's panel
     for it.
   - Tests: "never both" over every preset × setting × availability; the session's open/close sequence (open once,
     close on stop, a new session closes the old one, no session announces nothing); Robolectric service tests with
     and without a resolvable panel (built-in flat, shared session id, registry cleared on destroy, no broadcasts
     without a phone equaliser).

4. **Volume for this song** (`:core:data`, `:core:player`, `:app`, commits `c3fea5f`, `3da4055`).
   - `song_gain` table (track id, whole dB within ±6; 0 removes the row). Room schema 3 with an automatic 2→3
     migration (new table only). `3.json` was taken from CI's export (run 39496356192, whose only failure was the
     expected "schema is committed" check).
   - Migration test without `MigrationTestHelper` (its schema assets are unreliable under Robolectric with AGP 9). The
     test builds a real version 2 database from the committed `2.json` (tables, indices, identity hash), adds a liked
     song and cached lyrics, opens it with Room (which migrates and validates against schema 3) and checks both rows
     survived and trims work.
   - Applied with the equaliser's preamp ramp, so it glides. It applies with the bands off or the phone's equaliser
     on. The leveller runs first, so it does not undo the trim. The service follows the current song's trim live; a
     store that cannot be read plays the song untrimmed. The crossfade tail keeps the outgoing song's trim.
   - Tests: round trip, clamping, other songs untouched; DSP trim alone, with the phone equaliser, and added to the
     preamp; Robolectric service test following the current song, a change to its trim, and the next song.

5. **Equaliser screen** (`:feature:player`, `:feature:settings`, `:app`, `:core:designsystem`, commits `5aa7569`,
   `e08d408`, `6e3b862`, `e832cc9`, `b93207a`, `dc0f715`, `756b7a5`, `ce3bc02`).
   - Settings → Sound → Equaliser (a row showing Off / the preset / Custom / Phone equaliser) opens a page with Back,
     padded for the dock and the navigation bar like Settings.
   - Now Playing: the heading's Add to playlist button became **More**, a sheet with Add to playlist, Equaliser (with
     its state) and Volume for this song (with its trim). The equaliser and volume sheets open over Now Playing in the
     cover's colours (stage theme, accent at 4.5:1).
   - Panel: Use phone equaliser (only where the phone has one; while on, the bands are replaced by "Open phone
     equaliser"), an on/off switch, preset chips, ten vertical faders with dB labels, the preamp, and Reset when
     anything is off zero.
   - Faders sit in one row where each gets a 48 dp column; on phones they sit in two rows of five (lows above highs),
     so no horizontal scrolling and every column at least 48 dp wide. Each fader draws as Material's 44 dp slider;
     Compose widens its touch area to the 48 dp minimum touch target, as for every slider in the app (not asserted by
     a test: Compose reports touch bounds after the turn, so that check measured the wrong axis and was dropped).
   - Every slider announces its band and gain to TalkBack ("1 kHz", "+3 dB"); the labels around it are hidden from
     it. Nothing animates beyond the existing sheets, so reduced motion holds.
   - No inert controls: moving a band or the preamp or picking a preset turns the equaliser on.
   - New icons: Equaliser (three faders) and Volume, drawn on the 24-unit grid.
   - Tests (Robolectric, through the real shell): preset, band and preamp changes reach the player actions; all ten
     bands carry their name and gain; with the phone equaliser chosen the bands are gone and Open/Use phone equaliser
     work; without one it is not offered; the song volume sets and resets; Settings opens the page and Back returns.
     The existing playlist-picker test now goes through More.
   - Found while checking the first recorded baselines (not committed): each fader was only about 33 dp long, because
     its height sat inside the quarter turn and set its thickness (`e832cc9`); the screen-reader test now asserts
     each fader is at least 160 dp long. A minimum thickness added in `b93207a` had no effect (Material's slider sizes
     itself with `requiredSizeIn`) and was removed (`dc0f715`, `756b7a5`). Also fixed: a missing test import and a
     ktlint brace rule (`e08d408`); the "end of page" shot, which stopped with Reset under the dock instead of
     scrolling to the end (`ce3bc02`); and the
     screenshot test's tap on Settings → Equaliser. On the 891 dp capture screen that row was in the list's viewport
     but under the dock, so the tap reached the mini player (`6e3b862`); the test now checks the page opened before
     capturing.

6. **Baselines and docs**: Record screenshots 39500642822 (17 updated, 7 new, inspected; commit `c941685`); CHANGELOG Unreleased, PARITY (equaliser
   and per-song volume rows → v0.9.0), README feature table, how-it-works and known limits, ARCHITECTURE (equaliser,
   phone equaliser, schema 3), PROJECT_BRAIN, SKILLS_USED.

Decided scope: the song volume is set from Now Playing's More menu, where it can be judged by ear. The task asked for
Now Playing's overflow *or* the song menu, and the song menu was left unchanged.

## CI

| Push | Run | Result |
| --- | --- | --- |
| `5f43a51` DSP | CI 39493591626 | cancelled by the next push (one CI per branch) |
| `2aada89` presets | CI 39493951704 | 1 failure: the switch-off glide step (fixed in `5a3375d`) |
| `f8f2676` phone equaliser | CI 39494883639 | unit tests, screenshots and assemble green; ktlint: nested loops, a blank line |
| `001e311` ktlint | CI 39495743530 | green |
| `c3fea5f` trim | CI 39496356192 | unit tests (migration included) green; "Room schema is committed" failed as expected, schema 3 taken from its artifact |
| `3da4055` schema + screen | CI 39496918474 | test compile (missing import), ktlint (if/else braces) |
| `e08d408` | CI 39497358622 | equaliser end shot could not find Reset (tap under the dock) |
| `6e3b862`, `e832cc9`, `b93207a`, `dc0f715` | CI 39498036758, 39498520903, 39498956070, 39499568069 | fader length and touch checks, see 5 |
| `756b7a5`, `ce3bc02` | CI 39499987892, 39500643799 | unit tests green; only the expected screenshot diffs before the baselines |
| Record screenshots | 39497358265, 39498520344, 39498955784, 39499567734 failed on the tests above; 39498035957 and 39499988236 recorded but were not committed (short faders, end shot under the dock); 39500642822 committed | |

FINAL_RUNS

## Not verified on a real phone

- The audible result of each band and preset, and whether the presets sound as named.
- Clipping and limiter behaviour on loud masters with boosts. The limiter has no look-ahead, so a peak's first sample
  is turned down at once, which may be audible as slight distortion on heavy boosts.
- The phone equaliser hand-off on different vendors (Samsung, Xiaomi, Pixel without an equaliser app, Wavelet,
  Dolby). Not every vendor's equaliser listens for the open/close announcements. Whether the panel opens on the right
  session.
- Bluetooth output (the equaliser runs before the audio leaves the player, so it should apply; latency of slider
  changes over Bluetooth not checked).
- CPU cost of ten bands on a low-end phone, and TalkBack gestures on the rotated faders.
- The trim is picked up as the song starts, a little after the player has already begun processing it, so the first
  moment of a trimmed song can glide from the previous level.
- The phone-equaliser check runs once when the screen and the player start. Installing or removing an equaliser app
  while Sitara runs takes effect after a restart.

## Process notes

- `ponytail-review` was not invoked before two pushes (`f8f2676`, reviewed right after; `001e311`, a ktlint-only
  brace fix). Every other push was reviewed first.
- No Gradle, ktlint, tests or builds ran locally. The DSP was checked in a Python port before porting the fix; CI
  compiled and tested everything.
