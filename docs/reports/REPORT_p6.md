# p6 report: polish slice 6, everywhere the music goes

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development,
glance-canonical-layouts, ui-ux-pro-max, ponytail-review (before every push). Target release `0.9.6`. No tag or release
was made (the owner tags), and `versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

COMPETITOR_GAP item 10, PHASE_C_PLAN c8 (first half: the widget and Android Auto; scrobbling stays in c8). Before this
slice, Sitara's playback service was a plain media session service. Android Auto could only show a playing song's
transport (no browsing, nothing to pick), and there was no home-screen widget. Metrolist, InnerTune, SimpMusic and YT
Music have both.

## What changed

1. **Media library service** (`:core:player`, `:app`; `47d5aec`, `1edf936`, `a4353b3`).
   - `PlaybackService` is now a `MediaLibraryService`. The ExoPlayer, the engine, the saved queue, the notification's
     Like/Shuffle/Repeat (their tests are unchanged and green) and the app's `MediaController` all stay as they were.
   - **The tree.** `LibraryBrowser` (pure Kotlin) serves four tabs, since cars show at most four: Recently played (the
     last 100 songs), Liked songs, Downloads, and Library (Playlists, saved Albums, saved Artists). Playlists open on their
     songs from Room. Saved albums and artists open on their songs from YouTube Music, since the library stores albums
     without songs.
   - **Media ids** are stable and readable: `liked`, `album/<id>`, `song/<parent>/<track>`. Each segment is
     URL-encoded, so no id can break the format. Anything the app did not hand out is refused (`ERROR_BAD_VALUE`).
   - **Paging** follows the browser's page and page size; a browser that asks for one page of `Int.MAX_VALUE` gets
     everything without an overflow.
   - **Search** asks YouTube Music for songs. Offline it answers an empty list, not an error. The answer is reused for
     the song picked from it, so one search costs one request.
   - **Playing from the car.** A picked song goes to the engine, which queues its whole list from that song. The session
     is handed the engine's own playlist back, as playback resumption already does. A voice request ("play … on
     Sitara") plays the songs its search finds; with no query it plays the liked songs.
   - **Who may browse.** `ControllerPolicy` gives the car a new `BROWSE` access: transport, the library commands and
     setting one media item. It cannot replace or reorder the queue otherwise. The notification and Bluetooth keep
     `PLAYBACK_ONLY`.
   - **The app's side.** `LibraryContent` is implemented in `SitaraApplication` from Room, YouTube Music and
     `SongDownloads.downloaded()`. That reads the download index directly, so the Downloads tab is right on a cold start
     in the car before the downloads list has loaded.
2. **Android Auto** (`:core:player`, `:app`; `4709099`, `aa098be`, `bfa3e57`, `4a1b0b4`).
   - `automotive_app_desc.xml` (`<uses name="media"/>`) and its `com.google.android.gms.car.application` meta-data.
     The service also lists the platform's `android.media.browse.MediaBrowserService` action, which Auto looks for.
   - **Content styles.** List by default. The Playlists, Albums and Artists folders lay their covers out as a grid, and
     song lists are lists.
   - **Like and Shuffle** are the session's media button preferences, the same buttons as the notification. The car's
     controller gets those custom commands with its library commands, so they show on its player screen. Repeat comes
     along too.
   - **Errors.** A song that cannot be played now reads "Couldn't play this song. Try again, skip it, or play a
     download." on the car, the notification, the lock screen and Bluetooth displays, instead of ExoPlayer's "Source
     error". The first version used Media3's `setPlaybackException`. The review caught that this removes every
     transport command (on the notification too), so nobody could skip. `WordedErrors` replaces it: a
     `ForwardingSimpleBasePlayer` for the session that only rewords the player's own error. The test drives a real
     failed load and checks that play and skip are still there.
   - **Offline.** Album and artist pages say "Can't reach YouTube Music right now. Downloads play with no connection."
     Downloads play from the download cache with no network, as on the phone.
   - No new permissions.
3. **Home-screen widget** (`:app`, `:core:designsystem`, `:feature:player`; `f8b621a`, `4b51042`, `4a1b0b4`).
   - Jetpack Glance 1.2.0 (Apache-2.0), the only new runtime dependency (the owner asked for Glance). It brings
     WorkManager with it.
   - **Sizes.** One widget, 4 × 1 by default and resizable. One row: cover, song, artist, previous, play/pause and next.
     From 120 dp tall: the heart and a progress line as well, with the transport centred below.
   - **Colours.** With wallpaper colours on (Android 12+), the widget uses the system's Material You widget roles.
     Otherwise it takes the playing cover's colours from the same analysis as Now Playing: its dark wash as the
     background, black or white text (always at least 4.58:1), and its accent for the play button (the play icon at AA
     on it). A test checks eight covers, from white to black to mid blue. Before anything plays, it uses Sitara's saffron.
   - **Actions.**
     - A tap on the widget opens Now Playing: `MainActivity` gets `EXTRA_OPEN_PLAYER`, and the shell asks `PlayerSheet`
       to expand through a new `expandRequests` flow.
     - Buttons are Glance action callbacks that drive the engine on the main thread. The heart goes through the app's
       logged library write, like the notification's.
     - With the player not running, Play sends `KEYCODE_MEDIA_PLAY` to Media3's `MediaButtonReceiver`, which starts
       the service in the foreground and resumes the saved queue. This is the headset path, and Android 12+ allows the
       foreground start after a widget tap.
     - Idle, the widget shows **Play something**: it resumes the saved queue, or opens Home when there is none. It is
       never an inert button.
   - **Battery.** `shownWidgetStates` lets a change of song, play state, heart or failure through at once, and the
     position only in 15 s steps. While playing, it has the engine report its position every 15 s (the CPU is awake for
     the audio anyway). While paused, nothing runs. `updateAll` runs only when a widget is placed, and the feed starts
     only then (once started it runs until the process ends, so a widget removed mid-session leaves the 15 s refresh
     running while music plays). The widget never uses `updatePeriodMillis`.
   - **Picker.** A vector preview of the two-row widget and a description.

## Accessibility and layout

- Every widget control is a 48 dp target with a spoken name: Previous song, Play / Pause / Try again, Next song, Like /
  Remove from liked songs. Play something is a text button, 48 dp tall. Covers and the app icon are decorative.
- Text is one line with an ellipsis. Song and artist always read at AA on the cover wash; Material You roles are
  system-paired.
- A failed song reads "Couldn't play this song" in place of the artist, and the play button becomes Try again.
- In the car, the four tabs fit Auto's limit, and covers sit in grids where they help.

## Screens and baselines

No phone screen changed: the only shell change is the new `expandRequests` parameter, with no visual effect. So no
baselines were recorded, and the device capture script is unchanged. Widget previews are not part of the Roborazzi
suite. The widget's layout is covered by Glance unit tests (nodes, names, actions), not by images.

## CI

Green on `eb136e8` (the last code commit, on top of every code commit):
- **CI** 39556915577: ktlint, unit tests (including the browse tree, the car's access and picks, the worded error, the
  widget's states, cadence, colours and Glance controls), Room schema check, screenshot comparison, debug APK.
- **Secret scan** 39556915633.
- **Device screens** 39556915539 (API 34 emulator).

Pushed in small batches, tests first: the browse tests, then the media library service, then its compile fix; the Auto
tests; the Auto feature with the widget tests; the widget with the error fix; the test fixes.
- **Red on purpose.** CI 39553746075 (browse types missing), 39555394878 (Auto styles and error missing) and
  39555590122 (widget code missing).
- **Goal 1 green.** CI 39554563230, Device screens 39554563294 and Secret scan 39554563263 at `a4353b3`.

Notes:
- **My own mistakes, caught by CI.**
  - Kotlin could not infer the library helper's result type against Guava's `SettableFuture` (CI 39553941812, fixed
    in `a4353b3`).
  - The worded-error test waited on ExoPlayer's load retries, which never ran on Robolectric's paused clock. Its
    unreleased session then failed every later service test ("Session ID must be unique"). The widget test matched
    the resume broadcast by receiver class rather than by its intent (CI 39556248391, fixed in `eb136e8`).
- **Device screens.** Runs on intermediate commits were cancelled or superseded by newer pushes; only the run on the
  final commit counts.

## Not verified (needs a real car, the Desktop Head Unit or a real launcher)

- **Android Auto end to end** on a head unit or the Desktop Head Unit:
  - the four tabs;
  - grids and lists as hinted;
  - search and voice;
  - Like and Shuffle on the car's player;
  - the worded error;
  - offline playback of downloads in the car.
- **Covers in Auto's browse lists.** Items carry web (https) cover addresses. Auto may want content addresses there, in
  which case lists show without covers. The playing song's cover shows either way, since the session loads it as a
  bitmap. A content provider for covers would fix it if needed.
- **The widget on real launchers:**
  - Pixel Launcher, Samsung One UI and a third-party launcher;
  - resizing between one and two rows;
  - Material You colours from a real wallpaper;
  - the picker preview;
  - TalkBack on the controls.
- **Starting from the widget with the app's process gone** (Play and Play something through `MediaButtonReceiver`) on
  Android 12, 13, 14 and 15 under each vendor's background limits.
- **Battery.** No measurement was taken. The design runs nothing while paused, but how often launchers re-bind the
  widget was not observed.
- **No real plays.** CI emulators and cloud phones are refused by YouTube for streams, so none of this was heard
  playing real music there. Tests use fixtures and a stream resolver that always fails.
