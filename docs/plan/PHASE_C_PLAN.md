# Phase C plan: beat the field

Phase B (v0.2.0 to v0.6.0) made Sitara a real player: live YouTube Music search and link paste, detail pages, radio, an on-device library, background playback that survives restarts, synced lyrics, downloads, loudness levelling and crossfade.

Phase C closes the remaining gaps against the four apps Sitara is measured against (BitChord, Metrolist, Meld and ReVanced YouTube Music) and adds the things none of them do well. Each slice is one release with a signed APK, and it ships only when CI, the secret scan and the device screenshot suite are green on the release commit. The live parity table is [PARITY.md](PARITY.md); every slice updates it.

Status (2026-10-23): every slice below is on `main` as the 1.0.0 candidate (c1 to c4 as v0.7.0 to v0.10.0 and polish slices p1 to p10; the rest in SITARA100 pass 1, see [the report](../reports/REPORT_v100.md)). What permitted access cannot do is listed as not supported in [PARITY.md](PARITY.md).

Rules that hold for every slice:

- Tests first for every behaviour change; no network in unit tests.
- No inert settings: a control ships only when it changes playback or data.
- Claims stay honest. What CI cannot check on a real phone is listed in the slice report under docs/reports/.
- YouTube audio is never labelled lossless.

## c1: real artwork and a player that looks like the music (v0.7.0)

Gap: covers inside the app are generated gradients; BitChord and Metrolist show real artwork everywhere.

- Load real thumbnails (song, album, artist, playlist) with a memory and disk cache, square-cropped from YouTube's video thumbnails where no square art exists.
- Now Playing takes its colours from the artwork (dynamic palette with a contrast check), with the gradient as the accessible fallback when an image fails.
- Lyrics page: manual scrolling suspends follow mode, with an explicit "Back to current line" control.

Acceptance: artwork shows on Home, Search, Library, detail pages, Up next and Now Playing; a failed image keeps a readable fallback; palette text meets 4.5:1 contrast (unit-tested); scrolling lyrics by hand stops auto-follow until the user returns.

## c2: sound I, gapless, speed, pitch, skip silence, sleep timer (v0.8.0)

Gap: every rival offers speed and skip silence; BitChord is gapless.

- Gapless playback: the next queue item is prepared ahead of time so album tracks join without inserted silence (crossfade Off).
- Playback speed (0.5x to 2x) and independent pitch, per session, with lyrics staying in sync at any speed.
- Skip silence (Media3 silence skipping), off by default.
- Sleep timer: custom minutes, extend, and end of track.

Acceptance: unit tests for queue preparation order and timer expiry on monotonic time; lyric line timing follows media time at 1.5x; settings round-trip.

## c3: sound II, equaliser (v0.9.0)

Gap: Metrolist, Meld and BitChord ship an equaliser.

- Built-in 10-band equaliser with presets and a preamp, implemented as an audio processor in the playback chain.
- "Use system equaliser" hands the audio session to the device's equaliser instead, never both at once.
- Optional per-song gain trim saved with the song.

Acceptance: processor tests (flat response is bit-identical within rounding, a band boost matches its target within 0.5 dB on a sweep fixture, no clipping with the preamp); system and built-in are mutually exclusive (tested).

## c4: local audio (v0.10.0)

Gap: BitChord plays music already on the phone.

- Index the device's music (MediaStore) and user-picked folders (Storage Access Framework), shown as a Local section in the Library and mixed into search.
- Denying the permission leaves folder picking usable; a revoked folder offers relinking without deleting library entries.

Acceptance: indexer tests on fixtures (tags, missing tags, duplicates); local songs play, queue, like and join playlists like streamed ones.

## c5: portable downloads and backup (v0.11.0)

Gap: downloads in every rival are locked inside the app.

- Export downloaded songs to a user-chosen folder as tagged audio files (title, artist, album, cover art) with an .lrc lyrics file beside each when lyrics are cached.
- Versioned JSON backup and restore of likes, playlists, history and settings; a corrupt file fails without touching existing data.

Acceptance: tag writer tests read the tags back; backup and restore of a 1,000-item library keeps order and duplicates; a truncated backup is rejected atomically.

## c6: lyrics II, more sources, translation and share cards (v0.12.0)

Gap: BitChord and Metrolist offer several lyric sources and translation; only BitChord has share cards.

- More lyric sources behind the existing provider interface, with a picker to choose between them when several match.
- Opt-in lyric translation, with the source and target language shown and cached results.
- Lyric share cards: pick lines, render an image with the artwork, song and lyric source credit, and share it.
- Bangla, Hindi and Urdu lines render with correct shaping (screenshot fixtures).

Acceptance: provider selection and fallback tests; translation cache and failure states are distinct; share card screenshot fixtures; complex-script fixtures render.

## c7: automix (v0.13.0)

Gap: no rival does beat-aware transitions well; this is a lead, not catch-up.

- Tempo and beat estimation on decoded audio, cached per song.
- Automix mode: transitions start on a beat and blend over whole bars using the crossfade engine, with loudness matched.

Acceptance: beat estimates on annotated fixtures within a measured tolerance; transitions never leave attenuation behind after seek, skip or cancel (tested).

## c8: everywhere the music goes (v0.14.0)

- Home-screen widget with playback controls.
- Android Auto browsing (library, downloads offline, search) through a media library service.
- Last.fm and ListenBrainz scrobbling, opt-in, scrobbling each play once even when offline.

## c9: bring your music (v0.15.0)

- Import from a YouTube or YouTube Music playlist link, a public Spotify playlist, or an M3U/CSV file, with a review step for uncertain matches; interrupted imports resume without duplicates.
- Audio quality per network (Wi-Fi, mobile) and a codec and bitrate readout in Now Playing.

## c10: finish and polish (v1.0.0 candidate)

- Palette presets, long-press previews and QR sharing.
- App translations (starting with Bangla) and right-to-left layout.
- Accessibility pass on every screen: TalkBack journeys without gestures, 48 dp targets, 200% text.
- Predictive back on the player.
- Performance baselines (startup and scrolling) as regression gates.

Later, if access allows: account library sync, Cast, listen together, Wear, Discord presence. Anything that cannot be done with permitted access is listed in PARITY.md as not supported rather than disguised as parity.
