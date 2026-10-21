# p5 report: polish slice 5, your stats

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development, ui-ux-pro-max,
ponytail-review (before every push). Target release `0.9.5`. No tag or release was made (the owner tags), and
`versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

COMPETITOR_GAP item 9. Before this slice, Sitara recorded a song in its history the moment it started playing. Skips
counted as plays, nothing said how long a song was heard, and only the latest 500 plays were kept. Metrolist and
InnerTune have stats screens; Sitara had none.

## What changed

1. **History that counts** (`:core:model`, `:core:player`, `:core:data`, `:app`; `d51def2`, `e8ceeff`, `d7e8847`, `d09ff9a`, `29a38d6`).
   - **Measuring.** `ListeningTracker` (in `:core:player`) follows the engine's `refresh()`. Time counts only while the
     player is playing, measured on the monotonic clock, so pauses, buffering and a failed stream add nothing. A seek
     adds only what is then played.
   - **The rule.** A play counts once 30 s are heard, or half the song when the song is shorter than a minute. A song
     skipped before that is not a play.
   - **Reporting.** From then on the play is reported again, with more time heard, at each pause, at the next song, at
     the queue's 15 s save and when the service stops. A process killed while playing loses at most 15 s of one play.
     Repeat one, and Play on a finished queue, start a new play. A report with nothing more heard is dropped.
   - **Storing.** Room 4 adds `play_history.listened_ms` and an index on `played_at`. The 3 to 4 migration (an
     AutoMigration, with a test built from the committed schema 3) keeps every play. Older plays get no time heard: they
     stay in Recently played and play counts but are left out of stats. A play is keyed by its song and start, so a
     later report updates the same row. The 500-play cap is gone.
   - **Side effect.** Recently played and Most played follow the new rule, so a song skipped after a few seconds no
     longer appears there.
2. **Stats** (`:core:model`, `:core:data`, `:core:common`, `:feature:library`, `:feature:settings`, `:app`; `d347676`, `cc8249d`, `46a8dab`, `21ac0b2`, `069c5bf`).
   - **Ranges.** `StatsRange.startMs` places This week, This month and This year at local midnight in the phone's
     zone. The week starts on the locale's first day. Each range runs to now; All time starts at 0.
   - **Aggregation.** One grouped query per song (count, time heard, latest details through `MAX(played_at)`) plus
     the plays' start times. `ListeningStats.of` ranks the songs by plays, then time heard, then the latest play,
     then title. It sums artists (catalogue id, or name when there is none) and albums (title and artist, songs without
     an album skipped); each takes its top song's cover. It also buckets time heard by the local hour each play started.
   - **Screen.** Your stats opens from a tile in Library and from Settings → Listening history. It shows:
     - range chips;
     - a totals card: time listened in the accent, plays;
     - "When you listen": 24 bars drawn in Compose, the busiest in the accent, with "Most around 8:00 PM" above;
     - top songs with rank, cover, plays and minutes (a tap plays the top songs from that one);
     - top artists as round covers (a tap opens the artist page);
     - top albums (not tappable: the history keeps no album id).
     
     A range without plays shows fanned covers and one honest line: "A play counts after 30 seconds, or half of a
     short song." Loading has placeholders, and a store that cannot be read says so.
3. **Share card** (`:feature:library`, `:app`; `4049bcd`, `7dc7244`, `a931fab`).
   - **The card.** Share, shown only when the range has plays, opens a sheet with a 9:16 card. The top song's cover is
     blurred and shaded behind it like Now Playing (`ArtworkBackdrop`, whose shade keeps white and grey text at
     4.5:1). The card shows "This week on Sitara", the top song's cover, time listened, the top artist and the top five
     songs. Share image sits above the card so it stays in reach on a short phone.
   - **Sharing.** The card is recorded into a `GraphicsLayer` as it draws. Share image turns it into a PNG under
     `cache/shared/` and sends `ACTION_SEND` through the chooser. The `content://` URI comes from the app's FileProvider
     (`<package>.files`, not exported, only `cache/shared/` reachable), with read access granted to the chosen app.
     No storage permission.
