# p3 report: polish slice 3, Search and Library that look alive

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, ui-ux-pro-max, test-driven-development,
ponytail-review (before every push). Target release `0.9.3`; no tag or release was made (the owner tags), and
`versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

The owner found v0.8.3 "very bland and non-feature-rich" next to Metrolist, InnerTune/OuterTune, SimpMusic, ViMusic and
YT Music. Slices 1 and 2 fixed the stage, the shell and Home. Before this slice, an empty Search was a blank page with
three hard-coded "Try a search" lines, and Library was one long plain list with no grid, order, filters or collages.
A cloud test phone also showed a v0.9.1 bug: on Now Playing, a song that cannot play pushed the controls off a small
screen.

## What changed

1. **A failed song keeps the controls on screen** (`:feature:player`; `58d1570`, `4f02b95`, `b710f3a`, `fdc95c7`).
   - **Cause.** The Playing page reserved a fixed 240 dp under the cover for the heading and controls, which need about
     310 dp. So on a 360 × 640 phone the play row was already partly below the screen. The failed-song block
     (message, then Try again and Skip under it) stacked another 84 dp above the transport row.
   - **Fix.** The cover now takes the height the heading and controls leave: a weighted cover in a column at least as
     tall as the page, scrolling only when text is too large for the screen. "Couldn't play this song" is one row
     with Try again and Skip, as tall as its buttons.
   - **Test first.** A bounds test on a 360 × 568 dp window (a 360 × 640 phone minus a 24 dp status bar and a 48 dp
     navigation bar) checks that the seek bar and all five transport buttons end inside the screen. Its first
     version passed against the bug, because bounds in root are clipped to the scrolling page (CI 39530694579). The
     second measures unclipped position plus size and failed for the right reason (CI 39531162982). With the fix it
     passes.
   - **Baselines.** A 360 × 640 screenshot of the error state was added.
2. **Suggestions and Moods & genres in the source** (`:core:model`, `:source:api`, `:source:youtube`, `:source:fake`; `f754ab3`).
   - **Contracts.** `SearchSuggestionSource.suggestions(query)` and `MoodsSource.moods()` / `moodPage(params)`.
   - **Requests.** `YouTubeMusicSource` asks `music/get_search_suggestions` and the `FEmusic_moods_and_genres`
     browse with the same WEB_REMIX request as Home (one `music()` request builder now serves all three), as Home's
     visitor once there is one.
   - **Parsing.** `ExploreParsing` keeps the typed completions, not the songs and artists YouTube lists under them.
     It turns Moods & genres into typed `MoodSection`s of `Mood(title, colour, params)` and keeps only tiles that open
     a mood or genre page. That page is carousel shelves like Home's, so the Home parser reads it (all five shelves of
     the recorded Blues page survive: Songs, Featured playlists, Community playlists, Music videos, Albums).
   - **Tests.** `ExploreParsingTest`, 5 cases on responses recorded signed out (GB, today) and trimmed (tracking and
     visitor ids removed, a few items per group), plus a constructed tile that opens the charts page and is left out.
     The live smoke job gained a suggestions case and a moods case that opens the first tile's page.
3. **Settings and Library data** (`:core:model`, `:core:data`; `a5ecc3d`).
   - **Settings.** They now keep the last search filter, up to twenty recent searches (one per line), and Library's
     layout and order.
   - **Room.** The play history gives each kept song its plays and last play. A playlist lists its first four
     different covers (`PlaylistSummary.covers`; `artwork` is the first). These are queries only, so the Room schema
     is unchanged.
   - **Tests.** `SettingsCodecTest` and `LibraryStoreTest` cases.
4. **The Search page** (`:feature:search`, `:feature:home`, `:core:designsystem`, `:app`; `7c1f517`, `a3fc035`, `7c4b824`).
   - **Nothing typed.**
     - Recent searches, newest first: tap to search again; × removes one; Clear all asks in a sheet ("Clear" or "Keep
       them").
     - With none yet: a tinted icon, "Your searches show up here", and Start a search, which focuses the field.
     - Below them: Moods & genres in two columns of 64 dp colour blocks in YouTube Music's own colours. The name is in
       black or white, whichever contrasts more; one of the two always reaches 4.58:1 (`ArtworkPalette.inkOn`, with a
       sweep test over 36 hues × 7 lightnesses).
     - A tile opens `MoodScreen`, which reuses Home's shelves and placeholders.
   - **While typing.** Up to six suggestions, asked for once the query settles (200 ms), sit in a row of pills above the
     filters, so the results stay in view. When YouTube Music does not answer, the recent searches containing the
     query stand in.
   - **What counts as a search.** A search is kept once it is sent from the keyboard (search key or hardware Enter,
     closing the keyboard), picked from a list, or one of its results is played or opened. Typing alone does not keep
     it, since Search searches as you type.
   - **The filter.** The chips open on the last one used.
   - **Shared pieces.** The placeholder pulse moved to the design system (`rememberPlaceholderPulse`,
     `PlaceholderBlock`) for Home, Search and Library. Home's "More" became the shared `HeaderAction` ("Clear all"
     uses it too).
   - **Tests.** `SearchLandingTest` (recent-search rules, debounced suggestions, fallback, clearing) and
     `SearchLandingInteractionTest` (9 cases: search again, remove, Clear all with Keep them and Clear, the empty
     state's action, keyboard, suggestion and result each keeping the search, a tile opening its page and Back,
     moods Try again, the loading announcement). `DetailRouteTest` covers the new mood route.
5. **Library** (`:feature:library`, `:core:designsystem`, `:app`; `1b1a40f`). From the top:
   - **Pinned tiles.** Liked songs and Downloads with their song counts. Downloads opens its list.
   - **Recently played.** A row of 150 dp covers. A tap plays from there; a long press opens the song menu.
   - **Kinds.** Playlists, Songs, Albums, Artists, Downloads. Every chip changes what is listed.
   - **Order and layout.** The order (a sheet) and grid or list (both kept in the settings), and New playlist on
     playlists. Only orders that change something are offered:
     - Recently added and A to Z for playlists and albums;
     - those plus Recently played and Most played for songs and artists, from the play history on the phone;
     - none for downloads, which are rows in the download manager's order.

     A to Z follows the phone's language (Collator). Playlists order by when they were made (ids grow).
   - **Grid.** As many 150 dp covers as fit between the 20 dp gutters, two at least.
   - **Collages.** A playlist with four different covers shows a 2 × 2 collage. Library, Add to playlist and Home's
     Jump back in all use it.
   - **Empty.** A fresh Library shows three fanned covers in the theme's colours, "Your library starts here", and
     Find music, which opens Search.
   - **Loading.** Placeholders in Library's layout, announced once.
   - **Tests.** `LibraryOrderTest` (6 cases, every order on songs, artists, playlists, albums, and what each kind
     offers) and `LibraryInteractionTest` (9 cases: each chip shows its own collection; each offered order changes
     the order and is kept; playlists offer no plays; downloads have no order or layout; grid and list switch and
     are kept; only the four-cover playlist is a collage; the pinned tiles count and Downloads opens; Recently played
     plays from the tapped song; one loading announcement). Existing Library tests were moved onto the chips.
6. **Docs and baselines.** CHANGELOG Unreleased, the README features table and screenshots (Search and Library replace
   Downloads and Sound in the strip), the site's alt text, ARCHITECTURE, PARITY, SKILLS_USED and the project brain.
   Baselines from Record screenshots 39534467648, inspected before committing: Search landing dark and light (recent
   searches, mood tiles), no recent searches, moods loading, suggestions, the Jazz mood page, Library grid and list
   (the list shows Night Drive's 2 × 2 collage; the grid's sample playlists have fewer than four covers), Library
   songs, empty and loading, Downloads, Add to playlist; Now Playing at 360 × 640 with a failed song, tall and large
   text (from Record screenshots 39532132231).

## Accessibility and layout

- **TalkBack.**
  - Every tile is one merged node with its title and subtitle. A mood tile reads its name as a button.
  - A recent row reads its search, with "Search again" as the action. Its × reads "Remove … from recent searches".
  - The order control reads "Order: Recently added", with "Change the order" as the action. The layout button reads
    "Show as a list" or "Show as a grid".
  - Placeholders speak once.
- **Touch targets.** 48 dp or more: chips, pills, header actions, the × buttons, 56 dp sort rows, 64 dp mood tiles.
- **Contrast.** Mood names reach 4.58:1 or better. Other text uses the theme's checked roles.
- **Insets.** New pages (`MoodScreen` and both landing states) pad for the dock and the navigation bar like the rest.
  The bottom scroll edge stays a plain fade.

## Against the references

No image-by-image comparison with the competitors was made this session. What Sitara now follows is the structure
those clients share with YouTube Music: recent searches and moods and genres on an empty Search, completions while
typing, and a library of pinned collections, kinds, a grid/list switch and orders. It is drawn in Sitara's own
look: saffron accent on the chosen chip, 150 dp covers and 20 dp gutters, Inter display headers, the pill dock.

## CI

| Commit | What | Run | Result |
|---|---|---|---|
| `58d1570` | small-phone test (first version) | CI 39530694579 | red only for the new screenshot (no baseline yet); the bounds test passed wrongly (clipped bounds) |
| `4f02b95` | unclipped bounds | CI 39531162982 | red for the right reason: the transport ends below the 568 dp window |
| `b710f3a` | the fix | CI 39531764676 | unit tests green; screenshots stale as expected; Record screenshots 39532132231 green |
| `fdc95c7` | player baselines | CI 39532606943, Secret scan 39532606895, Device screens 39532606883 | green |
| `f754ab3`, `a5ecc3d`, `7c1f517` | source, data, Search page | CI 39533413998 | red: a duplicate constant in `ArtworkPalette` and a ktlint brace rule |
| `a3fc035` | that fix | CI 39533779008 | unit tests green (the Search interaction tests included); 12 screenshots stale or new as expected |
| `7c4b824`, `1b1a40f` | result-keeps-search test, Library | CI 39534118115 | one failure: an existing test tapped the first playlist cover, now under the floating dock in the grid; the 9 Library interaction and 6 order tests passed |
| `50c1d6a` | tap through the click action | CI 39534460222 | unit tests green; 18 screenshots stale or new as expected; Record screenshots 39534467648 green (95 baselines) |
| `87f9f7c` | baselines, docs, report | CI 39535060152, Secret scan 39535060119, Pages 39535060176 | green; Device screens 39535060180 red for a real reason, not the infrastructure flake: the capture script waited for a cover now below the first screen of Library and for Search's removed "Try a search" |
| `fca2adb` | capture waits for Liked songs and Recent searches | CI 39535614914, Secret scan 39535614948, Device screens 39535614945 | all green; Device screens passed first time |

## Not verified on a real phone

- **Live suggestions and Moods & genres from a home or mobile network.** CI emulators and cloud test devices are
  refused by YouTube, so the fixture tests and the fallbacks are what CI and the screenshots prove. The live smoke job
  runs on a GitHub runner, and in CI 39533779008 it did get live answers: six completions for "lofi"; Moods & moments (Chill, Commute, Energize, Feel good, Focus, Gaming, Halloween, Party, Romance, Sad, Sleep, Workout) and the genres; Chill's page with 15 playlist shelves. YouTube can change or close these endpoints at any time.
- **Mood pages live.** Whether every tile's page plays and opens its playlists and albums from a phone. Pages came whole
  (no continuation) when recorded; Sitara shows only the first page if that changes.
- **The small-phone fix on the cloud test phone itself.** It is checked at 360 × 568 dp in Robolectric and in the
  360 × 640 screenshot. Real system bars, display cutouts and the user's font size were not checked on a device.
- **Library orders with a real history:** Most played and Recently played with hundreds of plays, and A to Z in other
  languages.
- **TalkBack** across the new pages, and placeholder cost on a low-end phone.
