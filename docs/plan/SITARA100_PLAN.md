# Sitara 1.0.0 implementation plan (pass 1)

> For agentic workers: execute slice by slice, task by task, tests first (red, green, commit). Steps use checkbox syntax.
> This file is the plan for the frozen task "Sitara to full parity, one finished APK (v1.0.0)". Pass 1 of 3
> (Claude Code; Codex and DeepSeek passes follow). No tag, no release.

**Goal:** finish the rest of [PHASE_C_PLAN.md](PHASE_C_PLAN.md) (c5 remainder to c10) so `main` is a 1.0.0 candidate
that beats BitChord, Metrolist, Meld and ReVanced YouTube Music in every row of [PARITY.md](PARITY.md) that permitted
access allows, and says "not supported" for every row it does not.

**Architecture:** new behaviour goes where its neighbours already live. Pure algorithms (container remuxing and tags,
beat tracking, the transition planner, the QR encoder, the Spotify embed parser) are plain Kotlin with JVM tests and
no Android types; Android glue (SAF, MediaCodec, Compose) stays thin and is covered by Robolectric interaction tests,
Roborazzi baselines and the API 34 Device screens run.

**Tech stack:** Kotlin 2.4, Compose, Material 3, Media3 1.10 (ExoPlayer, session, downloads), Room 2.8 (auto
migrations, exported schema), OkHttp, DataStore, Glance, WorkManager. Android framework `MediaExtractor`/`MediaCodec`
for analysis decoding (no new runtime library).

**Spec:** the frozen task file (owner's), [PHASE_C_PLAN.md](PHASE_C_PLAN.md) c5-c10, [PARITY.md](PARITY.md).

## Where things already stand (read before planning work that exists)

| Slice | Plan item | State at 44221ce (v0.10.0) |
|---|---|---|
| c5 | backup and restore | done in p10 |
| c5 | export downloads as tagged files + .lrc | **to do** |
| c6 | more sources + picker, translation with cache, share cards, Bangla/Hindi/Urdu fixtures | done in p9 with two sources (LRCLIB, YouTube Music); **add a third keyless synced source (KuGou)** |
| c7 | automix | **to do** |
| c8 | widget, Android Auto library service, scrobbling | done in p6 and p8; keep, re-check acceptance |
| c9 | YouTube links, M3U/CSV, review, resume, quality per network, codec readout | done in p7; **public Spotify playlist to do** |
| c10 | palette presets, long-press previews, QR share, Bangla + RTL, accessibility, predictive back, baseline profiles and perf gates | **to do** (only six accents exist; no strings in resources) |

## Global constraints

- Original code only; other apps are studied for ideas, never copied. GPL-3.0.
- No new runtime dependencies unless essential. Test-only dependencies are allowed when they prove something (zxing
  only as `testImplementation`, to decode the QR codes the app draws). `androidx.profileinstaller` is the one runtime
  addition planned, because a baseline profile does nothing on a sideloaded APK without it.
- No inert settings: a control ships only when it changes playback or data.
- YouTube audio is never labelled lossless. Claims stay honest: what CI cannot check on a phone goes in the report's
  "Not verified" list; what permitted access cannot do goes in PARITY.md as "not supported".
- Tests first for every behaviour change; no network in unit tests.
- Local builds only through `./gradlew --no-daemon --max-workers=2` (Xmx2g, Kotlin daemon 1g, from
  `~/.gradle/gradle.properties`), `./gradlew --stop` afterwards, and only with at least 3 GB available. The wrapper
  is local and untracked; CI keeps `gradle` 9.8.1 from `setup-gradle`.
- Every commit: author and committer `Muhib Karim <62206502+muhib-karim@users.noreply.github.com>`, trailer
  `Pass: 1 (Claude Code Opus 5.5)`. gitleaks before each push. No local paths, hostnames, other emails or key-like
  literals in the repo (no fake `sk-` strings in tests).
- Room schema exports are committed from CI's `sitara-room-schema` artifact or the local build's export, never by hand.
- Screenshot baselines change only through recorded runs, inspected before they are committed.
- Do not tag or release. versionName 1.0.0, versionCode 23 at the end.

## Review focus

1. A download whose cache is incomplete or evicted mid-export: the export must skip it with a worded reason, never
   write a truncated file that looks complete. (Task c5.3 test: `exportSkipsSongNotWhollyCached`.)
2. Automix with a seek, skip, pause or queue edit during a blend: no volume, speed or leveller gain may stay changed.
   (Task c7.4 tests: `cancelRestoresVolumeSpeedAndGain` for each trigger.)
3. Automix when analysis is missing, low-confidence or still running: it must fall back to the plain crossfade (or
   gapless within an album), never stall or cut. (Task c7.3 test: `plannerFallsBackWithoutConfidentBeats`.)
4. A Spotify link that is private, removed, an album or a track rather than a playlist, or a page Spotify changed:
   worded problem with Try again, no crash, no empty playlist. (Task c9.1 tests.)
5. Bangla at 200% text and an RTL locale: no clipped or overlapping controls on the main screens. (Task c10.6
   screenshot cases `*_bn_large_text`, `*_rtl`.)

---

## Slice c5: downloads exported as tagged files with lyrics

Gap: downloads in every rival are locked inside the app. Downloads sit in Media3's `SimpleCache` (`filesDir/downloads`,
key `sitara://track/<id>`), usually WebM/Opus (itag 251), sometimes MP4/AAC; no format is recorded.

