# b3 report: Phase B slice 3, saved library

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.4.0`; no tag or release was made (the owner tags).

The Library is now the listener's own, stored on the device: liked songs, playlists, recently played, saved albums
and artists. Every song row has a menu to like, queue or add to a playlist, the player likes and adds the current
song, and Up next and your own playlists can be reordered and trimmed.

## What changed

1. **Room store in `:core:data`** (Room 2.8.5, KSP 2.3.12, no new module). Six tables: `liked_songs`,
   `play_history`, `saved_albums`, `saved_artists`, `playlists`, `playlist_entries`. Each row embeds the song's
   details, so no table points at a song another table dropped.
   - History keeps the newest 500 plays; Recently played lists each song once, by its last play.
   - Playlist positions stay contiguous on move and remove; a song appears once per playlist; deleting a playlist
     cascades to its entries; names are trimmed and must not be blank.
   - Four repository interfaces (`LikedSongsRepository`, `PlayHistoryRepository`, `SavedCollectionsRepository`,
     `PlaylistRepository`) with Room implementations, handed out by `LibraryStore` so Room stays inside the module.
   - `exportSchema = true`; `core/data/schemas/.../1.json` is the file Room exported in CI. CI uploads the export
     (`sitara-room-schema`) and fails when it differs from the committed one, so a schema change must ship with its
     version bump and migration.
   - App writes go through `SitaraApplication.writeLibrary`: they outlive the screen and a failed write is logged,
     never fatal.
2. **Queue operations.** Pure `QueueState.playNext`, `moveUpNext`, `removeUpNext`; the engine mirrors them into
   ExoPlayer with `insert`, `move` and `remove`, so the playing item never reloads. With nothing queued, Play next
   and Add to queue start playback.
3. **Play history.** The engine reports a song when the player is actually playing a new entry, and again when
   repeat replays the same entry; pausing and resuming do not count. The app records it in Room.
4. **Song menu everywhere.** A ⋮ button and a long press on song rows (search, album, playlist, artist, liked and
   your own playlists; long press only on two-column quick picks): Like, Play next, Add to queue, Add to playlist.
5. **Player.** A like heart (filled saffron when liked) and Add to playlist on Now Playing. Up next rows drag to
   reorder and have a remove button; TalkBack gets Move up and Move down.
6. **Add to playlist sheet.** Lists your playlists (newest change first) and names a new one inline, which starts
   with the song. A toast confirms "Added to …" or "Already in …".
7. **Pages.** Album and artist pages have Save / Saved. A Liked songs page and your own playlist pages (Play all,
   Shuffle, Rename, Delete, drag to reorder, remove). A deleted playlist shows "This playlist was deleted".
8. **Library tab reads Room only.** Liked songs, Playlists (with New playlist), Recently played, Albums, Artists,
   each with its own empty text; loading and error states. No sample data. Search's sample browse lost its sample
   "Liked songs" category.
9. **Prompts are sheets.** New playlist, rename and delete use the same `SitaraSheet` as the song menu (see the CI
   section for why).
10. **Docs.** CHANGELOG "Unreleased", README note and features, PLAN Phase B row, ARCHITECTURE module map, data flow
    and testing table. The README and site Library screenshots are the API 34 emulator captures from Device screens
    39402430844 (debug capture fixture: the sample catalogue shaped as a saved library).

## Tests (JVM and Robolectric, no network), written before the code

| Test | Covers |
|---|---|
| `LibraryStoreTest` (in-memory Room) | Song details survive storage (negative colours, null URL, explicit, no artist id); liked order, once each, unlike; history cap and last-play order; saved albums and artists with details and removal; playlist order and duplicates; move and remove keep positions contiguous, out-of-range moves ignored; library order by last change with count and first cover; rename; delete cascades; blank names rejected |
| `QueueEngineTest` | Play next keeps order; played-next songs stay next after unshuffle; Up next positions; edits outside Up next change nothing |
| `PlaybackEngineTest` | Play next and Add to queue insert without reloading; both start playback on an empty queue; Up next move/remove mirrored; a song recorded once when it starts, not while paused; a repeat counts again |
| `Media3PlayerPortTest` | Inserted tracks are known before the player can load them |
| `ReorderTest` | Drop target rounding and clamping, unmeasured rows, neighbours stepping aside |
| `DetailRouteTest` | Liked songs and own-playlist routes round-trip; bad own ids dropped |
| `BrowseInteractionTest` | Library opens liked songs, own playlists and albums; empty library texts; New playlist naming; song menu Add to queue and Add to playlist; album Save state; own playlist Move down, remove, rename, delete |
| `PlayerInteractionTest` | Like and the playlist picker from Now Playing; Up next remove and Move down |
| `ScreenshotTest` | New: `library_empty`, `library_loading`, `liked_page`, `liked_empty`, `user_playlist` (dark, light), `up_next`, `player_liked`, `song_actions`, `add_to_playlist`; `album` and `artist` now open by route; library list/grid shots removed with that layout |

## Commits and CI

| Commit | Change | CI run | Result |
|---|---|---|---|
| 7c7f058 | Room store, repositories, `LibraryStoreTest`, schema upload | 39397726505 (CI), 39397726540 (Device screens), 39397726520 (Secret scan) | green |
| e81b962 | Queue operations, history hook, schema JSON and drift check | 39398765664 | red: `:app` could not see `RoomDatabase` (Room is an implementation dependency) |
| 243ea0a | `LibraryStore` keeps Room inside `:core:data` | 39399088589, 39399088619, 39399088563 | green |
| 2beff86 | Song menus, like/save, playlists, editable queue, Room-only Library | 39399790089 | red: one test tapped a tile under the dock; then a hang and OutOfMemoryError failed every later test |
| f07d348 | Tap through the click action; set text without focus | 39400658456 | red: the hang remained (Record screenshots 39400657910 showed it starts before typing) |
| f013b4e | Prompts as bottom sheets | 39401555243 | red: one test matched a row that Home's quick picks also hold; every other test green |
| 686d17b | Up next matcher scoped to Up next | 39402042057 | superseded by the baselines push (its Record screenshots run 39402041556 passed every test) |
| 08d9f88 | Baselines from Record screenshots 39402041556 (28 changed, 10 new, 3 removed; library, playlist page, picker and Up next inspected) | 39402430963 (CI), 39402430844 (Device screens), 39402430962 (Secret scan) | green; Device screens passed on its re-run (the first attempt timed out waiting for Home on a slow emulator: 57 to 211 skipped frames, no crash in logcat) |

**Why the prompts are sheets.** Under Robolectric, showing material3 1.5.0-alpha28's `AlertDialog` never idles: the
test waits 60 s, Robolectric's text-layout shadow leaks native objects meanwhile, and the test JVM runs out of
memory, failing every test after it. A `ModalBottomSheet` holding the same text field passed. The f07d348 message
blamed the text cursor; that was wrong, the hang starts before any typing.

## Still sample or not done

- **Home and lyrics** still use the built-in sample catalogue (labelled). Search's browse is still the sample catalogue.
- **Drag** does not scroll the list when a row reaches the edge; long lists move in steps (or with Move up/down).
- **Artwork** is still generated gradients, so saved items keep their colours; image URLs are stored for the image
  loader.
- **Not verified on a device:** drag feel, the sheets with the keyboard open, history recording through the real
  player (repeat-one transitions), and the release build with Room under R8.

## Owner checklist before tagging 0.4.0

1. Like a song from the player and from a search row's ⋮; it appears in Library, Liked songs, live.
2. Save an album and an artist; they show in Library; Saved toggles back.
3. New playlist from Library; add songs from search and from the player; reorder by drag; remove; rename; delete.
4. Play a few songs, including repeat one; Recently played shows each once, newest first.
5. Up next: drag to reorder, remove, Play next and Add to queue from a row; nothing reloads the playing song.
6. Kill the app and reopen: everything above is still there.
7. Release build (`assembleRelease`) installs and the library works (Room under R8).
