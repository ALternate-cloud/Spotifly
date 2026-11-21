# Recon map — Spotify (Android) → Spotifly

Status: recon complete for v1 of the parity push. Every `no`/`partial` in
`replica/features.csv` is a work item; this file explains the shape of the app
being rebuilt and what is physically out of reach.

## Scope

| | |
| --- | --- |
| Target app | Spotify — Android phone app, free + premium |
| Platform | Native Android (Kotlin, Jetpack Compose, Media3) |
| Core loop | open app → find something → play it → keep playing things like it → save it for later |
| Slice | playback, library, discovery, playlists, search, offline, settings |
| Audience | personal / portfolio build, not a commercial Spotify competitor |

## Rules applied

This is a clean-room rebuild of **functionality and UX patterns**. It does not
copy Spotify's source, catalogue, trademarks, artwork, copy, or private APIs.
Streaming in the clone resolves through third-party public endpoints already in
the project (JioSaavn search/metadata, iTunes preview fallback, LRCLIB lyrics),
not through Spotify's service.

## Sources

| source | what it gives |
| --- | --- |
| https://support.spotify.com/ | the authoritative feature and settings list |
| https://www.spotify.com/premium/ | which features are gated, i.e. what matters |
| https://newsroom.spotify.com/ | changelog: what the team ships and prioritises |
| https://play.google.com/store/apps/details?id=com.spotify.music | store screenshots of every key screen |
| https://developer.spotify.com/documentation/web-api | the canonical data model (artists, albums, tracks, playlists) |
| public walkthrough videos | click-by-click flows and transition timings |

Confidence is marked per row in `features.csv` notes where it matters.

## Screen inventory

IDs are stable. `clone` = state in this repo today.

| ID | screen | how you get there | purpose | key components | clone |
| --- | --- | --- | --- | --- | --- |
| S01 | Launch | app start | restore session | splash, session restore | yes |
| S02 | Auth | first run | account | email/Google/Apple/phone | n/a — local-only app |
| S03 | Home | bottom nav | personalised feed | greeting, shelf carousels, quick grid | partial |
| S04 | Search idle | bottom nav | browse + history | search field, recent, genre tiles | partial |
| S05 | Search results | type query | find anything | top result, typed sections, filters | partial |
| S06 | Your Library | bottom nav | saved things | type filters, sort, list/grid | partial |
| S07 | Playlist detail | library / home | play a playlist | header, track list, edit, play | yes |
| S08 | Liked Songs | library | the default playlist | header, track list, sort | yes |
| S09 | Album detail | search / artist | play an album | header, track list, more | no |
| S10 | Artist detail | search / album | play an artist | top tracks, albums, related | no |
| S11 | Track row / overflow | any list | per-track actions | like, queue, playlist, download, share | yes |
| S12 | Now Playing | mini player tap | full player | art, scrub, transport, like, queue | yes |
| S13 | Lyrics | player action | sing along | synced lines, tap to seek | yes |
| S14 | Queue | player action | what plays next | reorder, remove, jump | yes |
| S15 | Add to playlist | overflow | file a track | playlist list, new playlist | yes |
| S16 | Create playlist | library / add | new list | name field | yes |
| S17 | Playlist edit | playlist overflow | rename/describe/cover | name, description, cover | partial |
| S18 | Downloads | library | offline content | downloaded list, storage | partial |
| S19 | Device audio | library | local files | MediaStore scan, permission | yes |
| S20 | Recently played | home | resume | recent carousel | yes |
| S21 | Settings | home gear | preferences | rows, switches, pickers | partial |
| S22 | Playback settings | settings | audio behaviour | crossfade, gapless, normalise, quality | no |
| S23 | Equalizer | settings | sound shape | 5-band EQ, presets, bass boost | no |
| S24 | Storage / data saver | settings | manage bytes | usage, cache clear, quality | partial |
| S25 | Stats / Wrapped | library / profile | what you listened to | top artists, tracks, minutes | yes |
| S26 | Genre browse | search idle | mood discovery | genre grid → filtered list | yes |
| S27 | Radio | track / artist action | endless similar | station queue | yes |
| S28 | Share sheet | overflow | send to a friend | Android share intent | yes |
| S29 | Podcast show | search | spoken audio | episodes, follow, speed | yes |
| S30 | Episode player | show | spoken audio | skip 15/30, speed, chapters | partial |
| S31 | Audiobook | search | long-form | chapters, progress | no |
| S32 | Profile | avatar | identity | name, photo, public playlists | no |
| S33 | Friend activity | home | social | live friend listening | no |
| S34 | Jam / group session | player | listen together | host, join, queue voting | no |
| S35 | Connect / devices | player | cast | device list, transfer | no |
| S36 | Offline mode | settings | no network | banner, downloaded-only | no |
| S37 | Empty / error states | everywhere | honesty | offline, no results, failed stream | partial |

