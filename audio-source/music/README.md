# Crypt score sources and mastering

Retrieved 25 November 2026. These are adaptations of published compositions,
not original compositions or recordings made for Couch Crypt. All six source
pages identify their releases as CC0. Optional credits are retained in the APK
at `assets/audio/CREDITS.txt` and in the source manifest.

| Runtime track | Composition | Author | Authoritative source |
| --- | --- | --- | --- |
| Menu | Dark Shrine Loop (remix of Shrine) | qubodup; original by yd | [OpenGameArt](https://opengameart.org/content/dark-shrine-loop) |
| Arena | Menace | yd | [OpenGameArt](https://opengameart.org/content/menace) |
| Hollow Sovereign | Determined Pursuit | Emma_MA | [OpenGameArt](https://opengameart.org/content/determined-pursuit-epic-orchestra-loop) |
| Grave King | Epic Boss Battle [Seamlessly Looping] | Juhani Junkala / SubspaceAudio | [OpenGameArt](https://opengameart.org/content/boss-battle-music) |
| Victory | Medieval: Victory Theme | RandomMind | [OpenGameArt](https://opengameart.org/content/medieval-victory-theme) |
| Defeat | Medieval: Defeat Theme | RandomMind | [OpenGameArt](https://opengameart.org/content/medieval-defeat-theme) |

`manifest.json` records the exact download URL and original SHA-256 for each
master. Downloaded full-size masters stay in the ignored
`.run/audio-production/music-sources/` cache rather than duplicating them in Git.
`BakeMusic.py` can fetch missing masters from those pinned URLs and verifies
source hashes before processing. The output receipt in `bake-report.json`
records the actual shipped files' hashes, lengths, sizes and decoded peaks.

The offline process removes DC/infrasonic energy below 25 Hz, overlaps loop
boundaries, and uses two-pass EBU R128 loudness normalization with a -3 dBTP
ceiling. Menu targets -23 LUFS; the other tracks target -22 LUFS. Four-millisecond
edge tapers avoid hard waveform steps; one-shot outcomes also receive a short
release taper. Source dynamics and arrangements are retained; no new voice,
synthesizer or drum layers are generated. The menu's exported trailing silence
is trimmed before a 1.5-second circular overlap. Arena uses a 150 ms overlap;
the two already-authored boss loops use an 8 ms seam overlap. These edits slightly
shorten source lengths, documented in the manifest and receipt.

Exports are stereo 44.1 kHz Ogg Vorbis at quality 3, with title/artist/license/source
metadata. The six files total **6,249,377 bytes (5.96 MiB)**. Tracks last 32–123
seconds; only the four menu/combat tracks loop. Native runtime uses one streamed
decoder at a time, independent of the resident short effects and GPU textures.

From the repository root, with Python 3.9–3.12:

```sh
python3 -m venv .run/audio-tools
.run/audio-tools/bin/pip install -r audio-source/requirements.txt
.run/audio-tools/bin/python scripts/BakeMusic.py
.run/audio-tools/bin/python scripts/ValidateAudio.py
```

FFmpeg can be supplied with `--ffmpeg /absolute/path`; otherwise the script uses
an installed binary or the imageio-ffmpeg bundled executable. The development
bake used bundled FFmpeg 7.1. Ogg container serials can vary between rebuilds;
PCM processing and source selection are explicit, and the bake refreshes the
output receipt rather than claiming container bytes are identical everywhere.

Decode/headroom/seam checks do not establish artistic fit, comfortable repetition
or balance on physical TV speakers. Human listening owns those judgments.
