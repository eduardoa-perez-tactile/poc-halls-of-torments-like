# Sprite combat feedback

## Isometric skill readability (2026-11-26)

The user approved `art-source/isometric-skill-readability/approved-proposal.png`,
including its brighter active auras, for presentation-only implementation.
This replaces the screen-rotated Sentinel/Glaive sprites and floating Reaping
Stroke skill emblem. Primary melee-wave geometry, timing and damage are unchanged.

Sentinel Blades and Returning Glaive rotate in the uncompressed world plane
before the arena's 2:1 projection, at their existing 26/28-unit display heights.
Small floor shadows anchor those heights. Fine tapered trails follow at most
100 ms of actual combat-tick positions; the glaive also uses faint past spinning
poses. Each of ten fixed weapon slots owns seven cached poses. No trail predicts
a return path, advances during camera rendering or survives a reused lifetime.
Choice/manual/background/disconnect pauses use the existing simulation gates.

Reaping Stroke now reuses the resident painted cutting crescent at a compact
48-unit visual radius, aligned with its committed world facing and drawn in the
complete ground pass beneath all actors. Its independent delay, cone, damage
and duration remain unchanged. The primary expanding wave is untouched.

Consecrated Ground keeps its exact live field radius, lifetime and final 450 ms
fade. Its original painted seal is more visible (64% instead of 32% tint alpha),
with a restrained resident-texture fill and a fine ivory-gold/teal edge using
cached antialiased geometry. Fields remain beneath hostile warnings and bodies.
The 57%-opacity exact-range targeting outline is unchanged. Relics retain their
original painted identities, with low hovering placement, a shallow three-quarter
lean, contact shadows and small gold/teal floor lights; they remain collectibles.

Native capture also exposed the existing oversized sword-contact bursts as a
source of floating-icon clutter. These keep their painted animation and contact
height, but use compact 62%-scale projected directional sweeps instead of the
former 145%-scale screen-rotated bursts. Hit timing, critical identity, damage
numbers and target health are untouched. Contact VFX remain body effects; floor
sweeps and fields stay beneath the entire actor pass.

All runtime raster files and texture dimensions stay byte-for-byte unchanged.
The additional history and geometry arrays allocate once, with no frame-loop
allocations, new textures, shader or framebuffer. Both cameras consume the same
simulation-owned state. This is a bounded presentation change, not a difficulty
or gameplay change.

### Verification

JDK 21 focused checks passed: **26 tests** across SkillExpansionCombatTest,
SkillExpansionCullingTest, CombatVfxStateTest and VfxAtlasTest. Desktop classes
and the Android debug APK compiled successfully. Integration checks cover real
past weapon positions while the owner moves, pool reuse/removal/death, bounded
history, choice-pause freezing and reset cleanup. A read-only review found no
gameplay-calculation changes. Runtime asset files have no diff.

The actual native desktop OpenGL renderer produced **14 raw 1920×1080 captures**,
all with GL error zero, in short existing all-skill debug fixtures. Combat views
retain five solo/ten co-op target enemies and their actual hit effects; a separate
solo close-up uses a temporary diagnostic camera zoom of 0.7. The isolated skill
views hide target bodies/contact effects after simulation and are labeled as
such. No screenshot is a generated mockup. Inspected views include simultaneous
four blades/field/glaive/Reap/primary wave/relics and six overlapping co-op fields.
Evidence is under ignored `.run/isometric-skill-review/`.

These captures verify presentation and depth, not physical-TV pacing, thermals
or a full survival run. No device installation or emulator launch occurred.
Capture processes exited. The existing saved LAN QR host, relay configuration
and ADB forwards remain unchanged. A read-only handoff check still received a
closed connection without response from that existing LAN endpoint, matching
its previously recorded state.

Current permanent player outlines and Dodge presentation are defined by the
25 November range-and-afterimage update below; earlier aura approvals are retained
as implementation history.

The later Warden melee-wave replacement below supersedes the compact primary
sword slash and its immediate damage; other earlier sections remain history.

