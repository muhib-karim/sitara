# Phase A fix plan: green CI and review findings

**Goal:** get the `CI` workflow green on `main` for the first time (with the debug APK artifact uploaded), and fix or
answer all 17 findings from the Phase A cross-model review.

**Architecture change:** the playback "brain" moves out of the UI process's controller facade and into the
`PlaybackService`, as a JVM-testable `PlaybackEngine` behind a small `PlayerPort` interface. The whole queue is handed
to ExoPlayer as a playlist. Items use `sitara://track/<id>` URIs that a `ResolvingDataSource` turns into stream URLs
when they load. Because of that, auto-advance, system Next/Previous and the sleep timer keep working after the UI is
gone. The UI-side `PlayerController` becomes a thin facade that buffers commands until the engine is available.

**Tech stack:** Kotlin 2.4.20, AGP 9.4.1, Gradle 9.8.1 (CI only), Compose 1.12.1, material3 1.5.0-alpha28, Media3 1.10.1,
JUnit 4/5, Robolectric 4.17, Roborazzi 1.76.0, ktlint 1.8.0.

**Inputs:** CI run 39050111148 logs (tests + ktlint jobs), `astra_review_phaseA.md`, `docs/SPEC.md`, `docs/PLAN.md`.

## Global constraints
- No Gradle, JDK install or emulator locally; CI is the compiler. Local ktlint 1.8.0 CLI is allowed.
- Original code only; GPL-3.0; no secrets; every push goes through the guarded push script; no force pushes.
- Tests come first: every logic task starts with a JVM test that fails (by assertion, or by not compiling against the
  new API), then the fix.
- compileSdk 37 on every Android module; targetSdk stays 36; minSdk 26.

## Root causes (CI run 39050111148)
| Error | Root cause | Fix |
|---|---|---|
| `PlayerController.kt:5 Unresolved reference 'core'`, `:61 'ContextCompat'` | `androidx.core` only reaches `:core:player` as a runtime dependency of Media3; it is not on the compile classpath | add `androidx-core-ktx` (1.19.1) to the catalog and to `:core:player` |
| `checkDebugUnitTestAarMetadata`: 13 issues | Compose 1.12.x (even stable `material-ripple 1.12.0`), material3 1.5.0-alpha28 (pulls Compose 1.13.0-alpha01) and Haze 1.7.3 declare `minCompileSdk=37` | compileSdk 37 everywhere. Stable libraries can't keep 36: Compose 1.12 stable needs 37 too |
| 9 ktlint errors | formatting only | `ktlint --format` locally with the CI's 1.8.0 binary |

## Tasks

### T1: compile fixes (CI)
- Modify `gradle/libs.versions.toml` (add `androidxCore = "1.19.1"`, `androidx-core-ktx`), `core/player/build.gradle.kts`,
  and every Android `build.gradle.kts` (`compileSdk = 37`).
- Check: CI `Unit tests` step gets past `compileDebugKotlin` and `check*AarMetadata`.

### T2: ktlint (CI)
- Run `ktlint --format` on `**/src/**/*.kt` and `**/*.kts`, then rerun lint until it reports 0 errors.
- Check: CI `ktlint` job green.

### T3: finding 1, screenshot tests only on debug
- Move `app/src/test/kotlin/.../ScreenshotTest.kt` to `app/src/testDebug/kotlin/...` (the release variant has no
  `ui-test-manifest` activity).
- Check: `gradle test` (which includes `testReleaseUnitTest`) passes in CI.

### T4: finding 13, screenshot verification gates CI
- `ci.yml`: the screenshot step is `gradle :app:verifyRoborazziDebug` with no `||` fallback.
- `screenshots.yml` (workflow_dispatch): records baselines and uploads them as the `sitara-screenshot-baselines`
  artifact (no bot pushes, no force-push). A maintainer reviews the PNGs, then commits them to `app/src/test/screenshots/`
  and copies them to `docs/screenshots/`.
- Order: CI fails honestly until baselines exist. Then dispatch `screenshots.yml`, review and commit the images, and CI
  verifies them on every push.

### T5: finding 8, loudness peak cap
- Test `AudioMathTest.normalisation attenuates when the true peak is already above the ceiling`:
  `gainDb(measuredLufs = -18f, targetLufs = -14f, truePeakDb = 0f) == -1f`.
- Fix `Loudness.gainDb`: always `min(gain, PEAK_CEILING_DB - truePeakDb)`, no zero clamp.

### T6: finding 9, fade window longer than the track
- Test `AudioMathTest.fade window is bounded by a short track`:
  `progress(positionMs = 0, durationMs = 1_000, windowMs = 6_000) == 0f` and `progress(1_000, 1_000, 6_000) == 1f`.
- Fix `Crossfade.progress`: `window = min(windowMs, durationMs)`.

### T7: finding 10, monotonic sleep timer
- Test `SleepTimerTest` builds the timer with a `kotlin.time.TimeSource` driven by virtual time
  (`SleepTimer(scope, timeSource, onExpire)`). It doesn't compile against the old wall-clock `() -> Long` API.
- Fix: `SleepTimer` takes a `TimeSource` and has no wall-clock default; production passes `ElapsedRealtimeTimeSource`
  (`SystemClock.elapsedRealtime()`, which keeps counting in deep sleep).

