# Couch Crypt v0.6.0 playtest

Android TV development playtest. Use the debug-signed `couch-crypt-tv-playtest-v0.6.0.apk` to update the existing playtest. Package: `com.couchcrypt.tv`; version **0.6.0**, Android version code **6**, minimum Android 8.0 / API 26.

Download from the [v0.6.0 release](https://github.com/eduardoa-perez-tactile/poc-halls-of-torments-like/releases/tag/v0.6.0). The release includes the APK, SHA-256 checksums, audio credits and build-verification metadata.

## Changes since v0.5.0

- **Portrait phone controls:** the top 75% of the screen handles movement; the bottom 25% is a rectangular Dodge button. A fresh movement touch places the stick under your finger. Turning the phone sideways shows an upright-phone prompt and neutralizes input.
- **Menu polish:** a stacked pause menu with a centered heading and proportionally scaled painted buttons. Results actions also preserve their painted proportions.
- **30 FPS rendering target:** display-paced Android rendering retains the 60 Hz simulation. Cached floor/range geometry, bounded sprite uploads, reusable GLES2 vertex storage and audio workers reduce render-thread work without changing attack timing.
- **A gentler opening:** lower early reinforcement pressure and fewer early Wraiths. Original enemies appear first; colour/stat variants unlock gradually from 4:00 through 16:00. Boss/elite times, chest opportunities and equipment sustain stay unchanged.
- **Clearer attacks:** Sentinel Blades and Returning Glaive use isometric projection and short motion trails. Active ground auras are brighter; collectible relics have grounded lighting. Ranger bolts are slimmer with distinct release/contact flashes, while Warden contact hits regain a broader ivory/gold cut and short sparks.
- **Shorter Ranger targeting:** Ranger now acquires primary-attack targets within **300 world units**, and the ground outline matches. Already released bolts retain their normal flight, speed, damage, piercing and ricochet rules.

Solo/co-op, phone skill/equipment choices, reconnect, late joining and remote-controlled shared menus remain included. Existing art is reused; the effects add no texture allocation.

## Checks and limits

The latest 300-unit Ranger change passed **20 focused headless checks** and JDK 21 Android debug assembly. That final range change has **not been installed or tested on a device/emulator**. Release packaging checks are recorded separately in the accompanying build-verification metadata.

Release preparation passed JDK 21 desktop compilation and Android debug assembly,
controller JavaScript syntax checking, package/version/signature verification,
and byte-for-byte checks of all 108 tracked runtime assets. The signing
certificate matches the published v0.5.0 APK, preserving the playtest upgrade
path. Four native ABIs are packaged: ARM64, ARMv7, x86 and x86_64.

The APK is **66,636,765 bytes**. Its SHA-256 is
`cafe108c374974fdcf5295c94717ab3c4ce2c4e6dc594b8629c41ccb7c1b1fcb`.
The built runtime/version source is commit
`99650148cd08b739793265a5d0cc73588dbd3145`; later release commits add documentation
and merge history. No device/emulator was accessed during release preparation.

Earlier effects builds completed a 320-second crowded co-op before/after comparison, maintaining approximately 30 FPS with seven isolated intervals over 50 ms in each build. Observed median process PSS was about 0.7 MiB higher with the new effects; texture memory was unchanged. That comparison predates the final Ranger range reduction and is not a performance certification of this release. Rare stutters, long-run memory behavior and physical-TV pacing/thermals remain playtest concerns.

No full survival playthrough or new device test is claimed for release preparation. Detailed evidence is in `docs/effects-performance-comparison.md`, `docs/buffer-upload-fix.md` and `docs/combat-vfx.md`.

## Try in this playtest

Check portrait movement/Dodge on a phone, then assess Ranger's shorter engagement distance and both heroes' attack readability. Play the opening minutes to judge reinforcement pressure and the gradual introduction of variants. On a physical TV, watch crowded co-op pacing and memory/thermal behavior over longer sessions.
