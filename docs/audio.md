# Audio foundation

The TV owns the mix. `GameAudio` plays short effects from simulation counters and
explicit UI events; `SceneMusic` streams the background score. Phones remain silent
controllers. No combat rules, timings or network messages depend on playback.

The repository already contains 21 original placeholder WAV effects, generated
by `scripts/GenerateCombatAudio.py`. This foundation preserves them. It adds no
soundtrack or newly generated effects; missing optional files produce silence.

## Adding the score

Place Ogg Vorbis files at these exact paths under `assets/`, then rebuild/restart:

| Path | Plays during | Playback / fallback |
| --- | --- | --- |
| `audio/music/menu.ogg` | Home, lobby, character info | Loop; silence if absent |
| `audio/music/arena.ogg` | Ordinary combat | Loop; silence if absent |
| `audio/music/sovereign.ogg` | Living Hollow Sovereign | Loop; arena fallback |
| `audio/music/grave-king.ogg` | Living Grave King, priority over Sovereign | Loop; Sovereign then arena fallback |
| `audio/music/victory.ogg` | Victory and its Run Details | Once; menu fallback loops |
| `audio/music/defeat.ogg` | Defeat and its Run Details | Once; menu fallback loops |

Use streaming `Music` for the score, never resident `Sound`. Only one native
music stream is held at a time. Scene changes fade out over 0.6 seconds, release
the old decoder, open the next track and fade in over 0.6 seconds. A fallback
that resolves to the current track continues playing without restarting.
A finished outcome track stays finished through Run Details, backgrounding and
mute/unmute; leaving that scene allows it to play on a later run.

Manual pause, disconnect pause, Invite Player 2, and invitation character info
pause the existing stream. Resuming continues its position. Phone skill/gear
choices keep the current score at 25% of its configured level. The defeat
transition pauses the score until Results appears. App backgrounding pauses
music immediately and stops effects; returning does not resume a manually paused
run. Music muted at 0% pauses instead of continuing inaudibly.

Absent/broken tracks are remembered for the app lifetime. Missing files are
silent; decoder/setup failures are logged once and use the same fallback chain.
Restart the app after adding or repairing a file. Stream opening occurs only at
audible scene transitions, outside the simulation tick; decoder startup cost and
loop boundaries still need checking with final assets on the physical TV.

## Replacing or adding effects

`SoundCue` is the authoritative file/mixer catalog: path, duration in seconds,
base volume, priority and minimum repeat interval. Existing effects cover weapon
releases and contacts, critical hits, Dodge, hurt, death, healing, pickups,
secondary abilities, enemy attacks, level-up, boss arrivals and outcomes.
The legacy `hit.wav` remains packaged but has no active routing.

The following hooks are connected and await optional WAV files:

| File under `assets/audio/` | Trigger |
| --- | --- |
| `ui-focus.wav` | A remote navigation action changes focused button |
| `ui-confirm.wav` | A remote/touch button activates, including provided hero/results views |
| `ui-back.wav` | Remote Back/Escape |
| `loot.wav` | A personal equipment reward is earned |
| `player-down.wav` | An existing participant falls while the party survives |
| `revive.wav` | A fallen participant revives |

Use short mono PCM WAV exports. Current files use 22.05 kHz, 16-bit PCM and total
236,146 PCM bytes. Update `SoundCue.seconds` to the actual replacement duration:
voices are explicitly stopped after that duration, adjusted for cosmetic pitch,
plus a 30 ms tail. UI and ceremony cues keep their original pitch; combat varies
between 0.96 and 1.04 independently of the simulation's random stream. Keep gain
conservative and check the combined mix on speakers; individual cue peaks do not
establish that a crowded mix sounds clean.

Effects load once at startup. Six tracked voices and four starts per rendered
frame bound overlap; important cues can replace lower-priority voices. Repeats
are rate-limited independently. Counters are consumed even when muted, absent or
rate-limited, so reconnect/resume/unmute never replays a backlog. Fresh joining
and run reset do not emit false down/revive or arrival cues. Finished effects
are retired during menus/results as well as gameplay. Missing optional effects
are safe; present files that fail to load are logged once during startup.

## Remote controls and resource limits

Home Settings -> Audio and Pause -> Audio expose Master, Music and Effects in
25% steps (100% wraps to 0%). Changing one row keeps its focus. Values persist in
local `couch-crypt` preferences. Effective output is master multiplied by the
channel volume, then the cue gain or choice duck. An old `sound=false` preference
migrates to master mute, preserving the user's silent setup. First-use defaults
are Master 100%, Music 65%, Effects 100%.

A volume change updates live effects immediately. Muting stops those voices;
backgrounding also suppresses UI effects. Music keeps its stream when paused or
muted and releases it on replacement/disposal. No audio resources are allocated
per simulation tick and no GPU textures are added. Native decoder/mixer memory
is separate from the 32 MiB texture ceiling; real soundtrack sizes, startup cost,
perceived loudness and physical-TV performance remain to be measured.

## Verification

Focused fake-backend checks cover independent weapon gates, priorities and voice
limits, stale-event consumption, live volume changes, optional cues, menu-frame
retirement, loot/down/revive routing, preference migration/persistence, and music
fade/disposal/fallback/pause/completion. Desktop compilation and Android debug
packaging also pass with JDK 21. These checks do not prove audible output or
hardware audio-focus behavior. No soundtrack files are included yet.
