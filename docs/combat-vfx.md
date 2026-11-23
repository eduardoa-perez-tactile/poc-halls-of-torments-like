# Sprite combat feedback

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
