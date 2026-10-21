# p1 report: polish slice 1, the stage and the shell

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, ui-ux-pro-max, ponytail-review (before
every push); tests were written first as a practice, without invoking the TDD skill. Target release `0.9.1`; no tag or release was made (the owner tags),
and `versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

The owner found v0.8.3 "very bland" next to other players, with BitChord as the main reference. This slice gives the
listening stage and the shell their own colour, depth and type: the cover blurred behind Now Playing and Lyrics, an
accent taken from the cover, one floating glass pill for the song and the tabs, hero lyrics and a stronger Now Playing.

## Licence check

BitChord is GPL-3.0 (its repository's LICENSE). `bc_GlassNavBar.kt` credits Echo-Music (GPL-3.0). Copying would have
been allowed with attribution, but AGENTS.md asks for original code, so **nothing was copied**: every piece below was
written from the behaviour described in DESIGN_SPEC v1 and seen in the reference files. No reference files, images,
fonts or icons are in the repository. NOTICE already lists BitChord and Metrolist as quality references only, and
that is still true, so NOTICE and the README credits are unchanged.

## What changed

1. **Cover analysis** (`:core:designsystem`, `2ef94f4`). One small read of the cover (128 px, from Coil's cache) now gives
   its main colour (the palette's dominant swatch), an accent seed (the vibrant swatch, then dark or light vibrant,
   then muted), the brightest colour its blur can show (the brightest cell of an 8 × 8 grid) and a 12 px copy. It is
   cached per artwork (`CoverLook`, 32 entries). `ArtworkPalette.themeFrom` takes the wash's hue from the main colour
   and the accent's from the vibrant one; a grey second colour falls back to the cover's own hue. Themes are worked out
   for dark surfaces (the stage) and light ones (the dock in light mode). An all-white or all-black cover, which the
   palette filters out, still gets colours.
   Tests: `ArtworkPaletteTest` (vibrant/dominant split, grey fallback, cover vs fallback themes) and a Robolectric
   `CoverColorsTest` on bitmaps drawn in the test (navy with a saffron detail, grey, half white, all white).
2. **Artwork backdrop, B1** (`813e525`). Now Playing and Lyrics sit on the cover blurred across the whole screen:
   RenderEffect blur (72 dp, overscaled 1.4×) on Android 12+, the 12 px copy stretched with bilinear filtering below
   that. Over it: the theme's wash at 35 %, a black shade, and a radial glow in the accent (22 % at its centre, 30 %
   down the screen). The shade is **worked out per cover** (`ArtworkPalette.stageScrim`): the lightest that keeps the
   dimmest stage text (`#BDBDBD`) at 4.5:1 over the brightest colour the stage can show, never under 30 %, reaching
   85 % at the bottom edge. A dark cover keeps a light shade and shows its colour; a white one gets about 76 %. The
   cover crossfades between songs (cuts under reduced motion); the blur radius never animates. AMOLED keeps pure black.
   Tests: `ThemeContractTest` sweeps white, black, yellow, mid grey and 72 hue/saturation/lightness covers: white text,
   secondary text and dimmed lyric lines all stay at 4.5:1 or better on the brightest point, and a dark navy cover
   keeps the 30 % floor. The maths was checked first in a Python port (worst case 4.50:1).
3. **Real-artwork fixture, goal 7** (`6798bc3`). The debug and test image loader
   (`app/src/debug/kotlin/app/sitara/music/SampleCovers.kt`, `sampleImageLoader`) now paints each `sample:cover/`
   image procedurally: a sky graded between the artwork's colours, a sun with a halo, two ranges of hills and an orbit
   ring. No image files and no third-party art; it lives in the debug source set, so release builds never contain it.
   The screenshot tests and the debug `DeviceCaptureActivity` both use it, so the backdrop, palette and glass show over
   cover-like art even when YouTube refuses the CI emulator.
4. **Floating glass pill, B3/B4** (`83cd137`, test fix `3b7f18b`, ktlint `4bb2453`). The mini player and the tabs are
   one glass body with 32 dp corners (half the tab row, so its ends are round), tinted with the cover's accent at 10 %.
   The mini player keeps its cover, title, artist, play/pause, swipe sideways to skip, tap or swipe up to open, and its
   TalkBack actions, now with an accent progress line. The open tab's icon takes the cover's accent (worked out for the
   current light or dark surface) on a glass indicator; labels stay in the neutral text colours. Scrolling on folds the
   pill into one row, the open tab beside the song (artist line dropped, a failure still shown); scrolling back or
   tapping that tab ("Home, show all tabs") unfolds it. Pages pad for the unfolded height, so folding never moves them;
   the bottom edge is still the plain gradient fade. Glass blur no longer switches off under reduced motion, as it
   does not move.
   Test: `readingOnFoldsTheDockIntoOneRowAndItsTabOrScrollingBackUnfoldsIt`.
