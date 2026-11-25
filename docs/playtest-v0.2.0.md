# Couch Crypt v0.2.0 — arena playtest

This playtest integrates the current twenty-minute arena, formal crypt layout,
enemy variants and threat markers, larger combat HUD, compact weapon feedback,
glowing gold/teal foot rings, shared outer co-op iris and smooth Defeat transition.
Hollow Sovereign arrives at 12:00 while waves continue; Grave King arrives at
20:00, and killing the Grave King grants victory. Personal phone choices remain
three skill cards or two equipment cards, with shared thirty-second party rounds.

## Install

Download the APK from the [v0.2.0 release](https://github.com/eduardoa-perez-tactile/poc-halls-of-torments-like/releases/tag/v0.2.0).

- APK: `couch-crypt-tv-playtest-v0.2.0.apk`, debug signed, 59,112,272 bytes.
- Package: `com.couchcrypt.tv`; version **0.2.0**, Android version code **2**.
- Minimum Android: 8.0 / API 26. Install over the previous playtest with `adb install -r`.
- SHA-256: `0a5f529f66deb2684254d2e555a72cbf787401b0d3079401636fafc5de2943dc`.
- Build source: `602422ed8817fde908a6a860f94e5e94e090dab3`. Subsequent release commits add handoff documentation only.
- Release assets include native Home/lobby/co-op screenshots, verification metadata
  with the tagged commit, and `SHA256SUMS`. Original art/provenance remains in the tagged repository under `art-source/`.

Open Couch Crypt using the TV remote. Choose Play, then scan the QR with phones
on the same Wi-Fi. One phone enables solo; two enable co-op. The remote owns
shared menus; phones control movement, Dodge and personal skill/equipment choices.

## Checked for this release

JDK 21 built desktop classes and the Android debug APK offline. Android signature
verification passed, and the APK installed successfully over the existing playtest
on Television_1080p: Android 13 / API 33, ARM64, 1920×1080. Installed version
metadata confirms 0.2.0 / code 2.

Brief native smoke checks covered Home, lobby/QR, remote navigation into co-op,
two synthetic WebSocket controllers sending movement and Dodge, and the equipped
late-run/final-boss rendering fixture. The latest gold/teal rings, enemy palettes
and markers, split cameras, HUD and Grave King strip rendered. No fatal Android
exception appeared in the retained capture. Synthetic controllers and capture
processes were closed afterward.

The lobby screenshot QR was decoded to the existing computer LAN endpoint;
the LAN controller endpoint responded successfully, saved join settings were
preserved, and existing ADB forwards were unchanged. The app was restored to a
normal, nonprofiling lobby for handoff. The emulator and Couch Crypt LAN relay
remain available for phone joining; the separate game's relay was left intact.

The pre-release assessment also passed 33 focused Java tests for boss encounters,
difficulty integration, Defeat timing, late joining and skill/loot pauses, plus
controller JavaScript syntax and skill/equipment protocol checks. Release
preparation changes version metadata and documentation, with no gameplay changes.

## Performance evidence and limits

Accounted textures including the live QR remained **33,221,452 bytes / 31.6824 MiB**,
below the 32 MiB texture ceiling. Application-wide retained decoded Pixmaps were
518,988 bytes; the gameplay bank retained none. These counts do not represent
total process or driver memory.

One 301-frame co-op boss report adjacent to the five-second observation window
recorded interval p95/p99/max **18.22/19.09/19.73 ms** and render-callback wall
p95/p99/max **9.26/11.07/13.25 ms**. Of 300 intervals, 142 exceeded 16.7 ms and
none exceeded 33.4 ms. The report straddled measurement markers, so it is a short
smoke observation rather than a controlled performance baseline. GPU timing is
unmeasured. Sustained physical-TV 60 FPS remains unproven.

No full survival run, physical-phone/browser usability check, physical-TV test,
forced GL-context-loss check or balance certification was performed for this
release. Profile fixtures skip survival time and do not demonstrate earning a
complete build or winning a normal run.

## User playtest priorities

1. Play complete solo runs as Warden and Ranger. Note first upgrade timing, party
   level at major checkpoints, death time, equipment/build and healing availability.
2. Play co-op using two real phones. Try Invite Player 2, revival, a disconnect
   and reconnect, then skill/equipment choices and automatic party resume.
3. Assess Sovereign pressure at 12:00 and Grave King duration after 20:00. Record
   difficulty, warning readability, and whether either fight feels excessively long.
4. Check HUD/enemy readability from the couch, crowd stutter, Defeat reveal,
   Run Details and replay. Report device model and the situation when stutter occurs.

The existing three-minute timeline and profiling shortcuts remain development
fixtures. Multiple arenas and persistent progression remain outside this release.
