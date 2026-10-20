# c2b report: fixes from the v0.7.0 phone test (streams, playback errors, lyrics, insets)

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.8.0`; no tag or release was made (the owner tags).

The owner tested the v0.7.0 release APK on a virtual Pixel 7 (Android 14) and found four problems. Three are fixed
with tests. The fourth (insets) does not reproduce on the API 34 emulator in any configuration tried. The device
capture now checks it on every push.

## What changed

1. **Stream fallback** (`:source:api`, `:source:youtube`, `:app`, commit `5f9ce3b`).
   - Phone evidence: `Could not resolve a stream for gsZauX8gwxo ... Got error UNPLAYABLE: "This video is not
     available"`. NewPipeExtractor v0.26.5 takes the player response from the Android client and fails as a whole
     when that one is refused, even though it also asks visionOS (and optionally iOS) for streams.
   - `youTubeStreamResolver()` chains three routes in a fixed, documented order: NewPipe's full extraction
     (`YouTubeStreamResolver`), then the visionOS client, then the iOS client. The two clients are asked directly
     through NewPipe's public `YoutubeStreamHelper` (`ClientStreamResolver`), without a proof-of-origin token.
     visionOS comes before iOS because YouTube is rolling out a token requirement on iOS.
   - `ClientStreamResolver` reads `playabilityStatus` (anything but OK fails with the status and reason) and picks
     the best audio format that has a direct https URL (`selectAudioFormat`): default audio track first, highest
     bitrate, opus before m4a on a tie, the same rules as `selectAudioStream`. Formats that need a deciphered
     signature are skipped.
   - `FallbackStreamResolver` (already in `:source:api`, unused in the app until now) remembers the route that served
     each song and asks it first for that song for the rest of the process. When every route fails, each cause is
     attached as a suppressed exception, so logcat shows why each one failed.
   - Downloads and the gapless prefetch use the same chain.
   - Tests (fake resolvers, no network): order, stop at the first success, per-song memory, fallback through the rest
     when the remembered route breaks, every failure reported. Player-response fixtures: best format, signature-only
     formats skipped, original over dubbed, opus tie-break, a refusal with status and reason, no playable format, and a
     non-YouTube id with no request.
   - Live smoke job (non-blocking): every route reported `LOGIN_REQUIRED "Sign in to confirm that you're not a bot"`
     from the CI runner (CI 39472983427). So all three routes reach YouTube and parse its answer, but that network is
     bot-checked for each of them, and the test skips as designed.

2. **Visible playback error with Try again and Skip** (`:core:player`, `:feature:player`, `:core:designsystem`,
   `:app`, commits `a70c35d`, `96b746e`).
   - `PlaybackService` forwards `onPlayerError` to `PlaybackEngine.onPlayerError`, which records the failed queue
     entry (`PlaybackSnapshot.failedUid`). `failed` is true only while that entry is the current one, and the mark
     clears once the song plays.
   - `retry()` clears the mark and asks the player to play. From the error state ExoPlayer prepares again, so the
     stream is looked up afresh (neither the prefetch nor the fallback chain caches a failure). Play on a failed song
     retries it. Skip is the existing `next()`.
   - **No automatic skip** (decision): a refusal from YouTube usually hits the next song too, so auto-skipping would
     run through the whole queue in seconds and hide the real problem. The listener chooses Try again or Skip.
   - UI: Now Playing replaces the scrubber with "Couldn't play this song", a Try again pill and a Skip pill, and the
     main button shows a new retry glyph (`SitaraIcons.Retry`, labelled "Try again"). The mini player shows the
     message in place of the artist and swaps Play for Try again and "Skip to next song". The message is a polite
     live region, so TalkBack announces it. All targets are 48 dp. ui-ux-pro-max guidance applied: a recovery path
     with every error, the error announced, text rather than colour alone.
   - Tests: engine (error marks the song and does not skip, retry from the same position, play retries, skip
     advances and clears, the mark stays with its song, playing again clears it); interaction (dock and Now Playing
     show the message, no Play button, and Try again and Skip call the right actions); screenshots
     `mini_player_failed_dark` and `player_failed_dark` (Record screenshots 39473866740, inspected).

