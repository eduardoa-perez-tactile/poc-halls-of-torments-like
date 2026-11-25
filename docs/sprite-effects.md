# Original sprite combat effects

Latest 25 November approval replaces the primary slash with the original
isometric expanding crescent in `art-source/warden-melee-wave/`. It uses one
normalized painted master, actual attack reach and simulation-owned damage
contact; the bank now contains 65 frames at the same 512×512 size. See
[the melee-wave implementation](combat-vfx.md#warden-melee-wave-replacement-2026-11-25)
for current timing and verification. Both public sprite bakers produce this
replacement. The following earlier art passes are retained as history.

24 November feedback: the Warden's sword crescent was enlarged to the
entire upgraded targeting radius, producing a very large, soft trail. Its paint
now uses a fixed 48-world-unit visual radius at the existing weapon-height
anchor. Skill/gear reach, damage, cadence, direction, 380 ms lifetime and
replacement of the previous swing are unchanged. The original atlas pixels and
texture dimensions remain unchanged; reducing the draw scale avoids enlarging
the small exported sprite across the arena.

Desktop/Android builds and eleven focused sprite/geometry/combat checks passed
after this correction. Brief actual-APK captures beside both bosses confirmed
the smaller directional crescent; no art was regenerated or GPU storage added.
The replacement APK remains installed on the TV emulator, returned to its lobby.

The user approved nine original animated sprite proposals on 2026-11-24 and then
authorized replacing the code-drawn world circles and streaks. The selected
artwork was created with the built-in `image_gen` tool, inspired by the supplied
aura screenshot. It is not an export of the paid Godot shader pack.

## Selected sources and geometry

`art-source/sprite-effects-canonical.zip` contains the exact selected PNG sheets,
their original generated ground/sword masters, exact prompts, the supplied visual
reference, measured ground-rim geometry, normalization script and SHA-256 source
index. Paths inside the archive are portable and no `.run` or OS temporary files
are needed for a rebuild.

The ground paintings use measured rim centers rather than alpha bounds. Their
per-frame translation removes the original sheet's row-to-row root jump; measured
vertical corrections put the fitted ground ellipse on the arena's **2:1** plane.
Horizontal expansion remains authored animation. Particle-only phases use the
nearest stable correction. Ground source cells are 448×1024 with root (224,640).
The sword crescent uses one 0.45 vertical correction around its fixed source root,
matching the approved character comparison. Other effects retain their selected
reference anchors and remain ordinary painted sprites.

Targeting/warning rim fits are strong; Soul Circle/impact strands and debris have
less certain fitted edges. The painted energy remains deliberately irregular.
Normalization does not recreate wisps already clipped in an original source.
Directional source sheets have one reference direction; changing character facing
does not create additional authored character or attack poses.

## Bounded runtime bank

`assets/art/sprite-effects.png` is one **512×512 RGBA** page, accompanied by
`sprite-effects.json`: 72 independently trimmed frames across nine eight-frame
clips. There are two reserved gutter pixels per side, one of them edge-extruded.
Existing actor, projectile, environment, floor, HUD and original VFX PNG/JSON files
remain unchanged.

The common export scale is exactly **15/64** in both axes. All selected source
dimensions divide exactly at this scale, retaining their isometric aspect and
fractional pivots without independent frame fitting. The exporter removes source
alpha ≤4 and remaps the remaining alpha using the existing VFX-bake convention;
after Lanczos downsampling it removes only alpha ≤2 interpolation tails. This
avoids retaining nearly invisible full-canvas generation residue. All effect
pixels otherwise come from the approved artwork; the baker paints no circles,
lines, rays or replacement geometry.

The page uses **1,048,576 GPU texture bytes (1 MiB)**. With the preceding measured
combined total including the live QR, the resulting accounted ceiling is
**33,516,364 bytes (31.9637 MiB)**, leaving **38,068 bytes** under 32 MiB. The loader
must use a file-backed managed texture and retain no decoded Pixmap. The bank adds
zero retained decoded Pixmap bytes. These are accounted texture bytes, not a
measurement of driver/process memory or a sustained performance result.

| Clip | Source animation | Runtime use |
|---|---:|---|
| TARGET | 1000 ms loop | Compact cosmetic 48-unit foot aura; gold/teal participant tint |
| SOUL_PULSE | 240 ms | Soul Circle damage pulse |
| WARNING | 1000 ms | Brute/boss warning seal with the authored inner countdown |
| GROUND_IMPACT | 300 ms | Brute/boss ground strike ripple |
| DODGE | 220 ms | Directional foot-level movement wake |
| SWORD_SWEEP | 380 ms | Directional Warden cutting trail |
| STORM | 200 ms | Connecting electrical arc; fixed authored source endpoints |
| CONTACT | 240 ms | Compact radial projectile/hit flash and small fragments |
| BLOCK | 180 ms | Compact spectral parry glint |

Frame durations describe source playback, not a new combat clock. Drawing can map
the selected clip over an existing pooled effect/windup lifetime, keeping its
original damage, cadence, radius, timeout and pause ownership. In particular the
Brute's 0.75-second windup must finish the warning inside that existing window;
shorter hit/critical flashes must not extend to the source clip duration. Ranger
contact art remains radial without incoming-direction rotation. The Warden's
0.38-second cosmetic sweep retains its fresh-swing replacement behavior.

## Rebuild and offline verification

```sh
python3 scripts/BakeSpriteEffects.py --output /path/to/rebuilt-art
```

The archive-only rebuild produced both runtime files byte-for-byte. Repackaging
the approved local review also reproduced the canonical ZIP byte-for-byte. The
baker checks every canonical source hash, all 72 saved frame/gutter rectangles,
atlas bounds and nonoverlap. The packed padded area is **245,339 pixels**. A
separate hash comparison confirmed that all **38** preceding art PNG/JSON files
were unchanged. Temporary verification outputs were removed.

The approved review compared the sprites with the resident Warden/Ranger art and
current floor; facing evidence covers SW/SE/NE/NW for the ground pulse and compact
contact. Those composited previews establish art alignment, not live gameplay,
physical-TV quality, balance or sustained 60 FPS. Native runtime checks are recorded below.


## Runtime correction approved during integration

The first native capture exposed oversized, soft auras and an incorrect foreground
pass over enemies. The user rejected that presentation and explicitly selected
**compact cosmetic auras around the feet** instead of an exact attack-range
boundary. The original source clip keeps its historical TARGET identifier.

The permanent aura now has a 48-world-unit visual radius; Soul Circle pulses use
60 units and Dodge uses a smaller 0.22 source scale. The duplicate persistent
Soul Circle rune is no longer drawn underneath the same foot marker. These are
cosmetic bounds: Warden/Ranger acquisition, Soul Circle damage radii, skill/gear
scaling and every combat-owned lifetime remain unchanged. Enemy warning and
strike footprints retain their actual authored hazard sizes.

All floor effects draw before shadows and the complete sorted actor pass. There
is no later foreground aura pass to paint a rim or wisp over enemy bodies. Ground
rotation uses the isometric affine (x*cos(a)-2*y*sin(a), 0.5*x*sin(a)+y*cos(a)); it
does not rotate a projected ellipse in screen space. Lightning fits painted
source endpoints to the existing live chain endpoints. Contact flashes stay
radial, while the Warden sweep and Dodge wake retain directional paint.

No new simulation clock, collision rule, attack cadence, pool size or network
message was introduced. The renderer reads existing elapsed/attack/effect ages,
so manual/choice/background/disconnect freezes keep owning those timers.


## Integration verification

Explicit Homebrew JDK21 built desktop classes and the Android debug APK offline.
Nineteen focused checks passed: the new bank's geometry/gutters/budget and weighted
sampling, combat-owned pause/resume/expiry, existing primary acquisition/swing
lifetimes and enemy telegraphs. No whole-run gameplay test was performed.

The corrected APK ran on the existing Television_1080p emulator (Android33 ARM64,
1920×1080). An isolated solo capture checked the compact foot marker against the
actual Warden. A five-second moving co-op/boss fixture checked ground effects
behind enemy bodies, busy rendering and controller activity. The fixture closed
its synthetic phones and restored the menu afterward. Proof is local under
`.run/sprite-effects-integration-corrected/`.

Live accounted GPU storage remained **33,516,364 bytes**, including the live QR;
retained decoded Pixmaps remained **518,988 bytes** across the application and
zero for this new bank. The measured combat report had301frames, interval
p95/p99/max **18.27/19.48/23.25ms**, render-callback wall p95 **12.58ms**, and
131 of300 intervals over16.7ms, with none over33.4ms. GPU timing is unmeasured.
This is a brief rendering/resource check, **not a sustained60FPS pass**, a balance
test or a guarantee for physical AndroidTV hardware. Native screenshots and
resource results do not remove the need for the user's gameplay playtest.
