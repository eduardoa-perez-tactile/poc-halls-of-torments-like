# Stable 30 FPS Android rendering — 26 November 2026

The user accepted a stable 30 FPS target in place of 60 FPS. Android now produces
30 frames per second while the existing simulation continues at 60 fixed steps
per second. Art, resolution, combat rules, difficulty and audio behavior are
unchanged. Desktop retains its existing rendering configuration.

## Focused changes

- One reusable Choreographer callback requests frames on compatible display
  ticks: every second tick at 60 Hz, third at 90 Hz or fourth at 120 Hz. One
  in-flight frame and one replaceable pending tick prevent a render backlog.
  The existing LibGDX/platform EGL swap and lifecycle synchronization remain in
  control; no sleep loop, new dependency or rendering-engine replacement is used.
- When supported, `EGL_ANDROID_presentation_time` associates each buffer with its
  intended display tick. This resolved the alternating short/long intervals seen
  when limiting render requests alone. EGL handles/extension support are cached
  on surface events. Unsupported drivers retain the cadence and report the
  fallback. Pause, surface replacement and destruction invalidate old requests.
- Lateness uses the 33.3 ms render budget, so a request is not discarded merely
  because it arrives after an intentionally unused 60 Hz refresh.
- The unchanged floor medallion uses a managed static SpriteCache, preserving its
  geometry, UVs, alpha blending and position before dynamic ground/actors. This
  removes a recurring 23,360-byte vertex upload per camera per frame. It costs
  26,864 bytes each of GPU geometry and CPU restoration storage, without adding
  textures. The extra cache can add one draw per camera.

