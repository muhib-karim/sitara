# v1.0.0 report: the rest of Phase C, first release candidate (pass 1 of 3)

Date: 2026-10-23. Harness: Claude Code · Opus 5.5, pass 1 of 3 (Codex and DeepSeek passes follow). Skills invoked:
writing-plans (the plan, [SITARA100_PLAN.md](../plan/SITARA100_PLAN.md)), code-review (whole pass), plus five parallel
agents for the string extraction. `versionName` is `1.0.0`, `versionCode` 23. **No tag or release was made** (the owner
tags after a check on a phone).

Builds and tests ran locally this time (the owner's override: `./gradlew --no-daemon --max-workers=2`, Xmx2g, Kotlin
daemon 1g, the wrapper local and untracked, `./gradlew --stop` after each run) and in CI on every push. Screenshot
baselines still come only from the Record screenshots workflow on CI's image, each inspected before it was committed.

## Where it started

At 44221ce (v0.10.0) Phase C slices c1 to c4, the backup half of c5, c6 (as p9), c8 (as p6 and p8) and most of c9 (as
p7) were done. This pass did the rest of [PHASE_C_PLAN.md](../plan/PHASE_C_PLAN.md) in order.

## What changed

### c5 remainder: downloads exported as tagged files (`ad35078`, `dee4330`, `58f3447`)

- Pure Kotlin in `:core:data` `export/`: a WebM reader (EBML, Xiph/EBML/fixed lacing, unknown sizes, DiscardPadding),
  `webmOpusToOgg` (Ogg pages with CRC and granules from each packet's TOC, OpusHead from CodecPrivate, OpusTags with
  TITLE/ARTIST/ALBUM/LYRICS and a FLAC picture block), `tagMp4` (an `ilst` in `moov/udta/meta`; `stco`/`co64`, `tfhd`
  base offsets and `tfra` entries moved when `moov` grows before the audio; other items kept), safe file names and
  `.lrc` files with headers added once. No new dependency.
- `readWholeDownload` reads a download from the cache only and refuses one that is partial, cut short or evicted.
- `DownloadExporter`: chosen lyrics source first, synced to `.lrc`, plain into the tag; cover from Coil as JPEG; one
  export at a time on the app's scope; a repeat export replaces its files; two songs of one name both keep a file. UI:
  Export on Library's Downloads header and Settings → Downloads → Export to a folder, through the system folder picker.

### c6: a third lyrics source (`9f953dd`)

KuGou's public lyrics service (no key): search by artist, title and length, a candidate within 3 s whose title
matches, LRC downloaded and its credit lines dropped. Ranked with LRCLIB and YouTube Music; exported lyrics use it too.

### c7: automix (`78e1017`, `372015a`, `b02201e`, `af9b7a5`, `962686f`, `fca1d58`)

- `BeatTracker`: spectral flux of log-compressed 512-point spectra every 128 samples at about 11 kHz, tempo from the
  autocorrelation weighted toward 120 BPM, beats by dynamic programming, a least-squares grid, bar lines from
  low-frequency onsets, confidence from periodicity scaled by onset strength, gated loudness, first and last sound.
- `AutomixPlanner`: blends start on an outgoing bar line over 16, 8 or 4 whole bars (5 to 16 s), end before the
  outgoing song falls silent, start the next song on its first bar line (at most 8 s of intro skipped), stretch the
  outgoing song to the next tempo within 8 % (half and double time count), bring the louder side down (±6 dB); plain
  crossfade without confident beats on both sides.
- `Crossfader` plays the plan: tail armed at the bar line, the next song started at its own bar line, the tail's speed
  set and nudged by beat phase every 250 ms (±3 %), automix gains; every cancel (pause, seek, skip, interruption, queue
  edit before the blend, automix off, release) restores the main volume and the tail's speed.
