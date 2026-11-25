# Mourning Bell, Sovereign and Grave King encounters

The 25 November 2026 addition is **the Mourning Bell at 300 elapsed combat seconds**,
before the existing Sovereign at 720 and Grave King at 1200. Waves and the combat
clock continue through both mini-boss fights. Only the Grave King kill wins the run.

## Early encounter: Mourning Bell

The approved creature is a hovering bone-shell crypt predator with one amber eye
and a large fanged maw. Its original literal-bell concept and frontal production
sheet were rejected. Runtime uses four elevated diagonal facings, idle/open-jaw
poses and combat-owned hover, modest tilt and recoil. Source, prompts and rejection
history are retained in [Mourning Bell provenance](../art-source/mourning-bell/README.md).

The Bell has 1,800 HP solo / 2,880 with two participants at spawn and moves at
70 world units/second. Late joining does not refill or rescale it. These are initial
tuning values; a 35–50-second fight is a playtest target, not a verified duration.
Its alternating attacks have no passive contact damage:

- **Lunge:** lock facing and a bounded lane, warn for 0.85 seconds, travel up to
  420 units over 0.4 seconds, then recover for 0.85 seconds. Swept body collision
  deals 16 base damage once per player. Dodge crossings consume that strike.
- **Toll:** warn for 0.9 seconds, then expand a thin annular wave from 20 to
  260 world units over 0.75 seconds, dealing 14 base damage once per player per
  beat. Below half health, the committed cast adds a second, separately warned
  wave. Standing inside the wave after it passes is safe. Ground warnings and
  waves draw below complete actors in both cameras.

Arrival retries unsafe/full-pool placement using the existing 300-unit safe spawn
rule; it can reclaim an ordinary reinforcement, never another boss or an elite.
Death grants 12 base souls and no equipment chest. All five elite and three
discoverable arena chest opportunities remain unchanged. HUD priority is Grave King,
then Sovereign, then Bell when encounters overlap; surviving encounters keep their
own health and attack state. Choice/manual/disconnect/background pauses freeze the
encounter, animation and warning clocks together. Reset clears all Bell state.

The short development timeline places the Bell at 45 seconds with 420 base HP.
Profile-only key `5` opens a short early-boss fixture; no five-minute automated
survival session is needed for rendering checks.

### Verification of the Bell addition

JDK 21 compiled desktop classes and the debug Android APK. The integrated
combat/timeline/animation/art/audio/results/font/resource group passed 77 checks;
after the native rendering correction and final size adjustment, the 15 affected
Bell/size checks passed, including a new co-op wave-distance check. The Bell
atlas `--check` passes, preserving the old pages and metadata. The offline UI bake
verifies original glyph pixels, metrics, kerning and 40 real font layouts; an
independent before/after comparison confirms unchanged retained font RGBA and
glyph coordinates, with only empty bottom rows removed.

Six brief actual LWJGL renderer captures exercise solo idle, lunge warning,
lunge travel, toll warning, expanding toll and co-op second toll. All return GL
error zero (22–42 draw calls in these fixtures). A native-only attack culling
lookup initially exposed the absent legacy attack-art entry; culling now checks
that bank's actor support before querying its bounds. Final captures use the
smaller 1.7 Bell visual scale, preserving its 54-unit collision radius.

Native accounting confirms 32,959,308 texture bytes including maximum QR and zero
retained gameplay Pixmap bytes. The APK installed successfully on the existing
TV emulator. Further emulator interaction was stopped at the user's request
because another game shares it; no Android combat/performance pass is claimed.
Temporary synthetic phones and the newly created Couch Crypt relay were stopped;
the other game's service was preserved. Capture evidence is ignored under
`.run/mourning-bell-check/`. Fight duration, balance and sustained physical-TV
60 FPS remain human playtest requirements.

## Existing later encounters

The approved 24 November 2026 encounter sequence is Hollow Sovereign at **720
elapsed combat seconds**, then Grave King at **1200 seconds**. The three-minute
development fixture scales the mini checkpoint to 108 seconds. Both spawn once
using the existing safe-distance search (at least 300 world units from each
living player), a short existing arrival effect/sound and 1.5-second initial
attack cooldown. Unsafe required spawns retry; a full pool may replace an
ordinary reinforcement, never another boss or an elite. There is no cutscene,
portal, summon condition or dependency on killing the mini boss first.

The Sovereign retains its original artwork and combined ground-warning/volley
attack, with 5,400 health solo or 8,640 with two participating players after the
approved difficulty pass. Waves,
elite checkpoints, the arena clock and discoverable chest appearances continue during this fight.
Its death grants souls, does not grant another equipment offer and does not end
the run. The full-run Sovereign moves at 65/80 units/s and starts attacks every
2.8/2.1 seconds before/after half health. Its existing one-second ground warning
also warns the volley: five/seven shots release afterward at 220/250 units/s for
18/22 damage, with visible gaps. Ground strikes retain 22/28 damage and 125/150-unit
radii. Cast phase and facing are snapshotted, so crossing half health during a
windup cannot change the warned shot count/speed/damage. The 45–75-second fight
length is a human playtest target, not an automated balance result. SHORT keeps
its legacy 450-health, immediate-volley development fixture. The existing five
elite chest drops plus three discoverable arena chest opportunities remain intact.

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
mini; Delete (or desktop Shift+F6) starts the mini. Neither is a player-facing mode. These fixtures
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
