# p8 report: polish slice 8, scrobbling

Date: 2026-10-21. Harness: Claude Code · Opus 5.5. Skills invoked: ponytail, test-driven-development, ui-ux-pro-max,
ponytail-review (before each push, except the two small fix pushes `9b3026f` and `a3eea07`, which
were reviewed after they were pushed; the review found nothing to change in them). Target release `0.9.8`. No tag or release was made (the owner tags), and
`versionCode`/`versionName` are unchanged (the owner's prep commit sets them).

PHASE_C_PLAN c8 (the part still open), PARITY row "Last.fm / ListenBrainz". Before this slice, every play stayed
on the phone; BitChord scrobbles to both services, Metrolist and Meld to Last.fm.

## What changed

1. **Scrobble engine** (`:core:model`, `:source:api`, `:core:data` `scrobble/`, `:core:player`; `6853345`, `2602de4`, `bb84023`).
   - `scrobbleCounts`: the rule Last.fm and ListenBrainz share. A song longer than 30 s counts once half of it, or
     4 minutes, is heard; a song of unknown length never counts. The time heard is `ListeningTracker`'s, so pauses
     and buffering add nothing, seeking adds only what is then played, and repeat-one starts a new play.
   - `scrobbleTargets`: the services a play goes to: none while paused or before the play counts; a service set to
     library songs only gets just songs that are liked or in a playlist; a service waiting for a new sign-in still
     collects plays.
   - `ListeningTracker` announces each new play as it first plays (not after a pause), so the services hear what is
     playing now; the engine and `PlaybackDependencies.onPlayStarted` carry it to the app.
   - `ScrobbleOutbox` over Room table `scrobble_outbox` (schema 6, auto-migration). Rows are keyed by service, song
     and start, inserted with "ignore", so the same play reported again at later checkpoints is kept once per
     service and asks for no send. `send` drains a service oldest first in batches of 50. A taken batch is marked
     `sent_at` and kept for 30 days, so a play still being reported is never sent twice. A refused batch is sent
     again play by play, and only a play refused on its own is dropped. "Try later" and "sign in again" keep every
     row. Waiting count and last sent time are flows on the same table.
2. **ListenBrainz and the Scrobbling page** (`:source:scrobble`, `:core:data`, `:feature:settings`, `:app`; `6f7711a`, `53a5419`, `c3ecc67`).
   - `checkListenBrainzToken` calls `/1/validate-token` (Authorization: Token). Valid → the user name, `valid: false`
     or 401 → refused, anything else → unreachable (so a token is never called wrong when offline).
   - `ListenBrainzClient` posts `/1/submit-listens`: `single` for one play, `import` for a batch, `playing_now` without a
     time; artist, title, the album when known, `duration_ms`, `origin_url` (the YouTube Music link of the video id),
     `media_player` and `submission_client` "Sitara". 200 → sent, 401 → sign in again, 429/5xx/no connection →
     try later, other 4xx → refused.
   - `ScrobbleAccounts`: user names, "only songs in your library", Pause and "sign in again" in a DataStore of its
     own; tokens and session keys are sealed with AES-GCM under an Android Keystore key before they are written (a
     sealed value that cannot be opened reads as signed out). App backup is off, as before.
   - `Scrobbling` (app): now playing at each new play for each signed-in service, plays that count into the outbox,
     and `ScrobbleWorker` (WorkManager, unique work, needs a connection, exponential back-off from 30 s). New plays
     keep a send already queued; Send now replaces one waiting out a back-off.
   - Settings → Listening history → Scrobbling (`ScrobblingScreen`, a `DetailRoute`): an introduction to the rule, Pause
     scrobbling for all services once one is connected, and a card per service with a one-line status chip (Connected,
     Sign in again, Paused, Not connected, Unavailable; the dot only repeats the words), who is signed in, the last
     send, an "N waiting" badge (TalkBack: "N plays waiting to be sent"), Send now while plays wait, Disconnect with a
     confirmation that says how many waiting plays it drops, and "Only songs in your library". The token field has a
     visible label and helper text, hides the token, never puts it in saved state, and words a wrong token or no
     connection under itself.
3. **Last.fm** (`:source:scrobble`, `:app`, `.github/workflows/release.yml`; `1b9c2f4`, `777c33b`, `014da0d`, `9b3026f`).
   - `lastFmSignature`: every parameter except `format`, in name order, as name and value, then the shared secret,
     MD5 in hex (UTF-8).
   - `LastFmAuth`: `auth.getToken`, the `last.fm/api/auth` page in the browser, `auth.getSession` when the listener comes
     back (on resume, or "I've allowed it"); error 14 → still waiting, other errors → refused.
   - `LastFmClient`: `track.updateNowPlaying` and `track.scrobble` (numbered `artist[i]`, `track[i]`, `timestamp[i]` in
     seconds, `duration[i]`, `album[i]` when known) as signed POST forms. Error 9 → sign in again; 11, 16 and 29 → try
     later (the worker backs off); other errors → refused; a server error page or no connection → try later.
   - The API key and shared secret are `BuildConfig` fields fed by the `LASTFM_API_KEY` and `LASTFM_SHARED_SECRET`
     Gradle properties or environment variables; the release workflow passes the repository secrets of those names.
     They are never committed. With either missing, the Last.fm card says "This build of Sitara was made without a
     Last.fm API key" and shows no sign-in.
4. **Screenshots** (`e86b6fe`, `a3eea07` and the baselines commit): the page connected (dark, light, pure black, 200% text, library only),
   signing in again with a refused token, Last.fm open in the browser, a build without a key, and Settings' Listening
   history with its Scrobbling row; baselines from Record screenshots 39586212491, inspected (connected dark and light, 200% text, signing in again).
   The three Settings baselines below Listening history (Playback, Sound, Downloads) moved by the new row. A card not
   yet connected says how to connect; the status chip moves under a title it does not fit beside.
   The device capture is unchanged: Settings still ends on "Storage used", which is what it waits for.
5. **Docs**: CHANGELOG (Unreleased), README feature table, PARITY row, ARCHITECTURE (module map, graph and a
   Scrobbling section with the outbox and the worker), SKILLS_USED rows, project brain.

## CI

FINAL_RUNS

## Not verified

- **No real account was used anywhere.** CI and the cloud test devices cannot sign in to Last.fm or ListenBrainz; every
  client test runs against a fake server with made-up keys and tokens. Unverified: the live token check and listen
  submission on ListenBrainz, the Last.fm web sign-in round trip (browser, allow, back to Sitara), scrobbles and now
  playing showing on both profiles, and both services' handling of a listen sent twice with the same start time (the
  case of a send cut off between their answer and the outbox's mark; both are expected to keep one listen, but
  neither was checked).
- **No Last.fm key in CI builds.** CI and Device screens builds have no key, so they show the "made without a Last.fm
  API key" state. A release has Last.fm only once the owner adds the `LASTFM_API_KEY` and `LASTFM_SHARED_SECRET`
  repository secrets. A key and secret built into an APK can be read out of it, as with every open-source scrobbler.
- WorkManager's network constraint and back-off on a real phone going offline and online, the worker after the app
  is killed mid-send, and Doze delays.
- The Keystore key on real phones (vendor Keystore quirks, a cleared Keystore leading to "Sign in again").
- TalkBack on the page on a device (the words are checked in Robolectric tests), and 200% text on a small phone.
