# b2 report: Phase B slice 2, real detail pages

Date: 2026-10-20. Harness: Claude Code · Opus 5.5. Target release `0.3.0`; no tag or release was made (the owner tags).

Search now finds albums, artists and playlists as well as songs, and each opens its own page from YouTube Music.
Albums, playlists and artist top songs play all, shuffle, or play from a tapped row. Up next can start a radio of
related songs, and a queue about to run out carries on with them.

## What changed

1. **Artist pages** (`BrowseSource.artist`). An artist is a YouTube channel. The page loads `ChannelInfo`, then in
   parallel the channel's Releases tab (`ChannelTabs.ALBUMS`) and a YouTube Music songs search for the artist's name.
   The pure `artistPage` mapper:
   - drops the " - Topic" suffix of auto-generated channels
   - upgrades the avatar to 544 px and keeps the widest banner
   - keeps only songs credited to this channel as top songs, in YouTube Music's ranking, once each
   - turns releases into album previews keyed by playlist id

   Songs now carry `Track.artistId` (from the song's artist link), so Now Playing can open the artist.
2. **Album and playlist pages** (`BrowseSource.album`, `.playlist`). YouTube Music albums are `OLAK5uy…` playlists.
   `PlaylistInfo` is paged up to 500 items (`shortcut:` comment, lazy paging if people hit it). The pure `albumPage`
   mapper removes YouTube's "Album - " title prefix, takes the artist from the uploader or the first track, keeps
   durations, and names the album on every track. `playlistPage` keeps the curator and drops repeats, because list
   rows are keyed by track id. The extractor exposes no release year, so `Album.year` became nullable.
3. **Search filters.** `SearchFilter` (Songs, Albums, Artists, Playlists) maps to the extractor's `music_*` filters,
   and the pure `musicResults` mapper maps each kind. `searchStates` takes a filter flow: a filter change searches
   the same query at once (no debounce), and a stale filter shows "Searching…". Chips sit under the search field.
   Playlist results were added to `SearchResults`.
4. **Play all / Shuffle / row taps.** Every page with songs has Play all and Shuffle. A tapped row plays the list from
   that row. `PlaybackEngine.shuffle` starts anywhere in random order, and turning shuffle off restores the list order.
5. **Radio.** `RadioSource.radio(seed, exclude)`. For YouTube, the pure `radioTracks` keeps a watch page's related
   items in order: video or audio streams of 1 to 10 minutes (no live streams, mixes, clips or hour-long
   compilations), nothing already queued, no repeats. "Start radio" is on the Up next page, and while it loads or
   finds nothing it says so instead of staying silent. A queue whose last two entries are playing (`runningOut`,
   repeat off) extends itself once per entry. `PlaybackEngine.append` adds entries without reloading the playing item.
   The Media3 port publishes the new tracks to the stream lookup before the player can load them.
6. **Navigation.** A sealed `DetailRoute` (Artist, Album, Playlist, each with its catalogue id) on a back stack, saved
   as `kind:id` with `rememberSaveable`:
   - Back unwinds the stack page by page, a tab switch or a shared link clears it, and each stack depth keeps its own
     scroll position.
   - Pages load from the sample catalogue first (it answers null for ids it does not own), then YouTube. They are
     cached in the view model.
   - Every page has loading, error with "Try again", and empty states. Sample albums, artists and playlists now open
     the same pages.
   - No navigation library was added (PLAN updated).
7. **Docs.** CHANGELOG "Unreleased", README note and features, PLAN Phase B row, ARCHITECTURE module map and source
   rules, ADR 0004 consequences.

## Tests (all plain JVM or Robolectric, no network), written before the code

| Test | Covers |
|---|---|
| `ArtistMappingTest` | Topic suffix, avatar upgrade, banner choice, missing images, artist-only and de-duplicated top songs, releases with fallback uploader, non-playlist items, channel/playlist id parsing, channel id validation |
| `CollectionMappingTest` | Album page (prefix, artist, artistId, order, durations, album name), album without uploader, playlist repeats and unplayable entries, album/artist/playlist search items, songs-only filtering, filter mapping |
| `SongMappingTest` | `artistId` from the artist link |
| `RadioSelectionTest` | Order kept, queue and repeats excluded, live/mix/clip/compilation dropped |
| `QueueEngineTest`, `PlaybackEngineTest`, `Media3PlayerPortTest` | `runningOut`; shuffle play and restore; append keeps the playing item and uids; empty-queue append; tracks known before the player loads them |
| `FakeMusicSourceTest` | Filtered sample search, sample pages by id and null for other ids, sample radio |
| `SearchStatesTest` | A filter change searches again at once with that filter |
| `DetailRouteTest` | Route encoding round trip, separator inside an id, unknown kinds and empty ids |
| `BrowseInteractionTest`, `PlayerInteractionTest` | Back stack survives restoration and unwinds in order; playlist row and Shuffle; failed page retries its route; filter chips and playlist tap-through; Now Playing artist link; Start radio |
| `ScreenshotTest` | New: `artist_page` (dark, light), `album_page`, `playlist_page`, `detail_loading`, `detail_failed`, `album_empty`, `search_albums`, from hand-built fixtures |

## Commits and CI

| Commit | Change | CI run | Result |
|---|---|---|---|
| 1d57d6b | Artist pages: model, `BrowseSource.artist`, mapper | 39392270648 | green |
| 44200ec | Album and playlist pages, music search filters, sample pages | 39392899527 | cancelled by the next push (concurrency); covered by the run below |
| 838a52f | Radio selection, shuffle play, queue append | 39392950255 | green (unit tests for 44200ec and 838a52f) |
| 61daef7 | Routes, detail pages, search chips, Start radio, auto-radio | 39394065186 | red: two test files missed the `getOrNull` import (main code and ktlint green) |
| b83a29b | Import fix | 39394377176 | unit and interaction tests green; screenshot step red on the 33 expected new or stale baselines |
| bb5578a | Baselines from Record screenshots run 39394377254 | 39394945043 (CI), 39394945101 (Device screens), 39394945152 (Secret scan) | green: verify, unit tests, assemble, emulator captures |

The first Record screenshots dispatch (39394064633) failed on the same missing import.

## Still sample or not done

- **Home and Library** still use the built-in sample catalogue; home and library playlists still play directly.
- **Lyrics** are sample-only.
- **Artwork.** Avatar, banner and cover URLs are mapped, but no image loader is bundled, so pages show generated
  gradients.
- **Album year**: YouTube Music data from the extractor has none, so real albums show "Album" alone.
- **Top songs** come from a YouTube Music songs search filtered to the artist's channel, not from YouTube Music's own
  artist shelf, which the extractor does not expose. An artist whose songs are credited to another channel id shows
  only releases.
- **Radio** follows YouTube's related videos for the current song, not a YouTube Music radio playlist.

## Verification and risks

- Nothing ran on this machine. CI compiled, ran ktlint, unit, Robolectric and screenshot tests, and assembled the
  debug APK.
- **No live network check of the new pages ran.** On a phone: search "Xefer" under Artists and open the artist; open
  an album from its shelf, play all, shuffle, and tap a row; search a playlist; tap the artist in Now Playing; let the
  queue run out and see radio songs appear; turn on aeroplane mode on a page and use "Try again".
- Bot checks: the player request behind link resolution, streams and radio can be bot-checked on data-centre IPs
  (seen on CI in b1a). Search, channel and playlist pages use other endpoints.
- Auto-radio also continues after an album or playlist ends, as YouTube Music's autoplay does; there is no setting to
  turn it off yet.
