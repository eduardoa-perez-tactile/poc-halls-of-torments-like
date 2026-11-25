# Couch Crypt v0.3.0 — audio playtest

This release adds the audio foundation, **27 sampled sound effects** and
**six background-music tracks** to v0.2.0. Menus, ordinary combat, both bosses
and outcomes have their own music. Combat, progression, loot, down/revive and
remote navigation have short material-based effects. Combat rules, difficulty,
controls, arena artwork and progression are unchanged.

## Install

Download the APK from the [v0.3.0 release](https://github.com/eduardoa-perez-tactile/poc-halls-of-torments-like/releases/tag/v0.3.0).

- APK: `couch-crypt-tv-playtest-v0.3.0.apk`, debug signed, **65,763,589 bytes**.
- Package: `com.couchcrypt.tv`; version **0.3.0**, Android version code **3**.
- Minimum Android: 8.0 / API 26. Install over the existing playtest with `adb install -r`.
- SHA-256: `19cbe283ca47a3e55946fe1d5324936afeca340ffa53119f21451f2c436ce225`.
- Build source: `33666aee982191bd5d38a63a4c24e9297091a293`. Subsequent release commits may add handoff documentation only.

Use the TV remote to open Play and scan the lobby QR with phones on the same
Wi-Fi. Existing solo/co-op, late joining and phone skill/equipment choices work
as before. Audio comes from the TV; phone controllers remain silent.

## Audio controls and credits

**Home Settings -> Audio** and **Pause -> Audio** provide saved Master, Music
and Effects levels. Activate a row to advance in 25% steps; 100% wraps to mute.
Defaults are Master 100%, Music 65%, Effects 100%. A previously saved Sound-off
setting becomes master mute, so raise Master if upgrading a muted installation.

Music fades between scenes with one streamed track at a time. Manual pause,
controller-disconnect pause, Invite and backgrounding preserve its position.
Phone choices lower music to 25% of the configured level. Victory/Defeat music
plays once and stays finished while browsing Run Details.

These are edited **third-party CC0** compositions and samples, with authors,
source links, licenses, hashes and reproducible bake recipes under
[`audio-source/`](../audio-source/). Credits also ship in
`assets/audio/CREDITS.txt`. See [audio details](audio.md) for composer credits,
effect sources and resource limits. No new GPU textures are required;
audio decoding/mixer memory is separate from the texture budget.

## Checks and limits

The audio implementation already passed **24 focused Java tests** covering
event routing, bounded voices, settings/mute migration, music fades, lifecycle,
fallbacks and one-shot completion. Those tests are not repeated solely for
release metadata changes. Source/output checks verified effect PCM, actual
catalog durations, formats, non-silence, headroom and endpoints, plus all six
decoded music files and their export receipts.

For this release, JDK 21 built desktop classes and the Android debug APK offline.
APK signature verification passed. All 34 audio/credit entries matched the
checked assets byte-for-byte. The installed APK hash matches the published
artifact; installed metadata confirms version 0.3.0 / code 3.

The release APK launched on Television_1080p (Android 13 / API 33, ARM64,
1920×1080). Brief remote navigation covered Home, Audio settings and the lobby.
Android reported native music and UI-effects playback; no app audio/fatal errors
were recorded. The lobby screenshot QR decoded to the existing LAN endpoint,
which responded successfully. Saved join settings and the existing relay/ADB
forward were preserved. Handoff leaves the emulator running in the normal,
nonprofiling lobby with no synthetic controllers attached.

The implementation's earlier native check also observed music pausing/resuming
through backgrounding. Playback state is not an audible listening review.
No fresh full survival/co-op run, physical-TV mix review or sustained performance
result is claimed for this audio release.

## User playtest priorities

1. Begin at a comfortable TV volume. Compare music, menu clicks, weapon attacks,
   player damage and announcements; try Master/Music/Effects independently and
   confirm saved levels after restarting the app.
2. Check repeated Warden sword and Ranger arrow/contact sounds during normal
   play. Report tiring repetition, harshness, masking or unexpectedly loud cues.
3. Listen through Home/lobby, combat, Sovereign and Grave King transitions, then
   Victory/Defeat and Run Details. Note abrupt cuts, gaps, inappropriate loops
   or an outcome track restarting while browsing.
4. During skill and gear choices, check the quieter music and its return after
   the party finishes. Try manual Pause, Audio settings, Invite Player 2 and
   background/return; music should preserve position and the run stay paused.
5. Disconnect and reconnect a participating phone. Check that combat audio stops,
   the run waits for host resume and no old effects burst on resuming. Report
   the TV model and volume settings with any audio or stutter issue.
