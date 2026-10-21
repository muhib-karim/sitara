# Changelog

All notable changes to Sitara are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.7] - 2026-10-21

Polish slice 7: bring your music.

### Added

- Import playlist, from Library or Settings → Library. Paste a YouTube or YouTube Music playlist link, or pick an M3U, M3U8 or CSV file (title, artist and album columns, found by name in a header row; comma, semicolon or tab separated). Picking a file needs no storage permission. Sharing a playlist link to Sitara, or opening one, opens the import with the link filled in.
- Songs from a file are found by searching YouTube Music for their title and artist; each result is scored on the title, the artist and the length. Sure matches go straight in, songs from a YouTube link go in as they are, and uncertain ones wait in a review step: the line from your list next to the best match with its cover, and Use this, Other matches or Skip.
- While it works the import shows how many songs were found, are to check and were not found, with Cancel, which stops at once and forgets the import. An import cut short (offline, or the app closed) waits on the import screen to resume where it stopped, without adding a song twice. Importing the same link or file again updates its playlist and keeps the choices you made in review.
- Problems are worded with a way on: a link that is not a playlist link, a private or deleted playlist, no connection (with Try again), a file that can't be read or is over 4 MB. Up to 2,000 songs per import.
- Settings → Playback: stream quality on Wi-Fi and on mobile data, each Auto (High on Wi-Fi, Normal on mobile data), High, Normal (up to 96 kbps) or Data saver (the lowest offered). Changes apply from the next song; downloads always take the best stream.
- Now Playing shows the codec and bitrate the song streams in under the scrubber, for example "Opus · 160 kbps" (TalkBack reads it in words); it is hidden when unknown.

### Fixed

- The home-screen widget stayed on a loading spinner in the release build and never drew. The release build's code shrinker had removed a constructor WorkManager needs, so the background job that draws the widget never started. The widget now draws within a few seconds of being placed (Sitara with Play something, or what is playing), and the widget picker shows a true one-row picture with the play button in full instead of the two-row layout squeezed in.

## [0.9.6] - 2026-10-21

Polish slice 6: everywhere the music goes.

### Added

- Android Auto: Sitara shows up as a media app in the car. Four tabs, Recently played, Liked songs, Downloads and Library (your playlists, saved albums and saved artists), with covers as a grid and songs as a list. Search in the car finds songs, and "play … on Sitara" plays what it finds (no query plays your liked songs). Picking a song plays its list from that song. Downloads play with no connection. Like and Shuffle are on the car's player screen next to the transport. Offline, a search in the car comes back empty and an album or artist page says it can't reach YouTube Music.
- A home-screen widget, four columns by one row: cover, song and artist with previous, play/pause and next. Make it two rows tall and it adds the heart and a progress line. It takes the playing cover's colours, or your wallpaper's when wallpaper colours are on. A tap opens Now Playing. With nothing playing, Play something picks up your last queue (or opens Home when there is none).

### Changed

- When a song can't be played, the notification, lock screen, Bluetooth displays and the car now say "Couldn't play this song. Try again, skip it, or play a download." instead of a technical error, and play and skip keep working there.
- The widget redraws only when the song, play state, heart or a failure changes, and moves its progress line in 15-second steps while music plays. Nothing runs for it while paused, and nothing starts for it until a widget is placed. No new permissions.

## [0.9.5] - 2026-10-21

Polish slice 5: your stats.

### Added