The Android implementation follows the [Android game-loop guidance](https://developer.android.com/games/develop/gameloops)
and the [AOSP explanation of presentation timestamps](https://source.android.com/docs/core/graphics/frame-pacing).

## Headless presentation checks

The final APK was measured on an isolated Android TV API 33 ARM64 emulator:
1920×1080, four virtual cores, 2 GiB RAM, host GPU and audio enabled. Each fixture
used two synthetic phone controllers with movement/Dodge, five seconds of warm-up
and fifteen seconds of untraced measurement.

| Measurement | Late co-op | Equipped co-op boss |
| --- | ---: | ---: |
| Observed presentation FPS | 30.0009 | 29.9991 |
| Observed span | 15.100 s | 15.100 s |
| Presented intervals | 453 | 453 |
| Intervals matching two display refreshes | 453 | 453 |
| Additional target-frame-like gaps | 0 | 0 |
| Presentation interval p95 | 35.12 ms | 35.25 ms |
| Presentation interval p99 | 36.82 ms | 37.57 ms |
| Maximum presentation interval | 40.33 ms | 40.80 ms |

These are actual SurfaceFlinger layer presentation timestamps, sampled throughout
the complete interval, rather than a short trailing latency snapshot. The tool
checks adjacent-snapshot coverage, deduplicates rows and rejects missing/invalid
data. Both captures had complete coverage. The two-refresh classification rounds
emulator timestamps to refresh multiples; the raw percentile/max values above
retain their jitter. This is evidence of stable 30 FPS in these captures, not a
claim that every interval was precisely 33.333 ms or proof of hardware deadlines.

The analyzer now distinguishes the intended 30 FPS period from the display's
60 Hz period. Its `missedTargetFrameLikeGaps` is the relevant lower-rate heuristic;
the retained legacy `missedRefreshLikeGaps` counts intentional unused display
refreshes and must not be used as the 30 FPS acceptance result. Callback-start
timings, callback wall time and GPU completion remain distinct measurements.

The complete measured callback windows retained about 302–303 simulation steps
per 151 rendered frames, with no simulation-delta clamping. Callback wall p95 was
12.33–14.03 ms for late co-op and 10.16–13.48 ms for the equipped boss. These are
separate window percentiles, not pooled run percentiles or GPU timings.

## Ten-minute stability run

The same APK completed 618.58 seconds in one process: 26 combat/replay cycles,
seven controller reconnects and eight background/resume checks. Each cycle
exercised real skill/equipment choices, moving two-player boss combat, remote
pause/resume and results/replay. Logs verified all 26 runs advanced combat and
performed attack collision tests, with 31,386 fixed simulation steps and 54
crowded boss windows. No fatal exception was recorded. Audio stayed at two workers
and one owned music handle; all 117 pacing reports had zero presentation-timestamp
failures/unavailable counts.

| Process memory | First five samples, median | Last five samples, median | Observed maximum |
| --- | ---: | ---: | ---: |
| Combat total PSS | 68.01 MiB | 69.09 MiB | 69.28 MiB |
| Results total PSS | 64.90 MiB | 65.76 MiB | 65.98 MiB |
| Combat Java PSS | 14.94 MiB | 14.96 MiB | 15.41 MiB |
| Results Java PSS | 11.93 MiB | 11.93 MiB | 12.20 MiB |

Memory remained bounded after an initial small rise. Java PSS returned to its
baseline; total PSS did not return to precisely its first-cycle value. Thread
counts stayed between 45 and 47. This supports stability for the observed repeated
stress, rather than establishing that memory can never grow. These are separate
phase samples, not forced-GC measurements or a complete twenty-minute survival
playthrough. ADB memory snapshots and transitions perturb pacing, so presentation
acceptance uses the separate steady-load captures above.

## Focused regression checks

JDK 21: 181 focused Java tests passed across cadence/request invalidation,
diagnostics, medallion geometry, audio ownership, combat/boss outcomes, choices,
reconnect and late joining. Android debug packaging and desktop compilation
passed. The presentation/capture/stability helpers passed 39 Python checks.

The additional eight-second expansion-effects capture initially recorded
26.68 FPS, twelve target-frame-like gaps and a 211.60 ms callback maximum,
localized to world drawing. The GC pause logged in that run was only
microseconds; it does not explain those stalls. A traced repeat and a separate
untraced repeat both delivered 30 FPS with every interval matching two refreshes
and no additional target-frame-like gaps. Callback maxima were 10.38 and 9.71 ms.
The original stall's cause remains unestablished; both the failed capture and
the clean repeats are retained. No speculative renderer rewrite was added to
explain a stall that the trace did not reproduce.

### Follow-up investigation

The requested follow-up still did not reproduce the original 211.60 ms callback
stall. There is **no verified fix for that original event**. The retained change
adds profiling-only diagnostics; production artwork, gameplay and the sprite
buffer backend remain unchanged.

The existing profiling batch now measures actual nonempty flush calls using
primitive per-frame counters. An arena render above 33.3 ms can emit one
`ProfileRenderStall` record per second, with monotonic frame-end time, combat time,
arena wall time, flush count/total/maximum, camera and flushing pass. Pass labels
identify the calling code; queued vertices can originate in preceding passes.
The counters add no GL calls, synchronization fences or per-frame objects.
Capture logging retains these records and ART collection messages.

The preceding production APK's additional fifteen-second traced combined-skills
capture delivered 30.0014 FPS with zero target-frame-like gaps. A diagnostic build
then completed six repeated fifteen-second combined-skills windows in the same
process, alternating traced and untraced capture. All six had complete
presentation coverage: 2,709 intervals, each matching two display refreshes,
29.9975–30.0011 FPS, zero target-frame-like gaps and no arena stall records.
Reported process PSS was 67,289–69,123 KiB (65.71–67.50 MiB), without monotonic
growth. Audio stayed at two workers/one music handle; textures stayed below the
unchanged ceiling. No fatal exception was recorded.

This fixture uses ten durable targets and repeated skill effects, with four
orbiters, six fields, up to two glaives and nine delayed strikes observed. Each
fixture is refreshed before its thirty-second development hold expires. It does
not represent a full survival run or simultaneous saturation of all pools.

The traces show recurring emulator-side `glBufferDataSyncAEMU` encoding work,
but do not establish it as the cause of the original long stall. A small
`VertexBufferObjectSubData` experiment was rejected and reverted: the resolved
LibGDX 1.14.2 implementation still invokes `glBufferData` during a dirty bind,
so normal SpriteBatch flushes retain the same upload path. Its trace confirmed
21,652 such calls over fifteen seconds. That experiment also had four
target-frame-like gaps and a 39.95 ms arena frame whose 53 flushes totaled
37.74 ms, with no individual flush longer than 1.98 ms. It is not evidence of
fixing the original event. See the pinned [SpriteBatch implementation](https://github.com/libgdx/libgdx/blob/1.14.2/gdx/src/com/badlogic/gdx/graphics/g2d/SpriteBatch.java)
and [buffer implementation](https://github.com/libgdx/libgdx/blob/1.14.2/gdx/src/com/badlogic/gdx/graphics/glutils/VertexBufferObjectSubData.java).

Android packaging, eight focused Java checks and eighteen capture-tool checks
passed for the retained diagnostics. All 108 APK assets remain byte-identical.
Diagnostic APK SHA-256:
`55fe1f803368006274cd8c4c21980979997a6eb6434a47d6c6d16f6d86429f0f`.
Follow-up evidence is in ignored `.run/skill-stutters-2026-11-26/`, including the
failed experiment. The original isolated stall remains open; clean repetitions
are not proof that it has been eliminated.

The separate equipped-boss trace also retained the complete 8.03-second span,
with no trace error counters. Its 229 `onDrawFrame` slices had a 17.89 ms maximum;
GL-thread CPU execution totaled 2.30 seconds and EGL swap wall time totaled
107.14 ms. The expansion trace retained 8.03 seconds without error counters;
239 draw slices had a 10.42 ms maximum. Those native trace slices have different
boundaries from the app's callback-stage reports and are not GPU duration.

`am start -W -R 2` finished/recreated the activity in the same PID, with two
startup reports and a newly created join server. The replacement instance
reported the active 30 FPS pacer, supported presentation timestamps with zero
failures, two audio workers and one music handle. Native thread names confirmed
only one `sound-mixer` and one `scene-music` remained. No fatal exception was
recorded. This is a bounded recreation check, not a prolonged context-loss soak.

## Resources and reproducibility

Texture storage stayed at 32,959,308 bytes (31.43 MiB), including QR, below the
32 MiB ceiling. Retained decoded pixmaps stayed at 518,988 bytes. These figures
exclude geometry, audio, framebuffer/driver overhead and other process memory.
All 108 packaged assets match the preceding APK byte for byte.

Runtime source: `2fadd00`. Tested APK SHA-256:
`5fb082f7043a62faedee26182d4482dcb5113522c8064ac7cf105c5af6ddc090`.
The preceding APK was
`58d2b76e9c97ca8da6b105c15fe16f8340744cd020bf9a9bde711a6c25726d7a`.
Earlier 60 FPS and intermediate 30 FPS experiments are retained separately;
the table uses only the final build's `stable30` captures.

Local raw evidence is under ignored `.run/frame-pacing-2026-11-26/`.
The temporary emulator is removed at handoff. The user's separate emulator,
saved phone-reachable QR host and existing forwards are preserved. Its LAN
endpoint closed the verification request without a response; that separate
application was not launched or modified to change its existing state.

```sh
python3 scripts/capture-performance.py --serial emulator-5556 \
  --host <phone-reachable-LAN-IP> --forward-port <existing-local-ADB-forward> \
  --fixture late-coop --seconds 15 --warmup-seconds 5 --moving-input \
  --target-fps 30 --output-dir .run/unique-late-coop
```

Use `--fixture art-coop --lifecycle` for the equipped boss capture. Target FPS is
an analysis parameter; it does not change the installed APK's production rate.
The profiling-only `--frame-pacing off` switch supports A/B observations.

Emulation depends on host scheduling and GPU translation. It cannot establish
physical-TV temperature, thermal throttling or long-run hardware performance.
Final hardware verification should use the same build with a normal run and
check presentation pacing and memory after warm-up.
