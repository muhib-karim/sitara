# fix1 report

The working tree contains fixes and regression coverage for N1, N2 and partial findings 5 and 6. Finding 16 is already superseded by the current player sheet; interaction and screenshot coverage now exercises that implementation. Source review is complete; compilation, test results, lint and visual acceptance remain pending in CI.

The supplied independent review is copied verbatim to [ASTRA_CROSS_REVIEW_1.md](../review/ASTRA_CROSS_REVIEW_1.md); it contains no local machine paths. Finding-by-finding outcomes are appended to [PHASE_A_REVIEW_RESPONSE.md](../review/PHASE_A_REVIEW_RESPONSE.md).

## Findings

| Finding | Root cause and change | Tests prepared for CI |
|---|---|---|
| N1: stale completion after seek | Explicit seeks and the long-position Previous branch retained `queue.ended`. The service only forwarded entry into ENDED. `PlaybackEngine` now clears completion before seeking; Previous uses that path. `PlaybackService` forwards both ended and non-ended playback states, including same-item system seeks. | Four `PlaybackEngineTest` cases cover end → seek → Play preserving 400 ms, resumed playback → Pause preserving position, Previous clearing completion, and a system seek leaving ENDED without an item transition. |
| N2: transient empty lookup during shuffle | Clearing and repopulating `ConcurrentHashMap` exposed empty/partial membership to loaders. `Media3PlayerPort` builds a private map and publishes it with one volatile assignment; published maps are never mutated. Reorder retains membership. | Two `Media3PlayerPortTest` cases force loader-thread reads during reorder and queue replacement. They check complete membership throughout and updated metadata after replacement. The old implementation exposes missing tracks at the controlled read points. |
| 5: concurrent resolution/cancellation | Synchronous fake seeks did not exercise the resolver. `runBlocking` interruption became a generic `IOException`, and cancellation lost its cause. `StreamUriResolver` now reports `InterruptedIOException` with its cause and restores the interrupt flag after interruption. | Three `StreamUriResolverTest` cases cover older resolution finishing last without altering either request's URI or byte range; interruption of a suspended request with cause/flag preservation and successful subsequent resolution; and coroutine cancellation retaining its cause. Request isolation characterizes existing behavior; cancellation assertions target the old error handling. |
| 6: replay after completion | Direct replay worked, but the stale completion flag still diverted seek-then-Play/Pause into the restart branch. Addressed by N1. | Existing direct replay test retained: Play restarts at zero. The four new completion cases distinguish replay from a deliberate seek. |
| 16: BackHandler recomposition during drag | The reviewed `Animatable.targetValue` composition read is absent in the current `PlayerSheet`. `open` depends on committed `expanded` state and track presence; drag deltas are ordinary local variables. No additional `derivedStateOf` or production UI change is needed. | `PlayerInteractionTest` checks Back stays disabled during an unfinished/cancelled drag, becomes enabled after opening, and collapses to Home. `ScreenshotTest.playerDark` opens by swipe before capture using the existing baseline path. These tests characterize behavior; they do not measure recomposition counts. |

## Files

- Playback state: `core/player/src/main/kotlin/app/sitara/music/core/player/PlaybackEngine.kt`, `PlaybackService.kt`; regression cases in `core/player/src/test/kotlin/app/sitara/music/core/player/PlaybackEngineTest.kt`.
- Lookup and resolution: `core/player/src/main/kotlin/app/sitara/music/core/player/Media3PlayerPort.kt`, `StreamUriResolver.kt`; new tests in `core/player/src/test/kotlin/app/sitara/music/core/player/Media3PlayerPortTest.kt`, `StreamUriResolverTest.kt`.
- Test dependency: `core/player/build.gradle.kts` adds the existing catalogued Robolectric dependency for Android URI/DataSpec behavior. No version or runtime dependency changed.
- UI coverage: `app/src/testDebug/kotlin/app/sitara/music/PlayerInteractionTest.kt`, `ScreenshotTest.kt`.
- Handoff: `docs/review/ASTRA_CROSS_REVIEW_1.md`, `docs/review/PHASE_A_REVIEW_RESPONSE.md`, `docs/PROJECT_BRAIN.md`, this report.

## Verification and risks

- Preserved the interrupted attempt's fixes and tests after tracing each affected code path; strengthened the interruption case with a thread-status assertion. Reviewed the final tracked diff once and read both new test files. Checked callback references, API signatures, imports, scopes, braces and multiline trailing commas in the edits.
- No local Gradle, tests, lint, builds or screenshot recording ran. Expected pre-fix failures are source-derived; no executed red/green result or successful compile is claimed. CI must run unit tests, ktlint, Roborazzi verification and debug assembly.
- The port tests mock ExoPlayer; resolver tests use controlled concurrent calls. Actual Media3 loader cancellation/scheduling, service/controller lifecycle and system transports still need integration/device verification. The concurrent tests use bounded waits, which CI must validate under load.
- Screenshot baselines and workflows are untouched. The swipe setup must settle into the expected expanded view in CI. Earlier ui1/ui2 visual acceptance remains pending, so baseline mismatches must be reviewed rather than bypassed.
- Existing original split-star SVG, adaptive foreground/background and monochrome assets are retained. This focused pass makes no visual redesign. N3 is outside the requested fix1 scope.
- No commits, pushes, tags or releases were made. All changes remain in the working tree.