## User flows

```
F01 Play a track from search
    S04 -> type -> S05 results -> tap row -> S12 now playing
    edge: no network, stream 404, empty query, slow network

F02 Save a track for later
    S12 / S05 row -> like -> S06 Liked Songs
    edge: unlike, duplicate like, offline toggle

F03 Build a playlist
    S06 -> create -> S16 -> name -> add tracks via S11/S15 -> S07
    edge: duplicate track, rename, delete, empty playlist

F04 Resume where you left off
    S01 -> restore queue + position -> S12
    edge: track removed from catalogue, session older than 30 days

F05 Listen offline
    S11 -> download -> S18 -> airplane mode -> play
    edge: partial download, cancelled download, storage full

F06 Discover something new
    S03 shelf -> S07/S09 -> play -> autoplay continues
    edge: autoplay off, recommendation endpoint empty

F07 Search by voice
    S04 -> mic -> speak -> S05
    edge: no speech provider, permission denied

F08 Read lyrics while playing
    S12 -> lyrics -> S13 -> tap a line -> seek
    edge: no lyrics for track, plain-only lyrics, instrumental

F09 Manage the queue
    S12 -> queue -> S14 -> reorder / remove / jump
    edge: removing the playing track, reordering across the current index

F10 Stop playback on a timer
    S12 -> sleep timer -> pick duration -> auto-pause
    edge: cancel, timer while paused

F11 Sort and filter the library  (gap)
    S06 -> filter type -> sort by recents/alphabetical -> pick
F12 Play an album start to finish  (gap)
    S05 -> album -> S09 -> play -> gapless/segue
F13 Explore an artist  (gap)
    S10 -> top tracks -> album -> related artist
F14 Shape the sound  (gap)
    S21 -> playback -> equalizer -> band -> preset
F15 See your listening stats  (gap)
    S25 -> top artists / tracks / minutes
```

## Components

| component | variants / states | used by |
| --- | --- | --- |
| Bottom nav | 3 tabs, selected/unselected | S03–S06 |
| Mini player | playing, paused, buffering, error | all |
| Full player | expanded, collapsed, buffering | S12 |
| Track row | default, playing, downloaded, unavailable | S05–S09, S14 |
| Artwork tile | square, rounded, fallback icon | home, search, album |
| Shelf carousel | horizontal, "see all" | S03 |
| Type chips | selected / unselected | S05, S06 |
| Search field | idle, typing, clearing, voice | S04 |
| Overflow sheet | track / playlist context | S11 |
| Bottom sheet | modal, drag, dismiss | S14–S17 |
| Slider | seek, volume, crossfade | S12, S22 |
| Snackbar | error, confirmation | all |
| Empty state | no results, no downloads, offline | S06, S18, S36 |

## Inferred data model

Existing today: `Song`, `Playlist`, `PlaybackState`, `Lyrics`, `RepeatMode`,
`DownloadStatus`, `SavedSession` — see `app/src/main/java/com/example/spotifly/model/`.

