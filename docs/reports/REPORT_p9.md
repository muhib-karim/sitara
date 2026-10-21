# p9 report: polish slice 9, lyrics II

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development, ui-ux-pro-max,
ponytail-review (before each push, except the two one-line compiler and direction fixes `1c5ebde` and `e806e46`,
reviewed after they were pushed; nothing further to change). Target release `0.9.9`. No tag or release was made (the
owner tags), and `versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

PHASE_C_PLAN c6, PARITY rows "Several lyric sources with a picker", "Lyric translation" and "Lyric share cards".
Before this slice lyrics came from LRCLIB alone, could not be translated or shared, and a song LRCLIB lacked showed
"No lyrics found" for a week.

## What changed

**Sources** (`4bea3cc` tests, `5abc501`, `9f98706`)
- `YouTubeMusicLyricsProvider` (`:source:youtube`) reads the Lyrics tab of a song's watch page with the WEB_REMIX
  InnerTube requests the app already makes: `next` gives the tab's browse id (found by its `MUSIC_PAGE_TYPE_TRACK_LYRICS`
  page type, since the tab's position moved when YouTube Music added Comments), `browse` the plain text and its licensor.
  Cached like LRCLIB: found for good, "none" for a week, errors never; a page without tabs is an error, not "none".
- `LyricsSources` (`:source:lyrics`) asks every provider at once with a 10 s timeout each and ranks: synced, then the
  matched song as long as this one within 2 s, then exact title and artist; the listener's pick comes first while it has
  lyrics. `LyricsProvider` kept its shape and gained a display `name`; `Lyrics` carries the licensor and matched song.
- Room 7: `lyrics_cache` keyed by song and source (auto-migration; version 6 rows become LRCLIB's, tested from the
  committed schema 6), the matched song and licensor, and `lyrics_choice`.
- No third source: Musixmatch and Genius need a key (Genius's API returns no lyric text), and lyrics.ovh, NetEase, KuGou
  and QQ Music are scrapers or unofficial endpoints. Reasons and the LRCLIB/YouTube Music notes are in ARCHITECTURE.

**Source picker** (`0d0feab` tests, `c4224e8`)
- A "Source: LRCLIB, synced" glass chip over the lyrics opens a sheet of every match: source and licensor, synced or
  plain, line count, length against the song ("1 s longer", "same length"), the first two lines; a pick switches at once
  and is kept. A source with nothing is named. With nothing found the page says "No lyrics found in LRCLIB or YouTube
  Music" with Try again, which deletes the song's "none" answers first so the sources are really asked again.

**Translation** (`b1416b3` tests, `a39c1d8`, `1c5ebde`)
- `MyMemoryTranslator`: distinct non-blank lines only, joined in requests of at most 500 bytes, `autodetect|<target>`;
  merged lines re-asked one by one; a request answered "two distinct languages" leaves its lines untranslated, all of
  them is `SAME_LANGUAGE`. Problems are typed: offline, daily limit (HTTP 429, `quotaFinished`, or the warning text),
  same language, service. Room 8 `lyrics_translation` per song, source and language.
- Translate (off by default) in the lyrics header; each line's translation under it, smaller and dimmer; a status line
  ("Translated from English by MyMemory", "Translating to Spanish…", offline and limit messages, Try again only where a
  retry can help) and a language chip opening the language sheet (the app's language first, then 16 common ones). The
  target lasts for the session.

**Lyric cards** (`a246a5a` tests, `f792a3c`)
- Share lyrics in the header starts picking at the sung line; a long press on any line does too. Taps move the run's
  nearer end (one to five lines, `extendSelection`, unit-tested); picking does not seek and holds the list still; Cancel
  and "Share N lines" sit at the bottom.
- The card editor: Artwork (the blurred, per-cover shaded backdrop), Colour (the cover's accent with its contrasting
  ink) and Light (paper white, near-black ink); lines in Inter Display ExtraBold sized by length; song, "Lyrics from …"
  credit and the Sitara mark. The card lays out at 360 × 450 dp at a fixed density of 3, so the PNG is 1080 × 1350 on
  any phone (tested); Share image records it into a `GraphicsLayer`. `shareImage` moved to the design system and serves
  the stats card too; files stay under the app cache's `shared/`.

**Complex scripts** (`ed091ae` tests, `2f935c3`, `e806e46`)
- Lines and translations take the full width and read their direction from the text (`TextDirection.Content`), so Urdu
  and Arabic lines start at the right edge; the focus scale grows from that edge (`isRightToLeft`, unit-tested).
  Bangla, Devanagari and Arabic script fall back from Inter to the system fonts. The first recording showed the Urdu card
  shaped correctly but left-aligned: Compose follows the layout direction by default, fixed in `e806e46`. Known
  cosmetic limit: on the sung right-to-left line, a left-to-right translation under it moves a few pixels left with the
  line's 4 % focus scale (`lyrics_urdu_translated_dark.png`).

**Screenshots and docs** (`d7a084b` fixtures; baselines from Record screenshots 39623292502, inspected, and docs in the
final commit): 21 new fixtures, 7 changed (every lyrics shot gains the header row; "No lyrics found" names the sources).
The device-capture wait text for Lyrics ("Sitara, light the dark") still shows, so the capture script is unchanged.

## CI

Green on `5dd3bb6` (code, baselines, docs and the ktlint fix): CI 39625459475, Secret scan 39625459520, Device
screens 39625459157.

Red runs on the way, each fixed in the next push:
- CI 39618813543 (`5abc501`): two older store tests still called the one-key lyrics cache (`9f98706`). The hand-written
  schema 7 matched Room's export.
- CI 39620722643 (`9f98706`): `DemoPlayer` (debug) lacked the new `chooseLyrics`; its overrides came with `a39c1d8`.
- Record screenshots 39620005964 (the same) and 39621380980 (`d7a084b`): the translation flow was inferred as
  `Flow<Off>` (`1c5ebde`).
- Record screenshots 39622073951 (`1c5ebde`) passed; inspecting it found the left-aligned Urdu card (`e806e46`).
- CI 39624113589 (`6585b30`, the docs commit): ktlint, one import out of order in `LibraryRepositories.kt` (`5dd3bb6`);
  every changed file's imports were then checked in order before pushing.
- Push-triggered runs arrived minutes late and out of order this session, so several intermediate runs were cancelled
  by newer pushes before they reported.

## Not verified

- **No live YouTube Music lyrics.** CI runners and the cloud test devices cannot reach YouTube from their addresses. The
  provider is tested on responses recorded once from music.youtube.com (trimmed, lyric text replaced; the "tab cannot be
  opened" page is built from the recorded one with `unselectable: true`, the shape other clients document). Whether
  YouTube Music answers a signed-out phone with lyrics on every network is not checked.
- **No live MyMemory calls from CI or a phone.** Tests use a fake HTTP layer; the same-language answer shape (HTTP 200,
  `responseStatus: "403"`) and newline-preserving batches were checked once by hand from a desktop connection. The daily
  limit message comes from MyMemory's documentation and its known warning text, not from hitting the limit.
- **Share sheet and PNG on a phone.** The intent, FileProvider and cache file are tested in Robolectric; the rendered PNG
  from a real `GraphicsLayer`, the share preview and the receiving apps are not.
- **Fonts on real phones.** Robolectric's fallback fonts rendered Bangla, Devanagari and Urdu (Naskh style) correctly;
  vendor fonts, and Nastaliq on phones that pick it for Urdu, are not checked.
- **TalkBack** on the chip, the sheets, Translate and line picking; the 10 s source timeout on a slow network.
- Schemas 7 and 8 were written by hand; Room's export check found them identical in structure, so it kept them and their
  identity hash fields are not Room's (they only matter to migration-test tooling the project does not use).