4. **Controls and privacy** (`:core:model`, `:core:data`, `:feature:settings`, `:feature:library`, `:app`; `8d909fd`, `3721ca9`).
   - **Pause listening history.** A switch in Settings → Listening history, kept in the settings store. Read as each
     play is reported, so new plays are not kept while it is on.
   - **Clear listening history.** Asks first, with Clear history or Keep history, then deletes every play. Recently
     played, play counts and stats start over. Likes, playlists and downloads stay. A song playing during Clear comes
     back at its next checkpoint, because it is still being listened to.
   - **Stats while paused.** A line says history is paused in Settings, so new plays aren't counted.
   - **On the phone only.** Nothing about listening leaves the phone; the share card goes out only when the listener
     shares it.

## Accessibility and layout

- **The chart.** TalkBack reads one summary instead of 24 bars: "Listening by hour of the day.", then the three busiest
  hours, each with its time heard. The busiest hour is also named in text above the chart, so the accent colour is never
  the only signal.
- **Rows and tiles.** Each song row reads "1, Title by Artist, 14 plays, 1 h 18 min". Each artist and album tile reads
  its name, plays and time. The share card reads as one summary of everything it shows.
- **Touch targets.** Chips, rows, tiles and the header buttons are at least 48 dp.
- **Reduce motion.** The placeholders hold still. The card's backdrop cuts instead of fading. The chart does not
  animate.
- **Dock and navigation bar.** Stats pads for the mini player, the dock and the navigation bar through the shell's
  content padding, like Settings.

## CI

Green on `f58d949` (the baselines and docs commit, on top of every code commit):
- **CI** 39549796485: ktlint, unit tests, Room schema check, screenshot comparison against the new baselines, debug APK.
- **Secret scan** 39549796460.
- **Device screens** 39549796408 (API 34 emulator).

Baselines came from **Record screenshots** 39549354884: 105 shots, 5 new (Stats light and dark, top artists and
albums, a range without plays, the share card) and 9 changed (the Library tile shifts Library's shots; the Listening
history group shifts Settings' scrolled shots). All were inspected before commit. The device capture script needed no
change: Library still waits for its pinned Liked songs, and Settings still scrolls to its last row.

Notes:
- **The v4 schema.** Room's schema 4 can only be exported by a build. CI 39546503878 exported it, and `29a38d6`
  commits it.
- **My own mistakes, caught by CI.**
  - JUnit 5 assertion messages go last (CI 39546865636).
  - The stats UI test built a `SavedLibrary` without its lists; ktlint wanted a `when` for the bar colours and sorted
    imports (CI 39547499193).
  - A range change needed `waitForIdle`, the artist page's releases are below the fold, and a partial description also
    matched song rows (CI 39547914868, 39548425978).
- **Device screens.** Runs on intermediate commits were cancelled by newer pushes (the workflow's concurrency group).
  Only the run on the final commit counts.

## Not verified on a real phone

- **Real listening over days.** CI emulators and cloud test devices are refused YouTube streams, so no real play ever
  reached the history. The tracker is tested against the engine with a virtual clock and a fake player. The store,
  queries and migration are tested against Room. The screens, the screenshots and the debug build show sample stats
  (`sampleListeningStats`). How the numbers feel after a real week, a month boundary, a time-zone change or a clock
  change has not been seen.
- **The share sheet.** Robolectric checks the PNG in the cache and the chooser intent with its FileProvider URI and read
  grant. Not seen on a phone:
  - the image a receiving app gets;
  - whether `GraphicsLayer.toImageBitmap()` keeps the backdrop's blur on Android 12+;
  - how the card looks in the share sheet's preview.
- **Large font scales.** The card is a fixed 9:16 image, so at the largest font sizes its last song rows may be cut off
  at the bottom.
- **Process death.** A killed process loses up to 15 s of the play in progress; this follows from the code and was not
  tried on a phone.
- **TalkBack** across Stats and the share sheet on a real phone.