5. **Hero lyrics, B5** (`05c5cfe`). Lines at 32 sp Inter Display extra bold; the sung one white at full size, the
   others at 68 % (still 4.5:1 on the brightest stage, see 2) and blurred 1.2 dp per line of distance up to 3 dp.
   Follow mode and Back to current line are unchanged. The header is the cover at 48 dp, title, artist and round glass
   Like and More (More opens the same menu as on Playing).
   Test: `lyricsHeaderLikesTheSongAndOpensMoreOptions`.
6. **Now Playing polish** (`c7b3450`, `5b1fbf1`). The scrubber is 6 dp, 10 dp under the finger (springs; snaps under
   reduced motion), with a white thumb and larger times. On a song that cannot play it stays in place, still and
   disabled, with the message, Try again and Skip under it. Shuffle and repeat are round glass buttons (the repeat-one
   figure is hidden from TalkBack, the button says "one"). The title is 24 sp extra bold over a 16 sp medium artist.
   Test: `failedSongInNowPlayingKeepsAStillScrubberAndOffersTryAgainAndSkip`.
7. **Equaliser addendum** (`ffd6d0b`). Each band and the preamp is a slim 4 dp track with a 0 dB mark and an 8 dp round
   thumb, filled from that mark up for a boost and down for a cut, nothing at 0 dB. While the equaliser is off the
   faders dim to 38 % (moving one still switches it on). A transparent slider over each keeps the 48 dp touch width,
   keys and the TalkBack name and value; the existing tests for those pass unchanged.
8. **Baselines and docs** (`971923d` and the docs commit). Record screenshots 39520783105 on `5b1fbf1`; Now Playing,
   Lyrics, Home and the equaliser end page inspected before committing; README images refreshed from the same run.
   CHANGELOG Unreleased, README features and Why Sitara, ARCHITECTURE and SKILLS_USED updated.

Not changed, on purpose: the neutral surfaces. Sitara's dark scheme was already true black with neutral greys (#000,
#101010, #202020), within a step of the spec's #000/#0D0D0F/#1C1C1E, so B2's "neutral surfaces" needed no churn.

## Against the references

| Ref | BitChord / spec | Sitara now |
| --- | --- | --- |
| B1 backdrop | Cover blurred ~72 dp, overscaled, rasterised once; fixed vertical scrim; flat tint when blur is off | Same blur and overscale, full screen rather than the top 72 %; a stretched 12 px copy instead of a flat tint below Android 12; a shade worked out per cover for 4.5:1 instead of fixed alphas; an accent glow BitChord's player does not have |
| B2 palette | Dominant if chromatic else vibrant; clamped saturation, fixed lightness; neutral iOS greys | Wash from the main colour, accent from the vibrant one (muted as the fallback), both clamped, with the existing 4.5:1 lightness walk; applied to the stage, the mini player tint and progress, and the open tab, crossfading in 400 ms |
| B3/B4 glass | Floating pill tab bar plus a now-playing accessory, folding inline on scroll (Echo's FloatingTabBar) | One glass pill with the song over the tabs, folding to one row on scroll and back; swipe to skip, tap to open; written from scratch on Haze, no vendored tab bar |
| B5 lyrics | Huge heavy type, white active line, dimmed blurred context, compact header with glass buttons | 32 sp extra bold, white active line, 68 % context blurred by distance, 48 dp cover header with glass Like and More; the dimmed lines are kept brighter than BitChord's to stay at 4.5:1 |
| Spec §4 player | Thick scrubber growing on drag, glass secondary buttons, weight contrast | Done as written; the scrubber also stays visible on a failed song |

On the fixture's bright cover (white sun, cyan sky) the AA shade is strong, so the stage reads as a deep blue rather
than BitChord's vivid wash; darker or more saturated covers keep much more colour. That is the trade for keeping every
line of text at 4.5:1.

## CI

- Unit tests green on `2ef94f4` (39518030519), `3b7f18b` (39519271723), `05c5cfe` (39519847852), `c7b3450`
  (39520362964) and `5b1fbf1` (39520772547). ktlint flagged the dock's one-line `if … else if` from `83cd137` until
  `4bb2453`, and was green from then on. Their screenshot step
  failed only on stale baselines, as expected for UI work, until the baselines landed.
- `83cd137` (39518759854): the new fold test swiped past the end of Home; fixed in `3b7f18b`.
- Some runs were cancelled by the next push (concurrency); after that each push waited for its own run.
- Record screenshots: 39520783105.
- On the baselines (`971923d`): CI 39521289388, Secret scan 39521289200, Device screens 39521289387, all green
  (no Device screens flake this time).

## Not verified on a real phone

- Blur cost on low-end phones: the 72 dp RenderEffect over a 128 px source, the Haze glass over scrolling lists, and
  the lyric lines' per-line blur (up to 3 dp) while the list follows the song.
- The glass and the wash over real covers, in light and dark, and with Material You.
- The swipe gestures on the pill (skip and open, folded and unfolded) and the fold on real scrolling and flings.
- The stretched-copy backdrop on Android 8 to 11 (Robolectric API 30 baseline only).
- The equaliser thumb's position against the touch point at the very ends of a fader.
- TalkBack on the folded pill ("Home, show all tabs") and on the transparent faders.
