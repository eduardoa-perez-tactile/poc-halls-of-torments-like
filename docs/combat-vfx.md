# Sprite combat feedback

Current permanent player outlines and Dodge presentation are defined by the
25 November range-and-afterimage update below; earlier aura approvals are retained
as implementation history.

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
