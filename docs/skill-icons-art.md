# Run Details skill icon atlas

- Created on 2026-11-13 with the built-in image-generation tool.
- Original artwork; no game screenshots or third-party assets copied.
- Runtime source: `assets/art/skill-icons.png`.
- Source dimensions: **1448 × 1086 RGBA**.
- Grid: **4 columns × 3 rows**, each source cell **362 × 362**.
- Transparency verified: all four outer corners alpha 0.
- Twelve complete icons verified in the required row-major order.
- First generation crowded some rims against cell edges. One focused spacing correction reduced each icon and separated the circles. No Python or other pixel editing was used; the output was copied directly from the built-in tool.
- Icons have slightly different positions inside their source cells. Runtime packing should alpha-trim each cell at alpha >32 and fit it into centered 104 × 104 content inside each 128 × 128 runtime cell. This produces the requested 512 × 384 atlas (0.75 MiB RGBA) without changing the source artwork.
- At small display sizes, the luminous center symbol should remain the emphasis; thin rim detail is decorative.

## Index mapping

| Index | Upgrade | Symbol |
|---|---|---|
| 0 | POWER | Flaming sword |
| 1 | HASTE | Bronze hourglass |
| 2 | FLEET | Winged boot |
| 3 | VITALITY | Crimson heart |
| 4 | EVASION | Shadow blink figure |
| 5 | SWEEP | Sword sweep arc |
| 6 | MULTISHOT | Three fanned arrows |
| 7 | PIERCE | Arrow through iron |
| 8 | AURA | Ghost-flame circle |
| 9 | STORM | Chain lightning |
| 10 | MAGNET | Souls drawn into a spiral |
| 11 | CRITICAL | Dagger cracking a red crystal |

## Local alpha bounds

Coordinates are local to each 362 × 362 source cell; right/bottom are exclusive.

```text
row 0: (80,72,322,319), (66,72,309,319), (51,73,294,319), (38,73,281,319)
row 1: (81,46,323,292), (66,46,309,292), (51,46,295,292), (38,46,281,291)
row 2: (80,20,323,266), (65,20,309,266), (51,20,295,266), (38,21,281,266)
```

## Initial generation prompt

```text
Use case: ui-mockup.
Asset type: production raster skill icon sprite atlas for an original dark-fantasy couch co-op game, no UI screen.
Primary request: exactly TWELVE illustrated circular skill icons in a mathematically regular FOUR COLUMNS by THREE ROWS grid, equal square cells. Wide 4:3 canvas. True transparent background everywhere outside the twelve circles. Each icon is centered within its cell, equal visual diameter, with generous 12% padding so no rims or glows touch cell boundaries. Straight-on icon view, not perspective.
Style: rich painterly prerendered gothic action-RPG inventory art, blackened iron and aged brass round rims, luminous bold central symbols, dark charcoal inset. Original artwork; restrained warm gold, teal and crimson palette. Readable silhouettes at 48–64 pixels. Match the atmosphere of torchlit medieval crypts; not flat vector, not cartoon.
Exact row-major order:
Row 1: 1 a flaming sword blade representing POWER; 2 a bronze hourglass surrounded by quickening gold energy representing HASTE; 3 a winged leather boot representing FLEET; 4 a vivid crimson anatomical heart representing VITALITY.
Row 2: 5 a human shadow silhouette blinking through teal mist representing EVASION; 6 a broad sword with a sweeping gold crescent arc representing SWEEP; 7 three arrows fanning apart representing MULTISHOT; 8 one arrow punching through an iron plate representing PIERCE.
Row 3: 9 a pale ghost-flame enclosed by a circular teal spirit aura representing AURA; 10 bright forked lightning chained across three sparks representing STORM; 11 three little souls being drawn into a glowing spiral representing MAGNET; 12 a dagger cracking a red crystal in a sharp impact representing CRITICAL.
Constraints: exactly 4 columns, exactly 3 rows, exactly 12 separate circular icons; all icons same size, grid cell centers aligned. No letters, words, labels, numerals, captions, watermarks, extra icons, square frames, surrounding panels, rows of text, UI screenshot, checkerboard texture, or opaque background. Transparent PNG with real alpha. Keep decoration subordinate to the central readable symbol.
```

## Spacing correction prompt

```text
Use case: precise-object-edit. Edit target: the attached skill icon atlas. Change ONLY the atlas spacing and alignment; preserve all twelve original icon symbols, exact row-major order, painterly materials, colors, round rims, and transparency.
The existing grid has crowded circles and rims crossing cell edges. Rebuild it on a precise 4:3 transparent canvas divided into EXACTLY FOUR equal columns and THREE equal rows of square cells. Shrink each complete circular icon, including its rim, to a diameter of ONLY 72 percent of its square cell. Place each circle at the exact horizontal and vertical center of its own cell. There must be wide fully transparent gutters between every pair of icons and equal transparent margins around all outside edges. No icon pixels may enter another cell. Keep the exact same twelve icons and their order, no additions, no omissions. Do not enlarge symbols beyond their individual circular rim. No text or new content. Genuine transparent alpha outside the circles.
Strict row-major order remains: flaming sword, hourglass, winged boot, red heart; shadow figure, sword sweep, three arrows, piercing arrow through iron; ghost-flame aura, chain lightning, souls spiral, dagger cracking red crystal.
```

