# Android submission and stability work — 25 November 2026

This change removes blocking Android audio work from rendering, reuses large
range-outline geometry and culls offscreen expansion effects. Asset pixels,
targeting, damage, movement, difficulty, encounter timing and choice rules are
unchanged. All 108 APK assets match the audited before APK byte for byte.

## Bounded implementation

Android effects retain the existing six voices, priorities, repeat gates and
four starts per render update. Six primitive latest-state mailboxes hand those
voices to one native audio owner; each slot has a generation, deadline and
private native sound ID. There is no growing command queue. Expired or replaced
starts are discarded; replacements stop their previous native voice before
starting another. Volume/stop apply to the actual owned ID.

Music has a separate latest-state worker, so stream preparation cannot block
effects or rendering. The existing fade/fallback/completion behavior stays in
that worker, with one preparing or active stream. Stable gains skip redundant
native volume calls. Pause, reset and disposal publish state without joining
workers or holding publication locks over native calls. Optional native failures
log once and attempt all cleanup. Cues requested during initial bank loading can
be dropped, rather than replayed late.

The Android backend skips duplicate framework music lifecycle calls because
CryptLobby already publishes suspension/resumption to both owners. SoundPool
release waits for those owners off-thread. The bundled LibGDX 1.14.2 backend's
`notifyMusicDisposed` removes the backend instead of its disposed music argument
from its private list (verified in the resolved bytecode). A private
MediaPlayer-backed Music adapter avoids that registry and retains only its one
preparing/active handle. It uses supported Android/LibGDX APIs, without reflection
or package-private constructors. Desktop keeps its original OpenAL thread model.

Large outlines retain the original 192 segments, triangle order, 2:1 projection,
antialiasing bands, packed colour and constant stroke. One managed static mesh
uses 25,344 bytes of GPU payload plus managed CPU restoration storage. Its shader
applies the live centre/radius; no recurring large-ring vertex upload is needed.
Small warnings retain their original 48-segment SpriteBatch path, avoiding a
shader/draw handoff for each common warning. Their reusable Java arrays occupy
12,304 bytes. No textures are added. Large custom draws are included in total
and world-draw diagnostics; their shader switches can increase draw counts.

Expansion ground/follow-up effects now use conservative per-camera bounds,
including rotated artwork and a sampling margin. Their drawing order and shared
combat-owned time remain unchanged.

## Diagnostics and checks

FrameMetrics now samples melee waves, orbiters, fields, glaives, relics, delayed
strikes and chests in addition to the original pools. World reports separate
bolt/melee/expansion collision candidates, melee/field/delayed acquisition
misses, and relic-pool versus placement back-pressure. These are cumulative run
counters, with collision instrumentation enabled only in profiling mode. Frame
pool peaks are end-of-frame observations, not intra-tick peaks.

World reports also expose Android audio worker and music-handle counts. These
are primitive ownership snapshots, not native calls. The stale diagnostics test
now correctly distinguishes the independent melee-wave pool from generic
contact/death effects.

JDK 21: 165 focused Java tests passed, plus desktop compilation and Android debug
packaging. They cover audio nonblocking/stale/failure/cleanup behavior, equivalent
ring geometry and culling, diagnostic gating/reset, combat, Dodge, boss outcomes,
choices, reconnect and late joining. Nineteen capture-tool checks passed. The
tests do not replace native Android playback or sustained runtime observation.

## Same short headless captures

Before APK SHA-256:
`67689f0044f1532c12180608950f69964780c499d955dba2b95e475cd6bfc614`.
After APK SHA-256:
`58d2b76e9c97ca8da6b105c15fe16f8340744cd020bf9a9bde711a6c25726d7a`.
Installed hashes match. Runtime source is feature commit `af7b587`; later capture
helper/documentation changes do not change that APK.

Both used an isolated headless Android TV API 33 ARM64 image, 1920×1080, 2 GiB RAM,
four virtual cores, host GPU, audio enabled. Each untraced fixture ran for fifteen
seconds after five seconds of warm-up, with two synthetic phones targeting 30 Hz
movement and Dodge. Tables select two complete approximately five-second windows
per scenario and exclude partial/transition/choice windows. Ranges are separate
window percentiles, never combined percentiles.

| Measurement | Late co-op before → after | Equipped co-op boss before → after |
| --- | --- | --- |
| Audio callback stage p95 | 4.39–5.65 → 0.08–0.11 ms | 4.92–5.06 → 0.08 ms |
| Callback wall p95 | 12.53–14.10 → 10.42–11.11 ms | 10.16–13.01 → 8.27–10.02 ms |
| Maximum callback wall | 38.13 → 20.40 ms | 25.43 → 15.45 ms |
| Render-start interval p95 | 18.05–18.17 → 18.72–19.13 ms | 18.31–18.50 → 19.19–19.52 ms |
| Maximum render-start interval | 38.76 → 21.23 ms | 25.95 → 21.51 ms |
| Peak draw calls | 190 → 184 | 144 → 157 |

Audio submission and callback tails were lower in these samples; interval p95
was worse, and boss draw counts rose. The captures have differing run seeds,
builds/enemy histories and host scheduling, so this is observational evidence,
not a controlled proof of a particular rendering speedup. Native audio now runs
off-thread by construction. Large ring reconstruction/upload is removed, but
that trades buffer work for explicit mesh draws/shader handoffs.

