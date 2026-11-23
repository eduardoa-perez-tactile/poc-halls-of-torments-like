# Performance and memory baseline

This milestone adds measurements only. The user reports no visible performance
problem; these captures are a preventive baseline, not evidence of a reported bug.
Combat rules, timestep, artwork and rendering order are unchanged by diagnostics.
The engraved-floor milestone was coordinated separately in the same checkout;
its renderer/assets must be stable before this baseline is captured.

## Measurement contract

Android `profile=true` and desktop `--profile` enable fixed-storage diagnostics.
Normal launches do not construct FrameMetrics, scan pools or emit these reports.

- `intervalMs` measures consecutive render-callback starts before delta clamping.
  It includes scheduling, work outside the callback and diagnostics reporting;
  it is not an actual displayed-frame timestamp.
- `renderCallbackWallMs` measures elapsed callback time, including blocking GL
  submission. The legacy `cpuMs` field has the same meaning for old tools; it is
  **not thread CPU time**, GPU completion time, or presentation latency.
- Nonoverlapping host stages cover handoff, fixed-step simulation, audio, status,
  arena submission and Scene2D UI. Floor/gather/worldDraw/HUD timers are nested
  inside arena and accumulate across cameras; do not add them to their parent.
  Floor covers the static cache, while worldDraw includes the dynamically staged
  medallion. Texture changes can flush earlier geometry in a later function;
  these CPU wall stages do not attribute GPU cost to an individual effect.
  Absent stages contribute zero. Stage p95 values
  must not be added: they can describe different frames. Callback minus stage
  totals also contains measurement/counter overhead and unclassified host work.
- Draw counts are sampled every frame and reported as a distribution, rather
  than only the last frame. Arena batch texture selections exclude static-cache
  and Scene2D texture binds; they are not a global GL-bind count. Visible sprites
  count depth-list entries across panes, including scenery and repeated views,
  and exclude separately drawn projectiles. Pool occupancy peaks are sampled at frame ends;
  transient intra-tick peaks can be missed. Pool misses/collision candidates are
  cumulative per run, with the run seed and arena time in `ProfileWorld` logs.
- Fixed-step counts include choice-only ticks. Raw deltas above 100 ms are counted
  before the existing host clamp; diagnostics do not change catch-up behavior.
- `ProfileStartup` covers creation stages, separately from settled rendering.
  `createTotal` starts inside the LibGDX create callback; it excludes Android
  process/activity initialization and is not time from launcher click to display.
- Texture bytes account RGBA asset residency and QR, excluding driver overhead,
  swapchain/framebuffers, geometry buffers and audio. The 32 MiB ceiling applies
  to these textures, not the whole process. Retained Pixmaps are native CPU
  copies; Java heap and Android PSS are distinct measurements.

Five-second reports allocate strings and sort fixed buffers. Frame collection
has fixed storage, but profiling still costs time. Do not infer release overhead
or optimize toward profiling-only spikes without a system trace.

## Repeatable short capture

Use the existing TV emulator and relay only after coordinating ownership. The
harness never installs a build or touches a physical device. Explicitly select
`emulator-5554`, the existing localhost forward and the QR's LAN host. For example:

```sh
python3 scripts/capture-performance.py --serial emulator-5554 \
  --host <existing-relay-host> --forward-port <existing-forward-port> \
  --fixture art-coop --seconds 7 --warmup-seconds 2 --moving-input \
  --output-dir .run/performance-baseline/boss-coop
```

The optional moving clients send ordinary input at 30 Hz each, with deterministic
bounded axes and occasional Dodge; they do not choose rewards or navigate shared
menus. They gate movement on host status and send neutral input during choices,
pause and cleanup. They exercise network parsing and camera motion, not browser
layout/touch latency or physical Wi-Fi conditions. Heartbeat-only mode remains
the compatibility default and is explicitly different workload.

Warm-up, fixture construction, memory snapshots and measurement boundaries are
recorded separately. Profile-only Page Down resets the metrics baseline without
altering game state. Only complete five-second windows inside the measurement
segment should be used as settled comparisons; partial/transition windows remain
in the raw log. Never average percentile values into a combined percentile.

Each capture stores raw logs, memory snapshots, summary and metadata under its
output directory. Metadata records Git/source/asset identity, installed APK hash,
device image/display and PID. A dirty tree is recorded, not silently treated as
its HEAD commit. Logs, APKs, traces and generated captures remain ignored.

Read-only SurfaceFlinger per-layer latency snapshots are collected where exposed.
Their finite history must be filtered to the observed app surface and segment;
zero, sentinel, old-process or duplicate records are not displayed frames. No
global SurfaceFlinger timing state is cleared. Missing presentation data must be
reported as unavailable, not replaced by callback intervals.

`--system-trace` attempts one finite Perfetto recording using a fixed 64 MiB
ring in the tracing service, then pulls and removes its owned device trace.
Unsupported capture is recorded as unavailable. This tracing buffer is separate
from game-process memory and is not retained by the game.

