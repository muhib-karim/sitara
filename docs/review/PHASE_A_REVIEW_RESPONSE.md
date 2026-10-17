# Phase A review: response

**CI:** green on `c189c13`, [run 39054185482](https://github.com/muhib-karim/sitara/actions/runs/39054185482) (ktlint,
unit tests for both variants, screenshot verification, `assembleDebug`, artifact `sitara-debug-apk`).

The response to the Phase A cross-model review (verdict: request changes; 17 findings). Every finding was fixed. Logic
fixes followed red-green: the failing test was pushed first, CI showed it failing, and the fix followed in a separate
commit. Plan: [`docs/plan/PHASE_A_FIX_PLAN.md`](../plan/PHASE_A_FIX_PLAN.md).

| # | Severity | Finding | Fix | Test(s) |
|---|---|---|---|---|
| 1 | Critical | Release unit tests launch `createComposeRule()` without the debug-only test manifest | `1b78474`: screenshot tests moved to `app/src/testDebug` | `gradle test` (debug + release variants) passes in CI |
| 2 | Critical | Spring overshoot gives `progress > 1` and a negative corner radius | `a87ca96`: `Animatable` bounded to `0f..1f`; geometry clamped in `SheetGeometry.at` | `SheetGeometryTest` (3 tests) |
| 3 | Important | Clearing the ViewModel kills auto-next and the sleep timer; a single-item playlist blocks system Next | `8d7f842`: `PlaybackEngine` lives in `PlaybackService`; the whole queue is an ExoPlayer playlist | `PlaybackEngineTest`: `the whole queue is handed to the player`, `sleep timer pauses the player with no UI attached`, `end-of-track sleep asks the player to pause at the item end`, `player-driven transitions update the queue` |
| 4 | Important | Reconnecting reloads the track; play requested before connection is lost | `8d7f842`: `EngineGate` buffers and replays commands; attaching never touches the player | `EngineGateTest` (3 tests) |
| 5 | Important | Overlapping async loads can finish out of order | `8d7f842`: resolution moved into the player's data source (`StreamUriResolver`), so the app has no async load and the race is gone; selections only seek within the playlist | `PlaybackEngineTest.rapid selections leave the player on the latest entry` |
| 6 | Important | Play after the last track ended does nothing | `8d7f842`: `togglePlay()` on an ended queue seeks the current item to 0, clears `ended` and plays | `PlaybackEngineTest.play after the queue ended restarts the current item` |
| 7 | Important | Unshuffle selects the wrong duplicate (`[A, B, A]`) | `df410e1`: `QueueEntry(uid, track)`; restore by uid | `QueueEngineTest.unshuffle restores the duplicate entry that was playing` |
| 8 | Important | Peak limiting never attenuates | `c4a4acf`: gain is always capped by `PEAK_CEILING_DB - truePeakDb` | `AudioMathTest.normalisation attenuates when the true peak is already above the ceiling` |
| 9 | Important | A fade window longer than the track starts part-way | `c4a4acf`: window bounded by the track duration | `AudioMathTest.fade window is bounded by a short track` |
| 10 | Important | Sleep timer uses the wall clock | `e204e2a`: `SleepTimer` takes a `kotlin.time.TimeSource`; production uses `ElapsedRealtimeTimeSource` | `SleepTimerTest` (virtual monotonic time) |
| 11 | Important | Enhanced LRC splits syllables and drops leading text | `ea463b5` (parser), `2223502` (karaoke rendering via `LyricsCursor.sweep`) | `LrcParserTest.keeps syllables joined and the text before the first word tag`, `LyricsCursorTest.karaoke sweep reports character offsets for sung and active spans` |
| 12 | Important | Plain lyrics scroll to the end, dim and seek to 0 | `2223502`: unsynced lyrics render as static text | `LyricsCursorTest.plain lyrics never have an active line` |
| 13 | Important | `verify \|\| record` hides screenshot mismatches; no baselines | `65a5e43`: CI runs `verifyRoborazziDebug` only; `screenshots.yml` records baselines as an artifact for review. `68717d7`: both jobs pinned to `ubuntu-24.04`. `c189c13`: reviewed baselines from run 39053926445 committed | CI `Screenshot tests` step: failed honestly with no baselines (run 39053530091), passes against them (run 39054185482) |
| 14 | Important | Exported service accepts any controller with full commands | `55ea405` (policy), `1ddbbb9` (stream allowlist), `8d7f842` (session callback): `onConnect` applies `ControllerPolicy`; external media items rejected; player loads only `sitara://` and https | `ControllerPolicyTest` (5 tests), `StreamPolicyTest` (2 tests) |
| 15 | Minor | Normalisation and crossfade controls do nothing | `a793769`: shown disabled, labelled "Coming in a future update" | screenshot `settings_*` |
| 16 | Minor | Sheet geometry recomputed in composition every frame | `a87ca96`: geometry read in `offset`/`layout`/`graphicsLayer` lambdas; `derivedStateOf` thresholds for content | screenshot `player_*`; manual device check pending |
| 17 | Minor | Library filters don't fit narrow screens | `a793769`: horizontally scrollable chip row | screenshot `library_*` |

## Notes and trade-offs
- **Finding 5:** the reviewer suggested cancelling and generation-checking loads. Resolving inside a
  `ResolvingDataSource` removes the app-side async step, which is simpler and covers system-initiated skips too.
  ExoPlayer cancels loads for items it leaves.
- **Finding 14:** the self check uses the binder-verified uid, not the package name. Platform uids (headset buttons,
  Bluetooth) and trusted controllers get transport controls only: no queue replacement, volume, shuffle or repeat
  changes.
- **Same-process engine:** the UI reaches the engine through `PlaybackEngineRegistry` because the UI and the service
  share a process. Cross-process control would need custom session commands; that is out of scope until there is a
  need.
- **Still manual:** spring feel, gestures and audio focus need a check on a real device (see `docs/PLAN.md`).