- Your stats, from a tile in Library or Settings → Listening history: This week, This month, This year or All time, each starting at midnight where the phone is (a week starts on the day your language's calendar starts it).
- Stats show the time you listened and your plays, a chart of the hours you listen in (TalkBack reads the busiest hours), your top songs with plays and minutes (a tap plays them from there), your top artists with their top song's cover (a tap opens the artist) and your top albums.
- Share turns a range into a card: the top song's cover blurred behind the time you listened, your top artist and your top five songs, previewed before it goes to the share sheet as an image. No storage permission is asked for.
- Settings → Listening history: Pause listening history (new plays are not kept) and Clear listening history, which asks first. Stats say when history is paused.

### Changed

- A play is now kept once you have heard 30 seconds of a song, or half of a song shorter than a minute, with when it started and how long you listened; paused time and skipped parts don't count. Recently played and Most played follow the same rule, so a song skipped after a few seconds no longer shows up there.
- Play history keeps every play instead of the latest 500. Plays from before this version still show in Recently played and play counts, but not in stats, because they were kept without the time listened.
- Everything stays on the phone: nothing about your listening is sent anywhere.

## [0.9.4] - 2026-10-21

Polish slice 4: make it yours.

### Added

- Settings → Appearance, with a live preview card at the top that shows the page, a card, chips and a playing song in the colours you pick.
- Theme: System, Light or Dark.
- Pure black: black instead of dark grey in the dark theme, and a black Now Playing without the cover's blur. The old "AMOLED black" choice carries over to it.
- Wallpaper colours (Material You) on Android 12 and later, on by default; turn it off to use your own accent.
- Accents: Saffron, Rose, Violet, Blue, Teal or Green, each tuned for light and dark, or From artwork, which follows the cover of the song that's playing. Every accent is checked to read at 4.5:1 on every background, card and chip, in both themes, and is darkened or lightened until it does.
- Use artwork colours across the app: backgrounds, cards and chips take a tint from the playing song's cover, not only Now Playing. Text stays at 4.5:1 on them.
- The media notification and lock screen get Like (a filled heart when the song is liked), Shuffle and Repeat buttons beside play and skip, which show their state and work from there. How many of them show at once, and their colours, are up to the phone.
- Up next: swipe a song left to remove it, or use its ×; either way an Undo puts it back where it was.
- Dragging a song in Up next or in a playlist ticks the phone's haptics once for each row it passes.
- Save as playlist in Up next makes a new playlist of the playing song and everything up next, in order.
- Songs that radio adds (Start radio, or a queue running out) show under "Up next from radio". Songs you queue go ahead of them, shuffle keeps them apart, and each part reorders within itself.
- TalkBack: Remove from the queue, Move up and Move down on every Up next song; accent swatches name their colour and say which is chosen.

### Changed

- A playlist name (New playlist, Save as playlist) is saved from the keyboard's Done key or a hardware Enter too, and the keyboard closes itself.

## [0.9.3] - 2026-10-21

Polish slice 3: Search and Library that look alive.

### Added

- Search opens on your recent searches, newest first: tap one to search again, remove one with its ×, or Clear all (Sitara asks first). They are kept on the phone only, up to twenty. With none yet, Search says where they will show up and offers Start a search.
- Moods & genres from YouTube Music under them, as colour blocks in YouTube Music's own colours with black or white names, whichever reads better. A tile opens that mood's or genre's page: songs, featured and community playlists, videos and albums, drawn like Home's shelves. Tiles that lead to a page Sitara cannot show are left out.
- Suggestions while you type, from YouTube Music, as a row of pills above the filters so the results stay in view. When YouTube Music does not answer, your matching recent searches stand in.
- Search remembers the filter you used last (Songs, Albums, Artists or Playlists).
- Library pins Liked songs and Downloads at the top with how many songs each holds, then Recently played, then one kind at a time: Playlists, Songs, Albums, Artists or Downloads.
- Library as a grid of covers or a list; your choice is kept. Each kind can be ordered: Recently added and A to Z everywhere, plus Recently played and Most played for songs and artists (from your play history on the phone). Only the orders a kind can follow are offered.
- A playlist with four or more different covers shows them as a 2 × 2 collage, in Library, Add to playlist and Home's Jump back in.
- A fresh Library shows three fanned covers, what fills it, and Find music.
- Placeholders in the real layout while Library and Moods & genres load (still with Reduce motion), each announced once to TalkBack.

### Fixed

- On Now Playing, a song that cannot play no longer pushes the seek bar and the play controls below the screen on a small phone (seen on a 360 × 640 dp test phone). The cover now takes the room the controls leave, and "Couldn't play this song" with Try again and Skip is one compact row.

## [0.9.2] - 2026-10-21

Polish slice 2: a Home that fills the screen.

### Added

- Home comes from YouTube Music's own Home (signed out, for your language and country): Quick picks, playlists, new albums, songs and artists, shelf after shelf. More shelves load as you near the end of the list.
- Mood chips at the top (Relax, Workout, Focus, Commute and the others YouTube Music offers) filter Home; tap the chosen one again for all of Home. A mood whose shelves Sitara cannot play (Podcasts) says so instead of showing an empty page.
- Quick picks page four songs at a time, with the next page peeking in; tap to play, long-press or the row's button for the song's menu. Albums, playlists and songs ride carousels of 150 dp covers with two-line titles, and artists show as round portraits. A shelf has More when it leads to a playlist or an artist page.
- Listen again (your recent plays), Your likes and Jump back in follow the first shelf.
- While Home loads, placeholders in its own layout pulse gently (they hold still with Reduce motion) and TalkBack hears "Loading Home" once.
- When YouTube Music does not answer, a card says so with Try again; the trending chart and your own shelves stay below it, so Home is never blank. A shelf page that fails to load more says so with its own Try again.

### Changed

- "Recently played" on Home is now "Listen again", and "From your likes" is "Your likes". Songs on Home's shelves open their menu on a long press.
- The trending chart now only stands in when YouTube Music's Home does not load.

### Not shown

- Podcasts, mood and genre buttons and the taste-builder shelf are left out of Home, since Sitara cannot play or open them yet.

## [0.9.1] - 2026-10-21

Polish slice 1: the stage and the shell.

### Added

- Now Playing and Lyrics sit on the song's cover, blurred across the whole screen (Android 12 and later; a stretched low-resolution copy on older phones), washed in its colour with a soft glow in its accent. How dark the stage gets depends on the cover: a dark cover keeps its colour, a white one gets enough shade that every line of text stays at 4.5:1 contrast or better.
- The dock folds while you scroll on: the tabs give way to just the open one, beside the song, in a single row. Scrolling back or tapping that tab brings the tabs back. Pages keep their place either way.

### Changed

- The accent of Now Playing, the mini player and the open tab comes from the cover's most vibrant colour (its muted one when nothing in it is vibrant), and the stage's wash from its main colour, crossfading when the song changes.
- The mini player and the tabs are one floating glass pill, tinted with the cover's accent, with an accent progress line under the song.
- Lyrics are larger and heavier (32 sp extra bold). The line being sung is white; the others are dimmed and softened the further they are from it. The lyrics header shows the cover, title and artist with round Like and More buttons.
- The seek bar is thicker and grows under the finger; when a song cannot play it stays in place, still, above Try again and Skip. Shuffle and repeat are round glass buttons, and the title is set extra bold over a medium-weight artist line.
- Equaliser faders are slimmer, with a round thumb, and fill from the 0 dB line (up for a boost, down for a cut), so a flat equaliser shows nothing lit. The faders dim while the equaliser is off.
- Glass blur stays on with Reduce motion, since it does not move.

## [0.9.0] - 2026-10-20

Phase C, slice 3: sound II.

### Added

- A 10-band equaliser (31 Hz to 16 kHz, ±12 dB per band, preamp ±12 dB) built into the player, with ten presets (Flat, Bass boost, Treble boost, Vocal, Rock, Pop, Classical, Electronic, Acoustic, Late night) and your own bands. It applies while you listen, gliding to new settings without clicks, and a limiter keeps boosts from clipping. Each preset lowers the preamp by its largest boost, so it adds no level. Open it from Settings → Sound → Equaliser or from Now Playing → More.
- Use phone equaliser: on phones with an equaliser of their own (Wavelet, a vendor's sound settings), Sitara can hand its sound to it instead. Sitara's bands turn off while it is on; the option is not shown on phones without one.
- Volume for this song: a trim from −6 to +6 dB for a song mastered louder or quieter than the rest, kept with the song and applied every time it plays. Set it from Now Playing → More.

### Changed

- Now Playing's Add to playlist moved into the new More menu, next to the equaliser and the song's volume.

## [0.8.3] - 2026-10-20

### Fixed

- No grey bar is left over the bottom of the dock after you search (seen on Android 14). Sending a search, from the keyboard's search key or a hardware Enter, now closes the keyboard; before, part of it could stay open as a grey band under the dock. 0.8.1 and 0.8.2 did not fix it.

## [0.8.2] - 2026-10-20

### Changed

- The fade under the dock is a plain gradient instead of a blur.

## [0.8.1] - 2026-10-20

### Fixed

- Lyrics load for songs from channels named like "ArtistVEVO": the channel name is split back into the artist's name ("OliviaRodrigoVEVO" becomes "Olivia Rodrigo"), and a busy or failing lyrics server on one search no longer stops the others from being tried.

## [0.8.0] - 2026-10-20

Phase C, slice 2: sound I.

### Added

- Gapless playback: while a song plays, the stream of the song that follows it in the queue (after shuffle, edits and repeat) is looked up ahead, so the player has it ready and songs join without inserted silence. A skip to that song starts at once. Downloaded songs need no lookup.
- Playback speed from 0.5× to 2× and pitch from −6 to +6 semitones, set independently in a sheet from the speed button in Now Playing, with presets and Reset. They last until playback stops. Lyrics keep time at any speed.
- Skip silence in Settings → Sound, off by default.
- Sleep timer: any number of minutes up to 3 hours besides the presets, and +5 or +15 minutes while it counts.

### Changed

- The sleep timer button opens a sheet (presets, custom minutes, end of this song, time left, extend and Turn off) instead of cycling through 15 min, 30 min and end of track. It takes the accent colour while a timer is set.

### Fixed

- When YouTube refuses a song's stream to NewPipe, Sitara asks two other YouTube clients (visionOS, then iOS) before it gives up, and asks the one that worked first for that song afterwards. This does not get past a "confirm you're not a bot" check on your network.
- A song that cannot be played says so instead of sitting at 0:00 behind a Play button: Now Playing shows "Couldn't play this song" with Try again and Skip in place of the scrubber, and the mini player swaps Play for the same two buttons. Nothing is skipped without you choosing to.
- Lyrics are found for more music videos: featured artists ("(feat. X)", "ft. X") are left out of the lyric search, "Artist - Topic" channels count as the artist, and the title as uploaded is tried when the cleaned-up one finds nothing.

## [0.7.0] - 2026-10-20

Phase C, slice 1: real artwork and a player that looks like the music.

### Added

- Real covers everywhere in the app: songs, albums, artists and playlists on Home, Search, the Library, artist, album and playlist pages, Up next, the mini player and Now Playing. Each cover is fetched in a size that fits where it is drawn and kept in memory and on disk. YouTube video thumbnails are cropped to the square of their picture, without the black bars.
- Now Playing takes its colours from the cover: the backdrop, the glow behind the sleeve, the scrubber, shuffle, repeat and like, and the mini player's tint. The highlight colour is checked to stay readable (at least 4.5:1) on the backdrop. Colours fade from song to song, or change at once with reduced motion. The AMOLED theme keeps saffron.
- Lyrics: scrolling them by hand stops the list from following the song, and a "Back to current line" button returns to it. Tapping a line still jumps there.

### Changed

- While a cover loads, or when it cannot (offline, removed, no image), the generated gradient cover shows instead, and the player keeps colours from the song's own palette.

### Fixed

- The device screenshot job waits longer on a slow emulator and dismisses a system "isn't responding" dialog instead of failing.

## [0.6.0] - 2026-10-20

Phase B, slice 5: offline listening and sound. This completes Phase B.

### Added

- Downloads: Download a song from its menu, or a whole album or playlist (yours, liked songs, or one from YouTube Music) with Download all. Downloaded songs play with no connection; songs that are not downloaded stream as before. A downloaded badge marks them on album and playlist pages.
- A Downloads section at the end of the Library lists every download with its progress, "Waiting for Wi-Fi" or "Failed", and lets you cancel, delete, retry, or play the downloads in order. Downloads carry on in the background with a progress notification after the app is closed.
- Settings, Downloads: "Download on Wi-Fi only" (on by default), storage used, and Remove all downloads (it asks first).
- Normalise loudness (on by default): songs play at a similar volume. YouTube's own loudness values are not available through NewPipeExtractor, so Sitara levels the sound gently as it plays: at most 6 dB louder, never past −1 dBFS peaks, never in sudden jumps. Off leaves the sound untouched.
- Crossfade (Off, or 1 to 12 seconds): the end of a song overlaps the start of the next. Songs from the same album, a song on repeat, and the song an end-of-song sleep timer stops after still play without a gap. Pausing, seeking, skipping or a phone call ends a fade at once.

### Changed

- Settings gain Sound and Downloads groups.

## [0.5.0] - 2026-10-20

Phase B, slice 4: a real Home, playback that survives restarts, and lyrics.

### Added

- Home shows the songs trending now on YouTube Charts for your country (the United Kingdom's chart where YouTube has none), with loading, error and Try again states, then Recently played, Jump back in (your playlists and saved albums) and From your likes (liked songs you have not played lately). A fresh install says how Home fills up.
- Lyrics from LRCLIB: synced lyrics highlight and follow the current line and seek on tap; plain lyrics show when nothing is synced; otherwise "No lyrics found". A failed lookup offers Try again. Lyrics are cached on the device (songs with none are asked again after a week), and the page credits LRCLIB. Requests name only the app and its repository.
- The queue, the current song and the position are saved as you listen and come back, paused, after the app is closed or killed. A headset or Bluetooth play button, or Android's resume card, resumes them even when the app is not running.
- The notification, lock screen and Bluetooth displays show the song's cover.

### Changed

- The built-in sample catalogue is gone from the app: Home's sample shelves and mood pills, the sample note and Search's sample browse are removed, and release builds no longer contain sample music. Songs from the sample catalogue saved in an earlier version can no longer play.
- The database moves to version 2 (new tables for the play queue and the lyrics cache; existing data is kept).

## [0.4.0] - 2026-10-20

Phase B, slice 3: your own library, stored on the device.

### Added

- Like songs from the player or from any song's menu (the ⋮ button, or a long press on the row). Liked songs have their own page with Play all and Shuffle.
- Save albums and artists from their pages.
- Playlists of your own: create one from the Library or inline while adding a song, then rename it, delete it, reorder songs by dragging (or with TalkBack's Move up and Move down) and remove songs.
- Add to playlist from any song's menu and from the player.
- Play next and Add to queue from any song's menu. Up next can be reordered by dragging, and songs removed from it.
- Recently played: songs are recorded when they actually start playing (the latest 500 plays are kept).
- The Library tab now shows only what you saved: Liked songs, Playlists, Recently played, Albums and Artists, each with its own empty state.

### Changed

- The library is stored on the device with Room. The database schema is exported and committed, and CI fails if it changes without being committed.
- Search's sample browse no longer lists sample liked songs.

## [0.3.0] - 2026-10-20

Phase B, slice 2: real detail pages.

### Added

- Artist pages from YouTube Music: name, top songs (the artist's own songs, as YouTube Music song search ranks them) and an albums-and-singles shelf from the channel's Releases tab.
- Album and playlist pages from YouTube Music, with the full track list and durations; long playlists load up to 500 songs.
- Search filters: Songs, Albums, Artists and Playlists. Each result opens its page.
- Play all and Shuffle on albums, playlists and artist top songs; tapping a song plays from that song.
- Tap an album's artist, or the artist in Now Playing, to open the artist.
- Start radio on the Up next page, and a queue about to run out carries on with related songs (songs only, nothing already queued).
- Detail pages show loading, error with "Try again", and empty states; Back returns page by page, and the pages survive the app being closed in the background.

### Changed

- Sample albums, artists and playlists open the same pages as real ones.
- YouTube Music gives no release year, so real albums show "Album" without one.

## [0.2.0] - 2026-10-20

Phase B, first release: real music from YouTube Music.

### Added

- Live song search on YouTube Music through NewPipeExtractor v0.26.5 (new `:source:youtube` module, OkHttp downloader).
- Search results play through the existing player and queue together; the best audio-only stream (opus or m4a, https) is picked per song.
- Search shows loading, empty and error states, with a "Try again" action.
- Paste a YouTube Music, YouTube or youtu.be link into Search to get that song, ready to play.
- Open or share those links into Sitara: the song starts playing. Shared plain text becomes a search. Tapping a link needs Sitara allowed under Android's "Open by default"; sharing always works.

### Changed

- Home and Library say that they show built-in sample music; search suggestions are now genre searches.
- Sample lyrics appear only on sample songs, never on real ones.
- Search says "1 result" instead of "1 results".

## [0.1.0] - 2026-10-18

Phase A: foundation, plus the first UI redesign.

### Changed

- Redesigned Home screen.
- Library with grid and list views, plus new album and artist pages.
- Redesigned Search and Settings screens.
- Slimmer scrubber in the full player.
- README with the split-star logo and current device screenshots.

### Fixed

- Play/Pause now responds correctly after seeking within a track.
- Shuffle no longer fails on an empty queue lookup.
- Track completion is synchronised with the player, and the stream loader keeps its state across reloads.

### Added

- Multi-module project: `:app`, `:core:model`, `:core:common`, `:core:data`, `:core:player`, `:core:designsystem`, `:source:api`, `:source:fake`, `:source:lyrics` and feature modules for home, search, library, player and settings.
- App shell with a floating glass pill tab bar (Haze blur on Android 12+, tinted scrim fallback on older versions).
- Home, Search and Library screens on a built-in sample source with generated gradient covers.
- Mini player docked above the tab bar: swipe up to open, swipe down to hide, swipe sideways to skip.
- Full player sheet on a single critically damped spring with drag-velocity hand-off.
- Artwork-tinted gradient backdrop, scrubber, and an Up next / Lyrics pager.
- Word-level karaoke lyrics, backed by an LRC and enhanced word-timed LRC parser.
- Queue engine with shuffle that restores the original order, and repeat off / all / one.
- Crossfade curve math and LUFS normalisation gain towards a −14 LUFS target.
- Sleep timer by minutes or at the end of the current track.
- Artwork palette to theme mapping with contrast-checked colour roles.
- Settings stored in DataStore: theme (dynamic, artwork or AMOLED black) and reduced motion.
- Demo playback of a royalty-free stream through a Media3 `MediaSessionService`.
- `SitaraMotion` design tokens: snappy, smooth and gentle springs plus 120, 220 and 360 ms fades.
- Unit tests (JUnit, Turbine, MockK) and Roborazzi screenshot tests on Robolectric.
- GitHub Actions workflows: CI (ktlint, unit tests, screenshot tests, debug APK artifact), release on `v*` tags with SHA256SUMS, GitHub Pages site, gitleaks scanning and a manual screenshot refresh workflow.
- Project documentation: README, architecture and design system guides, contributing guide, code of conduct, security policy and the project website.

[Unreleased]: https://github.com/muhib-karim/sitara/compare/v0.9.7...HEAD
[0.9.7]: https://github.com/muhib-karim/sitara/compare/v0.9.6...v0.9.7
[0.9.6]: https://github.com/muhib-karim/sitara/compare/v0.9.5...v0.9.6
[0.9.5]: https://github.com/muhib-karim/sitara/compare/v0.9.4...v0.9.5
[0.9.4]: https://github.com/muhib-karim/sitara/compare/v0.9.3...v0.9.4
[0.9.3]: https://github.com/muhib-karim/sitara/compare/v0.9.2...v0.9.3
[0.9.2]: https://github.com/muhib-karim/sitara/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/muhib-karim/sitara/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/muhib-karim/sitara/compare/v0.8.3...v0.9.0
[0.8.3]: https://github.com/muhib-karim/sitara/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/muhib-karim/sitara/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/muhib-karim/sitara/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/muhib-karim/sitara/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/muhib-karim/sitara/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/muhib-karim/sitara/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/muhib-karim/sitara/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/muhib-karim/sitara/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/muhib-karim/sitara/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/muhib-karim/sitara/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/muhib-karim/sitara/releases/tag/v0.1.0