A separate short Perfetto capture can correlate scheduling, GC and EGL/swap
activity. It is a traced diagnostic sample, not an untraced baseline replacement.
GPU execution timing remains unmeasured unless a supported GPU data source is
actually present. Perfetto's FrameTimeline documentation notes that SurfaceViews
are unsupported; a missing game track is not proof of no missed frames.

References: [Android game tracing](https://developer.android.com/games/optimize),
[Perfetto Android recording](https://perfetto.dev/docs/quickstart/android-tracing),
[FrameTimeline limits](https://perfetto.dev/docs/data-sources/frametimeline).

## Recorded baseline — 23 November 2026

Coordinated with the floor chat: its approved implementation was committed and
merged first at `cc8ab2a` (feature `36468f6`). No runtime assets, floor geometry,
render order or gameplay rules were changed during these captures. The baseline
precedes the next floor-detail follow-up; repeat the affected fixture after that
separate visual change before comparing it with future optimization work.

- Instrumentation source: `b3a6b70`; later harness-only trace-buffer correction
  does not change the APK. Per-capture source hashes record that distinction.
- Installed debug APK SHA-256:
  `34eec8d88d032d9534ee78f9ef9493340864bbf7a678e076c6159e959dbab19e`.
  Its hash was checked on the emulator and matches the built APK.
- Existing `Television_1080p`, emulator-5554, Android 13 / API 33, ARM64,
  1920×1080, 2 GiB RAM, four virtual cores, automatic GPU backend.
- Four untraced seven-second segments, each after a two-second warm-up and metric
  reset; one complete approximately five-second report window per segment below.
  No concurrent build, screenshot or ADB navigation ran inside these segments.
  Memory and compositor snapshots were taken around them; screenshots afterward.
- Combat clients targeted 30 Hz and actually sent 27.3–27.7 input messages per
  second each during these segments, with two Dodge edges each. This is measured
  synthetic protocol/camera traffic, not browser/physical-phone latency testing.
  The process PID stayed unchanged within every segment.

| Untraced scene | Frames | Start interval p95 / p99 / max (ms) | Callback wall p95 / p99 / max (ms) | Draws p50 / p95 / max |
| --- | ---: | --- | --- | --- |
| Lobby | 281 | 20.88 / 34.99 / 169.34 | 12.06 / 18.90 / 65.65 | 21 / 21 / 21 |
| Equipped solo boss | 275 | 19.82 / 71.16 / 104.16 | 11.46 / 70.63 / 102.93 | 56 / 65 / 85 |
| Equipped late co-op | 301 | 17.89 / 18.65 / 19.07 | 10.27 / 12.86 / 17.56 | 81 / 114 / 121 |
| Equipped co-op boss | 301 | 17.95 / 18.86 / 20.12 | 8.92 / 11.65 / 12.48 | 76 / 98 / 106 |

Do not compare the different scenarios as before/after speedups. Intervals above
16.7 ms were 142/280, 134/274, 137/300 and 145/300 respectively; above 33.4 ms
were 3, 9, 0 and 0. The solo boss window reached six catch-up steps and one
clamped raw delta; the lobby window had two clamped deltas. Those are observed
emulator stalls, not a user-reported problem or an established cause.

### Stage attribution

| Combat scene | Simulation p95 / max (ms) | Static floor submission p95 (ms) | Gather/sort p95 (ms) | Dynamic world submission p95 / max (ms) | HUD p95 (ms) |
| --- | --- | ---: | ---: | --- | ---: |
| Solo boss | 0.12 / 0.35 | 0.08 | 0.02 | 5.22 / 102.07 | 0.58 |
| Late co-op | 0.06 / 0.20 | 0.05 | 0.03 | 5.90 / 7.20 | 0.51 |
| Co-op boss | 0.06 / 0.23 | 0.05 | 0.03 | 5.18 / 6.86 | 0.41 |

Simulation and gathering were small in these samples. The solo outlier occurred
in dynamic submission; its exact cause remains unresolved and was not reproduced
in the full co-op trace. The lobby's main measured work was Scene2D drawing
(p95 11.61 ms, max 65.49 ms). These wall timers include descheduling or blocked
API calls; low floor submission time does not prove low floor GPU cost.

Sampled combat pool peaks across the untraced windows were 55 enemies, 12 bolts,
31 hostile shots, one hazard, 29 effects and 13 pickups. Logged run counters showed
no acquisition misses or pickup merges. This is not maximum-pool stress coverage.
The late co-op run recorded 117,535 eligible bolt/enemy candidates between two
five-second reports while its simulation remained small; a spatial-index rewrite
has no measured justification from this baseline.

### Memory and startup

| Accounted resource | Observed |
| --- | ---: |
| Textures including QR | 33,516,364 bytes / 31.9637 MiB |
| Remaining texture allowance | 38,068 bytes / 37.18 KiB |
| Retained native decoded Pixmaps | 20,245,324 bytes / 19.3074 MiB |
| UI share of retained Pixmaps | 16,843,340 bytes / 16.0631 MiB |
| Used static floor vertex / index payload | 453,280 / 67,992 bytes |
| Allocated static floor vertex / index capacity | 640,000 / 96,000 bytes |
| Cached medallion vertex payload | 23,360 bytes, plus object/array metadata |

Static meshes also retain CPU restoration storage; the payload figures are not
an additional decoded image or a total process/driver allocation estimate.
Profiling adds about 0.25 MiB of fixed numeric-array payload plus object headers.

| Scene | Process PSS before / after segment (KiB) |
| --- | ---: |
| Lobby | 119,051 / 82,245 |
| Solo boss | 122,149 / 84,809 |
| Late co-op | 122,615 / 86,023 |
| Co-op boss | 122,624 / 86,000 |

Textures and retained-pixel counts stayed constant throughout the reported
windows. Falling PSS reflects process-start allocation/collection and is not
proof of no leaks. One separate traced observation went through sample results,
lobby and background/resume: PSS 85,495 / 84,247 / 84,345 KiB, respectively, with
no fatal runtime exception recorded. This did not force GL-context loss or test
long-run stability.

Across the four untraced process restarts, LibGDX `createTotal` was 2.975–3.172 s:
UI artwork/fonts 1.359–1.484 s, shared titles/icons/HUD 0.707–0.762 s, and phone
art preparation/server setup 0.451–0.489 s. These include current runtime baking
and encoding and use warm OS file caches. They exclude earlier Android startup.
Offline artwork preparation and reduced retained pixel copies are therefore
concrete follow-up candidates, not changes made by this milestone.

### Presentation and system trace

Read-only SurfaceFlinger snapshots contained 127 valid actual-present timestamps
for the app's current BLAST SurfaceView, covering about 2.1 s (lobby 2.19 s).
Tail inter-present p95 was 22.30 ms lobby, 18.22 ms solo boss, 17.95 ms late co-op
and 17.94 ms co-op boss. These finite tails were read just after each segment,
can extend past its end marker, and must not be treated as the same five-second
window as the callback table. Zero/sentinel records and older start-snapshot
timestamps were excluded; no global counters were cleared.

An initial eight-second Perfetto attempt with an 8 MiB ring retained only about
1.36 s. That raw attempt is preserved but not used for whole-window conclusions.
The corrected 64 MiB bounded capture retained 8.018 s, including 468 complete
`onDrawFrame` slices spanning 7.794 s. Trace Processor v59.1 reported no positive
error-severity statistics. The traced run is separate from the untraced table.

On the emulator GL thread:

- `onDrawFrame`: 2,968.597 ms total, about 6.34 ms per callback, max 18.681 ms.
- `eglSwapBuffers`: 4,817.926 ms total, about 10.29 ms per swap, max 14.676 ms,
  outside `onDrawFrame`. Nested `dequeueBuffer` slices explain waiting but must
  not be summed again with their parents.
- Thread scheduling: 2,859.251 ms Running and 4,914.157 ms sleeping. Runnable
  states together accounted for 21.247 ms in the retained state records.
- Emulator-specific `glBufferDataSyncAEMU encode`: 1,684.451 ms inclusive wall
  duration across 29,976 calls. This is driver/emulator submission evidence,
  not GPU execution time or a physical-TV prediction.
- No GC pause slices appeared in this retained window; tiny heap-trimming slices
  do not explain the earlier untraced solo spike.

This demonstrates that a substantial part of the traced frame interval is spent
in swap/buffer waiting after the callback. It does not establish whether physical
TV GPU cost or presentation pacing needs optimization, nor explain every earlier
outlier. FrameTimeline does not provide a supported app SurfaceView deadline
track here. No missed-vsync count or GPU execution percentile is claimed.

### Verification and next decision

JDK 21: desktop classes, Android debug assembly and 23 focused Java tests passed
(FrameMetrics, world diagnostic semantics and the directly affected world tests).
Seventeen Python protocol/parser/trace-cleanup tests passed. A separate read-only
review found no render-order or extra-flush changes. Six bounded captures
completed without a recorded fatal app exception; one was repeated specifically
because the first trace buffer retained too little data. No full-game automated
playthrough, physical-phone test, sustained physical-TV test or optimization ran.

Keep simulation/networking architecture as-is on this evidence. Prioritize
startup/retained-pixel work if pursuing the next optimization milestone; use
representative physical-TV evidence before treating emulator swap waits or
individual drawing functions as a hardware bottleneck. Rebaseline the affected
scene after the separate floor-detail update.

Raw evidence, source/APK metadata, per-window JSON and SQL outputs are ignored
under `.run/performance-baseline/{lobby,boss-solo,late-coop,boss-coop,
boss-coop-traced,boss-coop-trace-full}/`. Temporary controllers, loggers, tracing
processes and device trace files were stopped/removed. The emulator and existing
relay remain running for the floor chat; the app is restored to ordinary Home.