### Task c5.1: containers and tags (pure, `:core:data` `export/`)

**Files:** create `core/data/src/main/kotlin/app/sitara/music/core/data/export/{AudioContainer,WebmReader,OggOpusWriter,Mp4Tagger,SongTags,ExportNames}.kt`;
tests in `core/data/src/test/kotlin/app/sitara/music/core/data/export/`; fixtures `core/data/src/test/resources/export/`
(tiny CC0 sine-tone files made with ffmpeg: `tone.webm` (Opus), `tone_frag.m4a` (fragmented MP4, as YouTube serves),
`tone.m4a` (plain MP4 with `stco`)).

**Interfaces (produces):**
- `data class SongTags(title, artist, album, durationMs, cover: ByteArray?, coverMime: String?, lyrics: String?)`
- `enum class AudioContainer { WEBM, MP4, UNKNOWN }`, `fun sniffContainer(head: ByteArray): AudioContainer`
- `fun webmOpusToOgg(input: InputStream, output: OutputStream, tags: SongTags)`: throws `UnsupportedAudioException` for
  WebM that is not Opus (Vorbis is copied untagged by the caller as `.webm`).
- `fun tagMp4(input: ByteArray, tags: SongTags): ByteArray`: writes `moov/udta/meta/ilst` (`©nam`, `©ART`, `©alb`,
  `©lyr`, `covr`), shifting `stco`/`co64` offsets when `moov` precedes `mdat`.
- `fun exportFileName(artist, title, extension): String` (filesystem-safe, ≤ 120 chars) and
  `fun lrcFile(tags: SongTags, synced: String): String` (adds `[ti:]`, `[ar:]`, `[al:]`, `[length:]` headers once).

**Tests:** read tags back with test-side readers (Ogg pages to OpusTags comments including `METADATA_BLOCK_PICTURE`;
MP4 `ilst` atoms): `opusTagsReadBack`, `oggPagesHaveValidCrcAndGranules`, `granuleEndsAtSampleCountMinusPreSkip`,
`mp4FragmentedTagsReadBack`, `mp4StcoOffsetsShifted`, `nonOpusWebmRefused`, `namesStripReservedCharacters`,
`lrcKeepsExistingHeaders`. Locally also check the outputs with `ffprobe` if present (recorded in the report).

### Task c5.2: reading a whole download (`:core:player`)

`SongDownloads.openWhole(trackId: String): InputStream?` reads through `CacheDataSource` with no upstream and no write
sink, and returns null unless `ContentMetadata` length is known and the whole range is cached. Test with Robolectric
and an in-memory `SimpleCache` in a temp folder: `openWholeReadsCachedBytes`, `openWholeNullWhenPartial`.

### Task c5.3: `DownloadExporter` (`:app`) and the UI

- `DownloadExporter(downloads, lyrics: LyricsCacheRepository, covers: suspend (Track) -> ByteArray?, folder: ExportFolder)`
  with `ExportFolder` an interface (`fun exists(name)`, `fun create(name, mime): OutputStream`) so the SAF version
  (`DocumentsContract.createDocument`, overwrite with mode `"wt"` when the name exists) and a test fake share the code.
- `ExportStatus` (`Idle`, `Working(done, total)`, `Done(exported, withLyrics, skipped: List<Pair<String, String>>)`,
  `Failed(reason)`); one job at a time on the application scope.
- Lyrics: the chosen source's row (`choice`), else LRCLIB's synced text, else any plain text; synced goes to a `.lrc`
  file beside the song and plain text into the tag.
