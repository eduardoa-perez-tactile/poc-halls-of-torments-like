# Couch Crypt v0.5.0 playtest

Android TV development playtest, package `com.couchcrypt.tv`, version **0.5.0**,
Android version code **5**, minimum Android 8.0 / API 26. The debug-signed APK
updates the existing playtest while preserving app preferences.

Download from the [v0.5.0 release](https://github.com/eduardoa-perez-tactile/poc-halls-of-torments-like/releases/tag/v0.5.0):
`couch-crypt-tv-playtest-v0.5.0.apk`. The release includes `SHA256SUMS`, audio
credits and build verification metadata.

## Changes since v0.4.0

- Warden's primary slash becomes the approved original isometric ivory/gold
  crescent wave. It expands through the existing 110-degree cone to the actual
  skill/equipment-adjusted attack reach in 240 ms and fades by 380 ms. Damage,
  critical/frost contact, sound and knockback follow wave contact. Damage values,
  attack cadence and separate secondary abilities retain their existing rules.
- Mourning Bell arrives at 5:00 elapsed combat time: a floating bone-shell creature
  with an amber eye, warned locked lunge and expanding toll. Below half health,
  its two tolls have separate warnings. Sovereign still arrives at 12:00 and
  Grave King at 20:00; only Grave King death wins the run.
- Four painted bonded-stone corner turns repair perimeter joins, and a separate
  original epic north gate fits the adjoining wall wings. Movement remains open;
  the scenery adds no collision obstacles.

The full v0.4.0 survivor expansion, three-card phone skill choices, eight collected
chest opportunities, solo/co-op, reconnect, late joining and remote-owned shared
menus remain included. The sprite-wave replacement adds no GPU texture storage,
and combined accounted textures remain within the 32 MiB ceiling.

## Checks and limits

The final implementation passed **151 focused checks across 17 classes**, covering
wave contact/range/cone, critical and frost state, snapshots, pool reuse, haste,
co-op ownership, choice freezes, reconnect/reset, feedback, equipment/chests,
boss deaths and progression. The approved sprite bank rebuilds exactly from the
tracked sources. Earlier Bell and scenery milestones have their own focused
checks and native captures documented in [boss encounters](boss-encounters.md)
and [painted borders](painted-arena-borders.md).

An eight-second actual APK recording in a separate headless Android TV API 33
ARM64 emulator checked the wave at base and upgraded reach. The user approved
the result. That temporary emulator and controller were removed; the TV agent's
active emulator, forwarding and saved preferences were preserved.

Release preparation changes version metadata and documentation only. JDK 21
built the desktop classes and Android APK. Packaging verification passed for
version 0.5.0/code 5, the APK signature, the unchanged v0.4.0 upgrade signing
certificate and all 108 tracked runtime asset files.
The release APK is not installed onto the occupied TV emulator. No full survival
run or sustained physical-TV performance result is claimed. Software headless
capture does not establish 60 FPS on a physical TV.

## User playtest

Install the APK on the intended TV when available. Open Play using the remote
and scan its QR with phones on the same Wi-Fi. Check Warden wave readability at
base and upgraded reach, then try Mourning Bell's lunge/tolls at five minutes.
Assess solo/co-op difficulty, later boss duration and sustained TV frame pacing
during normal play.
