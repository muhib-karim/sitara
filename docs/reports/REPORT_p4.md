# p4 report: polish slice 4, make it yours

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, ui-ux-pro-max, test-driven-development,
ponytail-review (before every push). Target release `0.9.4`. No tag or release was made (the owner tags), and
`versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

The owner found v0.8.3 "very bland and non-feature-rich" next to Metrolist, InnerTune/OuterTune, SimpMusic, ViMusic
and YT Music. Slices 1 to 3 fixed the stage, the shell, Home, Search and Library. Before this slice:
- Colour was a three-way choice: From artwork, which only ever coloured Now Playing; Wallpaper accent; and AMOLED black.
- The notification had transport controls only.
- Up next could reorder and remove songs, with no Undo, no way to keep the queue, and no difference between songs you
  queued and songs radio added.

## What changed

1. **Appearance** (`:core:model`, `:core:data`, `:core:designsystem`, `:feature:settings`, `:app`; `9eab2b1`, `5c8a925`, `9048f77`).
   - **Settings.** `SitaraSettings` drops `ThemeMode` for four new settings:
     - `pureBlack`;
     - `dynamicColor`: on by default, used on Android 12 and later;
     - `accent`: Saffron, Rose, Violet, Blue, Teal, Green, or From artwork;
     - `artworkColorsAcrossApp`.
     
     All are kept in the settings store. A stored `AMOLED` carries over to pure black in the dark theme, until the new
     keys are first written.
   - **Colours.** `sitaraColorScheme(dark, pureBlack, accent, tint)` starts from Sitara's neutral chrome. With a tint
     (the playing cover's main colour), it builds the background and container surfaces in that hue, at capped
     saturation, on lightness steps chosen so the neutral text colours keep 4.5:1. Pure black then makes the dark
     background, surface and container black. Last, the accent becomes primary through a guard: any accent (a swatch,
     the wallpaper's or the cover's) is made lighter on dark chrome or darker on light chrome until it reads at 4.5:1 on
     every surface. The ink on it is pure black or white, which always reaches 4.58:1. Each swatch is hand-tuned per
     theme so the guard leaves it alone. Saffron is exactly the old primary, so the default look did not change: every
     existing screenshot except Settings matched its old baseline.
   - **Precedence.** With wallpaper colours on, the wallpaper's accent is used. Picking a swatch or From artwork turns
     wallpaper colours off, and no swatch shows as chosen while they are on, so nothing on the page claims a choice it
     does not apply. From artwork with nothing playing falls back to saffron. The app re-themes live when the playing
     cover changes. The change is not animated, because a fade would recompose the whole app every frame.
   - **Settings page.** One Appearance group with a live preview card on top (page, chips, a card with a playing song and
     a progress line, in the current colours; one TalkBack node with nothing to press). Then:
     - System / Light / Dark as three 48 dp segments;
     - Pure black;
     - Wallpaper colours (hidden below Android 12);
     - Accent: six 48 dp swatches, the chosen one ringed and ticked so the choice does not rest on colour alone, then
       From artwork;
     - Use artwork colours across the app.
   - **Tests first.** Codec round-trip, defaults and the AMOLED carry-over. A contrast sweep: every swatch on light,
     dark and pure black; 76 cover colours as accents; the same 76 as tints with both the cover's accent and saffron;
     primary, text and secondary text against six surfaces, and ink on primary. Interaction tests for theme, pure black,
     artwork colours, picking an accent (wallpaper turns off, the swatch is selected, turning wallpaper on deselects it)
     and the hidden switch on Android 11.
   - **Not done:** app icon variants (optional in the brief). An activity-alias icon switch needs its own artwork and
     restarts the launcher entry, which did not fit this slice.
2. **Notification and lock screen** (`:core:player`, `:app`; `476416a`, `1cddc3f`).
   - **Commands.** `SessionActions` defines three custom session commands, LIKE, SHUFFLE and REPEAT, and builds Media3
     `CommandButton`s for the session's media button preferences:
     - a filled heart when the song is liked ("Remove from liked songs") or an outline ("Like");
     - shuffle on or off;
     - repeat off, all or one, each named for what a press does.
   - **Who may send them.** The commands are granted only to the controllers that already get playback-only access
     (media notification, System UI, car, headset). Other apps are still rejected.
   - **State.** The service follows the playing song's liked state through a new `PlaybackDependencies.isLiked` flow,
     plus the engine's shuffle and repeat, and updates the buttons when any changes. A press runs `onSessionAction`:
     like or unlike through `setLiked` (a library write handed off by the app), or the engine's own shuffle and repeat,
     so the app's screens follow.
   - **Artwork.** The cover was already the notification's and lock screen's large icon. Each media item carries its
     artwork URI, which the session's bitmap loader fetches. The platform's media style decides the colours; Sitara
     sets none.
   - **Tests first.** A Robolectric service test:
     - the buttons show a liked song's filled heart, shuffle off and repeat off;
     - pressing all three unlikes the song, shuffles and sets repeat all, and the icons follow;
     - repeat cycles to one;
     - an unknown command is refused;
     - the heart does nothing with nothing queued.
3. **Queue** (`:core:player`, `:core:designsystem`, `:feature:player`, `:feature:library`, `:app`; `c619c31`, `5888baa`, `a39c3a7`).
   - **Radio's songs.** `QueueEntry.fromRadio` marks songs radio added (Start radio, or a queue running out).
     - Songs you queue go ahead of radio's, and radio's go to the end.
     - Shuffle shuffles each group on its own, so radio's still come last. A song queued while shuffled goes ahead of
       radio's in the unshuffled order too.
     - Up next shows radio's songs under "Up next from radio". Each part reorders within itself.
     - The radio mark is not saved, so after a restart the restored queue shows all songs as yours.
   - **Undo.** Removing a song offers Undo on a snackbar; the engine puts the same entry back (same id, same radio mark)
     where it was, or as near as the queue now allows. A new queue forgets it, and a second Undo does nothing.
   - **Ways to remove.** Swipe left past a third of the row, the ×, or TalkBack's "Remove from the queue". Only a
     leftward swipe belongs to the row. A rightward swipe goes back to the pager (Lyrics). A short swipe springs back,
     or snaps back under Reduce motion.
   - **Haptics.** Dragging ticks the phone's haptics (`SegmentFrequentTick`) once for each row passed, in Up next and in
     playlists.
   - **Save as playlist.** Up next has a Save as playlist button. It asks for a name in the same sheet as New playlist,
     then creates a playlist of the playing song and Up next in order. A song queued twice goes in once, since playlists
     hold each song once. The name sheet now saves on the keyboard's Done key and a hardware Enter, and closes the
     keyboard itself.
   - **Tests first.**
     - `QueueEngineTest`: radio behind your songs, radio's playing song, shuffle, queuing while shuffled, put-back in
       place, put-back after the queue moved, put-back while shuffled.
     - `PlaybackEngineTest`: undo in the queue and the player, a second undo, undo after a new queue, and port order
       with radio.
     - `ReorderTest`: one tick per row passed, none past the ends.
     - `PlayerInteractionTest`: × then Undo, Move down, Remove action, swipe; radio section and section-bound moves;
       Save as playlist through the Done key.
4. **Screenshots and docs.** New baselines: Appearance with the Violet accent (light and dark), Home with artwork
   colours across the app (light and dark), and Up next with the radio section and the Undo snackbar. Settings and Up next
   were re-recorded. CHANGELOG (Unreleased), README, PARITY and ARCHITECTURE are updated.

## Accessibility and layout

- **TalkBack.**
  - Swatches read their colour name as radio buttons, selected or not.
  - Theme segments are radio buttons, and switches read their title and description.
  - The preview is one node, "Preview of the chosen colours".
  - Every Up next song has Remove from the queue, Move up and Move down (only within its own part), and the × reads
    "Remove … from the queue".
  - The radio heading is a heading. The snackbar is Material's, announced, with its time extended by the accessibility
    settings.
- **Touch targets.** 48 dp: swatches, segments, the ×, drag handles, pills.
- **Reduce motion.** Swipe spring-back snaps. Theme changes are instant anyway.
- **Insets.** Settings keeps its dock and navigation-bar padding (the last row is still checked by the device capture).
  Up next lives in the full player, which covers the dock.

## CI

| Commit | What | Run | Result |
|---|---|---|---|
| `9eab2b1`, `5c8a925` | Appearance tests and feature | CI 39538721262, Secret scan 39538721293, Device screens 39538721348 | Compiled. All new contrast, codec and Appearance tests passed. Three older tests failed: index scrolls and taps on rows under the dock in a longer Settings. Device screens green |
| `9048f77`, `476416a`, `1cddc3f` | test fixes; notification tests and feature | CI 39539455875, Secret scan 39539455814, Device screens 39539455830 | Unit tests green (the notification service test included). Only `settings_dark`/`settings_light` stale, as expected. Device screens green |
| `c619c31`, `5888baa` | queue tests and feature | CI 39540079626, Device screens 39540079621 | Red: the debug-only `DemoPlayer` lacked `undoRemoveUpNext` (compile error in the debug source set) |
| `a39c3a7`, `0e920ce` | demo Undo; new screenshot tests | CI 39540346939, Record screenshots 39540346128 | Red: two of the new Up next tests were wrong. The snackbar needs the clock moved while it is frozen, and a removal right after a move reports the moved song's new place. The recording stopped on the same failures |
| `354702d` | those test fixes | CI 39540746770, Secret scan 39540747002, Record screenshots 39540746242 | Unit tests green. Only the 8 new or changed screenshots fail, as expected. Recording green (100 baselines; the 8 inspected before commit) |

## Not verified on a real phone

- **The notification and lock screen buttons.** CI proves the session's button preferences and the command handling in
  Robolectric, not how System UI draws them. Android 13 and later show at most two custom actions beside the transport
  in the compact view, so Repeat may only be in the expanded notification. Vendors' lock screens differ. A press from a
  locked phone, Bluetooth and Android Auto were not tried. CI emulators and cloud test devices are refused YouTube
  streams, so no notification with a live song was seen at all.
- **Haptic ticks.** Robolectric has no vibrator. How strong the tick feels varies by phone, and some phones turn touch
  feedback off.
- **Dynamic colour on a real Android 12+ device.** The screenshots turn wallpaper colours off (Robolectric has no
  wallpaper). The API 34 emulator in Device screens uses the default wallpaper's accent, through the same contrast guard.
- **Artwork colours from a live cover.** Tested with fixture covers and the cover-colour maths. With a live song, the
  tint changes once the cover's colours load.
- **Swipe to remove** against the pager on a real touch screen, Save as playlist with a long queue, and TalkBack across
  the new controls.
