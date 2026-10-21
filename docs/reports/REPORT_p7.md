# p7 report: polish slice 7, bring your music

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development, ui-ux-pro-max,
ponytail-review (before every push). Target release `0.9.7`. No tag or release was made (the owner tags), and
`versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

COMPETITOR_GAP item 10 (import and sync), PHASE_C_PLAN c9. Before this slice, a listener coming from YouTube Music or
another player had to rebuild every playlist by hand, and Sitara always streamed the highest bitrate on any network,
showing nothing about the stream. BitChord, Metrolist and Meld import playlists; BitChord sets quality per network.

## What changed

1. **Import engine** (`:core:data` `imports/`, `:core:model`, `:source:youtube`; `a4c9b20`, `39b4b3f`, `dd3d2ce`, `8e051fd`).
   - `playlistIdFromLink`: the `list=` id of a youtube.com / music.youtube.com / m.youtube.com playlist page, a
     `watch?v=…&list=…` link or a youtu.be share link, inside share text or without a scheme; look-alike hosts and
     ids with other characters are refused.
   - `parseM3u`: `#EXTINF:<seconds>,Artist - Title` names the next entry; without one, the file name (file: URLs
     decoded, underscores read as spaces); web addresses kept as links; other `#` lines skipped; BOM and CR, LF or
     CRLF line ends.
   - `parseCsv`: RFC 4180 fields (quoted delimiters, line breaks and doubled quotes), comma, semicolon or tab found
     from the first line, a header row recognised by a title column name (Exportify's "Track Name", "Artist Name(s)",
     "Duration (ms)" included), else title, artist, album by position; durations in ms, seconds or m:ss; a link column.
   - `matchScore`: title 0.6, artist 0.3, length 0.1, each only when the source gives it. Case, Latin accents,
     punctuation, bracketed notes, "feat." and "- Remastered"-style suffixes are ignored, and a video title that leads
     with the artist ("Tame Impala - Elephant (Official Video)") is read without it. Credits match on any shared
     artist ("Queen & David Bowie" against "Queen"), channel suffixes (" - Topic", "VEVO") dropped. Other scripts
     (Bangla) are compared as written. From 0.8 a match is taken, from 0.45 it goes to review, below that it is not
     found. YouTube songs and song links are taken as they are (confidence 1).
   - `PlaylistImporter` over three Room tables (schema 5, auto-migration): `import_jobs` (source key, name, the
     playlist it made or will update), `import_rows` (each source line, its state, its match so far) and
     `import_candidates` (the best five results of an uncertain row). `open` reads the source once and writes the
     job with its rows in one transaction. `match` looks one row up at a time and writes the answer with its
     candidates in one transaction, so an import cut short at any point resumes at the first pending row without a
     second lookup. `finish` gives the playlist exactly the matched songs, in source order and once each, in one
     transaction. Importing the same source again (same playlist id, or same file name) creates a new job that carries
     the earlier job's decided rows (matched or skipped) and updates the same playlist; a deleted playlist is made again.
   - `YouTubeMusicSource.wholePlaylist`: the existing page-by-page playlist read with a limit of 2,000 songs.
2. **Import UI** (`:feature:library` `ImportScreen`, `:app`; `c2ab3e3`, `fcc2462`).
   - Entry points: a pinned "Import playlist" tile in Library under Your stats, "Import a playlist" on the empty
     Library, Settings → Library → Import playlist, and playlist links shared to or opened in Sitara (the manifest now
     also takes `/playlist` links). A shared link is rebuilt from its validated id before it fills the field. A song
     played from a playlist (`watch?v=…&list=…`) still plays the song.
   - Start: the link field (URI keyboard, Go imports) and Import link; Choose a file through the Storage Access
     Framework (`OpenDocument`, no storage permission), read off the main thread up to 4 MB. An unfinished import
     shows as a card with Resume and Discard.
   - Running: the playlist's name, a progress bar TalkBack reads as "37 of 120 songs checked", and Found / To check /
     Not found counts in a polite live region; Cancel import cancels the coroutine (the lookup under way is
     interrupted), waits for it, then forgets the job.
   - Review: each uncertain song shows its line from the list ("On your list: Artist – Title"), the best match with its
     cover, and Use this, Other matches (a sheet of the candidates with covers) and Skip; the state ("Added",
     "Skipped", "Not decided") is part of the row's TalkBack label. Finish (or "Update playlist" for a repeat import)
     adds the matched songs; undecided ones are left out, and the sentence above the button says so.
   - Done: "“Road trip” is in your library" (or "Updated …"), songs added and left out, Open playlist (its page; in Library it shows
     as a 2 × 2 collage of its first covers) and Import another.
   - Problems, worded with a way on: not a playlist link, a private or deleted playlist, no connection (Try again),
     nothing to import, an unreadable file or one over 4 MB (Choose another file). A YouTube search error during
     lookups stops the import with "Couldn't reach YouTube Music" and Try again, which resumes.
   - The import runs in the app's view model scope, so leaving the screen does not stop it. Opening the screen reads
     only the database.
