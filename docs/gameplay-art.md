> Status: temporary gameplay art. On 2026-11-12 the user said these assets do not match the intended isometric direction. Keep them for functional development; revisit visuals separately. These assets are not approved final art.

# First playable gameplay art

Created 2026-11-12 with the built-in `image_gen.imagegen` tool (default built-in mode). These are original generated game assets. PVGames' Other Worlds direction is inspiration only; no PVGames files were used. Existing original project images `assets/art/adventurers.png` and `assets/art/home-background.png` serve only as character/material references. The approved menu art and branding are unchanged.

These PNGs are source artwork. The runtime should use the normalized, smaller atlas and floor baked by the renderer integration, rather than loading all full-resolution sources. Sources preserve generated alpha; inspect silhouette bounds above alpha 32 because the generator leaves tiny alpha noise in otherwise empty space.

## Source layout and inspection

| Source under assets/art/ | Canvas | Grid | Role |
| --- | --- | --- | --- |
| game-floor.png | 1254 × 1254 RGB | one square | Opaque top-down flagstones |
| game-players.png | 1774 × 887 RGBA | 4 columns × 2 rows | Warden top, Ranger bottom; four walk poses each |
| game-enemy.png | 2172 × 724 RGBA | 4 columns × 1 row | Four ghoul walk poses |
| game-props.png | 2172 × 724 RGBA | 4 columns × 1 row | Obelisk, brazier, broken pillar, entry arch |

Floor inspected visually for even lighting and absence of objects. Opposing-edge mean RGB differences are 7.08 (horizontal seam) and 5.74 (vertical seam), versus 4.85 / 5.04 at example adjacent interior pixel lines: repeat is visually plausible but not mathematically pixel-perfect. Seam visibility should be judged in the actual arena.

Players inspected: exactly eight complete separate silhouettes, recognizable original costumes, four changing limb poses per row, generous gutters, no floors or labels. Enemy inspected: exactly four complete ghoul walk poses with consistent ragged purple costume. All face approximately down-right. These are one-facing animation sets with optional renderer mirroring, not multidirectional animation. Pose/costume consistency is suitable for the first slice; gameplay playtesting may identify animation adjustments.

Props inspected: exactly four isolated original props. The first generated sheet had a semiopaque white backdrop; one focused extraction pass removed it while preserving the artwork. Empty corner is now RGBA (0,0,0,0), the arch opening is transparent, and the brazier has a soft flame halo. Arch fills nearly its full cell width but is complete. Foot baselines vary slightly; the runtime bake should bottom-anchor each silhouette.

Cell partitions use `x * width / columns` and `y * height / rows` rounded down. Bounds below are cell-local, right/bottom exclusive, alpha > 32:

- Warden: (115,9,362,428), (126,9,324,429), (109,9,346,429), (131,9,309,430).
- Ranger: (115,13,359,432), (129,13,338,431), (98,13,342,429), (135,13,327,430).
- Ghoul: (50,51,479,678), (97,55,436,675), (50,59,482,678), (75,58,437,678).
- Props: (116,18,428,703), (114,13,433,702), (106,46,429,702), (3,10,540,689).

The source sheets are larger than the intended runtime art. Normalizing/baking them must preserve aspect ratio, per-character scale, and consistent bottom-center feet. Suggested runtime budget is a 1024 × 512 RGBA atlas plus 512 × 512 opaque floor, around 3 MiB without mipmaps; actual runtime measurements belong in the renderer integration.

## Exact generation prompts

### Floor

No image references. Opaque output requested.

```text
Use case: stylized-concept.
Asset type: seamless repeating square ground texture for an original medieval dark-fantasy isometric arena game, Couch Crypt.
Create a perfectly TOP DOWN orthographic square texture of old warm gray and charcoal crypt flagstones. Rough rectangular stone blocks, shallow fine cracks, restrained tiny moss at a few seams, subtle hand-painted surface variation. The stonework must extend to every edge and tile seamlessly horizontally and vertically, with matching opposing edges and no border. Even diffuse ambient lighting throughout: no vignette, directional lighting, highlights or cast shadows. Calm low-contrast ground that will remain readable under small fighters. Painted realistic 2D game art with crisp stone texture at small size, original asset.
No perspective, no isometric diamond, no objects, walls, bones, debris, plants above ground, people, architecture, symbols, labels, text or UI. Opaque full canvas square, preferably 1024x1024.
```

### Players

Reference image: `assets/art/adventurers.png` (identity/material reference). Transparent background requested.

