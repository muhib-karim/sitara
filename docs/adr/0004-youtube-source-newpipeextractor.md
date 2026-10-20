# ADR 0004: NewPipeExtractor for YouTube Music search and streams

- Status: accepted (2026-10-20)

## Context
Phase B makes Sitara a real YouTube Music client. It needs song search and a playable audio URL for any song, without
a Google account or API key, and it must survive upstream changes (ADR 0003). The options were:

1. **NewPipeExtractor** (GPL-3.0-or-later, Java, used by NewPipe and several music clients): search with YouTube Music
   filters, stream extraction including signature and throttling deobfuscation; actively maintained, with frequent
   releases.
2. **A hand-written InnerTube client**: full control over requests and YouTube Music browse, but every client, cipher
   and token change becomes Sitara's own emergency.
3. **The official YouTube Data API**: needs an API key, has a small daily quota, and does not return stream URLs.

## Decision
`:source:youtube` uses NewPipeExtractor v0.26.5 from JitPack. The repository is declared with `exclusiveContent` for
the `com.github.TeamNewPipe` group only, so no other dependency can resolve from JitPack. Its HTTP layer is Sitara's
own `OkHttpDownloader`. Extractor types stay inside the module: pure, unit-tested functions map song search items to
`Track` and pick the audio stream (`selectAudioStream`). The app uses `YouTubeMusicSource` for search and `youTubeStreamResolver()`
for streams: NewPipe's extraction, then the visionOS and iOS clients, in a `FallbackStreamResolver`.

GPL-3.0-or-later is compatible with Sitara's GPL-3.0 (ADR 0002).

## Consequences
- Search and playback work without an account. Home, library and lyrics stay sample-backed until browse, storage and
  LRCLIB land.
- An upstream break is fixed by bumping one version in `gradle/libs.versions.toml`, or by adding an InnerTube resolver
  behind the same `StreamResolver` interface.
- The extractor uses Java 9+ APIs, so the app enables core library desugaring, and Rhino needs R8 keep rules.
- Song search items carry no album, so `Track.album` is empty for search results. Album items and album playlists
  carry no release year, so `Album.year` is null for YouTube albums.
- Browse uses the same extractor (slice 2): an artist is a channel (`ChannelInfo`, its Releases tab for albums and
  singles, and a songs search filtered to that channel for top songs), albums and playlists are `PlaylistInfo` paged
  up to 500 items, and radio is the watch page's related songs.
- Thumbnail URLs are mapped (upgraded to 544 px) but not drawn yet; covers stay generated gradients until an image
  loader is added.
