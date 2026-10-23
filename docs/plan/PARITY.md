# Feature parity

How Sitara compares with the four apps it is measured against, updated with every release. Competitor columns come from their public documentation and release notes; **Y** = has it, **~** = partial or depends on version or setup, **—** = not offered, **?** = not documented. A Sitara entry says the release that shipped it, or the Phase C slice planned for it ([PHASE_C_PLAN.md](PHASE_C_PLAN.md)).

Last updated: 1.0.0 candidate (pass 1, on `main`; the owner tags). Every Phase C slice is in; rows that permitted access cannot reach say **not supported**.

| Capability | BitChord | Metrolist | Meld | ReVanced | Sitara |
|---|:---:|:---:|:---:|:---:|---|
| Search, home and catalogue browsing | Y | Y | Y | Y | v0.2.0 search, v0.5.0 Home; YouTube Music's own Home with mood chips in v0.9.2 (signed out, so no personal mixes); recent searches, suggestions and Moods & genres pages in v0.9.3 |
| Library as a grid or list, with orders and filters | ? | ? | ? | ? | v0.9.3 (orders by plays come from the history kept on the phone) |
| Play from a pasted YouTube / YouTube Music link | ? | ? | ? | ? | v0.2.0 |
| Artist, album and playlist pages | Y | Y | Y | Y | v0.3.0 |
| Radio and autoplay | Y | Y | Y | Y | v0.3.0 |
| Ad-free listening | Y | Y | Y | Y | v0.2.0 |
| Background playback and system media controls | Y | Y | Y | Y | v0.5.0 |
| Queue restored after the app is killed | ? | ? | ? | ? | v0.5.0 |
| Likes, playlists and history on the device | Y | Y | Y | Y | v0.4.0; plays counted after 30 s heard, kept with the time listened, Pause and Clear history in v0.9.5 |
| Listening stats (top songs, artists, time listened) and a share card | ? | Y | ? | ? | v0.9.5 (week, month, year, all time; on the phone only) |
| Queue reorder, play next, remove | Y | Y | Y | Y | v0.4.0; Undo, swipe to remove, haptic reorder, Save as playlist and radio's own section in v0.9.4 |
| Offline downloads | Y | Y | Y | ~ | v0.6.0 (no storage cap yet) |
| Synced lyrics | Y | Y | Y | Y | v0.5.0 (LRCLIB); YouTube Music's plain lyrics as a second source in v0.9.9 |
| Loudness normalisation | — | Y | Y | — | v0.6.0 |
| Crossfade | Y | Y | Y | — | v0.6.0 |
| Real artwork in the app, artwork-coloured player | Y | ~ | ~ | Y | v0.7.0; lock screen and notification since v0.5.0; artwork colours across the whole app in v0.9.4 |
| Like, shuffle and repeat on the notification and lock screen | ? | ? | ? | ? | v0.9.4 (not yet checked on a phone) |
| Lyrics manual scroll with return to current line | ? | ? | ? | ? | v0.7.0 |
| Gapless playback | Y | ~ | ~ | ? | v0.8.0 (next stream looked up ahead; not yet checked on a phone) |
| Speed and independent pitch | Y/— | Y/Y | Y/Y | ? | v0.8.0 (0.5× to 2×, ±6 semitones) |
| Skip silence | Y | Y | Y | — | v0.8.0 |
| Sleep timer with custom time | Y | Y | Y | Y | v0.8.0 (any minutes up to 3 h, extend, end of song; presets since v0.1.0) |
| Equaliser (built-in or system) | Y | Y | Y | system | v0.9.0 (10 bands, presets, preamp, limiter; or the phone's own equaliser) |
| Per-song volume trim | ? | ? | ? | — | v0.9.0 (±6 dB, kept with the song) |
| Local audio files | Y | — | — | ~ | v0.10.0 (the phone's music through MediaStore and picked folders through the system folder picker; songs, albums, artists and folders; in Search with a badge; indexed on the CI emulator, not yet on a phone with a real collection) |
| Downloads exported as tagged files with lyrics | — | ? | ? | — | 1.0.0 (Opus as `.opus`, AAC as `.m4a`, tagged with title, artist, album, cover and plain lyrics, `.lrc` beside synced lyrics, to any folder you pick; not yet tried on a phone) |
| Backup and restore | Y | Y | Y | — | v0.10.0 (likes, saved albums and artists, playlists, history, per-song volume and lyric source, settings; versioned JSON, checked whole before one transaction replaces the library; no audio, no scrobbling sign-ins) |
| Several lyric sources with a picker | Y | Y | ? | — | v0.9.9 (LRCLIB and YouTube Music); KuGou as a third, synced source in 1.0.0 (no key) |
| Lyric translation | Y | Y | ~ | — | v0.9.9 (MyMemory, free daily limit; not yet checked on a phone) |
| Lyric share cards | Y | — | — | — | v0.9.9 (three styles, 1080 × 1350 PNG) |
| Beat-aware automix | ~ | — | — | — | 1.0.0 (tempo, beats and bar lines found on the phone and kept per song; blends start on a bar line over whole bars, tempo matched within 8 % and held on the beat, levels matched; plain crossfade without a clear beat; decoding checked on the CI emulator, not yet listened to on a phone) |
| Widgets | Y | Y | Y | Y | v0.9.6 (one now-playing widget, 4 × 1 resizable to 4 × 2 with the heart and progress; not yet checked on a real launcher) |
| Android Auto | Y | Y | ~ | Y | v0.9.6 (library, downloads offline, search and voice, Like and Shuffle; not yet checked in a car or the Desktop Head Unit) |
| Last.fm / ListenBrainz | Y/Y | Y/— | Y/— | — | Y/Y, v0.9.8 (opt-in; now playing and scrobbles kept offline and sent once; Last.fm only in builds made with an API key; not yet tried against real accounts) |
| Playlist imports (YouTube, Spotify, M3U/CSV) | Y | ~ | Y | — | v0.9.7 for YouTube and YouTube Music links and M3U/CSV files, with a review step and resumable imports; public Spotify playlists and albums by link in 1.0.0, without a key: the first 100 songs Spotify shows publicly (longer playlists through a CSV export); not yet tried on a phone |
| Audio quality per network, codec readout | Y | — | — | ~ | v0.9.7 (Auto / High / Normal / Data saver for Wi-Fi and mobile data; codec and bitrate under the scrubber) |
| Palette presets, QR sharing | Y | Y | Y | — | 1.0.0 (seven palettes, each an accent with matching surfaces at AA; share sheet with a QR code drawn by Sitara's own encoder, Send link, QR card image) |
| App translations and right-to-left layout | ~ | Y | ~ | Y | 1.0.0 (Bangla, the whole app; per-app language on Android 13+; layouts mirror in right-to-left languages; no other translations yet) |
| Long-press previews of albums, playlists and artists | ? | ? | ? | ? | 1.0.0 (cover, first songs, Play, Shuffle, Open, Share) |
| Predictive back on the player | ? | ? | ? | ? | 1.0.0 (Android 14+ gesture; reduced motion only dims) |
| Accessibility gate (TalkBack names, 48 dp targets, headings), 200 % text | ? | ? | ? | ? | 1.0.0 (checked on every main screen in CI; TalkBack journeys not yet walked on a phone) |
| Baseline profile and performance gates | ? | ? | ? | ? | 1.0.0 (the APK's own profile; cold start and scrolling jank gated on the CI emulator) |
| Dynamic, light, dark and black themes | Y | Y | Y | limited | v0.1.0; Appearance with pure black, six accents or the cover's, and a live preview in v0.9.4 |
| Google account library sync | Y | Y | Y | Y | **Not supported**: it means signing in to a Google account inside an unofficial client and replaying the session; Sitara stays signed out. Backup and restore and playlist imports cover moving a library in |
| Cast, listen together, Wear, Discord presence | Y | Y | ~ | ~ | **Not supported**: Cast needs Google's closed Cast SDK, which a GPL, store-free build does not ship; listen together needs a server Sitara does not run; Discord shows presence from phones only through a signed-in user token, against its terms. Wear: not built yet |

Not offered on purpose: nothing is labelled lossless, because YouTube audio is not.
