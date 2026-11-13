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


## First playable slice — current measurement

Gameplay adds a 1024x512 managed actor/prop/FX atlas and 512x512 floor texture, exactly 3 MiB. Combined resident art/font texture data is 23.0656 MiB plus QR, checked against a 32 MiB startup budget. Matching small CPU pixmaps are retained for GL context recovery; large source masters are decoded only during startup and disposed immediately after baking.

Simulation uses fixed pools capped at 64 enemies, 96 bolts and 96 pickups, with a fixed 60 Hz step and bounded catch-up. World rendering uses culled floor diamonds, one actor atlas, no framebuffers/post-processing and one or two scissored cameras. HUD text refreshes at 5 Hz; reusable controller samples avoid render-loop input allocation. Networking and status broadcasts remain outside the render thread.

A brief debug APK check on the visible Android 33 ARM64 emulator with Apple M2 Max OpenGL translation observed 59–61 FPS, about 5 MiB live Java heap after collection, and 8 combined draw calls in solo combat. This was a short launch/movement/dodge/late-join/split-screen check, not a full survival playthrough or maximum-enemy benchmark. Sustained 60 FPS and total process memory on representative physical TVs remain unverified. Gameplay art is temporary per user direction.


## Initial framed HUD measurement

The ornate vitals, timer crest, optional boss strip, and notification cards reuse the existing UI paintings, portraits, atlas pixels and font textures. No new textures or framebuffers are allocated. Combined resident texture estimate remains 23.0656 MiB plus QR. Text layouts are cached, stat strings refresh at 5 Hz, and the notification queue reuses three fixed objects. New notices arise only on gameplay transitions, not on every frame.

A brief visual check of the debug APK on the same visible hardware-translated TV emulator observed 60–61 FPS, 21 combined draw calls with the boss preview visible (25 while a notification was also visible), and approximately 5–7 MiB live Java heap after startup collection. This is visual/layout evidence, not a maximum-load or sustained physical-TV benchmark. The four focused HUD-state tests cover queue overflow, duplicate suppression, timing, and boss-data reset/clamping. No full survival playthrough or broad regression run was performed.


## Compact unboxed HUD

The latest layout removes painted backplates, timer slabs, portraits, duplicate class labels, and notification paragraphs. Small text and thin rails reuse the same pixel/font atlas resources; the resident texture estimate remains unchanged. Boss and notification presentation stay conditional and bounded. Layouts/stat strings remain cached. This is a visual-only follow-up: desktop/APK compilation and one brief rendering check are appropriate; no new unit tests or regression playthrough are required.


## Shared XP and personal upgrades

Progression and per-player offers use fixed objects and three enum references per offer; level entitlement queues are bounded by the level cap (20). No textures or render targets are added. XP/offer presentation copies at 5 Hz; WebSocket serialization remains on server threads. Countdown resolution and upgrade effects happen in the fixed simulation tick, and no tick advances during manual pause/background/disconnect. A short live check used the profiling-only F5 XP grant to show simultaneous level-ups and an actual phone selection while the other player's offer remained open; no full survival playthrough was performed. Focused tests cover progression, real stat effects, timeout/default, late joining, replay/ownership checks, and the real upgrade WebSocket message.


## Complete one-arena combat

The wave director, melee cone, arrows, two secondary attacks and final boss add no resident textures. New fixed pools hold 96 hostile shots, 12 warnings and 96 visual effects. Ground rings, sword arcs and lightning reuse the existing atlas pixels and are culled per camera; there are no new framebuffers or post-processing passes. Eight original mono PCM cues total 133,970 bytes on disk and are generated offline, loaded once, then rate-limited during playback.

Thirty focused combat/progression tests passed (ten simulation boundary tests, nine combat fixtures and eleven progression tests). Desktop compilation and the debug APK build passed. A brief emulator fixture verified hero selection, split rendering, the live boss bar and encounter sprites. Samples during the visible emulator check were 60–61 FPS with about 7–8 MiB Java heap and the unchanged 23.0656 MiB art estimate, excluding QR and platform/audio/driver overhead. No full three-minute survival playthrough or sustained physical-TV benchmark was run. The replacement outcome concepts were subsequently approved and layered for runtime.


## Approved outcome artwork and Run Details

Both 1024x256 outcome titles (2 MiB) and the 512x384 skill atlas (0.75 MiB) are prepared once during startup. Alpha scanning, icon recentering and normalization do not run when opening Run Details. Only the active 1536x864 outcome scene is loaded on entry, adding 5.0625 MiB; it is disposed on replay/lobby. The UI/game/title/icon baseline is 25.8156 MiB and the outcome total 30.8781 MiB, or about 31.3725 MiB including the 0.49 MiB QR. Source paintings are released after preparation, and retained small pixmaps support managed GL restoration.

Results skip world/camera rendering. Live summary controls and the unboxed hero/build view use shared fonts, textures and menu styles; no framebuffers or per-frame image processing are introduced. A brief TV check verified both outcome layouts, the new illustrated details view and Back/focus behavior using explicit sample-data fixtures. These screenshots verify UI presentation, not a completed survival playthrough.

Visible-emulator frame samples were mostly 60–61 FPS, with occasional 51–59 FPS readings during transitions/development activity. Shared title/icon preparation removes known Java alpha-scan work from those transitions, but hitch-free transitions and sustained 60 FPS on representative physical TVs remain unverified. The latest results estimate stays below the combined 32 MiB art budget.