- `SongAnalyser` decodes the playing song's last 30 s and the next song's first 30 s with `MediaExtractor` and
  `MediaCodec` (from the phone, a download, or the stream's byte ranges), once per song per analyser version, kept in
  Room 10 (`song_beats`, not backed up). Settings → Sound → Automix; Now Playing shows "Automix · 124 → 128 BPM · 8 bars".

### c8: kept

The widget, Android Auto and scrobbling from p6 to p8 are unchanged apart from translated Auto titles, notification
buttons and the playback error.

### c9: public Spotify playlists (`ea9dfb3`)

`:source:spotify` reads a public playlist or album from `open.spotify.com/embed/...` (`__NEXT_DATA__`), no account or
key, at most 100 songs. The import treats its rows like a file's (search, score, review, resumable). Private or
missing lists, a page that changed, and a song link are worded; a list of 100 songs says Spotify may hold more and
points to a CSV export (Exportify's headers were already read).

### c10: finish and polish

- QR codes (`42dd91b`, `bb66760`): Sitara's own encoder (byte mode, versions 1 to 40, all levels, Reed-Solomon, best of
  eight masks), decoded by zxing in tests only. Share on songs (menu), albums, playlists and artists (page header): a
  sheet with the code, Send link and a QR card image.
- Long-press previews (`ef87d37`) for albums, playlists and artists on Home, Search, Library and artist pages.
- Palette presets (`3c8acc9`): seven, each an accent with a surface tone, all at AA in light, dark and pure black.
- Predictive back on Now Playing (`fcaf616`).
- Strings and Bangla (`557b59f`, `1d55407`): about 600 strings and plurals in resources across eight modules, Bangla
  for all of them, `localeConfig` (en, bn), Appearance → Language on Android 13+; headings for section and page titles.
- Accessibility gate (`40226a1`) and screenshots in Bangla, right to left and at 200 % text (`9033ee7`, `34439c0`); the
  200 % Library baseline showed the pinned tiles' names cut short, fixed by stacking them (`efa91b4`).
- Baseline profile and performance gates (`2380923`): `app/src/main/baseline-prof.txt` (the APK carries
  `assets/dexopt/baseline.prof`, checked in a local release build and in Device screens); cold start and scrolling jank
  measured on the minified build after compiling it with its profile, against `tools/perf-thresholds.json`. First
  measurement (Device screens 40013999720): cold start 1,549 ms median (1,413 to 1,733 ms). The emulator renders in
  software, so every frame missed 16 ms (100 % "janky"); the scrolling gate is therefore the 90th percentile frame time
  (`frame_p90_ms`), and the janky share is only recorded.

### Review fixes (`7d9b77f`, `b4cdfbc`, `8a8db18`, `5067c3b`, `21f4150`, `efa91b4`, `a881d3e`)

A code review of the whole pass (code-review skill, high) found, and this pass fixed: an import-order slip that would
have failed ktlint; export holding a song several times in memory (now one buffer, streamed to the file, 160 MB cap,
an out-of-memory song skipped) and stopping the whole export on a file its parsers did not expect (now copied
untagged); automix reading whole downloads to analyse 30 s (now byte ranges), analysing songs skipped past and reading
streams on mobile data (now dropped, and deferred to an unmetered network); the predictive back exit snapping to full
size; a cut-short Spotify list hidden by an unavailable track (the flag is kept with the import, Room 11); KuGou taking
"Love" for "Love Story" or another artist's song; two exports of one name (in another case or kind) sharing an `.lrc`.
The 200 % Library baseline also showed pinned tiles cut short (now stacked) and Import's line ellipsized (shorter copy).

## Tests

- `ExportFormatsTest` (12; test-side Ogg and MP4 readers written from the specifications; outputs also read by ffprobe
  locally), `WholeDownloadTest` (4), `DownloadExporterTest` (9), `ExportInteractionTest` (4) and Library export cases.
- `KugouLyricsProviderTest` (8), a three-source ranking case in `LyricsSourcesTest`.
- `BeatTrackerTest` (13) on synthesised annotated drum patterns (85 to 174 BPM, swing, noise, a silent intro, a
  beatless pad). **Measured tolerance:** worst tempo error 0.00 %, mean beat offset 6.4 ms, worst 13 ms; asserted
  within 2 % and 35 ms (F-measure ≥ 0.9), bar lines within 35 ms. `AutomixPlannerTest` (14), `CrossfaderTest` (12, mocked
  players in virtual time: hand-over points, fallbacks, beat sync, and no attenuation or stretch left behind after
  pause, seek, skip, interruption, automix off or a queue edit), `SongAnalyserTest` (5), `SongBeatsStoreTest` (4,
  including opening a version 9 database), `PlaybackServiceTest` and `AutomixSettingTest`.
- `SpotifyTest` (8), Spotify cases in `PlaylistImporterTest`, `PlaylistImportsTest` and `SharedRequestTest`.
- `QrCodeTest` (6, versions 1 to 40 decoded by zxing), `ShareLinksTest`, `ShareInteractionTest`,
  `PreviewInteractionTest`, `ThemeContractTest` (palettes), `PaletteSettingTest`, `PlayerBackTest` (3, through the
  activity's back dispatcher).
- `TranslationsTest` (every English string has a Bangla one with the same arguments; no `Text("…")` literal outside
  the resources) and `AccessibilityGateTest` (13 screens and pages, 40 to 230 controls each).
- Device screens (API 34 emulator): `AutomixCheckActivity` decodes a generated 126 BPM drum track through the platform
  decoder; the run of `fca1d58` found 126.0 BPM at both ends, the first bar line at 393 ms against 400 ms marked. Four
  screens captured with the app in Bangla (Android draws the titles whole; Robolectric breaks some Bangla words in
  large titles, noted in `ScreenshotTest`). Performance gates on the minified build.
- Full local suite green before each push of code; CI and Device screens per push, see below.

## CI

Each push ran CI, Secret scan and Device screens; screenshot baselines came from Record screenshots runs 39992885640,
39997316880, 39998728395, 40000829891, 40004330488, 40007870221, 40010671274 and 40014002674, each inspected.

- Green along the way: CI 40005181553 and Device screens 40005181496 at `0d3ee2a` (export, KuGou, automix, Spotify,
  share, previews, palettes, predictive back); Device screens 39992874862 at `58f3447`, 39998724701 at `ea9dfb3`.
- Red runs, each fixed in the next push: screenshot baselines stale by design until recorded (CI at `58f3447`,
  `fca1d58`, `ea9dfb3`, `70817b9`, `fcaf616`, `1d55407`, `efa91b4`, `21f4150`); the ktlint import order at `efa91b4`
  (`7d9b77f`); Device screens at `1d55407` (the Bangla Now Playing pass looked for the English label) and at `21f4150`
  (the jank share is 100 % on the software-rendered emulator; the gate now uses the 90th percentile frame time).
- Device screens runs cancelled by newer pushes are not listed.
- **Green on `5f22deb`** (all code, baselines and docs of this pass): CI 40019706248, Secret scan 40019705886, Device
  screens 40019705866 (automix check passed; Bangla captures; performance gate passed with cold start 1,323 ms median
  and a 90th percentile frame time of 150 ms against limits of 3,500 ms and 250 ms). The commit that records these runs
  is checked by its own runs, named in the pass's closing line.

## Not verified

- **Exports on a phone**: a real download's WebM or MP4 from YouTube, opening the files in other music apps and on a
  computer, a large export (hundreds of songs) and the app closed mid-export (the export stops; files written stay),
  cloud and SD-card folders through the folder picker.
- **Automix by ear**: real music (live drums, tempo changes, songs without a clear downbeat), the time-stretch quality
  of Media3's Sonic at ±8 %, how the beat nudging sounds, the data used to read the ends of streamed songs, CPU on a
  low-end phone. The analysis is checked on synthetic fixtures and one generated track on the emulator.
- **KuGou** from a home or mobile network over time (the service can change or rate-limit); how often its match is
  right for non-Chinese catalogues.
- **Spotify** links from the Spotify app's share sheet on a phone; how long the embed page keeps its shape.
- **QR codes** scanned by a phone camera from the screen and from the shared image (zxing decodes them in tests).
- **Bangla** reviewed by a native editor; TalkBack in Bangla; the per-app language picker on Android 13 to 16 phones.
- **Predictive back** with a real gesture on Android 14+ (tests drive the dispatcher).
- **Performance**: thresholds are set loose for the CI emulator's software rendering; a real low-end phone's start
  time and jank are not measured; the baseline profile is rules for Sitara's own code, not a profile generated from a
  recorded journey (a Macrobenchmark module is left for a later pass).
- Everything on the earlier reports' "Not verified" lists still stands (p1 to p10).

## Not supported (in PARITY.md)

Google account library sync (signing in to a Google account inside an unofficial client), Cast (Google's closed Cast
SDK), listen together (needs a server), Discord presence (a signed-in user token). Wear is not built yet.