3. **Lyrics title cleaning** (`:source:lyrics`, commits `6aecf8b`, `1906ae3`).
   - Phone evidence: "No lyrics found" for "expectations (feat. Chappell Roan) (Official Music Video)". Video labels
     were already dropped, but the featured artist stayed in the title sent to LRCLIB.
   - `lyricsQuery` now also drops "(feat. X)", "[ft. X]", "(featuring X)" and a trailing "feat./ft. X", on both
     sides of an "Artist - Song" title, and reads "Artist - Topic" channels as the artist (VEVO was already handled).
     The existing labels cover (Official Music Video), (Official Video), (Official Audio), (Lyric Video), (Lyrics),
     [Lyrics], (Visualizer), (Audio), [HD] and (4K Remaster), case-insensitive, in parentheses or brackets.
   - `LrclibLyricsProvider` asks with the cleaned query first (exact, then search), then with the title and artist
     as uploaded when that finds nothing.
   - Tests: a 20-row table of real-world title shapes, including the exact failing title, with lookalikes that must
     stay ("Left Behind", "Soft Light", "(Acoustic)"). Interceptor tests check the request order (cleaned get,
     cleaned search, raw get, raw search) and that a hit with the cleaned title makes no second lookup. `1906ae3`
     fixed a test-only defect CI found: the raw string's trailing whitespace line was read as an empty extra row.

4. **Insets: investigated, not reproduced; regression guard added** (`tools/`, debug capture, commits `3b63a31`,
   `78920ad`).
   - The shell already gives every tab and detail screen a bottom padding of dock height (mini player plus tab bar,
     measured) plus the navigation bar inset plus spacing. This was the same in v0.7.0.
   - The API 34 device capture (`pixel_6` profile, 1080x2400 at 420 dpi like a Pixel 7) now scrolls Settings to the
     end and fails if the last row reaches under the mini player. It also saves Search while typing and after Back
     closes the keyboard. This runs in dark and light, with gesture navigation and then with three-button navigation
     (where the system may draw a contrast scrim).
   - Result (Device screens 39476007551): the last row ends 209 px above the mini player in all four Settings runs.
     None of the Search captures shows a strip under the dock after the keyboard closes. The Robolectric baseline
     `settings_downloads_dark` already shows Settings scrolled to the bottom, clear of the mini player.
   - I made no speculative inset change. What the capture does not cover: the release build's `MainActivity` (the
     capture uses the debug `DeviceCaptureActivity`, which sets up edge-to-edge the same way), real search results,
     a font or display size above default, and the owner's exact AVD.

5. **Docs**: CHANGELOG Unreleased "Fixed"; README (how playback falls back and reports failures, the lyrics lookup,
   the bot-check limit); ARCHITECTURE (stream fallback section rewritten to describe what exists, test table);
   ADR 0004; this report; SKILLS_USED.

## Commits

| Commit | What |
| --- | --- |
| `5f9ce3b` | fix(streams): fall back to the visionOS and iOS clients when NewPipe's lookup is refused |
| `a70c35d` | fix(player): say when a song cannot play, with Try again and Skip |
| `6aecf8b` | fix(lyrics): drop featured artists and Topic suffixes, then try the title as uploaded |
| `96b746e` | test(screenshots): record the failed-song mini player and Now Playing |
| `3b63a31` | ci(device): capture Settings scrolled to the end and Search after the keyboard closes |
| `1906ae3` | test(lyrics): end the title table on its last row |
| `78920ad` | ci(device): capture Settings and Search again with three-button navigation |
| (this commit) | docs: record the c2b fixes |

## CI

- `5f9ce3b`: CI 39472983427 and Secret scan 39472983492 green. Its Device screens run was cancelled by the next push.
- `a70c35d`: CI 39473858404 failed only on the two new screenshot tests, which had no baselines yet (expected).
  Record screenshots 39473866740 produced them (2 new, 0 changed), committed in `96b746e`.
- `3b63a31` (with `6aecf8b`, `96b746e`): CI 39474616716 failed on the lyrics table's empty extra row, fixed in
  `1906ae3`. Device screens 39474616616 green (gesture navigation captures).
- `78920ad` (with `1906ae3`): CI 39476007550, Device screens 39476007551 green; Secret scan green on every push.
- Final docs commit: see the session log line in PROJECT_BRAIN.

## Not verified on a real phone

- Whether the visionOS or iOS route actually plays a song that NewPipe's Android request is refused for, from a
  home or mobile network. From CI every route is bot-checked. Their URLs are fetched with ExoPlayer's default
  User-Agent; if YouTube rejects them (HTTP 403), the error UI shows, and the next step would be a client-matched
  User-Agent.
- The error UI on real failures: that ExoPlayer reports the error on the failing song (including a failing next
  song during a gapless join), that Try again recovers once a network comes back, and how TalkBack reads it.
- Lyrics for the reported song and other real uploads (LRCLIB's catalogue decides).
- The Settings and Search inset symptoms: not reproduced on the emulator (see item 4). If they recur, the owner's
  navigation mode, theme (app and system), font and display size, and a screenshot would let the capture reproduce
  them.
- An R8 release build of these changes. `YoutubeStreamHelper` and the nanojson types are reached by direct calls, so
  R8 keeps what they use, but no release APK was built or run in this session.
