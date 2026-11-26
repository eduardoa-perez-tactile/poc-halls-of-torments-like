# Sprite-buffer stutter fix — 26 November 2026

## Focused changes

The native stalls in [the previous long stress test](long-skill-stress.md)
concentrated inside AEMU's synchronous `glBufferData` encoding. The dynamic
arena batch now stages adjacent texture runs into one bounded vertex buffer,
preserving their original draw order, vertices, UVs, colours and state barriers.
Explicit flushes, shader/matrix/blend changes, clipping, capacity and custom
geometry passes still submit immediately. This reduces vertex uploads without
reordering transparent artwork or changing shaders.

On the Android GLES2 path the managed vertex store is allocated once and updated
with `glBufferSubData` while bound, rather than replaced on each submission.
Context restoration reinstates its full capacity before a partial update.
The adapter uses the pinned LibGDX 1.14.2 package-private SpriteBatch contract;
check it when updating LibGDX. GLES3 retains its VAO path.

The post-swap request gate also uses the same request-age deadline as the UI
callback. Previously the post-swap path could discard a tick after half a frame
even though the UI would still accept it and its intended presentation remained
in the future. There is still only one in-flight request and one replaceable
credit, with no catch-up rendering burst.

Art, assets, simulation, combat timings, targeting, difficulty and audio are
unchanged. Rendering stays at 30 FPS and simulation at 60 Hz. The added fixed
numeric storage is 477,248 bytes across Java staging, managed CPU restoration and
GPU vertex/index storage, plus 256 texture references, object/driver overhead.
It is allocated with the renderer, not per frame. The inherited batch mesh is
still owned and disposed. No textures or decoded pixmaps were added.

`FrameMetrics` now reports `dynamicSpriteBatchVertexUploads`, separating upload
count from texture draw count. Existing stall logs remain rate limited and are
not a complete count of slow frames.

## Correctness checks

- 45 focused Java tests passed, including seven recording-GL batch tests for
  exact vertices/texture order, state barriers, overflow, fixed storage across
  200 frames, disposal and managed context restoration followed by a larger
  update. The cadence/gate tests cover a valid 20 ms-old credit and expiry.
- Android debug assembly and desktop compilation passed using JDK 21.
- Twelve native GL frozen-scene comparisons against the original SpriteBatch
  produced zero differing RGBA bytes and identical draw counts. They include
  solo Warden/Ranger skill builds, crowded co-op, bosses, palette variants,
  chest additive effects, warnings/impacts and capacity boundaries.
- All 108 packaged assets are byte-identical to the previous APK.
- The candidate's Android activity recreated in the same PID with a fresh
  server and exactly one native mixer and one music thread. The resulting
  diagnostics report two audio workers, one music handle and 30 FPS pacing.

The measured APK SHA-256 is
`62eeee8e606502bdae3a482fb49bcf7b04114fc88af6ae30e3fb4e7e19c6642a`.
Raw evidence is retained locally under ignored
`.run/buffer-upload-fix-2026-11-26/`.

## Long headless measurement

The unchanged harness completed **906.64 seconds / 37 cycles in one PID**,
including ten controller reconnects and twelve background/resume checks. There
were no fatal exceptions or process restarts. Every run advanced combat and
performed attack collision tests: 45,836 fixed simulation steps, 81 crowded
report windows and 221 windows with occupied secondary-skill pools. Separate
observed peaks were 62 enemies, four orbiters, six fields, two glaives, twelve
delayed strikes, sixteen friendly bolts, twenty-eight hostile shots and
fifty-seven effects.

All 37 presentation windows had complete coverage, with no excluded windows.

| Actual presentation | Untraced | Traced |
| --- | ---: | ---: |
| Windows / intervals | 24 / 14,450 | 13 / 7,832 |
| Observed time | 481.70 s | 261.10 s |
| Weighted FPS | 29.9979 | 29.9964 |
| Interval p95 / p99 | 35.08 / 36.48 ms | 35.13 / 36.40 ms |
| Maximum interval | 67.47 ms | 60.15 ms |
| Intervals over 50 ms | 4 (0.0277%) | 2 (0.0255%) |

The six isolated outliers among 22,282 intervals are retained. This is near
30 FPS with rare gaps, not perfectly uniform 33.333 ms delivery. The user accepted
isolated outliers and requested no further speculative optimization. Compared
with the old controlled baseline's eighteen untraced gaps and 96.93 ms maximum,
the new untraced observation has four gaps and a 67.47 ms maximum; host conditions
prevent treating that comparison as a controlled speedup experiment.

