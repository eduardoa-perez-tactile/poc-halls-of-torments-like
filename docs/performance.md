# Lobby performance

## Targets and design

Target 60 FPS with a 16.7 ms frame budget and low memory usage. This milestone has a static painted background, two regions of one portrait texture, a shared font atlas, and a small QR texture. There are no gameplay entities, post-processing effects, multisampling, mipmaps, or offscreen framebuffers. Audio is disabled until it is implemented.

The render loop does not allocate layouts, read files, generate art, or send network data. Lobby snapshots are sampled twice per second; labels change only when their values change. The heartbeat runs on a daemon thread, and host removal queues its socket messages away from the render thread. The QR is generated only when entering the lobby, changing its advertised address, or restoring the app.

Art/font textures are budgeted below 32 MiB. The initial assets use approximately 16.01 MiB, plus 0.49 MiB for a 360 x 360 QR. GPU texture byte estimates exclude driver overhead. Fonts retain their small CPU atlas for Android GL context recovery; file-backed artwork is restored from assets.

## Brief initial measurement — 2026-11-12

Device: Television_1080p, Android 33 ARM64, 1920 x 1080, software SwiftShader rendering on the development Mac. Debug APK; no long playthrough or regression exercise.

- Frame counter samples: mostly 60–61 FPS, with some 56–57 FPS readings while development work was active. Consistent 60 FPS is not yet proven.
- Static home: 9 SpriteBatch draw calls; lobby: 19.
- Live Java heap samples: approximately 4–9 MiB.
- Android dumpsys meminfo after entering the lobby: total PSS 55,555 KiB (~54.3 MiB); total RSS 153,664 KiB (~150.1 MiB). PSS/RSS include platform/native costs and are not interchangeable with Java heap or art texture bytes.
- The headless software emulator reports zero graphics PSS; this does not mean textures cost no memory. Use the resource estimate above and actual hardware graphics accounting when available.

These are observations from a short foundation check. They do not establish an all-devices frame-rate or memory guarantee. Profile a representative physical TV with hardware rendering before accepting the sustained 60 FPS requirement; gameplay will need its own bounded entity/effect budgets in the next milestone.

## Recheck only when relevant

The subsequent UI art pass adds two baked textures (512 x 512 and 1024 x 224), totaling 1.875 MiB of GPU data and 1.875 MiB of retained CPU pixmaps. Large painted UI source pixmaps are freed after the one-time startup bake. The estimated art/font texture total is approximately 17.88 MiB plus QR, within the 32 MiB budget. A static gold focus rim adds one small draw layer, with no animation or post-processing.

Run `python3 scripts/tv-dev.py --profile`, inspect a few LobbyPerf log lines and one dumpsys meminfo snapshot. F3 displays the same frame/resource summary on desktop. No prolonged automated playtesting is required for the static lobby.

## Approved Home menu

The approved shared theme uses a 1536 x 864 background, 1536 x 384 transparent wordmark, two 1024 x 224 button states, and one 512 x 512 panel. Home, lobby, pause, and dialogs reuse these textures through appropriately scaled nine-slices. The former background and blue/iron UI skins are no longer loaded. Including portraits and the font atlas, the measured art texture footprint is 20.0656 MiB before the QR. Small managed textures retain their CPU pixmaps for Android context recovery. Large source pixmaps are released immediately after baking. A startup check rejects art above the 32 MiB GPU texture budget.

Navigation reuses cached backgrounds and styles. Focus uses a separate prepainted gold/cream plate with the same crop and geometry as the normal plate; no framebuffer, glow pass, particle system, or per-frame texture generation is introduced. Buttons and labels remain functional Scene2D controls. Phones receive PNGs encoded once from the small baked textures, including a 512 x 128 logo and 640 x 360 background, avoiding retention/download of full-size paintings. Only a brief visual/navigation check is needed for this art/layout change; the existing joining protocol is unchanged.

Brief software-emulator frame samples during this pass ranged from 55 to 61 FPS; a steady 60 FPS guarantee on physical TVs remains unverified. The Home screen used 11 SpriteBatch draw calls. These observations are not a long gameplay benchmark.