The strict every-frame 16.7 ms target remains unverified and was exceeded here.
Callback wall time is neither thread CPU execution time nor GPU completion or
presentation time. No physical-TV thermal or GPU result is claimed.

Resident textures remain 32,959,308 bytes / 31.4324 MiB including maximum QR, with
595,124 bytes of texture headroom. Retained decoded Pixmaps remain 518,988 bytes.
Those figures exclude audio, geometry, driver/framebuffer storage and other
process memory.

## Sustained capture

`scripts/capture-stability.py` performs bounded repeated fixture cycles on one
explicit emulator and one game PID: personal gear/skill choices, crowded equipped
co-op boss combat, remote pause/resume, pending-choice reconnect, alternating
Victory/Defeat results, replay/lobby and periodic background/resume. It samples
combat/results PSS, Java/native PSS and thread counts. The default duration is ten
minutes, bounded to 60–900 seconds; finishing the current cycle can extend it
slightly. It never installs an APK, targets physical hardware or runs a complete
survival playthrough.

It also validates logged boss-time progression, actual collision work,
fixed-step activity, crowded pool windows, texture bounds and audio ownership.
Being responsive in the `session` UI alone is insufficient to pass. ADB snapshots
and scripted transitions perturb pacing, so this is stability evidence separate
from the untraced short performance comparisons.

The first attempt stopped on a harness assumption: a natural level-up earned
around pause correctly returned to its pending choice, while the harness expected
ordinary combat. The helper was corrected to acknowledge resumed/queued personal
rounds without changing the game. The final capture's duration, cycles, memory
observations and validation results are recorded with the final runtime evidence.

The completed capture ran **619.55 seconds**, finishing its final cycle after the
600-second deadline: **26 cycles in one PID**, seven pending-choice reconnects,
eight background/resume checks, and alternating results/replay/lobby transitions.
There were no recorded fatal exceptions or process restarts. Post-capture log
validation confirmed 26 distinct advancing/attacking boss runs, 56 crowded boss
windows, 31,686 boss-simulation steps and a sampled peak of 63 enemies. It was not
an idle-menu soak. Audio stayed at two workers and one music handle; reported
textures stayed below the unchanged ceiling. No acquisition misses were observed
for enemies, bolts, hostile shots, hazards, effects, melee waves, fields or delayed
strikes in these sampled run reports.

| Same-phase process PSS | First-five median | Last-five median | Sample range |
| --- | ---: | ---: | ---: |
| Crowded combat | 68,721 KiB | 68,724 KiB | 66,973–71,788 KiB |
| Results | 66,238 KiB | 66,041 KiB | 65,439–68,490 KiB |

There was no upward PSS trend over this run. Live thread counts were 45–47.
PSS is an OS estimate and varies with allocation/collection and graphics/audio
caches; these observations do not prove the absence of every possible leak.

An additional eight-second expansion fixture exercised four orbiters, six fields,
two glaives, two relics and nine delayed strikes in frame-sampled peaks. Its full
five-second window had 7.11 ms callback-wall p95. This is not simultaneous
saturation of all pools.

The matching eight-second-requested system trace retained a 5.574-second tail,
parsed without positive error-severity stats. It contained 322 complete
`onDrawFrame` slices (maximum 16.220 ms), alongside swap/buffer waits outside the
callback. Emulator-specific dynamic buffer submission remained substantial.
As with the earlier limited trace, inclusive/nested slices must not be added;
no GPU timing or controlled rendering speedup is inferred.

A separate `am start -W -R 2` check finished/recreated the Android activity in the
same PID, with two startup reports and a newly created join server. It recorded
no fatal exception; the new instance reported two workers, one music handle and
the same texture total. Process thread names confirmed only one `sound-mixer`
and one `scene-music` afterward. A preceding developer-setting attempt did not
actually recreate the activity and was explicitly rejected as evidence; its
result is retained separately. This bounded recreation check is not a repeated
context-loss memory soak.

Use the current saved QR LAN address and a separate existing ADB localhost forward:

```sh
python3 scripts/capture-stability.py --serial emulator-5556 \
  --host <saved-LAN-host> --forward-port <owned-forward-port> \
  --seconds 600 --output-dir .run/performance-fixes-2026-11-25/stability-final
```

The saved QR host must never be replaced by localhost or an emulator-only IP.
The temporary test emulator is stopped/removed after capture; existing TV
emulator preferences, relay and forwards are preserved.

At cleanup the shared emulator was running the other game, and its Couch Crypt
process was not running, so the Couch Crypt LAN endpoint was unavailable. Its
saved LAN host and both original forwards were verified unchanged; no app was
installed or launched on that shared emulator. The isolated test emulator, owned
forward, synthetic phones and temporary AVD storage were removed.

Raw before evidence is ignored under `.run/performance-audit-2026-11-25/`; after
logs, metadata, asset proof, memory samples and screenshots are under
`.run/performance-fixes-2026-11-25/`. Short runtime checks cannot establish
stability on every Android TV model or sustained physical-device thermals.
