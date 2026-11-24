# Sovereign mini boss and Grave King final encounter

The approved 24 November 2026 encounter sequence is Hollow Sovereign at **720
elapsed combat seconds**, then Grave King at **1200 seconds**. The three-minute
development fixture scales the mini checkpoint to 108 seconds. Both spawn once
using the existing safe-distance search (at least 300 world units from each
living player), a short existing arrival effect/sound and 1.5-second initial
attack cooldown. Unsafe required spawns retry; a full pool may replace an
ordinary reinforcement, never another boss or an elite. There is no cutscene,
portal, summon condition or dependency on killing the mini boss first.

The Sovereign retains its original artwork and combined ground-warning/volley
attack, with 3,000 health solo or 4,800 with two participating players. Waves,
elite checkpoints, the arena clock and reliquaries continue during this fight.
Its death grants souls, does not grant another equipment offer and does not end
the run. The existing five elite plus three reliquary equipment opportunities
remain intact.

The Grave King starts at 8,200 health solo or 13,120 co-op. It alternates a
one-second ground strike warning with a locked-direction, 0.7-second windup and
slow five-projectile spread. At half health, its recovery shortens and the spread
widens to seven projectiles. It uses the existing pools, ground warning sprites,
missiles, damage paths and dodge invulnerability. Boss health scaling is captured
at each boss's spawn. Late joining does not refill or rescale either boss.

If the Sovereign survives to 20:00, both remain active with independent attack
and phase state. The existing top boss strip prioritizes the Grave King; after a
mini-only fight ends it restores the survival countdown. Victory requires the
Grave King kill. Results name the correct encounter and show remaining health on
defeat. All existing choice/manual/background/disconnect freezes apply to
combat, casts and visual hover. The skull has a ground-root hitbox and ordinary
targeting, so Warden melee and Ranger projectiles can hit it normally.

The new sprites and the approved proposal are documented in
[Grave King art provenance](../art-source/grave-king/README.md). The new file-backed
managed 512x256 page adds 524,288 GPU bytes. Removing the unreferenced legacy
640x320 full-body portrait GPU load saves 819,200 bytes; the original PNG remains
preserved. Net residency decreases by 294,912 bytes. Original gameplay pixels,
frame indices, borders, floor materials, hero portraits and menu painting stay
unchanged. Floor/border storage keeps its original page indices despite the new
fourth actor page. The actor bank is now 6.5 MiB; combined application residency
remains the governing 32 MiB ceiling.

Profiling shortcuts: F6 starts the final encounter without creating a historical
mini; M (or desktop Shift+F6) starts the mini. Neither is a player-facing mode. These fixtures
avoid a full survival playthrough. Human solo/co-op playtests still own encounter
feel, health tuning, visual scale and sustained physical-TV performance.

## Verification

Homebrew JDK 21 built desktop classes and the Android debug APK offline. One
hundred focused checks passed across encounter deadlines/independence, safe/full
pools, attacks, animation, audio, results, skill/loot pauses, atlas metadata and
the directly affected floor/border page integrations. A stale reconnect test was
corrected to inspect the approved two equipment cards rather than a third card.
The Grave King baker's `--check` also passes and verifies old entries/pixels.

The installed APK ran on the existing Television_1080p Android 33 ARM64 emulator
at 1920x1080. Short solo fixtures showed both original Sovereign and painted
Grave King with their live names; a five-second moving co-op/final-boss fixture
exercised split cameras, warnings and projectiles. Its 301-frame report measured
**33,221,452 accounted texture bytes (31.6824 MiB)** including the live QR, with
518,988 retained decoded Pixmap bytes application-wide and zero in the gameplay
bank. Frame interval p95/p99/max was 18.10/18.79/22.08 ms, with 154 of 300 intervals
above 16.7 ms and none above 33.4 ms. GPU timing is unmeasured. This is not a
sustained 60 FPS pass or a balance claim. No full survival playthrough was run.
Local native evidence is under ignored `.run/grave-king-check/`; temporary phones
were closed and the emulator returned to the lobby.