## Warden melee-wave replacement (2026-11-25)

The user approved the actual generated isometric ivory/gold crescent sprite and
authorized replacing the primary sword trail with an outward wave. Its bright
edge travels linearly from the release point to the actual skill/equipment
targeting reach over **240 ms**, then fades at full reach until **380 ms**.
The existing **110-degree** cone and enemy ground hitboxes determine contact.
Damage, critical/frost contact, sound, floating numbers, stun and knockback now
occur as the wave reaches each enemy, rather than on the release frame. The
damage values, targeting range, attack cadence and equipment sustain rules stay
unchanged. The separate Reaping Stroke bonus strike and other acquired abilities
retain their existing independent behavior.

Each released wave snapshots origin, facing, reach, damage/critical modifiers
and Frostbound effects. Moving or choosing an upgrade cannot bend, teleport or
resize a wave already in flight. New attacks use the current build. One hit per
enemy spawn identity prevents repeated contact and safely handles pool reuse;
fresh enemies behind an already passed rim receive no retroactive damage.
Eight fixed slots, four reserved per participant, keep gameplay independent of
the optional cosmetic-effect pool and allow haste waves to finish together.
Both cameras read the same age/state. Choice pauses and host simulation gates
freeze expansion and damage; reconnect retains it, removal/reset clears it.

`BakeMeleeWave.py` uses the approved full-reach cell, measures its ivory rim and
fits its generated arc to the real cone on the 2:1 ground plane. One stable
painted master scales continuously with the collision radius and fades using
the same clock. It draws in the complete ground pass beneath actors and enemy
warnings. The previous weapon-height slash is no longer emitted or drawn.
The managed **512×512 RGBA / 1 MiB** sprite page now stores 65 frames instead of
72, with no added texture bytes, retained gameplay Pixmaps, dependencies or
frame-loop allocations. All 64 unrelated frame/gutter samples remain exact.
Original sheets, exact ImageGen prompts, source hash and normalization details
are preserved in `art-source/warden-melee-wave/`.

### Melee-wave verification

JDK 21 desktop compilation, Android debug assembly and **151 focused headless
checks across 17 classes** passed. Coverage includes near/far contact timing,
cone exclusions, actual acquired reach, once-only contact, pool reuse/exhaustion,
haste overlap, co-op ownership, snapshots, choice freeze, reconnect/reset,
overlapping target positions, feedback, rewards/chests and boss death fixtures.
Pixel checks verify the painted leading rim against its radius and ground cone;
an independent public-baker rebuild reproduces both production atlas files
exactly.

The user subsequently authorized a separate headless emulator check. A fresh
temporary Android TV API 33 ARM64 AVD, **1920×1080**, booted without a window;
the existing TV-agent emulator, its preferences and `tcp:57298 → tcp:8788`
forward were preserved. An eight-second actual APK recording checked base and
upgraded reach in a short solo fixture, showing the expanding rim, ground
occlusion and contact feedback. A synthetic controller connected successfully.
No full survival playthrough or physical-device test was run. Native evidence
is ignored under `.run/melee-wave-headless/`.

Software-rendered headless capture is not a performance acceptance test: its
sampled solo interval p95 was 52.30–62.45 ms while recording and running alongside
another emulator. GPU timing was unmeasured; no sustained physical-TV 60 FPS
claim is made. Accounted textures in this capture were 32,959,308 bytes including
the current QR. The temporary phone, forward, emulator and AVD data were removed
after verification. The pre-existing LAN `:8787/session` probe refused connection;
that relay was left untouched for its owning agent.

The 23 November 2026 pass integrates the approved enemy-arrival and level-up sprite direction, corrects the gold ring's flat perspective and replaces the rejected small impact glint with separate, larger sword and bolt bursts. Weapon contact now combines a brief directional sprite with painted recoil, stronger attack trails and distinct sound cues. Existing damage, attack cadence, movement, Dodge distance/invulnerability and enemy collision positions are preserved.

## Art and resources