- UI: Library → Downloads header gets "Export"; Settings → Downloads gets "Export downloads to a folder". Both open the
  system folder picker (`OpenDocumentTree`); progress and the outcome are worded on Settings' row and as a snackbar.
- Tests: `DownloadExporterTest` (fake folder): `exportWritesTaggedOpusAndLrc`, `exportSkipsSongNotWhollyCached`,
  `existingFileOverwrittenNotDuplicated`, `plainLyricsGoIntoTagOnly`, `oneJobAtATime`. `ExportInteractionTest`
  (Robolectric): the Downloads header and the Settings row open the folder picker; outcome strings.
- Screenshot cases: Settings Downloads with an export done; Library Downloads header.

## Slice c6: lyrics II, a third source

### Task c6.1: `KugouLyricsProvider` (`:source:lyrics`)

- `search?ver=1&man=yes&client=pc&keyword=<artist> - <title>&duration=<ms>` then
  `download?ver=1&client=pc&fmt=lrc&charset=utf8&id=&accesskey=`, base64 LRC. Candidate kept only when its duration is
  within 3 s (when known) and its title matches after the existing clean-up.
- Credit lines are dropped: leading lines that repeat "Title - Artist" or carry `：`/`:` credits ("Lyrics by",
  "Composed by", "Produced by", and their Chinese forms 词/曲/编曲/制作人) before the first sung line.
