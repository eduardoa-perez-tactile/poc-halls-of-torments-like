# Crowded full-skill headless stress — 26 November 2026

The longer checks reproduce intermittent drawing stalls. The traced examples
concentrate their time in the emulator's GPU-buffer upload encoding path.
They do not establish a physical-TV GPU bottleneck or a verified production fix.

## Result

The run completed **915.15 seconds / 37 cycles in one PID**, with ten controller
reconnects and twelve background/resume checks. No fatal exception or process
restart occurred. Logs verify all 37 runs advanced real combat and performed
attack collision tests: 46,016 fixed simulation steps, 81 crowded report windows
and 228 windows with multiple occupied skill pools.

Observed pool peaks were 62 enemies, four orbiters, six fields, two glaives,
thirteen delayed strikes, sixteen friendly bolts, twenty-seven hostile shots and
sixty generic effects. These are separate frame-sampled peaks, not a claim that
every peak occurred in one frame.

All 37 presentation captures had complete timestamp coverage. The controlled
summary excludes the requested preview's two overlapping windows:

| Presentation measurement | Untraced | Traced |
| --- | ---: | ---: |
| Windows / intervals | 23 / 13,849 | 12 / 7,224 |
| Observed time | 461.96 s | 240.96 s |
| Weighted presentation FPS | 29.9785 | 29.9796 |
| Interval p95 / p99 | 35.13 / 38.08 ms | 35.04 / 38.03 ms |
| Maximum interval | 96.93 ms | 122.68 ms |
| Target-frame-like gaps over 50 ms | 18 (0.130%) | 6 (0.083%) |

Including both preview windows gives 22,276 intervals over 743.06 seconds:
24 untraced windows at 29.9794 FPS with eighteen gaps, and thirteen traced windows
at 29.9775 FPS with ten gaps. All raw observations are retained. Percentiles above
come from deduplicated raw intervals, not averaged window percentiles. The gap
counter is the same 1.5-target-period heuristic as the earlier tools; it is not
proof of hardware scan-out deadlines.

**Average FPS stays near 30, but strict stable pacing is not met.** Thirty
rate-limited arena-stall diagnostic records were captured across all modes;
twenty-two fall inside observed combat windows. These records are not a complete
count of slow frames because logging is limited to one record per second.

| Same-phase process memory | First-five median | Last-five median | Sample range |
| --- | ---: | ---: | ---: |
| Combat total PSS | 68.20 MiB | 65.21 MiB | 63.31–71.12 MiB |
| Results total PSS | 64.99 MiB | 61.26 MiB | 59.82–65.42 MiB |
| Combat Java PSS | 15.25 MiB | 15.59 MiB | 13.88–16.03 MiB |
| Results Java PSS | 11.85 MiB | 11.93 MiB | 11.81–12.15 MiB |

There is no upward total-PSS trend over this run. PSS is an OS estimate; its
decrease is not an optimization result. Native-heap medians rose modestly from
22.11 to 22.54 MiB in combat and 22.06 to 22.36 MiB at results. Live thread counts
stayed between 51 and 54. Android audio stayed at two workers and one music
handle. Accounted textures reached 32,959,308 bytes / 31.43 MiB, below 32 MiB;
steady combat retained pixmaps stayed at 518,988 bytes. These observations
support bounded memory during this test, not proof of absence of every leak.

## Method

The existing diagnostic APK is unchanged:
`55fe1f803368006274cd8c4c21980979997a6eb6434a47d6c6d16f6d86429f0f`.
No gameplay, art, resolution, texture or rendering-backend changes were made.
The isolated headless emulator uses Android TV API 33 ARM64, 1920×1080, four
virtual cores, 2 GiB RAM, host GPU and enabled audio.

Two synthetic phones supply movement and Dodge. Each repeated cycle gives both
players their full class skill sets, fills the late encounter with mixed enemies
and applies the existing equipment preview. Twenty seconds of combat are
followed by choice/pause/results/replay checks. Selected cycles also reconnect a
phone or background/resume the same process. The boss is omitted so an early
boss kill cannot turn the combat measurement into an idle results capture.
Enemies can die and normal reinforcements continue; the initial crowd count is
not held constant. This is repeated stress, not a twenty-minute survival run or
a normally earned build.

Every combat window samples actual SurfaceFlinger presentation timestamps at the
30 FPS target. Per-window storage and polling are bounded. Memory snapshots,
trace pulling and lifecycle actions happen after sampling stops. Every third
cycle records a fifteen-second Perfetto trace with a bounded 128 MiB ring;
traced and untraced presentation results are reported separately.

The injected numeric skill shortcut was found to open Android TV's on-screen
keyboard. The helpers now inspect IME visibility, dismiss it when shown and
verify it is absent before measurement. The captured overlay is a confounder
for earlier numeric-fixture observations, not proof of their stall's cause.

During the longer run the user requested a clean solo screenshot. A separate
short-lived hidden desktop GL context used the same game renderer and artwork,
with targets hidden only for the preview. Cycles 13–14 overlap that requested
host graphics work; their raw measurements are retained and distinguished from
the controlled presentation summary. No test process restart was needed.

## Caught native stalls

Validation and long-run traces caught these events with the keyboard absent:

| Native draw callback | AEMU upload calls | Total upload-encode wall time | Guest thread Running / Runnable |
| --- | ---: | ---: | ---: |
| 103.33 ms | 95 | 93.41 ms | 101.82 / 1.52 ms |
| 91.48 ms | 68 | 78.35 ms | 89.42 / 2.06 ms |
| 78.33 ms (long run, cycle 16) | 84 | 69.80 ms | 63.75 / 14.58 ms |

These align with the game's `ProfileRenderStall` records. Cost is distributed
across many `glBufferDataSyncAEMU encode` calls rather than one long flush.
Neither validation event overlaps GC, compilation or suspension slices in the
app process. The nearby GC's mutator pause was 0.006 ms. Their subsequent EGL
swaps, outside the callbacks, were 1.62 and 4.74 ms. A separate 151 ms arena stall ended before
the trace's retained start and therefore has no native attribution here.

Cycle 16 of the longer run independently confirms the same upload-heavy path,
with no overlapping app GC/compilation/suspension activity. Its following swap
took 0.68 ms. Guest runqueue delay contributed 14.58 ms, partly inside upload
encoding slices; that delay must not be added to their inclusive wall time.
The longest 15.75 ms encoding call contained 14.96 ms guest Running time, while
another 9.26 ms call contained 8.53 ms Runnable delay. Both submission overhead
and scheduling variability matter in this emulated environment.

Guest thread Running time does not isolate host-vCPU execution, host GPU work or
emulator transport costs. This identifies the expensive observed submission
path; it does not prove that physical hardware will behave the same way.

Raw screenshots, per-cycle timestamp samples, traces, logs and analysis are under
ignored `.run/long-skill-stress-2026-11-26/`. The capture-tool checks cover bounded
trace configuration, IME dismissal, per-cycle sampling and cleanup on failure;
all 26 checks passed. The temporary emulator, forward and AVD are removed at
handoff. The separate existing emulator, saved QR LAN host and forwards are
preserved. Its LAN endpoint still closed the verification request without a
response; that separate application was not launched or modified.

```sh
python3 scripts/capture-stability.py --serial emulator-5556 \
  --host <phone-reachable-LAN-IP> --forward-port <existing-local-ADB-forward> \
  --seconds 900 --presentation --trace-every 3 --full-skills \
  --output-dir .run/unique-long-skill-stress
```
