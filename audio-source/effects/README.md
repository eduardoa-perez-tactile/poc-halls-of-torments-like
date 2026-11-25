# Sampled crypt effects

The 25 November 2026 sound pass replaces the procedural placeholder bank with
27 layered, sampled effects. The selected source material consists of physical
cloth, leather, blades, coins, chainmail, bells, metal, glass and body impacts,
along with an author's fantasy swishes, creature textures and spell sound.
No generated oscillator tones or white-noise beds are used by this bake.
These are edited derivatives of the credited sources, not original recordings
made for Couch Crypt. Source recordings and transformations remain separate
from the small runtime files.

## Sources and permission

All selected material is **CC0 1.0**, permitting modification and redistribution,
including commercial use. Attribution is optional; it is retained here for
provenance. Original archive member names, exact fetched download URLs, archive
SHA-256 hashes and each selected source SHA-256 are in
[manifest.json](manifest.json). Only the 28 selected files are retained under
`sources/`; complete downloaded archives are not part of the repository.

| Publisher | Pack and authoritative license declaration | Evidence retained |
| --- | --- | --- |
| Kenney Vleugels / Kenney.nl | [RPG Audio](https://kenney.nl/assets/rpg-audio) | `rpg-audio-License.txt` from the downloaded archive |
| Kenney Vleugels / Kenney.nl | [Impact Sounds](https://kenney.nl/assets/impact-sounds) | `impact-sounds-License.txt` from the downloaded archive |
| Kenney Vleugels / Kenney.nl | [Interface Sounds](https://kenney.nl/assets/interface-sounds) | `interface-sounds-License.txt` from the downloaded archive |
| artisticdude | [RPG Sound Pack](https://opengameart.org/content/rpg-sound-pack) | `oga-license-evidence.txt`, transcribed publisher-page title/author/license; original archive has no license file |

[CC0 legal terms](https://creativecommons.org/publicdomain/zero/1.0/legalcode).
Kenney's website now issues temporary signed public download URLs through its
`/download-link/` endpoint. The fetched URL in the manifest is an exact retrieval
record and may expire; use the corresponding pack page to retrieve a fresh copy.
The bake uses the retained selected files and makes no network requests.

## Recipe and runtime contract

The complete declarative layer recipe is in
[`scripts/BakeSoundEffects.py`](../../scripts/BakeSoundEffects.py): source filename,
gain, playback speed, delay, trim, optional reversal, high/low-pass filters and
room level are explicit per cue. The processing chain averages stereo to mono,
removes DC/leading silence, resamples source playback speed, filters, applies
short edge fades, mixes material layers and adds a few quiet filtered early room
reflections. Gentle offline saturation controls stacked transients. Each result
receives a bounded peak and end fade before quantization to PCM16. There is no
audio generation or DSP in the simulation/render loop.

| Runtime cues | Material and presentation |
| --- | --- |
| `sword`, `arrow` | Blade swish/sheath; lowered plucked-string release with fast air swish and mechanism click |
| `hit`, `sword-hit`, `bolt-hit`, `critical` | Soft body impact, blade chop, wood contact and restrained metal/glass accents |
| `pickup`, `loot` | Handled coins; opened latch, coins and a burnished bell |
| `level`, `victory`, `revive` | Bell/ringing-metal arrivals, with higher metal overtones and short crypt reflections |
| `boss`, `defeat`, `player-down` | Lowered bell, material/body weight, creak or falling chainmail; boss adds a low creature texture |
| `dodge`, `hurt`, `death`, `block` | Cloth rush; body/armor contact; soft fall and shade texture; ringing shield metal |
| `heal`, `aura`, `storm`, `magic-cast` | Liquid/ringing metal; reversed cloth resonance; mining/plate impact; sampled spell and air/metal release |
| `wraith-dash`, `brute-slam` | Shade texture with reversed cloth rush; low body/wood/stone impact |
| `ui-focus`, `ui-confirm`, `ui-back` | Small physical metal clicks/latch and cloth, with quieter peaks |

All runtime outputs are **mono, 22,050 Hz, signed 16-bit PCM WAV**. The entire
27-file bank decodes to **635,478 PCM bytes (0.606 MiB)**, plus small backend
overhead. Ordinary combat cues last 0.11–0.60 seconds; announcements last up to
1.40 seconds. `SoundCue.seconds` matches each measured file duration to within
one sample so the six-voice mixer retires actual source tails correctly.
Runtime pitch variation and existing cue gates/priorities remain bounded.

## Rebuild and check

Use Python with `numpy`, `scipy` and `soundfile` installed. The development bake
used Python 3.9, NumPy 2.0.2, SciPy 1.13.1 and SoundFile 0.13.1.

```sh
.run/audio-tools/bin/python scripts/BakeSoundEffects.py
.run/audio-tools/bin/python scripts/BakeSoundEffects.py --check
```

`--check` verifies selected-source hashes, deterministic rendered PCM, WAV
format, all catalog durations, non-silent output, peak headroom and silent edge
samples without modifying files. `GenerateCombatAudio.py` now forwards to this sampled bake, preserving the old
command without restoring the placeholder sounds.

Format/peak checks do not establish perceived loudness, material recognition,
comfortable repetition or the sound of the combined mix on TV speakers. Those
need audible human review. No automated analysis here is represented as having
listened to the clips.