3. **Audio quality per network** (`:core:model`, `:source:youtube`, `:app`, `:feature:settings`, `:feature:player`;
   `be0b34e`, `99f52e1`).
   - Settings → Playback: Quality on Wi-Fi and Quality on mobile data, each Auto, High, Normal or Data saver, picked in
     a sheet. Auto is High on Wi-Fi (or Ethernet) and Normal on mobile data (`streamQuality`).
   - All three stream routes (NewPipe, visionOS, iOS) choose by quality: High the highest bitrate, Normal the highest
     up to 96 kbps (else the lowest), Data saver the lowest. Streams that do not say their bitrate are used only when
     none does. The original audio track still comes first. The setting and the network are read at each lookup, so a
     change applies from the next song. Downloads use their own route at High, because a download is kept.
   - `ResolvedStream.codec` ("Opus", "AAC", "Vorbis") from NewPipe's format or the player response's `codecs=`. The
     app's `FormatRecordingResolver` keeps the format of the latest 50 songs looked up. Now Playing shows
     "Opus · 160 kbps" between the times under the scrubber (TalkBack: "Streaming Opus at 160 kilobits per second"),
     and nothing when either is unknown.
   - `ACCESS_NETWORK_STATE` is now declared by the app (Media3 already merged it in).

## Accessibility and layout

- Every control on the import and review screens is a 48 dp pill or row with a role. Rows of buttons wrap
  (`FlowRow`), so 200% text never clips them (screenshot `import_review_large_text`).
- Headings are marked, problems are announced in a polite live region, and progress has a spoken label.
- The review's match row reads as one TalkBack item with its decision.
- The readout uses the stage's secondary text colour, which the per-cover shade keeps at 4.5:1.

## Screens and baselines

Baselines from Record screenshots 39565698457 at `82cab98`, inspected before commit (the review, the problem start,
the readout and the 200% review):
- **New:** `import_start` (dark, light), `import_start_problem` (an unfinished import with a link problem),
  `import_matching`, `import_review` (dark, light), `import_review_large_text` (200%), `import_done`,
  `settings_playback`, `player_stream_readout`.
- **Changed:** the Library shots (the Import playlist tile under Your stats, and Import a playlist on the empty
  Library) and the Settings shots scrolled to Motion, Sound and Downloads (the Playback and Library groups move them).
- Every other baseline is byte-identical.

The device capture script is unchanged: Settings still opens on Appearance and ends on "Storage used" (its scroll
finds it however long the page is), and Library still shows "Liked songs".

## CI

Green on `eb82c1f`, the last code commit, on top of every P7 and widget commit:
- **CI** 39573956302: ktlint, unit tests (parsers, scorer, importer, controller, quality, formats), Room schema
  check, screenshot comparison, debug APK.
- **Secret scan** 39573956409.
- **Device screens** 39573956418: the API 34 captures, plus the widget placement check on the minified build.

P7 alone was green at `fed0c3e` (CI 39566449584, Secret scan 39566449657, Device screens 39566449527) before the
widget fix started.

Pushed in small batches, tests first: the engine tests with the engine, two compile and lint fixes, the import UI
tests with the UI, the quality tests with the quality code, two fixes, then the baselines and docs.
- **Goal 1 green.** CI 39562906154 and Secret scan 39562906150 at `8e051fd` (its Device screens run was cancelled by
  the next push; that slice changed no screen).
- **Goal 2.** CI 39563504589 at `fcc2462`: every unit and interaction test passed (253); only the screenshot
  comparison failed, on the new and changed screens, recorded later. Secret scan 39563504650 green.

