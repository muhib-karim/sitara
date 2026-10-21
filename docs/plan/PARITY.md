# Feature parity

How Sitara compares with the four apps it is measured against, updated with every release. Competitor columns come from their public documentation and release notes; **Y** = has it, **~** = partial or depends on version or setup, **—** = not offered, **?** = not documented. A Sitara entry says the release that shipped it, or the Phase C slice planned for it ([PHASE_C_PLAN.md](PHASE_C_PLAN.md)).

Last updated: polish slice 8 (target v0.9.8, on `main`; the owner tags).

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
| Synced lyrics | Y | Y | Y | Y | v0.5.0 (LRCLIB) |
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
| Local audio files | Y | — | — | ~ | c4 |
| Downloads exported as tagged files with lyrics | — | ? | ? | — | c5 |
| Backup and restore | Y | Y | Y | — | c5 |
| Several lyric sources with a picker | Y | Y | ? | — | c6 |
| Lyric translation | Y | Y | ~ | — | c6 |
| Lyric share cards | Y | — | — | — | c6 |
| Beat-aware automix | ~ | — | — | — | c7 |
| Widgets | Y | Y | Y | Y | v0.9.6 (one now-playing widget, 4 × 1 resizable to 4 × 2 with the heart and progress; not yet checked on a real launcher) |
| Android Auto | Y | Y | ~ | Y | v0.9.6 (library, downloads offline, search and voice, Like and Shuffle; not yet checked in a car or the Desktop Head Unit) |
| Last.fm / ListenBrainz | Y/Y | Y/— | Y/— | — | Y/Y, v0.9.8 (opt-in; now playing and scrobbles kept offline and sent once; Last.fm only in builds made with an API key; not yet tried against real accounts) |
| Playlist imports (YouTube, Spotify, M3U/CSV) | Y | ~ | Y | — | v0.9.7 for YouTube and YouTube Music links and M3U/CSV files, with a review step and resumable imports (not yet tried on a phone); Spotify later (it needs an API key) |
| Audio quality per network, codec readout | Y | — | — | ~ | v0.9.7 (Auto / High / Normal / Data saver for Wi-Fi and mobile data; codec and bitrate under the scrubber) |
| Palette presets, QR sharing | Y | Y | Y | — | c10 |
| App translations and right-to-left layout | ~ | Y | ~ | Y | c10 |
| Dynamic, light, dark and black themes | Y | Y | Y | limited | v0.1.0; Appearance with pure black, six accents or the cover's, and a live preview in v0.9.4 |
| Google account library sync | Y | Y | Y | Y | Later, if permitted access allows |
| Cast, listen together, Wear, Discord presence | Y | Y | ~ | ~ | Later |

Not offered on purpose: nothing is labelled lossless, because YouTube audio is not.
