# p10 report: polish slice 10, your own music

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development, ui-ux-pro-max,
ponytail-review (before each push, except two: `d9c445d` (a one-line test fix and the generated schema) and `1421939`
(screenshot cases only), both reviewed afterwards with the next change, with nothing more to fix). Target release
`0.10.0`. No tag or release was made (the owner tags), and `versionCode`/`versionName` are unchanged (the owner's prep
commit sets them).

PHASE_C_PLAN c4 and the backup half of c5; PARITY rows "Local audio files" and "Backup and restore". Before this slice
Sitara played only YouTube Music, and nothing could leave the phone except scrobbles and shared images.

## What changed

### Local index (`24b218f`, `5b0b51e`, `d9c445d`)

- A song on the phone has the id `local:<content URI>` (`localTrackId`, `Track.localUri`, `Track.isLocal` in
  `:core:model`), so likes, playlists, history, the saved queue, scrobbles and backups keep it like any id and play the
  file without the index. `toDownload` skips these songs.
- `LocalIndex` (`:core:data`, `local/`) reads two kinds of place: the phone's music through MediaStore (`IS_MUSIC`, tags
  from its columns; only with `READ_MEDIA_AUDIO` on Android 13+ or `READ_EXTERNAL_STORAGE` up to 12, `maxSdkVersion` 32)
  and each folder picked through the system folder picker (Storage Access Framework, tree walked with
  `DocumentsContract`, tags from `MediaMetadataRetriever`, only while the persisted read grant is held).