Notes:
- **My own mistakes, caught by CI.**
  - A private helper named like an existing `playlistIdFromUrl`, and a colon in a test name, which JVM names can't
    have on Android (CI 39561337561, fixed in `dd3d2ce`).
  - Room schema 5 not yet committed (expected: it is exported by CI's build, then committed) and a one-line
    `if … else if` ktlint wants as a `when` (CI 39561934484, fixed in `8e051fd`; the same rule again in
    `core:model`, fixed in `e2b2bfa`).
  - The equaliser test looked for Sound without scrolling; the new Playback group pushed it below the fold (CI
    39564289967, fixed in `e2b2bfa`).
  - A real bug the new test found: `MutableStateFlow` drops a value equal to the current one, and maps compare
    without order, so a song looked up again kept its old place and was the next one dropped. The order now lives
    in its own map and the flow publishes copies (Record screenshots 39565081708 failed on it, fixed in `82cab98`).
- **Record screenshots** runs 39564299293 and 39565081708 failed on those unit tests, so they uploaded nothing.
  39565698457 recorded the baselines.
- **Device screens.** Runs on intermediate commits were cancelled or superseded by newer pushes; only the run on the
  final commit counts.

## Widget fix (priority addendum)

Found on a real Pixel launcher after the 0.9.6 release: the release build's home-screen widget, once placed, stayed on
Glance's loading spinner for minutes and never drew, with no Glance error in logcat. The picker preview also showed the
two-row layout squeezed into the 4 × 1 box, with the play button cut off.

- **Reproduced first** (`6a35130`, `fc57f11`, `14b494b`). A new check in the Device screens workflow builds the
  minified (R8) release APK, signed with the debug key, and installs it on the API 34 emulator. It grants bind
  permission (`appwidget grantbind`) and runs `WidgetCheckInstrumentation`, a plain `Instrumentation` in Java with no
  test library. That instrumentation binds Sitara's provider through an `AppWidgetHost` at the 4 × 1 size, waits up to
  10 s for "Play something", saves a picture of the placed widget, and prints WIDGET DRAWN or WIDGET CHECK FAILED.
  The workflow keeps the result, the picture and logcat as artifacts. On the 0.9.6 code: "WIDGET CHECK FAILED: still
  loading after 10000 ms" (Device screens 39570187961).
- **Cause, from that run's logcat.** `WM-InputMerger: Trouble instantiating androidx.work.OverwritingInputMerger …
  has no zero argument constructor`, then `WM-WorkerWrapper: Could not create Input Merger`. WorkManager creates the
  input merger by reflection before it runs any worker, and Glance draws app widgets in a WorkManager job
  (`SessionWorker`). WorkManager's own consumer rule keeps `InputMerger` subclasses, but under R8's full mode (AGP 9's
  default) a class-only keep no longer keeps the no-argument constructor. So the session never started, the launcher
  kept the loading layout, and Glance had nothing to log. Debug builds and Robolectric tests were not minified, so they
  never showed it.
- **Fix** (`9717b6c`). `app/proguard-rules.pro` keeps `public <init>()` of every `androidx.work.InputMerger`
  subclass. It also keeps Glance's `ActionCallback` implementations' no-argument constructors, which Glance creates the
  same way when a widget button is tapped. That second rule is a precaution: the check does not tap buttons. Device
  screens 39572246142: "WIDGET DRAWN in 500 ms", with the picture in
  [docs/screenshots/widget_placed_release.png](../screenshots/widget_placed_release.png). It shows Material You colours,
  since wallpaper colours are on by default.
- **Regression checks.** The placement check runs on every push to main. A workflow step also fails if R8's mapping
  stops listing `OverwritingInputMerger.<init>()`.
- **Preview.** `widget_preview.xml` is now a 300 × 72 dp picture of the one-row widget (cover, title and artist,
  previous, play on the accent disc, next), every control inside the frame. `android:previewLayout` (API 31+) was not
  added, since nothing here can check how launchers render it.
- **Mistakes along the way, caught by CI.**
  - The first check used AndroidJUnitRunner. R8 needed a `-dontwarn` for Error Prone's annotations (`fc57f11`), and
    the runner then crashed because the minified app had dropped `androidx.tracing`. A plain Instrumentation replaced it
    (`14b494b`).
  - `testBuildType = "release"` made AGP 9 create unit-test tasks only for release, so CI's
    `:app:verifyRoborazziDebug` disappeared (CI 39572246269). The default came back, and the check now drives the
    minified app with the debug test APK, since its code uses nothing from the app (`eb82c1f`).
- **Not verified:**
  - the fixed release build on a real launcher (Pixel, One UI);
  - the now-playing state on a placed widget (the check covers the nothing-playing state);
  - the precautionary ActionCallback rule, through a button tap in a minified build.

## Not verified

- **A real import from YouTube on a real phone.** CI emulators and cloud test phones are refused by YouTube for
  search, streams and browse, so no playlist was read and no song was looked up there. Tests use fixtures: a fake
  catalogue for the engine and controller, fixed states for the screens.
- **A large file.** The parsers and the resumable job are tested on small inputs. A 2,000-line file means 2,000
  sequential searches (several minutes), and its speed, YouTube's rate limits on it and memory use have not been
  measured.
- **Resume after the process is killed** mid-import, on a phone. The database side is tested (an interrupted `match`
  resumes without a second lookup); the app being killed and reopened was not.
- **The share sheet and opening links**: sharing a playlist from the YouTube and YouTube Music apps, and opening a
  `/playlist` link once Sitara is allowed under "Open by default".
- **File pickers per vendor**: whether each file manager offers .m3u and .csv files under the types asked for.
- **Quality per network on a phone**: switching between Wi-Fi and mobile data, the bitrates YouTube actually offers
  per setting, and the readout for each route (NewPipe, visionOS, iOS).
- **Matching quality on real libraries.** The thresholds come from hand-built cases; how often real exports land in
  review, and how often a sure match is wrong, is unknown.
- **Spotify import** is out of scope: it needs an API key. It is listed as later in PARITY.md and the README.