Missing versus Spotify's model (evidence: Web API object reference, app screens):

```
Artist    id, name, imageUrl, genres[], followers, popularity, bio
          evidence: S10 header, S05 typed results; confidence high
Album     id, name, artistId, artistName, releaseDate, totalTracks,
          imageUrl, albumType (album | single | compilation)
          evidence: S09 header, S05 typed results; confidence high
Track     + albumId, artistIds[], trackNumber, discNumber, explicit,
            isPlayable, previewUrl, externalIds
          evidence: S09 track list order, S05 explicit badges; confidence high
Playlist  + description, ownerName, isPublic, isCollaborative, coverUrl,
            snapshotId, trackAddedAt[]
          evidence: S17 edit sheet, S07 header; confidence high
PlayHistory  trackId, playedAt, msPlayed, source
          evidence: S25 stats, S20 recently played; confidence medium
AudioSettings crossfadeMs, gapless, normalise, eqBands[], bassBoost,
              virtualizer, downloadQuality, streamQuality
          evidence: S22, S23; confidence high
```

## Feature matrix

`replica/features.csv` — 153 rows, weighted must / should / could. Current
clone state at the time of writing:

| state | count |
| --- | --- |
| yes | 93 |
| partial | 9 |
| no | 42 |
| skip (cannot / should not clone) | 9 |

Parity after the four parity clusters (library, settings/offline, playback
polish, search/Wrapped), the deep-link slice, and the autoplay engine:
**77.0 / 100**, with **42 of 42 must-haves done**. Parity is measured against
*all* of Spotify including account, social and billing, which the clone
deliberately skips — so the remaining gap is mostly features that need a
backend, a licensed catalogue, or Spotify's own recommendation graph.

Weighted parity (must=3, should=2, could=1, partial=half) is computed by
`replica-diff/parity.py`.

## Autoplay engine

"Autoplay similar" is a local audio-similarity engine, not a clone of Spotify's
recommendation model, and it never recommends on title or artist text:

- **Features measured from the audio (primary):** every analysed track gets a
  5-axis `VibeVector` (`energy`, `valence`, `tempo`, `acousticness`,
  `danceability`). `AudioVibeAnalyzer` decodes the track with
  `MediaExtractor`/`MediaCodec` — local files and JioSaavn `streamUrl`s alike
  (per-call timeouts: 20 s local, 11 s remote, local→remote fallback within a
  call) — downmixes to mono at 8 kHz; `PcmVibeAnalyzer` then measures RMS
  energy, spectral centroid/flatness, an onset-envelope tempo estimate
  (60–180 BPM), a Krumhansl key estimate for valence, and pulse strength for
  danceability. Results are cached in `vibe_profiles.json`, so each source URL
  is analysed at most once. Streaming seeds are measured in the background
  after playback starts; unmeasured queue candidates get a budgeted measurement
  pass before ranking (25 s overall deadline, max 8 new decodes; skipped in
  offline mode and data-saver).
- **Feature fallback (metadata):** when a track cannot be decoded, a 5-axis
  vector is estimated from genre tags, language, music-scanner genre/year, and
  title/album cue words ("sad", "acoustic", "remix", "party"). Metadata
  vectors only ever act as fallback; a measured vector is used untouched in
  scoring (no blending with the metadata estimate).
- **Similarity math:** `VibeVector.similarityTo` is a weighted, mean-centred
  cosine similarity — weights energy 1.40, valence 1.20,
  tempo/acoustic/danceability 1.00, centred on (0.45, 0.50, 0.47, 0.45, 0.40)
  and mapped from [-1, 1] to [0, 1]. Short/low-confidence vectors shrink
  toward the neutral 0.5.
