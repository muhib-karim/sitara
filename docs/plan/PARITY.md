# Feature parity

How Sitara compares with the four apps it is measured against, updated with every release. Competitor columns come from their public documentation and release notes; **Y** = has it, **~** = partial or depends on version or setup, **—** = not offered, **?** = not documented. A Sitara entry says the release that shipped it, or the Phase C slice planned for it ([PHASE_C_PLAN.md](PHASE_C_PLAN.md)).

Last updated: v0.6.0.

| Capability | BitChord | Metrolist | Meld | ReVanced | Sitara |
|---|:---:|:---:|:---:|:---:|---|
| Search, home and catalogue browsing | Y | Y | Y | Y | v0.2.0 search, v0.5.0 Home |
| Play from a pasted YouTube / YouTube Music link | ? | ? | ? | ? | v0.2.0 |
| Artist, album and playlist pages | Y | Y | Y | Y | v0.3.0 |
| Radio and autoplay | Y | Y | Y | Y | v0.3.0 |
| Ad-free listening | Y | Y | Y | Y | v0.2.0 |
| Background playback and system media controls | Y | Y | Y | Y | v0.5.0 |
| Queue restored after the app is killed | ? | ? | ? | ? | v0.5.0 |
| Likes, playlists and history on the device | Y | Y | Y | Y | v0.4.0 |
| Queue reorder, play next, remove | Y | Y | Y | Y | v0.4.0 |
| Offline downloads | Y | Y | Y | ~ | v0.6.0 (no storage cap yet) |
| Synced lyrics | Y | Y | Y | Y | v0.5.0 (LRCLIB) |
| Loudness normalisation | — | Y | Y | — | v0.6.0 |
| Crossfade | Y | Y | Y | — | v0.6.0 |
| Real artwork in the app, artwork-coloured player | Y | ~ | ~ | Y | c1 (v0.7.0); lock screen and notification since v0.5.0 |
| Lyrics manual scroll with return to current line | ? | ? | ? | ? | c1 |
| Gapless playback | Y | ~ | ~ | ? | c2 (since v0.6.0, crossfade keeps same-album songs gapless; not yet checked on a phone) |
| Speed and independent pitch | Y/— | Y/Y | Y/Y | ? | c2 |
| Skip silence | Y | Y | Y | — | c2 |
| Sleep timer with custom time | Y | Y | Y | Y | c2 (presets since v0.1.0) |
| Equaliser (built-in or system) | Y | Y | Y | system | c3 |
| Local audio files | Y | — | — | ~ | c4 |
| Downloads exported as tagged files with lyrics | — | ? | ? | — | c5 |
| Backup and restore | Y | Y | Y | — | c5 |
| Several lyric sources with a picker | Y | Y | ? | — | c6 |
| Lyric translation | Y | Y | ~ | — | c6 |
| Lyric share cards | Y | — | — | — | c6 |
| Beat-aware automix | ~ | — | — | — | c7 |
| Widgets | Y | Y | Y | Y | c8 |
| Android Auto | Y | Y | ~ | Y | c8 |
| Last.fm / ListenBrainz | Y/Y | Y/— | Y/— | — | c8 |
| Playlist imports (YouTube, Spotify, M3U/CSV) | Y | ~ | Y | — | c9 |
| Audio quality per network, codec readout | Y | — | — | ~ | c9 |
| Palette presets, QR sharing | Y | Y | Y | — | c10 |
| App translations and right-to-left layout | ~ | Y | ~ | Y | c10 |
| Dynamic, light, dark and black themes | Y | Y | Y | limited | v0.1.0 |
| Google account library sync | Y | Y | Y | Y | Later, if permitted access allows |
| Cast, listen together, Wear, Discord presence | Y | Y | ~ | ~ | Later |

Not offered on purpose: nothing is labelled lossless, because YouTube audio is not.
