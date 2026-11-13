# Menu orb motes and background dust

User-approved addition on 2026-11-13: a few simple particles around the soul orbs and sparse drifting dust in the crypt. The approved depth/material painting, menu layouts, titles, buttons and panels are unchanged. This supersedes the initial static pass's no-particle description; fire and fog remain excluded.

## Implementation

- A fixed pool of 24 dust points and six motes per orb (36 total), with deterministic startup variation.
- Dust drifts slowly with gentle sway and fades fully before wrapping. Small gold motes follow slow local orbits without trails or fire-like motion.
- Dust renders over the room but behind the foreground guardians; orb motes render over the light devices. All effects remain behind real menu UI and inherit its existing dimming.
- A 32 × 32 code-generated soft white point occupies unused space at `(260,772)` in the existing 512 × 1024 atlas. No new texture, external bitmap, shader, framebuffer, image-generation call or blend-mode switch is needed.
- Particle state is preallocated. The frame loop changes numeric state and draws the shared region; it does not allocate objects or rebuild geometry/layout.
- The existing visible-menu/host-active gate stops updates when gameplay hides the crypt or the app is backgrounded. Phones retain their static composed backdrop; this is TV menu ambience, not a combat effect or phone animation.

The shared scene remains 7.0625 MiB GPU and the same retained managed pixels. Additional particle state is a small fixed Java allocation, independent of elapsed time. Full-resolution painting sources are still freed after startup baking.

## Verification

All three focused helper tests passed: 600 simulated seconds exercise multiple invisible dust wraps and continuous bounded orbits; timestep partitioning, invalid/paused/large deltas, inherited color/alpha, scene scaling and untouched blend state are covered without GL. JDK 21 core tests, desktop compilation and Android debug packaging passed. After the initial rendering check, soft point sizes were increased slightly for full-HD visibility (orb motes 8–10 logical pixels, regular dust 3.8–5.4, four softer foreground flecks 6.5–8.1); the focused tests and Android build passed again.

Brief checks on Television_1080p (Android 33 ARM64) inspected Home and Lobby/QR. An eight-second screen recording and two actual PNG frames show changing particles while the scene/UI remain fixed; the frame comparison found 119 changed pixels above a 12-channel-value threshold outside the two orb-body regions. This is a small visible effect, not an animated painting or fog layer. No gameplay playthrough was performed.

Texture dimensions and accounting remain unchanged: 32,836,820 GPU bytes (31.32 MiB) without QR, with the normal 360×360 QR adding 518,400 bytes (31.81 MiB combined). Home remains 12 draw calls; all particle sprites share the existing atlas and blend mode. Retained pixmaps remain unchanged. The new particle state is bounded Java storage.

One pre-recording complete Home window contained 291 frames: interval p95/p99/max 26.55/36.12/56.28 ms; CPU submission p95/p99/max 12.70/27.04/34.50 ms, with 143 intervals over 16.7 ms and four over 33.4 ms. Other short windows varied substantially; this capture does not isolate particle cost or establish a performance comparison. Video-encoding work is excluded from that window. The emulator exceeded the strict frame target; sustained 60 FPS and physical-TV performance remain unverified.

Ignored local evidence: `.run/menu-ambient-vfx.mp4`, `.run/menu-ambient-review-home.png`, `.run/menu-ambient-review-after.png`, `.run/menu-ambient-lobby.png` and `.run/menu-ambient-*.log`. Temporary on-device recordings were removed. No synthetic controllers or temporary background helpers remain. The app was restarted on Home without profiling; the existing emulator/relay remain available for review.