- **Deliberately not shipped:** a trained embedding model or a precomputed
  catalogue-feature service. Both would improve accuracy, but the first adds a
  large model to an offline-first APK and the second needs a backend plus
  licensed feature data. The PCM/metadata vectors are already a usable pick for
  every track; the measured-profile store is the natural place to plug in
  embeddings later.
- **Ranking:** the target blends the current track (0.62) with the last five
  plays (0.30, decaying 0.55×). Candidates combine cosine similarity (dominant)
  with language affinity (Hindi↔Urdu 0.92, Hindustani 0.78, other South Asian
  0.6, unrelated 0.15 · 0.35), genre (0.15), a small catalogue-reco prior
  (0.15) and era (0.05), minus recent-artist repeats (0.15). Tiered fallbacks:
  strong vibe (sim ≥ 0.78, different artist) → same scene (sim ≥ 0.60 with
  language ≥ 0.75 or genre ≥ 0.70) → same artist → any non-recent track.
- **"No other version of the same song" rule:** every track gets an
  artist-agnostic `titleKey` (normalised title with repeated letters collapsed,
  so "Mere Nishan"/"Mere Nishaan" group). Before ranking, the keys of the
  current track, all queued songs and the last-10 history are excluded, so
  remixes, variants and same-title covers by other artists are never staged.
  The last 10 ids, dedupe pairs and back-to-back same artist are also guarded.
- **Flow:** tapping a search result queues exactly that one song; when it is
  last in the queue, a continuation is ranked and appended as a real Media3
  playlist item immediately (with a 30 s before-end safety net). Media3
  transitions gaplessly; after it starts, the next one is prefetched. Manual
  "add to queue"/"play next"/reorder/remove actions drop the staged tail so
  user picks always come first. The queue sheet shows an "Autoplay" hint while
  a continuation is staged.
- **Shuffle and repeat:** shuffle keeps its own progression, so no tail is
  staged while it is on (an appended item would take a random position in the
  shuffle order). Turning shuffle on drops any staged tail, and when the
  shuffle order is exhausted autoplay starts a continuation by absolute index.
  Repeat-one and repeat-all keep looping instead of invoking autoplay.

## What cannot be cloned

| item | why |
| --- | --- |
| Spotify catalogue and its licences | licensed recordings, not a feature. The clone resolves public third-party streams instead. |
| Spotify Connect protocol | proprietary, requires Spotify device authentication. Chromecast is a separate public SDK. |
| Recommendation ML (Discover Weekly, Release Radar) | trained on Spotify's listening graph. The clone can do heuristic mixes, not that model. |
| Social graph and friend activity | requires Spotify accounts and their privacy model. |
| Podcasts / audiobooks catalogue | Spotify's licensed catalogue is unavailable. Podcasts are built instead on the public iTunes search index plus each show's own RSS feed; audiobooks have no equivalent open source and stay out. |
| Jam / group sessions | requires realtime backend + accounts. |
| Ads and premium billing | commercial infrastructure, and unwanted in a personal player. |
| Spotify trademarks, logo, artwork, UI copy | owned brand assets. The clone keeps a distinct name and its own assets. |

## Size

- Full Spotify parity including social, Connect and billing: **XL** — multi-quarter, needs backend, accounts, licensing. Podcasts are the exception and are now built on public RSS.
- Functional parity for a single-user local+streaming player (playback, library, playlists, discovery, search, podcasts, offline, settings, stats, accessibility): **L** — this is the tractable target, and it is now largely built.
- The core loop alone (search → play → queue → like → playlist): **M**, and it is already built.

## Next

Build pass complete: the parity clusters are implemented and unit-tested, and
`assembleDebug` produces an installable APK. On-device verification is still
outstanding because no device is currently attached. Remaining honest gaps are
listed in `features.csv` — chiefly account/social features, licensed
audiobooks, recommendation ML, Android Auto and Cast, plus the partials
(per-item storage management, data saver enforcement, explicit filtering of
online results, image-format share cards, podcast chapters and transcripts).