Only one rate-limited arena-stall record appeared in the final run: 36.88 ms
inside a traced window, with six flushes. The previous run had twenty-two such
records inside observed windows, including the caught 78.33 ms native callback.
No 100–187 ms stall like those caught in earlier validation/partial runs
recurred. All 173 pacing reports had zero presentation-extension failures or
unavailable counts.

The corresponding native callback was 37.28 ms: 15.05 ms Running and 22.24 ms
Runnable on the guest runqueue. Six SubData encodes spanned 32.77 ms, including
that scheduling delay; this is not a 32 ms GPU execution/wait measurement.
There was no app-process GC/JIT/suspend overlap. The complete fifteen-second
trace had only twenty-one BufferDataSync calls, with a 0.269 ms maximum, and
zero trace error counters. The repeated allocation path did not return.

| Same-phase process PSS | First-five median | Last-five median | Sample range |
| --- | ---: | ---: | ---: |
| Combat total | 69.15 MiB | 75.97 MiB | 67.96–76.84 MiB |
| Results total | 66.03 MiB | 72.49 MiB | 64.29–72.83 MiB |
| Combat Java | 15.61 MiB | 15.95 MiB | 11.83–16.40 MiB |
| Results Java | 12.18 MiB | 12.29 MiB | 12.14–12.52 MiB |
| Combat native | 23.95 MiB | 29.32 MiB | 23.18–29.51 MiB |
| Results native | 23.60 MiB | 29.32 MiB | 22.62–29.51 MiB |

**Memory is not flat in this run.** Most of the increase is native; growth slows
but a plateau is not established. Static inspection of the pinned library found
no per-upload direct-buffer allocation or per-replay mesh creation: both reuse
their constructor storage. That does not establish the cause of native growth
or prove there is no leak. Driver/JNI retention, allocation high-water and
existing audio/QR churn remain possible explanations requiring separate evidence.
No speculative memory mechanism or forced collection was added.

Live threads stayed between 49 and 51; audio stayed at two workers and one music
handle. Accounted textures stayed below 32 MiB at 32,959,308 bytes / 31.43 MiB;
steady combat retained pixmaps stayed at 518,988 bytes. Fixed application resource
counts are stable, while total native/process memory still needs observation.

Before that run, three fully traced twenty-second Android windows confirmed
the new path: about 6.1–6.5 SubData uploads per callback, maximum callback
4.78–5.37 ms and zero trace error counters. There were 21/42/35 residual
`glBufferDataSyncAEMU` calls in the respective complete traces, each at most
0.35 ms; this is not a claim that all application buffer allocations vanished.
All three presentation windows were near 30 FPS, with maxima 39.94–40.19 ms
and no intervals above 50 ms.

## Method and limits

The isolated headless Android TV API 33 ARM64 emulator runs at 1920×1080 with
four virtual cores, 2 GiB RAM, host GPU and audio enabled. The existing user's
primary emulator, LAN join host and forwards are preserved. The same harness
uses two moving synthetic phones, Dodge, full class skills, mixed late enemies,
real personal choices, pause/results/replay, reconnects and background/resume.
Each cycle measures twenty seconds of combat; it is repeated stress rather
than a normally earned twenty-minute build. Enemies can die and reinforcements
continue. Every third cycle traces fifteen seconds with a bounded 128 MiB ring.
Memory snapshots and lifecycle actions follow the presentation window.

Actual SurfaceFlinger timestamps are sampled across complete combat windows;
pooled percentiles use deduplicated raw intervals. Traced and untraced samples
are separate. A gap over 50 ms is the existing 1.5-target-period heuristic,
not a hardware deadline assertion. No screenshot renderer runs concurrently.
A temporary host power assertion prevents display/idle sleep during the final
validation and stress run; it changes no preferences. The older baseline did
not control host power state, so timing comparisons are observational.

The initial coalescing-only experiment still caught a 187 ms native draw callback,
including a 132 ms `glBufferData` call, and was stopped. Its raw partial results
remain in `stress15/`; they are not the final run. That concrete failure led to
the fixed-store refinement measured in `stress15-final/`.

Emulator host scheduling, trace overhead and AEMU differ from physical TV
drivers. This measures this build and fixture; it does not certify physical-TV
performance, thermals, all memory leaks or final gameplay balance.
