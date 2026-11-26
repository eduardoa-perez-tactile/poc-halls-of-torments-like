# Ranger/Warden effects performance comparison — 26 November 2026

## Findings

**No sustained 30 FPS regression was observed in this crowded co-op test.**
The new effects add modest rendering work and about 0.7 MiB to observed median
process PSS. Both builds retain rare presentation gaps; this does not establish
perfectly uniform frame delivery, leak freedom or physical-TV performance.

All sixteen windows passed real-combat and complete timestamp-coverage checks.
The same party seed (1387067285), level 20, Warden/Ranger composition and peak
62 enemies were verified in every arm. Bolt, melee and secondary-ability
collision counters advanced throughout; no choice pause, crash or process
restart contaminated a measured window. Every observation and outlier is kept.

| Actual presentation, pooled raw intervals | Before | New effects |
| --- | ---: | ---: |
| Valid intervals / observed span | 4,808 / 160.267 s | 4,806 / 160.202 s |
| Weighted presentation FPS | 29.99998 | 29.99962 |
| Interval p95 / p99 | 36.404 / 38.240 ms | 35.286 / 37.109 ms |
| Maximum interval | 72.280 ms | 84.832 ms |
| Intervals over 50 ms | 7 (0.146%) | 7 (0.146%) |

The latest build's larger isolated maximum is retained. Lower pooled p95/p99
does not prove a speedup: later runs varied for both builds, including the final
baseline. The result supports maintaining the accepted near-30-FPS target with
rare gaps, rather than a new guarantee of every frame meeting 33.333 ms.

| Arm in execution order | FPS | p95 / p99 | Maximum | Gaps over 50 ms |
| --- | ---: | ---: | ---: | ---: |
| Before 1 | 29.99993 | 35.287 / 36.587 ms | 57.105 ms | 2 |
| After 1 | 29.99953 | 35.096 / 36.361 ms | 84.832 ms | 1 |
| After 2 | 29.99971 | 35.533 / 38.202 ms | 69.678 ms | 6 |
| Before 2 | 30.00003 | 37.407 / 38.644 ms | 72.280 ms | 5 |

| Same-phase post-window PSS medians | Before | New effects |
| --- | ---: | ---: |
| Total process | 69.651 MiB | 70.358 MiB |
| Native | 24.074 MiB | 24.742 MiB |
| Java | 13.164 MiB | 13.152 MiB |

Observed total PSS is 0.707 MiB higher (about 1%), mainly native. Post-window
PSS ended lower than its first post-window value in every arm; no sustained
latest-only growth pattern appeared. Warm-up-to-final memory increased in both
builds, so those transient/phase-dependent observations are also retained.
The short result does not attribute the entire native difference to one effect
or prove the absence of longer-term growth.

Accounted textures stayed exactly **32,959,308 bytes / 31.43 MiB**, and retained
pixmaps **518,988 bytes**, for every diagnostic report in both builds. Audio
remained two workers and one music handle. No texture allocation was added.

The median of reported callback p50 values rose **1.715 → 1.810 ms**; world-draw
p50 summaries rose **1.105 → 1.175 ms**. Median reported draw calls rose
**127 → 136**, while vertex uploads remained **7 → 7**. These are diagnostic
report summaries, not pooled raw-frame percentiles or GPU execution timings.
There are 28 complete baseline reports and 32 latest reports because five-second
report boundaries do not exactly align with twenty-second capture markers.
Summed diagnostic simulation steps must not be mistaken for different simulation
speed; both report about two fixed simulation steps per rendered frame.

The approved effects were retained without speculative optimization. The latest
APK was restored and its installed hash reverified. The game remains running at
Home, the saved LAN QR host and both ADB forwards are unchanged, and the phone
endpoint returned HTTP 200. Synthetic phones, samplers, log readers and the
temporary host power assertion were closed. No production gameplay/rendering
code changed during this comparison.

## Scope and method

This compares `c80bb4d` (immediately before the Ranger bolt/impact and stronger
Warden-contact update) with `b842fae` (the approved new effects). Their only
runtime source differences are three rendering files; all 108 packaged assets
are byte-identical. The earlier isometric skills/auras and the latest staged
enemy introductions are present in both builds. Gameplay is unchanged.

Both debug APKs are rebuilt/preserved in the existing checkout, without another
checkout or worktree. Baseline SHA-256:
`2be9804f3e80a6869c22a649752051e63955fa4470584ee7623dc90d6d45283b`.
Latest-effects SHA-256:
`5cffdd9a39a32dcc15f1aef415527b5d3e2d8ec7b6cfab1af13e0d2a7f8e1359`.

The existing Television_1080p emulator runs Android TV API 33 ARM64 at 1920×1080,
with four virtual CPUs, 2 GiB RAM and its existing automatic GPU configuration.
The order is before → after → after → before. Each arm starts a fresh app
process and the same default Warden/Ranger party with two heartbeat-only phones.
Movement and Dodge remain neutral, concentrating the test on crowded attacks
and impacts without controller-clock movement differences.

Existing NUM4/F12 fixtures supply full class skills, equipment and the late
crowd. Existing phone choices prime level 20 outside measurement so level-up
pauses cannot hide combat cost. A fifteen-second warm-up precedes four separate
twenty-second combat windows in each arm: **320 measured seconds total**.
Each window reapplies the same fixture before sampling, avoiding progression to
the final boss. This is repeated crowded-fixture testing, not a survival run.

The harness rejects non-session/dead/background states, incomplete timestamp
coverage, missing Ranger bolts/Warden orbiters, insufficient crowded enemies,
nonadvancing simulation/collision counters, mixed seeds or wrong party level.
It verifies the installed APK hash and unchanged saved join host/ADB forwards.

Actual SurfaceFlinger presentation timestamps are measured against 30 FPS /
33.333 ms. Raw adjacency-validated intervals are deduplicated by timestamp pair
and pooled; per-window percentiles are not averaged. Gaps over 50 ms use the
existing 1.5-target-period diagnostic threshold, not an asserted hardware scan-out
deadline. Memory snapshots and log analysis happen between cadence windows.
No video, screenshot, Perfetto trace, Gradle build or native screenshot renderer
runs during measurement. A temporary bounded host power assertion prevents
idle/display sleep without changing saved power settings.

Same-phase process PSS, Java/native PSS, threads, texture/pixmap counts and
rendering diagnostics are retained alongside raw presentation samples under
ignored `.run/effects-performance-2026-11-26/`. The capture entry point is
`scripts/effects-performance.py`.

## Limits

Debug/profile emulator results do not establish physical-TV pacing or thermals.
Normal combat advances and host scheduling can cause small workload differences
even with matched seeds and setup. PSS is an OS estimate; these short warmed
windows can reveal obvious regressions but cannot establish absence of leaks.