- Room 9 adds `local_songs` and `local_folders` (auto-migration 8 → 9, schema from CI's export). A rescan per place reads
  only new and changed files (size and modified time) and keeps their date added; files that are gone are dropped, in one
  transaction per place. A place that cannot be read (no permission, no grant, a `SecurityException` mid-scan) keeps its
  songs, and a folder is marked not linked until it is relinked.
- Missing or blank tags (and MediaStore's `<unknown>`) become the file name, "Unknown artist" and "Unknown album".
  MediaStore's `TRACK` keeps the disc in its thousands, so 2003 is track 3. A cover is the first embedded picture found
  for an album and artist, copied once into the app's files. The same song found twice (same title, artist and album,
  lengths within 2 s, typically a picked folder inside the phone's music) is listed once, the phone's copy first.
- Tiny fixture files (`tools/fixtures/local-audio/`, 2-5 KB MP3s made with ffmpeg from sine tones, CC0): two tagged
  songs of one album with a cover, one file without tags, and a copy of the first in a second folder.

### Songs on the phone in the app (`6d357a0`, `bcf5a74`, `a280bff`)

- Playback: `StreamUriResolver` hands a queued local song's content URI to ExoPlayer's default data source instead of
  resolving a stream. Only songs in the queue get that far, so the exported session still cannot open other content.
  The prefetcher, downloads, radio (Start radio is hidden and the queue is not extended for a song on the phone) and
  ListenBrainz's `origin_url` skip them; YouTube Music's lyrics source ignores them (it reads video ids only) and LRCLIB
  looks them up by their tags. Likes, playlists, stats and scrobbling needed no change.
- Library → On this phone (a pinned tile with the song count): Play and Shuffle; Songs, Albums, Artists and Folders;
  the existing grid/list switch and orders (songs by Recently added, Recently played, A to Z or Most played; groups by
  Recently added or A to Z); an album, artist or folder opens a page of its songs (albums in track order). Picked
  folders list their songs or "Sitara can no longer read this folder · N songs kept" with Relink folder, and can be
  removed (their songs go, likes and playlists stay). The empty state says why and offers Allow access (when it is
  missing) and Choose a folder; a compact card offers Allow access when only folders are read.
- Access is asked for from Allow access only; once the system stops asking, the button opens Sitara's page in the system
  settings. The page rescans on every return, so a grant made there shows at once.
- Search lists songs on the phone that match, first, with an On this phone badge, with or without a connection.
- Device screens pushes the fixtures to the emulator's Music folder, grants `READ_MEDIA_AUDIO` and captures On this
  phone from the real index (`phone` screen); `check_local_index` fails the run unless each fixture song is listed once
  and the untagged file is "field recording 03 by Unknown artist".

### Backup and restore (`92a7885`, `b91bf3a`, `d616157`)

- `LibraryBackups.export` reads liked songs, saved albums and artists, playlists with their songs in order, every kept
  play, volume trims and lyric choices in one transaction, plus the settings map, and writes one JSON document with
  `org.json` (no new dependency): `format: "sitara-backup"`, `schemaVersion: 1`, `appVersion`, `createdAt`. The app writes
  it through the system's Create document picker (`sitara-backup-<date>.json`, mode `"wt"`). The format, with an example,
  is in ARCHITECTURE.md.
- `LibraryBackups.restore` parses the whole file and checks every value's type before writing: a cut-off file is
  "damaged", other text "not a Sitara backup", a higher schema version "made by a newer Sitara". A good file replaces the
  library in one Room transaction (`BackupDao.replaceAll`, playlists get new ids in the same order), then the settings are
  applied through `SettingsCodec`, so old keys carry over as they do for an old install.
- Settings → Library → Backup and restore: what a backup holds and leaves out, Back up to a file, Restore from a file
  (which first asks "Replace your library with a backup?"), and every outcome worded, with counts after a backup or a
  restore. Files over 64 MB are refused as not a backup.

### Translation language (`c02db6e`, `3d01a3e`)

- The language picked for lyric translation is kept in the settings (`translation_language`), so it survives restarts
  and goes into backups; a missing, blank or malformed value means the app's language.

### Docs and screenshots (`1421939`, `2f5390e` and this report)

- 16 new baselines (On this phone dark, light, list, albums, folders with a revoked folder, without access, nothing
  found; an album of songs on the phone; Library with the On this phone tile; Search with a match on the phone; Backup and
  restore dark, light, asking before a restore, restored, damaged) and 11 changed ones (Library gained a tile, Settings'
  Library group a row), from Record screenshots 39643681185, inspected.
- CHANGELOG (Unreleased), README (features, how it works, known limits), PARITY, ARCHITECTURE (module map, songs on
  the phone with the URI permissions, backup and restore with a schema example), SKILLS_USED, project brain.

## Tests

- `:core:model`: local ids, search matching, album/artist/folder grouping (track order, Various artists, Phone storage,
  newest first).
- `:core:data`: `LocalIndexTest` (13 cases on fake readers and in-memory Room: tags, missing tags, one cover per album,
  duplicates, different lengths kept, incremental rescans, date added kept, revoked folder and missing grant kept and not
  linked, relink, no music permission, removing a folder, newest first); `LibraryBackupsTest` (a 1,000-song library with
  two playlists of one name sharing songs, 1,500 plays with repeats, trims, lyric sources and settings restored whole,
  rewriting the identical file; header fields; truncated file and a file with one missing title refused with the library
  untouched; foreign files; a newer schema; replace rather than merge; an older Sitara's backup with plays without time
  heard and the AMOLED key; a song twice in one playlist kept once); `SettingsCodecTest` for the translation language.
- `:core:player`: the resolver hands over a local song's URI without a lookup and refuses one not queued; the prefetcher
  never looks one up. `:source:scrobble`: ListenBrainz sends no `origin_url` for one.
- `:app`: `LocalInteractionTest` (10 cases through the shell: the Library tile and count, play order, an album page in
  track order, both empty states, Choose a folder and Relink folder open the system folder picker, removing a folder,
  Search's badge with Search failing, no Download in a local song's menu, no badge without a match);
  `BackupInteractionTest` (7 cases: Settings opens the page, Back up opens Create document with a dated JSON name, Restore
  asks before Open document, Cancel, worded outcomes, both buttons hidden while working); `DetailRouteTest` for the new
  routes.
- `tools/test_capture_device_screens.py`: the emulator check of the indexed fixtures.

## CI

Green on `2f5390e` (code, baselines and docs): CI 39644642662, Secret scan 39644642604, Device screens 39644642719.
Device screens 39641482445 (`d616157`) was the first to index the fixtures on the API 34 emulator: three rows, each
song once (the copy folded), "field recording 03 by Unknown artist", and the files' embedded covers drawn; the captures
were inspected.

Red runs on the way, each fixed in the next push:
- CI 39637484674 (`5b0b51e`): the duplicates test added a folder without first reading the phone's music (test fixed),
  and the expected schema drift; 9.json taken from that run's Room export (`d9c445d`).
- CI 39639540387 (`bcf5a74`): ktlint, imports out of order in `AppViewModel.kt`; and `BrowseInteractionTest` tapped a
  playlist tile the new pinned tile had moved below the fold (`a280bff`).
- CI 39640753896 (`b91bf3a`): `LibraryBackupsTest` found a real bug: the reader's private `optLong` was shadowed by
  `JSONObject`'s own member (members win over extensions), so a play without time heard and an album without a year
  came back as 0 (`d616157`).
- CI 39641481975 (`d616157`): only the 11 Library and Settings baselines, stale by design until recorded.
- Device screens runs for `5b0b51e` and `bcf5a74` were cancelled by newer pushes before they reported.

## Not verified

- **A real phone's storage.** MediaStore indexing ran only on the CI emulator with three tiny generated files. A real
  collection (thousands of files, odd tags, other formats such as FLAC, OGG and M4A with covers, files on an SD card),
  scan time and memory, and the permission prompt itself on Android 13+ (music and audio) and 12 or older (storage) are
  not checked. Opening system settings after a permanent denial is not checked either.
- **The system folder picker (SAF).** Only tested through fakes: walking a real tree, `MediaMetadataRetriever` tags from
  document URIs, cloud or SD-card providers, the persisted grant surviving a restart, revoking it (clearing the app's
  storage access or uninstalling the provider) and Relink folder picking the same tree. CI cannot drive the system picker.
- **Playback of files on the phone** on a device: content URIs through ExoPlayer, gapless and crossfade between local
  and streamed songs, the notification, Android Auto (local covers are app-private `file://` URIs, which Auto likely
  cannot load) and the widget with a local song. Lyrics found by tags for real local songs.
- **Backups on a phone.** Writing to and restoring from Downloads, a cloud drive (Google Drive, which may label JSON
  files differently) or another phone; restoring a backup made on another phone, where its songs on the phone do not
  exist (they stay listed and fail to play, worded as a playback error); a real library's backup size and restore time.
- **Not atomic across stores.** The library is restored in one Room transaction and the settings after it; a failure in
  the settings write would leave the library restored and the settings as they were (noted as a shortcut in
  `Backups.kt`).
- **"Older schema version migrates."** There is only schema version 1 so far, so no older backup shape exists to
  migrate. The test covers what does exist: a backup written by an older Sitara (plays without time heard, the AMOLED
  theme key from before Pure black) restores, and a newer schema is refused. The restore checks the version so the next
  shape change can read version 1.
- **"Duplicates kept" in playlists.** A playlist holds each song once by design (as since 0.4.0), so a file listing a
  song twice in one playlist restores it once. Repeats the library does hold (plays, the same song in several playlists,
  two playlists with one name) round-trip.
- **TalkBack** on On this phone, the folder rows and the Backup page; large text on the folder row with Relink.
- Local tool checks: the capture script's unit tests were run once on this machine (pure Python, no build), then in CI.
