# Bounded performance checks

The target is 60 FPS (about 16.7 ms per frame) with low memory use. The existing 32 MiB limit applies to resident uncompressed GPU texture data, including QR. It is not a limit on the whole Android process.

## Measurements

Start the installed Android build with the `profile` intent extra (or desktop `--profile`). `FrameMetrics` allocates two fixed 2,048-entry arrays only in profiling mode and reports about every five seconds or on mode changes. Normal builds do not sample these metrics. Each report includes:

- Raw frame-start interval p95/p99/max, plus counts above 16.7 and 33.4 ms. Intervals use monotonic time before the simulation delta clamp, so long frames are not hidden.
- CPU duration of `render()` submission, separately. This excludes asynchronous GPU completion, display presentation and work before the render callback. A low CPU value does not prove the GPU meets its budget.
- Screen/mode (including solo/co-op boss and paused/results), sample count, final draw calls and memory snapshot. Mode changes preserve the transition interval; a lazy outcome load may appear as a large maximum.
- `gpuTextureBytes`: bounded RGBA texture storage, including QR. Driver overhead and framebuffer storage are not included.
- `retainedPixmapBytes`: native CPU pixel copies intentionally kept for managed texture restoration. This includes generated art/font atlases and QR. File-backed portraits are reloaded from their source and have no permanently retained decoded pixmap.
- `javaHeapBytes`: LibGDX's used Java-heap estimate. It excludes native pixmaps, audio, graphics-driver allocations and much of the Android process.

Pause/resume reset the frame baseline, so time spent backgrounded is not reported as a rendering stall. Arrays are sorted only when reporting; collection has no per-frame object creation. Reports allocate their log strings. A mode-change report uses the previous mode's latest memory snapshot, rather than mislabeling the newly loaded mode's resources.

Use Android `dumpsys meminfo com.couchcrypt.tv` for process PSS/native/Java measurements. PSS is an OS estimate, not directly comparable to texture bytes, and includes shared pages proportionally. A short increase may reflect heap growth or collection timing rather than a leak. No arbitrary total-process memory threshold has been introduced.

## Short fixture capture

`scripts/capture-performance.py` uses only Python's standard library and an existing ADB forward/relay. It does not build or install an APK, create a relay, touch a physical device, or run a survival playthrough. It restarts the installed profiling build, opens the lobby, creates heartbeat-only synthetic controllers, then uses the existing F6 boss/F7 outcome fixtures. These controllers do not move or dodge; a boss fixture may end in defeat. Read the mode labels rather than treating the entire log as sustained boss rendering.

Example with the current local setup:

```sh
python3 scripts/capture-performance.py --serial emulator-5554 \
  --host 192.168.14.248 --forward-port 51849 --fixture boss-coop --seconds 20 --lifecycle
```

Other fixtures are `boss-solo`, `results` and `lobby`. Choose the directly relevant fixture; do not run all of them as a default regression suite. Duration defaults to 20 seconds and is bounded to 5–30 seconds; use a short results-only capture for a directly affected transition. Logging starts before entering the fixture so the first transition is included. Logs and beginning/end memory snapshots replace the same fixture's files under ignored `.run/`. The results fixture returns to the lobby once to observe resource release. Optional `--lifecycle` adds one sample victory/lobby transition and a brief Home/resume check, with a resume screenshot; it does not force GL-context loss. This is not a repeated leak test. The script closes synthetic clients, stops its logcat process, and restores a fresh empty lobby, leaving the existing emulator/relay/forward running.

For a context-restoration check, briefly background/resume the app and inspect the QR/art. QR now retains a managed pixmap and disposes both pixels and texture on replacement/shutdown. Android may retain the GL context on this brief transition, so this verifies lifecycle behavior without proving a forced GL-context loss.

## Interpretation and limits

Emulator timings depend on host load, the emulator GPU backend, scheduling and ADB observation overhead. They can reveal stalls, resource accounting errors and regressions; they do not establish sustained 60 FPS on a physical TV. CPU submission time is not GPU timing. Short fixtures do not represent the maximum simultaneous effects/enemies, thermal throttling or a full run's memory behavior.

Use one focused build and a short capture after relevant changes. Fix concrete findings, then remeasure only the affected fixture. Physical-TV acceptance should later cover representative hardware at native TV resolution, including the busiest co-op encounter; the user remains responsible for gameplay playtesting.

## 2026-11-13 emulator observation

One 20-second co-op boss fixture ran on `Television_1080p` / `emulator-5554`, followed by one sample Victory -> lobby -> Home/resume observation. Both synthetic controllers supplied heartbeats only. No full run, physical-phone automation or full regression was performed. Local raw evidence is ignored under `.run/performance-boss-coop.log`, `.memory.txt` and `.resume.png`.

Four full windows contained 1,203 boss frames:

| Measurement | Observed range across full windows |
| --- | --- |
| Raw interval p95 | 18.59–19.21 ms |
| Raw interval p99 | 19.41–20.06 ms |
| CPU submission p95 | 2.33–3.09 ms |
| CPU submission p99 | 2.97–7.23 ms |
| Longest boss interval / CPU submission | 40.83 / 40.26 ms |
| Boss frames above 16.7 / 33.4 ms | 576 / 1 |

The existing coarse counter showed about 61 FPS while these raw intervals exposed pacing variation. This capture does **not** satisfy a strict every-frame 16.7 ms claim. The sample Victory transition had an 81.59 ms interval; its render callback CPU maximum was 2.30 ms. In this initial build, synchronous artwork preparation happened before that render callback, illustrating why both metrics are needed. The measured transition prompted the bounded asynchronous fix below; the isolated boss spike has no established cause and did not prompt a speculative simulation/renderer refactor.

| Memory observation | Boss | Active Victory | Lobby after release | Lobby after resume |
| --- | --- | --- | --- | --- |
| Accounted GPU textures | 26.31 MiB | 31.37 MiB | 26.31 MiB | 26.31 MiB |
| Accounted retained CPU pixmaps | 20.31 MiB | 25.37 MiB | 20.31 MiB | 20.31 MiB |
| Android total PSS | 75.75 MiB | 82.67 MiB | 75.25 MiB | 75.31 MiB |

Texture accounting remained below 32 MiB, and the outcome's roughly 5.06 MiB texture/pixmap pair was released when returning to the lobby. PSS returned near the end-of-boss baseline in this single observation. Startup PSS was higher (103.61 MiB); decoding/collection timing means those samples are not evidence of a long-term leak. The resumed lobby screenshot showed the QR and both connected controllers. Synthetic clients were then closed and the app restarted into an empty lobby. No fatal runtime error appeared in the captured log. Brief background/resume did not force GL-context loss, and a short capture does not establish long-run memory stability or physical-TV frame pacing.


## Asynchronous outcome preparation

The measured synchronous outcome decode/downsample was moved to one daemon worker. It holds one running decode and one replaceable latest request, without an executor backlog. Results immediately show their prewarmed title, live summary and functional controls over the existing approved crypt backdrop; the final approved outcome scene replaces that backdrop when ready. GPU texture upload remains on the render thread. Only the active outcome backdrop resides on the GPU.

Request generations reject stale Victory/Defeat completions. Returning to lobby, restarting or shutting down cancels pending work and disposes prepared/stale native pixels. Shutdown is nonblocking: a native decode already in progress completes, disposes its stale result and exits. The loader's ready-to-upload CPU pixmap bytes are included in `retainedPixmapBytes`; temporary full-resolution source decode/downsample allocations are transient and visible in process PSS, not reported as permanently retained pixels. The decoded result transfers ownership into the active outcome without keeping a second copy.

Two focused loader tests cover waiting-request coalescing, stale failure rejection, single-delivery errors, and cancellation/disposal during an in-flight decode. They use an injected failing decoder without GL/native dependencies; visual arrival and resource release are checked in the short integrated transition observation.

### Targeted outcome remeasurement

After the async fix, one eight-second results-only capture included the initial F7 transition and a rapid F7/F8/Back cancellation check. The approved Victory backdrop uploaded successfully, with the same 31.37 MiB combined GPU texture total. The first results window contained 294 frames: interval p95 22.71 ms, p99 33.63 ms, maximum 68.15 ms; CPU submission p95 8.23 ms, p99 16.76 ms, maximum 50.78 ms. The following partial window had interval maximum 52.89 ms. The rapid switch/cancel screenshot showed the lobby, rather than a stale outcome scene. No fatal Android runtime error appeared.

The previously observed outcome maximum was 81.59 ms, versus 68.15 ms in this sample. These are short emulator observations with different warm-up histories, not a controlled proof of a particular speedup. Native image decode/downsampling no longer runs on the render thread, but GPU upload/driver work and initial result rendering still occur there. The new CPU maximum includes those operations; this capture does not isolate their individual costs. The strict every-frame 16.7 ms target remains unverified and was exceeded in this emulator sample. Further optimization should follow a representative physical-TV trace rather than guessing from one emulator maximum.

The capture harness initially stopped after the useful window because a slow ADB screenshot blocked its main-thread synthetic heartbeats. Its finally cleanup still closed clients and restored an empty lobby. Heartbeats/draining now run independently on bounded per-client threads, so ADB operations do not suspend controller liveness; partial memory snapshots are saved immediately. The revised harness passed syntax validation; no additional device run was performed solely to retest that development helper. The targeted log and cancellation screenshot remain under ignored `.run/performance-results.*`. The existing 20-second boss memory observation above is the measured memory baseline; no new full performance/gameplay regression was run.