### T8: finding 7, unique queue entries
- Test `QueueEngineTest.unshuffle restores the duplicate entry that was playing`: queue `[A, B, A]`, start index 2,
  shuffle then unshuffle, so `currentIndex == 2`.
- Fix: `QueueEntry(uid: Long, track: Track)`; `QueueState.entries`, `originalOrder: List<QueueEntry>`, a `nextUid`
  counter, and `items`/`current`/`upNext` derived as `Track`s. Add `select(uid)` for player-driven transitions.

### T9: finding 11, syllable-safe enhanced LRC
- Tests in `LrcParserTest`:
  `[00:01.00]Oh <00:01.50>Hel<00:02.00>lo <00:02.50>world` gives text `"Oh Hello world"`, word texts
  `["Oh ", "Hel", "lo ", "world"]`, the first word starting at 1_000, and `line.text == words.joinToString("")`.
- Fix `LrcParser.buildLine`: keep original separators and leading untimed text. Add `LyricsCursor.sweep(line, pos)`
  that returns character offsets for the karaoke fill. `KaraokeLine` draws one `Text`, clipped to the sung path, so it
  no longer inserts padding between syllables.

### T10: finding 12, unsynced lyrics
- Test `LyricsCursorTest.plain lyrics never have an active line`: for `Lyrics(synced = false)`,
  `activeLineIndex(lyrics, 99_000) == -1`.
- Fix: `LyricsPage` renders plain lyrics as static text: no highlight, auto-scroll, blur or seek.

### T11: findings 3, 4, 5, 6, service-owned playback engine
- Tests in `PlaybackEngineTest` (fake `PlayerPort`, virtual time):
  - `the whole queue is handed to the player` (3): `play(five, 1)` puts 5 items in the player, index 1.
  - `sleep timer pauses the player with no UI attached` (3): `startSleepTimer(1)`, then after 60 s `port.paused`.
  - `end-of-track sleep asks the player to pause at the item end` (3).
  - `player-driven transitions update the queue` (3, system Next): `onItemTransition(uid of item 3)` makes `current` t3.
  - `rapid selections leave the player on the latest entry` (5).
  - `play after the queue ended restarts the current item` (6): `onPlaybackEnded()`, then `togglePlay()` seeks the
    current index to 0, plays, and sets `ended == false`.
- Tests in `EngineGateTest` (4): commands sent before the engine attaches are replayed once, in order; attaching
  never reloads the player.
- Fix: `PlayerPort`, `PlaybackEngine`, `EngineGate`, `PlaybackEngineRegistry`, `Media3PlayerPort`, `StreamUriResolver`
  in `:core:player`; `PlaybackService` hosts the engine; `PlayerController` becomes a facade; the app implements
  `PlaybackDependencies`.

### T12: finding 14, controller policy
- Test `ControllerPolicyTest`: own package gives FULL; a media-notification, Android Auto/Automotive or trusted system
  controller gives PLAYBACK_ONLY; an unknown app gives REJECT. `PLAYBACK_COMMANDS` contains play/pause, seek and
  next/previous but not `COMMAND_SET_MEDIA_ITEM`, `COMMAND_CHANGE_MEDIA_ITEMS` or `COMMAND_SET_VOLUME`.
- Fix: `PlaybackService` session callback `onConnect` applies the policy. `onAddMediaItems` rejects external items
  (the app never sends items through the session).

### T13: findings 2 and 16, sheet stability and phase-deferred reads
- Test `SheetGeometryTest` (JVM): `SheetGeometry.at(progress = 1.3f, ...)` has a radius >= 0 and y >= 0, and
  `at(-0.2f)` equals `at(0f)`.
- Fix: `Animatable(...).apply { updateBounds(0f, 1f) }`. Geometry is read in `offset {}`/`layout {}`/`graphicsLayer {}`
  lambdas, and content visibility comes from `derivedStateOf` thresholds, so the sheet doesn't recompose every frame.

### T14: findings 15 and 17, honest settings, reachable filters
- Settings: normalisation and crossfade are shown disabled with "Arrives with the audio engine (Phase B)".
- Library: the filter chips sit in a horizontally scrollable row.
- Check: screenshot baselines (T4) are reviewed with an impeccable critique.

### T15: docs
- `docs/review/PHASE_A_REVIEW_RESPONSE.md` (finding, commit, test), README accuracy pass, `docs/PLAN.md` status,
  `SKILLS_USED.md`.

## Review focus (failure modes that no single task's test covers)
1. A process with UI and service both alive where the engine attaches after the user taps play. Covered by
   `EngineGateTest.commands before attach are replayed in order`.
2. An empty queue with Play pressed: the engine must do nothing. Covered by `PlaybackEngineTest.toggle play on an empty
   queue is a no-op`.
3. A stream that fails to resolve: the error surfaces as a player error, not a crash. `StreamUriResolver` throws
   `IOException`, which ExoPlayer reports; verified by the code review.
4. Lyrics with only leading untimed text and no word tags: they stay a plain synced line. Covered by the existing
   `parses plain synced lines`.
5. Screenshot drift between runners: verification runs on the same `ubuntu-latest` image that recorded the baselines.
