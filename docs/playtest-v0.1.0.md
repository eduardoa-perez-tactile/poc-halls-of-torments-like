# Couch Crypt v0.1.0 — TV playtest

The original crypt environment and full seven-character cast are integrated into the Android TV game: Warden, Ranger, Ghoul, Wraith, Brute, Cultist and Hollow Sovereign. Warden includes the revised northeast walk and northwest sword strike. Ranger fires the approved crossbow with no added reload delay. Approved menus, HUD, icons and results remain in place.

## Build and artifacts

Release: [v0.1.0](https://github.com/eduardoa-perez-tactile/poc-halls-of-torments-like/releases/tag/v0.1.0).

- `couch-crypt-tv-playtest-v0.1.0.apk`: debug-signed Android TV playtest build, package `com.couchcrypt.tv`, version 0.1.0 / code 1, minimum Android 8.0 (API 26).
- APK: **55,207,922 bytes**, SHA-256 **24a89f003b49286aa005f8e66567beb1cb166b87cbcd627d28b6882507be5611**.
- Runtime/build source: `6aaa036`; the release preparation only adds documentation. Release metadata records the full tagged commit and checksums.
- Self-contained animation-kit HTML, seven-character preview, actual TV-emulator screenshot and short native animation clip accompany the APK.
- Canonical art/provenance ZIP, verification report and SHA256SUMS support reproduction and artifact identity. See [art integration details](gameplay-art-integration.md) and [source rebuild instructions](../art-source/README.md).

Install the APK on the intended Android TV test device using the existing deployment setup. Open Couch Crypt, connect phones to the same Wi-Fi and scan the lobby QR code. The remote controls shared menus; phones control movement, Dodge and personal choices. This is a development playtest package, with existing debug fixtures retained, rather than a store release.

## Try on the TV

1. Start solo as Warden, then Ranger. Check diagonal walking, moving attacks, Dodge and silhouette readability at normal couch distance.
2. Invite Player 2 from Pause; check independent cameras, roots/shadows and floor coverage near the arena edges. Try a phone disconnect and reconnect.
3. Resolve skill and equipment choices on phones. Confirm combat freezes and both players resume together after shared skill choices.
4. Watch the distinct enemies and boss cues during a normal run. Check damage feedback, revival, menu focus, results and Run Details.
5. Report stutter, crowd readability, animation defects and balance observations. A full twenty-minute run and physical-TV performance remain user playtests.

## Evidence and limits

The JDK 21 offline Android build and **55 focused tests** passed. Short checks on the existing 1920×1080 ARM64 Android TV API 33 emulator covered solo/co-op rendering, remote navigation, phone choices and pause, fresh late-join catch-up, ordinary Home/resume and result fixtures. Result fixtures are presentation checks, not proof of winning a complete run. Forced GL-context loss was not exercised.

The three managed gameplay pages total **6 MiB** and retain no decoded gameplay Pixmaps. Actual accounted resident textures including the QR reached **30.8074455 MiB**, leaving **1.1925545 MiB** below the 32 MiB limit. Existing application resources still account for 19.3074455 MiB of retained decoded Pixmaps.

A final seven-second co-op boss fixture sampled 301 frames: frame-interval p95/p99/max **19.10/20.99/22.56 ms**, CPU-submission **5.71/9.99/12.93 ms**, 159 intervals over 16.7 ms and none over 33.4 ms. The reported frame used 56 draws. The displayed-frame 60 FPS target was exceeded in this emulator window; sustained physical-TV performance and balance remain unproven. See [performance notes](performance.md).

Minor cloth occlusion and pose/costume variation remain in generated art. One earlier emulator/relay session terminated without an app fatal exception recorded; cause was not established. Subsequent checks and the final measurement completed. No tools, packages, SDKs, browsers or dependencies were installed or upgraded.
