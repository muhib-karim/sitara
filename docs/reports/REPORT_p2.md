# p2 report: polish slice 2, a Home that fills the screen

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, ui-ux-pro-max, test-driven-development,
ponytail-review (before each push but one, see CI). Target release `0.9.2`; no tag or release was made (the owner tags),
and `versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

The owner found v0.8.3 "very bland and non-feature-rich" next to Metrolist, InnerTune/OuterTune, SimpMusic, ViMusic and
YT Music. On first launch Home showed one 2 × 2 "Trending now" shelf and an empty "Make Home yours" note, so about 70 %
of the screen was blank. Home now comes from YouTube Music's own Home, the way those apps build theirs, and it is never
blank: it has placeholders while it loads, and when YouTube Music does not answer, the chart and the listener's shelves take its place.

## What changed

1. **The feed** (`:core:model`, `:source:api`, `:source:youtube`; `de00424`, `b3e1526`). `HomeFeedSource` gives
   `home(chip)` and `moreHome(continuation)`. `YouTubeMusicSource` asks `music.youtube.com/youtubei/v1/browse` for
   `FEmusic_home` as the WEB_REMIX client, through NewPipe's downloader with NewPipe's YouTube Music client version
   and headers, in the device's language and country. Chip filters send the chip's `params`; continuations go as
   `ctoken`/`continuation`. YouTube answers a continuation only for the visitor that got the first page, so the source
   keeps the `visitorData` of the first page and sends it with every later request (found while recording; without it
   continuations come back empty). `HomeFeedParsing` turns a page into `HomePage(chips, shelves, continuation)`.
   Only what Sitara can play or open is kept. List rows become songs (title, artists before " • ", artist id, album).
   Two-row cards become, by what they open: songs (watch endpoint), playlists (`VL` prefix dropped), albums (the
   `OLAK5uy…` playlist named by the card's play button, artists without the release type and year) and artists.
   Podcasts, mood and genre buttons and the taste-builder shelf are dropped, and so is a shelf left empty.
   "More" survives only when it leads to a playlist or an artist page; New releases' More (a page Sitara has no screen for) is dropped.
   An unfiltered Home with nothing to show is an error, so the fallback below takes over.
   Tests: `HomeFeedParsingTest`, 8 cases on 4 responses recorded signed out (GB, today) and trimmed to a few items per
   shelf (tracking, menus and the visitor id removed), plus `home_constructed.json` built from recorded items for what
   YouTube does not send signed out (artist shelf with an artist More, video songs with a playlist More, mood buttons).
   `NetworkSmokeTest` gained a live Home case (non-blocking job).
2. **Loading Home** (`:feature:home`, `cb47482`, `8065250`). `OnlineHomeLoader` holds `OnlineHome(chips, feed, trending)`.
   One request runs at a time, so a chip tapped mid-load replaces the request. The tapped chip shows as selected at
   once; tapping it again goes back to all of Home. More shelves are appended from the continuation; a failed page
   keeps the shelves and asks again on Try again. The trending chart is only fetched when the unfiltered feed fails, and
   is kept once it has loaded. The view model passes the YouTube source in as three functions; the loader is also the
   screen's `HomeActions`. Tests: `OnlineHomeLoaderTest` (6 cases: first page, fallback, Try again keeps the chart,
   chip select/clear, paging to the last page, failed page retried).
3. **The screen** (`cb47482`). From the top:
   - the greeting;
   - the mood chips (`SelectionPill`, 48 dp);
   - the first feed shelf, then Listen again, Your likes and Jump back in from Room (hidden while a mood is chosen,
     since they are not filtered by it);
   - the rest of the feed, and a placeholder carousel while more is on its way.

   Shelves render as follows:
   - **Song-row shelves** (Quick picks, Trending songs for you) page four rows at a time; the next page peeks 36 dp in
     at the edge in place of the old "1 / 5 · Swipe for more" line. A row plays the shelf from that song; its button
     or a long press opens the song menu.
   - **Carousels** use 150 dp square covers, 12 dp apart, with two-line titles. Artists are round. Song cards play
     the shelf's songs and long-press to their menu; album, playlist and artist cards open their pages.
   - **"More"** is a 48 dp text button labelled "More from <shelf>" for TalkBack.
   - **Straplines** ("FOR RELAXING") show in sentence case as the header's quiet line.

   Shelf keys carry the mood, so another mood's rails start unscrolled (found in review).
   `SectionHeader` gained an optional trailing action, and `ArtworkTile` an optional long press labelled "More options".
4. **Loading and offline** (`cb47482`).
   - **Loading.** Placeholders in Home's real layout: chips, four song rows, two carousels. They pulse between full
     and 45 % opacity in the draw phase, without recomposing, and hold still under reduced motion. Only the first group
     speaks to TalkBack, once, as "Loading Home". This is a pulse, not a moving shimmer band.
   - **Feed failed.** A glass card ("Home didn't load", Try again) sits above Trending now (or its placeholder) and the
     listener's shelves; on a fresh install, the "Make Home yours" hint is added. When the chart fails too, the card
     alone says so: no second error.
   - **A chosen mood that fails** shows only the card. **A mood with nothing playable** (YouTube's Podcasts chip)
     says "Nothing under Podcasts that Sitara can play yet."
5. **Fixtures and docs**. `FakeMusicSource` implements `HomeFeedSource` (six moods, Quick picks, New releases, Mixed for
   you with More, Artists you might like), so screenshots, interaction tests and the device capture screen show a full
   Home. CHANGELOG Unreleased, README features and screenshot alt text, ARCHITECTURE and PARITY were updated.

## Against the references

No image-by-image comparison with the competitors' screenshots was made this session. Metrolist's README was read: its
text names "Personalized quick picks" (a signed-in feed) but does not describe the Home layout, and its Home screenshot
has no caption. What Sitara now follows is the structure of YouTube Music's own Home, which those clients render from the
same `browse` response: a chip row, a Quick picks shelf of song rows, then cover carousels. It is drawn in Sitara's own
look (the saffron accent on the chosen chip, a glass offline card, Inter display headers, the v0.9.1 pill dock); nothing
was copied. What signed-in clients show and Sitara cannot: Mixed for you, account-based Listen again, Your mixes, the
moods-and-genres pages, and podcasts. Sitara is signed out by design; its Listen again comes from history on the device.
Gutters stay at the app's 20 dp token (`SitaraSpacing.Gutter`) rather than 16 dp. Every other screen uses 20 dp, and
one Home with a different edge would break the consistency the goal asks for. Card spacing is 12 dp, chip spacing 8 dp.

## CI

| Commit | What | Run | Result |
|---|---|---|---|
| `de00424` | feed parser, request, fixtures | CI 39525377765 | unit tests and the live smoke job green; ktlint red (import order) |
| `b3e1526` | import order | CI 39525863214, Secret scan 39525863259 | green |
| `cb47482` | Home UI, loader, placeholders, offline | CI 39526458220 | red: the 6 loader tests ran in `backgroundScope`, which `advanceUntilIdle()` does not drive, and a full-width swipe passed Quick picks page two; Record screenshots 39526464850 red for the same test |
| `8065250` | those two tests fixed | CI 39526995726 | unit tests green; ktlint red (a comment's indent); screenshots stale as expected; Record screenshots 39526995408 green |
| `d29de87` | tile subtitle clip, loading covers | CI 39527528905 | only the 22 stale or new Home/Library/mini-player baselines red; Record screenshots 39527535797 green (88 baselines) |
| `73c4680` | baselines + docs | CI 39528071646, Secret scan 39528071716, Device screens 39528071679, Pages 39528071711 | all green; Device screens passed first time (no infrastructure flake to rerun) |

The recorded baselines were inspected before committing: Home dark and light, loading dark and light, offline,
mood and the scrolled shelves. Inspection found the tile clip that cut the first letter of every tile's subtitle
(also in Library and Search) and the squeezed third cover in the loading carousel; both were fixed in `d29de87` and
re-recorded. ponytail-review ran before every push except `8065250` (a test-only fix), which is noted in SKILLS_USED.
Device screens runs for intermediate commits were cancelled by the next push (one run per branch).

## Not verified on a real phone

- **The live feed from a home or mobile network.** CI and the screenshots run on the sample fixture. The live smoke job
  on the GitHub runner did get a real Home (Quick picks 20 songs, Trending community playlists, 11 chips) and its first
  continuation (Throwbacks, Autumn loading, All-time essentials), so `browse` was not bot-checked there today. Song
  streams still are, and YouTube can close or change `browse` at any time.
- **Continuation paging** while scrolling on a device: how early the next page arrives, and that the loader stops at
  the last page.
- **Chip filters** live: each chip's shelves, tapping the chosen chip again, and the Podcasts chip's message.
- Album cards opening (`OLAK5uy…` ids) and editorial playlists (`RDCLAK5uy…`, through NewPipe's mix extractor; one
  was checked by hand to answer with 24 songs) from a phone.
- TalkBack across the whole page (chips, More, long-press actions, the single "Loading Home").
- Placeholder pulse cost on a low-end phone, and large fonts on a small screen beyond the 320 dp baseline.
