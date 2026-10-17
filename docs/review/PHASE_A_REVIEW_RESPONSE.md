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

## fix1: independent cross-review response

Source: [ASTRA_CROSS_REVIEW_1.md](ASTRA_CROSS_REVIEW_1.md). The review describes the earlier Phase A tree; the current sheet was subsequently replaced by ui1/ui2. This section supersedes the earlier blanket resolution claim for findings 5, 6 and 16. The historical green CI above does **not** validate fix1.

Regression tests were written before the production fixes. No local Gradle, tests, lint, builds or screenshot recording were run, and no commit or push was made. Expected failures below come from tracing the old code; red/green execution and compile acceptance remain pending in CI.

| Finding | Root cause | Outcome / fix | Regression coverage and expected old behavior |
|---|---|---|---|
| N1 — completion after a seek | `seekTo()` and the long-position Previous branch left `queue.ended` true. The service only published entry into ENDED, so system seeks leaving ENDED did not clear it. | Explicit seeks clear completion before forwarding; Previous uses the same path. The service now forwards both ended and non-ended playback states to the engine. Direct replay still restarts at zero. | `PlaybackEngineTest`: seek after completion preserves 400 ms on Play; resumed playback pauses without a restart; Previous clears completion; a system seek clears completion without an item transition. The old implementation retains `ended` or restarts at zero. Existing direct replay regression retained. |
| N2 — empty lookup during shuffle | `ConcurrentHashMap.clear()` followed by insertions exposed empty or partial membership to loader threads. | `Media3PlayerPort` privately builds a map and publishes it through one volatile assignment. Published maps are never mutated. Reorder does not rebuild membership. | `Media3PlayerPortTest`: deterministic list-read callbacks run lookups on a separate loader thread while reorder or replacement is in progress. The old clear/repopulate path returns null for valid entries. Replacement also verifies updated metadata is published. |
| 5 — PARTIAL resolution/cancellation coverage | Synchronous fake seeks did not exercise the real resolver. Thread interruption from `runBlocking` was wrapped as a generic I/O failure; coroutine cancellation lost its cause. | Keep resolution scoped to each `DataSpec`; classify loader interruption as `InterruptedIOException`, preserve its cause and restore the interrupt flag. Preserve coroutine cancellation causes too. N2 closes the membership race. | `StreamUriResolverTest`: a blocked older resolution completes after a newer one without changing its URI or byte range; interrupting a suspended request preserves the interrupt flag and cause, and a later request still resolves; coroutine cancellation retains its cause. The old interruption type, flag and cancellation-cause assertions fail by source inspection. Concurrency success coverage characterizes the existing request isolation. |
| 6 — PARTIAL replay behavior | Same stale completion flag as N1, beyond the existing direct-replay case. | Addressed with N1; direct replay and seek-then-Play/Pause now use distinct state paths. | Existing direct replay plus all four new completion regressions in `PlaybackEngineTest`. |
| 16 — PARTIAL drag recomposition | The reviewed sheet read `Animatable.targetValue` during composition. That implementation no longer exists in the current tree. | Already superseded: current `PlayerSheet` uses `open = expanded && track != null`; pointer deltas are ordinary local variables, and `expanded` changes only when an expansion is committed. `BackHandler` has no per-frame target read. Adding `derivedStateOf` around this Boolean would be redundant. No production UI change required. | `PlayerInteractionTest.backHandlingStaysDisabledDuringDragAndCollapsesTheExpandedPlayer`: disabled during an unfinished/cancelled drag, enabled after opening, Back collapses and restores Home. `ScreenshotTest.playerDark` now swipes open before comparing with the existing `player_dark.png` target. These are characterization tests, not claimed failing reproductions or recomposition benchmarks. |

### Remaining verification

- CI must compile both variants, run unit tests, ktlint and the existing Roborazzi verification, then assemble. Reuse the catalogued Robolectric dependency in `core:player` tests for real Android URI/DataSpec behavior; no runtime dependency changed.
- Tests exercise the real resolver and port membership with a mocked ExoPlayer. Real Media3 loader scheduling, service/controller lifecycle and system transport interactions still require integration/device verification.
- Screenshot baseline files and workflows are unchanged. The changed screenshot setup must converge to the same expanded state in CI; existing ui1/ui2 visual acceptance remains outstanding.
- N3 was not requested in fix1 and remains outside this pass.