```text
Use case: stylized-concept.
Asset type: transparent sprite animation atlas for original medieval dark-fantasy isometric arena game, Couch Crypt.
Input image 1 is CHARACTER IDENTITY AND MATERIAL REFERENCE ONLY: the original Warden knight at left and original hooded Ranger at right. Make a NEW transparent animation sheet.
EXACT layout: FOUR equal-width columns and TWO equal-height rows, exactly EIGHT separate full-body character sprites total, one centered in each cell. Uniform regular grid, ample clear transparent gutters, all characters same height and scale. Feet rest on the same baseline within every cell, with no clipping. No grid lines or labels.
TOP ROW: four successive subtle WALK cycle poses of the SAME Warden wearing weathered iron plate and closed iron helmet, burgundy cloak and tabard, sheathed or held compact downward sword. Alternate left foot forward, passing pose, right foot forward, passing pose.
BOTTOM ROW: four successive subtle WALK cycle poses of the SAME Ranger in muted teal hood and cloak, brown leather armor, bow held compactly at her side. Alternate corresponding walking poses.
Every frame faces down-right, a slightly overhead three-quarter isometric camera, consistent body proportions, compact silhouettes, complete head to toes. Motion must change limbs between the four poses while keeping costume and identity stable. Recognizable readable silhouettes when reduced to roughly 100 pixels high. Hand-painted realistic medieval dark-fantasy 2D game sprites, subtle worn material detail, neutral pale diffuse lighting.
Transparent background with true alpha, no floor, no cast shadows, no contact shadow ovals, no backdrop, no text, no UI, no extra figures, no decorative borders. Landscape sheet around 2048x1024.
```

### Enemy

Transparent background requested, no image references.

```text
Use case: stylized-concept.
Asset type: transparent FOUR-frame walk sprite sheet for an original medieval dark-fantasy isometric arena game, Couch Crypt.
Create exactly FOUR full-body sprites of the SAME skeletal crypt ghoul in one horizontal row, four equal-width regular cells. One character per cell, centered; all same scale, all feet on same baseline, ample transparent gutters, every head and foot fully visible.
Character: original sinister hunched human-sized undead ghoul, exposed bone skull and hands, scraps of rusted iron armor, ragged desaturated purple cloth, thin limbs and readable hooked silhouette. Faces down-right in all four frames, slight overhead three-quarter isometric camera, painterly realistic 2D dark-fantasy game art, compact detail that reads clearly at 90px tall.
Four distinct consecutive walking poses: left foot advanced, passing pose, right foot advanced, passing pose. Consistent head, torso, costume, proportions and lighting across every frame. Neutral ambient lighting, bone highlights against dark purple cloth.
True transparent background. No floor or ground, no cast/contact shadows, no UI, no text, no labels, no grid, no extra characters. Landscape sprite strip, prefer 2048x512; use four equal regular columns regardless of final canvas dimensions.
```

### Props

Reference image: `assets/art/home-background.png` (material/style reference). Transparent background requested.

```text
Use case: stylized-concept.
Asset type: transparent four-prop atlas for original medieval dark-fantasy isometric arena game, Couch Crypt.
Input image 1 is STYLE AND MATERIAL REFERENCE ONLY: worn crypt masonry and wrought iron, warm fire against charcoal stone. Create NEW isolated props, not a scene.
Exactly FOUR separate props in ONE horizontal row of FOUR equal regular cells, one full prop centered per cell, no overlap. Left to right: 1 tall carved weathered stone obelisk with subtle abstract incised ornament, 2 burning wrought-iron standing brazier, 3 broken fluted stone pillar with ragged top, 4 freestanding ancient stone crypt entry arch with dark transparent open center.
Slight overhead three-quarter isometric game camera, hand-painted realistic medieval dark-fantasy 2D art, sturdy readable silhouettes. Every base sits on the same baseline, all complete including bases and flames, generous transparent gutters. Original designs. Stone in cool charcoal gray with muted warm weathering, iron dark brown-black, fire amber-orange.
Transparent true alpha background, including through the arch. No ground plane, cast shadow, contact shadow, landscape, architecture beyond the four requested props, characters, text, UI, labels or borders. Wide landscape strip, ideally 2048x512, equal regular columns.
```

### Props transparency correction

Edit target: first generated prop atlas. Transparent output requested. The initial semiopaque-backdrop variant is not a project asset.

```text
Use case: background-extraction. Edit target: the attached four-prop atlas. Preserve the four original props exactly: obelisk, burning brazier including fire, broken pillar, and arch, same details, scale, regular four-column positions and baseline. Change ONLY the background. Remove every white or gray backdrop pixel and residual backdrop haze to TRUE fully transparent alpha=0 empty space, including through the arch opening and between brazier legs. Preserve clean opaque prop interiors and naturally soft fire edges. This must be a usable transparent 2D game sprite atlas with no visible white rectangles, no ground, no shadows. Do not redraw, reposition, crop, add text, or add any props.
```

