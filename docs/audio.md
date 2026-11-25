# Dark-fantasy audio

The TV now ships a complete score and sampled effect bank. `GameAudio` owns short
effects; `SceneMusic` owns one streamed track at a time. Phones remain silent.
No combat rules, timings or network messages depend on playback.

## Included score

| Runtime path under `assets/` | Scene | Source composition / author |
| --- | --- | --- |
| `audio/music/menu.ogg` | Home, lobby, character info | Dark Shrine Loop — qubodup; original Shrine by yd |
| `audio/music/arena.ogg` | Ordinary combat | Menace — yd |
| `audio/music/sovereign.ogg` | Living Hollow Sovereign | Determined Pursuit — Emma_MA |
| `audio/music/grave-king.ogg` | Living Grave King, highest boss priority | Epic Boss Battle — Juhani Junkala / SubspaceAudio |
| `audio/music/victory.ogg` | Victory and its Run Details | Medieval: Victory Theme — RandomMind |
| `audio/music/defeat.ogg` | Defeat and its Run Details | Medieval: Defeat Theme — RandomMind |

The first four loop; outcomes play once and stay finished while browsing Run
Details, including through backgrounding or mute/unmute. Tracks last 32–123
seconds. Stereo 44.1 kHz Vorbis exports total **6,249,377 bytes (5.96 MiB)**.
Menu is mastered to -23 LUFS and the other tracks to -22 LUFS, with headroom for
combat effects and short boundary edits to avoid hard waveform steps.

A scene change fades out over 0.6 seconds, releases its decoder, then opens and
fades in the next track over 0.6 seconds. Only one native music stream is held.
Pause, disconnect pause, Invite and invitation character info preserve playback
position. Phone skill/gear choices lower music to 25% of the configured level.
The defeat transition pauses the score until Results. Backgrounding pauses
music and stops/suppresses effects; it does not resume a paused run on return.
Music at 0% pauses instead of continuing inaudibly.

All six source releases are CC0. These are edited third-party compositions,
not original compositions made for this game. Exact sources, authors, licenses,
download hashes and processing steps are in
[audio-source/music](../audio-source/music/README.md); credits also ship in
`assets/audio/CREDITS.txt`.

## Included effects

All 27 event slots have sampled sounds. Physical blades, cloth, leather, armor,
coins, stone/body impacts, bells and metal are layered with licensed fantasy
swishes, shade/giant textures and a spell sound. Their recipes use trims,
playback-speed edits, filtering and small room reflections. The old procedural
beeps/noise have been replaced. No synthesis or DSP runs in the game loop.

Combat covers weapon releases/contacts, critical hits, Dodge, hurt, death,
healing, pickups, secondary abilities and enemy attacks. Announcements cover
level-up, boss arrivals, loot, falling/revival and outcomes. Remote focus,
button activation and Back have small material clicks/latch sounds. `hit.wav`
is retained for compatibility; actual weapon contacts have separate cues.

The entire bank is **mono 22.05 kHz PCM16 WAV, 635,478 decoded bytes (0.606 MiB)**.
Short actions last 0.11–0.60 seconds; announcements last up to 1.40 seconds.
`SoundCue` defines paths, actual durations, gains, priorities and repeat gates.
Six voices and four starts per frame bound overlap. Muted/gated/absent events
are consumed, so resuming never replays a backlog. Menus and results also retire
finished voices. Cosmetic combat pitch varies independently of simulation RNG;
UI and ceremony cues retain their pitch. Missing replacement files remain safe.

Source selections, original archive licenses, hashes and the full layer recipes
are in [audio-source/effects](../audio-source/effects/README.md). All selected
material is CC0; these are edited sample derivatives, not original recordings.

## Settings and replacement

Home Settings -> Audio and Pause -> Audio expose Master, Music and Effects in
25% steps; 100% wraps to mute. Focus stays on the changed row. Defaults are
Master 100%, Music 65%, Effects 100%. The old Sound-off preference migrates to
master mute. Effective gain is master × channel × cue gain (or choice duck).
Live effect volumes update immediately; muted voices stop.

Replace files at their existing paths, then rebuild/restart. Update
`SoundCue.seconds` if an effect's duration changes, or the bounded mixer will
stop it at the old lifetime. Optional music fallback remains:
Sovereign -> arena; Grave King -> Sovereign -> arena; results -> looping menu.
Missing/broken tracks are remembered until restart, with setup failures logged
once. Source conversion and stream opening occur outside the simulation tick.

To rebuild with the pinned offline tooling dependencies:

```sh
python3 -m venv .run/audio-tools
.run/audio-tools/bin/pip install -r audio-source/requirements.txt
.run/audio-tools/bin/python scripts/BakeSoundEffects.py
.run/audio-tools/bin/python scripts/BakeMusic.py
.run/audio-tools/bin/python scripts/ValidateAudio.py
```

The legacy `GenerateCombatAudio.py` command forwards to the sampled bake.
Selected effect masters are retained outside runtime assets. Full music masters
are downloaded to an ignored cache with pinned source hashes. No GPU textures
are added; native audio decoder/mixer memory is separate from the texture limit.

## Verification and listening

The bake checks every source hash and effect's reproducible PCM, format,
duration, non-silence, headroom and zero-valued endpoints. The score check
decodes all six Vorbis files and validates metadata, lengths, peak headroom,
loop boundary steps and output receipts. Focused fake-backend tests cover
voice caps, event routing, stale-event consumption, volume persistence/migration,
pause/fade/disposal/fallback and completed one-shot behavior. Desktop compilation
and Android debug packaging are checked with JDK 21.

These checks do not establish how the mix sounds on TV speakers. Human review
should check music/effects balance, repeated weapon sounds, boss contrast,
choice ducking and pause/background behavior. A brief Android TV API 33 launch reported native MediaPlayer music and SoundPool
UI playback, with the same music player pausing/resuming through backgrounding;
remote Audio settings rendered correctly. The APK matched all 34 audio/credit
files byte-for-byte. Automated waveform checks and native playback state are not
a listening review or a sustained physical-TV performance result.