Four original eight-frame strips are packed into `assets/art/combat-vfx.png` with `combat-vfx.json`: spawn **640 ms**, level-up **900 ms**, sword contact **200 ms** and bolt contact **200 ms**. The original whole sheets were generated with built-in ImageGen. Canonical raw sources, exact prompts, source hashes, provenance and normalization parameters are preserved in `art-source/combat-vfx-canonical.zip`. The Godot [magic](https://godot-shaders.itch.io/isometric-magic-shaders) and [weather](https://godot-shaders.itch.io/isometric-weather-shaders) demos supplied visual references; no purchased code or downloaded artwork was used.

The gold ring uses one shared baked vertical projection factor of **1.65** across the original strip, bringing the approximately 3.3:1 generated ground shape close to the arena's **2:1** plane. Its ribbons rise taller as part of the same baked image. Failed generated perspective edits remain local and unselected. All strips retain fixed measured pivots, source offsets and per-frame durations; no runtime image fitting or shader distortion is involved.

The new **512×288 RGBA** file-backed managed page costs **589,824 bytes (0.5625 MiB)** and retains no decoded Pixmap. `VfxArt` caches complete/back/front regions and bounded animation envelopes once. Foot effects draw around each depth-sorted body; rotated weapon effects use their contact pivots. Linear filtering, two-pixel gutters and edge extrusion protect packed samples. Ordinary SpriteBatch rendering handles everything; no custom shaders, frame allocations or new dependencies were introduced.

Actual combined accounted textures including the QR reached **32,893,772 bytes (31.3699455 MiB)**, leaving **660,660 bytes (0.6300545 MiB)** below 32 MiB. The existing actor/environment bank remains 6 MiB. Existing whole-application retained decoded Pixmaps total 20,245,324 bytes; the new gameplay/VFX banks retain none.

Rebuild only from the checkout and tracked source archive:

```sh
python3 scripts/BakeCombatVfx.py --output /path/to/rebuilt-art
```

The baker verifies source hashes, packs every frame without rotation, validates exact saved-page reconstruction and emits deterministic metadata. An archive-only rebuild reproduced both shipped files byte for byte.

## Behavior

- Enemy arrivals use combat-owned ages and fully visible actors. They introduce no hidden contact damage, spawn delay or AI change.
- Every new shared skill-choice round triggers one gold burst. One player's acknowledgment and existing-player reconnect do not restart it. The cosmetic age advances during active host-owned choice updates while combat and combat effects remain frozen; manual/background/disconnect pause gates freeze it too. Loot does not trigger an extra level-up burst.
- The approved crest/phone-choice ceremony fades in over the first 0.15–0.45 seconds so the initial world burst is readable. Offers and the shared thirty-second deadline begin immediately. Existing text, crest, choice controls and skill/loot priority are preserved.
- Bolt impacts retain the actual projectile position, launch facing and critical flag. Their visual offsets match the flying bolt even after turns, enemy death or pool reuse. Sword knockback/stun remains unchanged; extra recoil moves only the drawing.
- Dodge uses two short afterimages; collection, blocks and deaths use bounded existing-atlas accents. Forty-eight sparse grey/blue dust motes drift in world space using combat time, so both cameras agree and combat pauses freeze them. No rain, fog, wet surfaces, scene flashes or menu changes were added.
- Contact, critical, player hurt, death and Dodge sounds have separate cues/gates. Six tracked voices and at most four starts per update bound crowded audio. All eighteen cues total **212,332 PCM bytes**, mono 22.05 kHz/16-bit. Ceremony cues remain unchanged. Offline format/routing checks do not establish perceived loudness or a clip-free combined device mix.

## Verification and handoff

The offline JDK 21 Android build and **57 focused checks** passed, including new impact snapshots, choice-burst ownership/freezes, atlas bounds/timing and fake-mixer routing/voice limits, plus directly affected combat, pause and late-join fixtures. Production APK contents matched the exported VFX and sound files exactly.

A short actual 1920×1080 Android TV API 33 ARM64 emulator recording shows co-op enemy arrivals, sword/bolt contacts, Dodge and the gold burst leading into the shared choice ceremony. Profile-only **F1** creates a small close encounter for this capture; ordinary sessions never call it. Existing F5 triggers a real skill-choice round. The ten-second native MP4 is silent because Android screenrecord omits the new audio; the running APK contains the cues.

After closing the reference demo, a seven-second busy co-op boss fixture reported 301 frames: interval p95/p99/max **19.84/21.28/26.80 ms**, CPU submission **10.83/15.45/21.54 ms**, 148 intervals above 16.7 ms and none above 33.4 ms; the reported frame used 64 draws. A separate twelve-frame fixture-construction window included a **52.71 ms** interval / **51.49 ms** CPU outlier and 170 draws while the late fixture and simultaneous arrivals were prepared. This is not a sustained 60 FPS pass; physical-TV performance and perceived game feel require user playtesting. No full survival playthrough was run.

Local raw exploration and rejected candidates are under `.run/art-exploration/vfx-integration-v2/`; native video, captures, measurements, APK hash and verification are under its `native-preview/`. The existing emulator and phone relay remain available for the user; synthetic capture controllers were closed.


## Radial projectile contacts (2026-11-23)

Player crossbow contacts now use a brief circular white core, cyan bloom, six small
round motes and an expanding ring. Critical contacts are slightly larger with warm
highlights. The effect never rotates or fans along incoming velocity. The existing
projectile-plane/launch-facing offset still positions it at the visible collision;
shot trajectory, damage, recoil and pooled lifetimes are unchanged. Sword contacts
retain their authored directional feedback. These accents reuse the existing glow
and pixel regions, add no texture or retained Pixmap, and create no frame-loop objects.

The old short player-colour marker below living heroes is removed. Actual downed
revival progress, ally guidance and combat ground telegraphs are retained.


## Weapon reach and Warden sweep readability (2026-11-23)

Living heroes have a quiet, thin projected ground ring at the exact primary
acquisition radius; Warden reach upgrades/gear apply immediately. Ranger's 800-unit
radius is much wider than the close camera, so only intersecting boundary portions
are visible. The ring has no fill, glow or spikes and reuses the existing pixel.
Warden's cosmetic slash now lasts 0.38 seconds instead of 0.18, spending 70% of that
window sweeping and the rest fading. A new swing replaces an old same-player trail
at high haste; damage, attack cadence and committed aim remain unchanged.

## Simple permanent foot rings (2026-11-24)

The user rejected the ornate permanent aura after inspecting the native capture.
`PlayerFootRing` now draws one plain thin fixed 52×26 logical-unit ellipse around
each living player's feet: gold for Player 1, teal for Player 2. The stroke is 1.2
world units, including two small antialiasing fringes. Its 64 cached segments use
the resident white texel, with 22,272 bytes of reusable CPU geometry and no added
textures, retained Pixmaps or frame-loop allocations. The ring stays stationary,
with no ornaments, glow, rotation or pulse, and draws in the existing ground pass
beneath every actor. Targeting/damage radii and the approved Soul Circle/Dodge
sprites remain unchanged. All existing runtime artwork is preserved.

Native co-op capture confirms the gold/teal rings stay flat and tight around both
heroes. Current build/transition/performance evidence is recorded in
[Defeat verification](end-screens.md#defeat-fade-verification).

## Approved glowing double-ring replacement (2026-11-24)

The later user-approved mockup supersedes the single thin ring above. Living
players now have two luminous concentric rings at 26/32-world-unit radii, eight
short radial ticks from 30.5→36.5 units, and a soft narrow gradient halo. Gold and
teal have slightly brighter cores. The complete effect occupies 73×36.5 logical
units and stays in the true 2:1 ground plane; radius 38 is its conservative culling
bound. The centre remains transparent and the glow stays beneath every actor.

Each draw uses 712 cached quads, with 102,528 bytes of fixed CPU array payload. The
existing white texel supplies all colours/gradients, adding zero textures or
retained Pixmaps; no frame-loop allocations or trigonometry are introduced.
The same ground pass/living-player gate remains. Difficulty, actual attack
radii, equipment sustain, Soul Circle, Dodge and the Defeat transition are
unchanged. The approved mockup, exact built-in ImageGen prompt and provenance
are preserved in `art-source/glowing-player-auras/`; no mockup pixels enter runtime.

### Glowing-aura verification

JDK 21 desktop compilation and Android debug assembly passed. Native OpenGL
captures show both colours at the feet and deliberate enemy overlap confirms
that bodies occlude the entire effect. The isolated renderer exercised 62 enemies
with both players visible in both panes (four aura draws), then sampled 180 static
frames after warm-up: renderer submission wall p50/p95/p99/max 0.641/0.819/0.903/
0.932 ms. This excludes simulation, audio, menu UI and GPU completion; it does not
establish sustained physical-TV 60 FPS. No textures were added and gameplay retained
zero Pixmaps. Existing runtime artwork and all difficulty/sustain/Defeat files
remain unchanged.

The TV emulator was running a separate game, so its activity, controller server,
LAN relay/QR configuration and existing forwards were left alone. Verification
used a separate desktop GL process, which was closed afterward. The Android APK
is available for playtesting; its updated rendering was not launched on that busy
emulator. Native captures/logs are ignored under `.run/glowing-auras/`.

## Live attack range and mild Dodge ghosts (2026-11-25)

The user's new feedback supersedes the fixed cosmetic/glowing foot auras. Each
living player now has one plain gold/teal outline at `primaryAttackRange`, the same
radius used to acquire targets. Warden starts at 160 world units and changes
immediately with reach ranks and equipment; Ranger stays at 800. Ranger's outline
can extend beyond the close camera and is never clamped to a cosmetic radius.
The outline describes acquisition distance to enemy centres; Warden still swings
its existing directional cone and its compact sword paint remains unchanged.

`PlayerFootRing` projects the circle into the true 2:1 ground plane. Its 192 cached
segments have a constant 1.2-logical-unit core and 0.7-unit antialiasing fringes,
without double rings, ticks, glow, pulse or fill. Cached unit directions/normals
allow immediate scaling without frame-loop trigonometry or allocations. The
576 quads reuse the resident white texel, with 49,184 bytes of instance float-array
payload (reduced from 102,528). The entire outline stays beneath all actors.

Dodge replaces the closely attached copies/body glow with at most four stored
past poses per player, captured every 50 ms while actually moving. Each preserves
its real position, hero, animation state/facing/phase and fades from 24% opacity
over 180 ms. The subtle warm/teal tint uses existing actor frames, depth-sorted
at each snapshot's own ground root, without an extra shadow. Both cameras read
the same combat-owned history; choices, manual pause, disconnect, background and
the frozen Defeat arena cannot age it. Fresh participation/reset/removal clears
the history, while existing-participant reconnect preserves it. Blocked movement
does not accumulate stationary ghosts. Dodge ground artwork remains unchanged.

No textures, retained Pixmaps, gameplay balance changes or dependencies are added.
Targeting/damage, sword cadence, Dodge distance/cooldown/invulnerability, equipment
sustain and the existing artwork are preserved.

## Projectile release scale (2026-11-25)

Ranger, Cultist and both bosses' moving missiles emerge at 20% visual size and
smoothly reach their existing size over the first 100 ms of combat-owned age.
Scaling stays anchored to the existing painted head pivot and muzzle offset.
Delayed trail copies use their own sample ages and are omitted before birth,
so a full-size tail cannot appear behind a newly fired small projectile. Ranger's
brief weapon flash/glow uses the same emergence curve. All four authored facings,
critical projectile sizes, trajectories, speed, collision, damage, lifetime and
pause behavior remain unchanged. The effect reuses existing frames and adds no
textures, retained Pixmaps or frame-loop objects.

## Range/Dodge/release verification

JDK 21 desktop compilation, Android debug assembly and 43 focused checks passed,
covering live reach acquisition, Dodge distance/cooldown/history/expiry, choice
freeze/reset/reconnect, actual projectile firing/travel/collision sizes, pool-age
reset, actor presentation and projectile atlas metadata. No survival playthrough
was run.

Isolated 1920×1080 native desktop OpenGL captures show Warden range changing from
160 to 256, Ranger's exact 800-unit boundary, co-op outlines and fading Dodge
history. A 180-frame static crowded co-op renderer sample after warmup measured
CPU submission p50/p95/p99/max 0.439/0.592/0.657/0.665 ms with 165 draws. This
excludes simulation, audio and GPU completion and does not prove sustained
physical-TV 60 FPS. The current run's texture accounting excludes QR, while these
changes add zero texture bytes and retain no gameplay Pixmaps.

The existing TV emulator was running another game, so its activity, saved LAN
join override (192.168.14.248:8787), relay and ADB forwards were preserved. The
temporary desktop render process was closed. Captures/logs are ignored under
`.run/range-dodge-preview/`; the Android APK is ready for user playtesting.

## Longer escape Dodge (2026-11-25)

The user's later escape feedback authorizes 25% farther Dodge travel: the movement
multiplier increases from 3× to 3.75× effective movement speed. Duration stays
220 ms, and cooldown/invulnerability keep their existing windows. Without speed
modifiers, Warden travels 148.5 world units (previously 118.8) and Ranger 163.35
(previously 130.68). Movement skills/equipment still apply normally. Normalized
diagonals gain no extra distance and arena bounds still clamp the path. Existing
ground art and the short frozen-pose ghost history follow the longer real path.

Focused checks cover both heroes' diagonal distance, unchanged timing/cooldown,
bounded movement and the Dodge history lifecycle. The earlier preservation of
Dodge distance describes the presentation milestone before this separate request.

## Arena life and compact damage numbers (2026-11-25)

The user selected drifting dust plus sparse ivory soul motes. `ArenaAmbience`
replaces the old nearly invisible 48-speck field with 160 slow dust specks and
12 tiny ivory motes: six around the original seal and one beside each authored
tomb. Startup seeds vary size, opacity, height, sway and drift. Each camera samples
the same world positions and combat time, so all motion freezes with combat.
Existing glow/pixel regions supply soft specks in the pre-actor pass; nothing
adds fog, fire, rain, collision or collectible behavior. Fixed float-array payload
is 6,784 bytes, with no added textures or frame-loop object allocation.

Enemy hits now produce compact floating damage numbers. A separate 32-entry pool
records actual health removed, including lethal overkill clipping, with critical
identity and source position/species/render scale captured before enemy reuse.
Numbers display rounded whole HP (positive fractions display at least 1), in
ivory for normal hits and slightly larger gold for criticals. Each rises 24 logical
units and fades over a 650 ms lifetime. Older entries recycle under crowd pressure;
number capacity never consumes combat-effect slots. Numbers draw above complete
enemy bodies with a small dark shadow and share their combat clock across cameras.
Choice/manual/background/disconnect pauses freeze them; run reset clears them.
Cached digits from the resident body font use a reusable vertex buffer, with no
new textures, per-hit strings or frame-loop glyph layouts.

The final combined JDK 21 desktop/Android build and 48 focused checks passed,
including both heroes' longer Dodge and four damage-number cases for lethal
critical snapshots, pause/reset, independent pool saturation and player damage.
Native 1920×1080 OpenGL captures show ambience at early/later combat time and near
tombs, plus co-op normal/critical labels above ordinary, elite and boss bodies.
The fixed dust/mote field's crowded co-op renderer sample measured CPU submission
p95 0.618 ms across 180 static frames; this excludes simulation/audio/GPU completion
and does not establish sustained TV frame rate or balance. Final screenshots/logs
remain ignored under `.run/ambient-preview/`. Temporary render processes and
harness files were removed; the existing emulator, saved LAN host and relay were
left unchanged. No full gameplay playthrough was run.


### Elite chest reward light (2026-11-25)

The approved chest revision uses warm gold hardware, a bright lid seam and a
compact reward light. A soft amber pool and fine broken 2:1 rim draw before
ground hazards and the complete actor pass. Two very short wisps and five small
ivory-gold glints draw at the chest's depth, so nearer actors cover them. The
wisps stop within one chest height above the lid; there is no tall loot beam.

`LootChestVfx` borrows the existing gameplay glow and white texel. Ring geometry
is cached at renderer creation; a fixed five glints and two wisps per active
chest are sampled analytically from the shared combat clock, without frame-loop
allocations or camera-local particle state. Choices, manual pause, background
and disconnect therefore freeze their motion. Collecting/resetting the chest
removes its light. Chest contact, landing delay, reward sources, phone choices
and reliquary scheduling are unchanged. Sprite provenance and the selected
mockup are preserved in `art-source/elite-loot-chest/v2/`.

JDK 21 desktop compilation and Android debug assembly passed. Twelve focused
chest/atlas checks passed. A brief native desktop fixture verified solo/co-op
rendering and shared collection; the full world image stayed byte-identical
while a loot-choice clock advanced, confirming that chest VFX freeze with
combat. All old atlas pixels outside the replacement chest patch and all other
frame metadata were preserved; the APK was checked for the updated sprite. No
physical-TV performance or full-run balance claim is made. The existing TV
emulator and LAN relay were not changed.

## Dodge and simple enemy circles follow-up (2026-11-25)

The user's follow-up extends Dodge another 13.3% from the previous build: speed
is now 4.25× effective movement speed for the same 220 ms window. Unmodified
Warden/Ranger travel is 168.3/185.13 world units. Cooldown and invulnerability
duration stay unchanged; normalized diagonals and arena bounds remain in force.
Ghost snapshots now peak at 42% opacity instead of 24% and fade over 240 ms
instead of 180 ms. Six fixed slots retain actual past positions/poses, with no
new textures or frame-loop allocations. They still freeze with combat and clear
on fresh participation, removal or reset.

The approved attack-range outline keeps its exact live targeting radius and
thin 2:1 geometry, with core opacity reduced from 88% to 57%. Enemy attack alerts
and floor-smash warnings/impacts use the same simple outline language, with
brighter ivory warnings and warm peach impacts at their existing locked
positions/radii. Brute impact keeps its actual 55-unit radius, replacing the
former 66-unit cosmetic footprint. Small circles reuse 48 of the 192 cached
directions; the colour scratch buffer adds only 16 bytes of CPU float payload.

The later directional feedback replaces Wraith's three lane circles with simple
open `>` chevrons pointing along the committed dash path. The user rejected full
shaft/filled-head arrows and then removed the dark outlines; each marker is just
two plain ivory strokes, without a shaft, border or filled centre. The Wraith's
redundant foot warning circle is removed. Brute's
actual fist strike adds a compact dust/stone-chip burst and ground shock,
separate from the clean warning outline. All ground cues remain beneath the
complete actor pass and freeze with combat. These presentation changes preserve
enemy damage, attack timings, movement, targeting, boss behavior, equipment
sustain, chest rewards and the resident texture budget.

JDK 21 desktop compilation and Android debug assembly passed for the final
plain-chevron version. Twenty-four focused Dodge/enemy-attack/Brute-geometry
checks passed. Brief native 1920×1080 captures verified the opposing committed
Wraith directions, compact Brute impact, stronger co-op Dodge trails and dimmed
player range; the subsequent border removal retained that same chevron geometry
and was compile-checked. No full survival playthrough was run. Rendering samples
exclude full simulation/GPU completion and do not establish physical-TV 60 FPS.
Temporary render processes/harness files were removed; proof remains ignored in
`.run/vfx-feedback/`. The emulator, saved LAN host 192.168.14.248:8787 and existing
relay/forwards were preserved; the LAN controller endpoint returned HTTP 200.