- Provider id `kugou`, name "KuGou"; cached like LRCLIB through `LyricsCache`; joins `LyricsSources` with the same 10 s
  timeout. Tests with canned JSON on a `MockWebServer`-free fake HTTP seam (as LRCLIB's tests do):
  `kugouPicksDurationMatch`, `kugouDropsCreditLines`, `kugouNoCandidateIsMiss`, `kugouBadBase64IsMiss`,
  `sourcesRankSyncedKugouOverPlainYouTube`. Source sheet copy, PARITY row and ARCHITECTURE updated.

## Slice c7: automix

Gap: no rival does beat-aware transitions well. Today `Crossfader` fades at `duration - window` with equal-power
volume ramps; the tail player plays the outgoing song while the main player starts the next one.

### Task c7.1: beat tracking (pure, `:core:player` `automix/`)

- `class OnsetEnvelope(sampleRate: Int)`: mono, downsampled to about 11 kHz, 128-sample hops (about 11.6 ms),
  log-energy flux in four bands, half-wave rectified, summed.
- `object BeatTracker { fun analyse(envelope: FloatArray, hopSeconds: Double): BeatGrid? }`: tempo by autocorrelation
  over 60-190 BPM weighted by a log-normal prior centred on 120 BPM; beats by dynamic programming (onset strength plus
  a tempo-consistency penalty); downbeat phase = the beat phase of 4 with the most onset energy; confidence = peak
  autocorrelation ratio.
- `data class BeatGrid(bpm: Double, firstBeatMs: Long, downbeatMs: Long, confidence: Float)` and region loudness
  `loudnessDb` (gated RMS).
- Annotated fixtures, synthesized in the test (deterministic): drum patterns at 85, 100, 120, 128, 140 and 174 BPM
  with swing, noise and a bass line, with annotated beat times; a near-silent intro case and a beatless pad case.
  Tests: `tempoWithinTwoPercent` per fixture (174 may report 87; octave errors counted and kept at 0 for 85-140),
  `beatsWithin35ms` (F-measure ≥ 0.9), `downbeatFound`, `padHasLowConfidence`, `silentIntroSkipped`. The measured
  tolerance is written in the report.

### Task c7.2: analysis on the device, cached per song

- Room 10: `song_beats(track_id PK, bpm, intro_downbeat_ms, outro_downbeat_ms, intro_db, outro_db, outro_end_ms,
  confidence, analysed_at, version)`; `SongBeatsDao`, `SongBeatsRepository`, `LibraryStore.beats`. Not in backups
  (it is a cache that can be rebuilt).
- `RegionDecoder` (`:core:player`): `MediaExtractor.setDataSource(MediaDataSource)` over the player's own data source
  stack (cache, local URI or resolved stream), `MediaCodec` to PCM, for the first 30 s and the last 30 s of a song.
- `SongAnalyser`: when automix is on, the playing song's outro and the next song's intro are analysed on one
  background thread, once per song (version-gated), and stored.
- Tests: Room store round trip and migration 9 → 10 from the committed schema; `SongAnalyserTest` with a fake decoder:
  `analysesOncePerSong`, `nextSongIntroQueuedWhenAutomixOn`, `nothingAnalysedWhenOff`, `failureLeavesNoRow`.
  Device screens: an instrumentation check decodes a CC0 click-track fixture MP3 on the emulator and prints its BPM.

### Task c7.3: the transition planner (pure)

- `object AutomixPlanner { fun plan(out: BeatGrid?, outDurationMs, incoming: BeatGrid?, sessionSpeed, fallbackWindowMs): Transition }`
  where `Transition(fadeAtMs, windowMs, incomingStartMs, tailSpeed, incomingGainDb, onBeat: Boolean)`.
- Blend over whole bars: 8 bars (4 or 16 if 8 does not fit between 6 s and 16 s), starting on an outgoing downbeat,
  ending before the outro's end; incoming starts on its first downbeat (skipping a quiet intro up to 8 s); the
  outgoing tail is time-stretched to the incoming tempo when within ±8 % (half or double tempo accepted); incoming
  gain starts at the loudness difference (±6 dB cap) and ramps to 0 dB by the blend's end.
- Fallback to the plain crossfade window (or 6 s when crossfade is 0) when either grid is missing or below 0.35
  confidence. Tests: `fadeStartsOnDownbeat`, `windowIsWholeBars`, `incomingStartsOnItsDownbeat`,
  `tempoMatchedWithinEightPercent`, `halfTimeAccepted`, `farTemposNotStretched`, `loudnessGainCapped`,
  `plannerFallsBackWithoutConfidentBeats`, `albumsStayGapless`.

### Task c7.4: crossfader that plays the plan, and the setting

- `Crossfader` gets an injected clock and a `MixOutput` seam (`mainVolume`, `tailVolume`, `tailSpeed`,
  `incomingGainDb`) so its state machine is tested without ExoPlayer; `arm()` asks the planner when automix is on.
- `stop()` restores volumes, tail speed, the incoming gain offset and the leveller start, and is called on every
  cancel path it is today (pause, suppression, foreign transition, seek, window change, release) plus queue edits.
- Setting `automix: Boolean` (`SettingsCodec` key `automix`, default false), Settings → Sound "Automix" switch with a
  one-line explanation; Now Playing shows "Automix · 124 → 128 BPM" during a planned blend (TalkBack reads it).
- Tests: `CrossfaderTest` with fakes: `cancelRestoresVolumeSpeedAndGain` for pause, seek, skip, queue edit and window
  change; `handOverAtPlannedBeat`; `fallbackUsesCrossfadeWindow`. `SettingsCodecTest` key. Screenshot: Sound settings.

## Slice c8: keep and verify

No new feature: the widget (p6, p7 fix), the media library service and Android Auto (p6), and scrobbling (p8) stay.
Check PHASE_C_PLAN c8 acceptance against the code once more and list what a phone must still confirm.

## Slice c9: a public Spotify playlist

Spotify's Web API needs a registered app key. Its public embed page (`open.spotify.com/embed/playlist/<id>`) carries
the first 100 songs (title, artists, duration) without a key.

### Task c9.1: `:source:spotify` (pure JVM) and the import

- `SpotifyLinks.playlistId(text): String?` (open.spotify.com/playlist/<id>, `/intl-xx/` paths, `spotify:playlist:`).
- `SpotifyEmbedReader.playlist(id): SourcePlaylist(name, songs: List<SourceSong>, truncated: Boolean)` parsing the
  page's `__NEXT_DATA__` JSON with nanojson (already in the app, as `:source:lyrics` uses it).
- `ImportSource.SpotifyPlaylist(id)` with key `spotify:<id>`; rows matched exactly like file rows (search, score,
  review). A playlist of more than 100 songs imports its first 100 and says so, pointing to a CSV export for the rest
  (the CSV parser already reads Exportify's headers; a test pins that).
- Problems worded: not public or removed, not a playlist (album or track link), page changed, offline.
- Tests: `spotifyLinksParsed`, `embedJsonParsed` (fixture JSON trimmed from a public page shape, no real names needed),
  `truncatedAt100Flagged`, `privatePlaylistIsNotFound`, `albumLinkRefused`, `exportifyCsvHeadersRead`,
  `PlaylistImporterTest.spotifyRowsMatchAndResume`. UI copy: the import sheet accepts Spotify links; shared links too.

## Slice c10: finish and polish

### Task c10.1: QR codes and sharing

- `QrCode.encode(text: String, ecc = Ecc.M): QrMatrix` in `:core:common` (byte mode, versions 1-10, Reed-Solomon over
  GF(256), all eight masks scored, format and version bits). zxing `core` as `testImplementation` decodes rendered
  matrices: `roundTripsShortLink`, `roundTripsLongPlaylistLink`, `picksSmallestVersion`, `tooLongRefused`.
- Share: song menu, album/playlist/artist pages and previews get "Share link" (`ACTION_SEND` text) and "Show QR code"
  (a sheet with the code drawn in Compose, high contrast quiet zone, "Share image" through `shareImage`). Local songs
  have no link and no share action.

### Task c10.2: long-press previews

- Long-press on album, playlist and artist tiles (today nothing) opens a preview sheet: cover, title, subtitle, the
  first five songs (from `BrowseSource`, loading and error states), Play, Shuffle, Save, Open, Share, QR.
  Songs keep their menu, which gains a header preview (cover, title, artist, album).
- Tests: `PreviewInteractionTest` (long-press opens, Play plays in order, Open navigates, failure worded with Try
  again). Screenshot cases.

### Task c10.3: palette presets

- `Palette` presets in `:core:model` (six or more named palettes, each an accent plus a surface tone: neutral, warm,
  cool, or ink), stored as `palette` (codec key), applied in `SitaraTheme` (surface tint), shown in Appearance as
  swatch cards with the live preview; picking an accent alone keeps working ("Custom").
- Every palette's text and accent walked to 4.5:1 on every surface (unit test over all palettes, both themes).

### Task c10.4: predictive back on the player

- `PredictiveBackHandler` on the full player: progress scales and lifts the stage toward the mini player, release
  collapses, cancel springs back; reduced motion uses a plain fade. Tests through `OnBackPressedDispatcher`
  (`dispatchOnBackStarted/Progressed/Cancelled`) in `PlayerBackTest`.

### Task c10.5: accessibility pass

- A semantics gate test walks every main screen (Home, Search, Library, detail, Now Playing, Lyrics, Up next,
  Settings and its pages) and fails on a clickable node whose touch bounds are under 48 × 48 dp or that has no label.
- Section titles marked as headings, switches and choices with state descriptions, song rows with custom actions
  (Play next, Add to queue, Like, Download) so TalkBack needs no long-press, the scrubber with a progress range.
- Screenshot cases at 200 % text for Library, Search, Settings, detail page, Up next and lyrics.

### Task c10.6: strings in resources, Bangla and right-to-left

- Every user-visible string moves to `res/values/strings.xml` of its module (plurals as `<plurals>`), labels of
  `:core:model` enums mapped in the UI layer. `values-bn` translations for all of them, written as natural Bangla.
  `localeConfig` (en, bn) for the system's per-app language setting on Android 13+, and an in-app Language row
  (System, English, বাংলা) using `LocaleManager`/`AppCompatDelegate`-free `Configuration` updates.
- RTL: mirrored icons where direction matters (Back, chevrons, Up next drag affordance stays), screenshot cases in an
  RTL pseudo-locale (`ar-rXB`) and Bangla (`bn`), including 200 % text.
- Tests: a resource completeness test (every `values` key has a `values-bn` entry, the same format arguments), the
  screenshot cases above, `LanguageSettingTest`.

### Task c10.7: baseline profile and performance gates

- `androidx.profileinstaller` in `:app`; a `:baselineprofile` `com.android.test` module with a `BaselineProfileRule`
  journey (start, Home scroll, open Now Playing, Library, Search), run in Device screens on the API 34 emulator; the
  generated `app/src/main/baseline-prof.txt` is committed from that run's artifact.
- Perf gates in Device screens: cold start (`am start -W`, median of 5) and Home/Library scroll jank
  (`dumpsys gfxinfo` janky-frame share) against thresholds recorded in `tools/perf-thresholds.json`, set from the first
  green runs with headroom for the emulator's noise; a regression past them fails the job.

## Release candidate docs (no tag)

- `versionName = "1.0.0"`, `versionCode = 23`; CHANGELOG 1.0.0; README screenshots refreshed from the baselines and
  Device screens captures; PARITY.md fully updated (every "c5", "c7", "c10", "later" row resolved or "not supported");
  ARCHITECTURE; `docs/reports/REPORT_v100.md` with what changed, tests, CI runs and the "Not verified" list;
  PROJECT_BRAIN status, next step and session log.
- Finish when CI and Device screens are green on the head of `main`: print `SITARA100_DONE <sha> <ci run> <device run>`.

## Order and commits

c5 → c6 → c7 → c8 → c9 → c10 (c10.1 → c10.7) → docs. One commit per task step that builds and passes locally; push
after each task (gitleaks first), watch CI, Secret scan and Device screens, fix forward. Baselines from the "Record
screenshots" workflow, inspected, committed per slice.
