# Painted Hollow Sovereign HUD

Historical source: the 2026-11-24 approval replaces this masked frame with the
[wide face-free header](../epic-combat-hud/README.md). The original crimson fill
from this folder remains in use; other files are retained for provenance.

The user approved the original painted concept on 2026-11-23, then requested a
smaller boss header to preserve arena visibility. The illustrated death-mask and
weathered iron/gold frame are original generated artwork. The rejected stock-icon
HUD proposal is not a source for these sprites.

All image generation used the built-in `image_gen` tool. The concept edited a real
development screenshot; runtime never loads that screenshot. The first extraction
and the width correction requested `transparent_background: true`. Their output
contains genuine alpha outside the frame and inside the health reservoir.

- `approved-concept.png`: user-approved appearance, including screenshot context.
- `concept-prompt.txt`: exact original concept generation prompt.
- `frame-source.png`: final transparent master after the width-only correction.
- `frame-512x96.png`: normalized frame, preserving portrait proportions; alpha-bounds
  crop at alpha 16, proportional Lanczos fit to 504×88, centered on a 512×96 canvas.
  The visible art is 434×88 at image offset (39,4). No repainting, background
  removal, recoloring or alpha masking was applied by the normalization step.
- `fill-128x16.png`: the approved concept's pure crimson reservoir interior,
  cropped from image rectangle (560,123)–(1040,151) and resized to 128×16. This
  preserves the original painted energy texture instead of baking a health value.
- `layout.json`: normalized art bounds and the live fill/name anchors.

The frame is packed at (0,256,512,96) and the fill at (260,212,120,16) in the
file-backed managed 512×352 HUD atlas. The health fill is drawn behind the alpha
opening. The renderer reveals the fill using the live boss-health fraction.
No boss name or health percentage is baked into
the runtime art; the name uses the existing game font.
The fill has a four-pixel guard to protect neighboring cells from linear-filter
bleeding. The first transparent extraction was an intermediate working image;
the final width-corrected transparent master is the canonical retained source.

The 512×96 frame uses bottom-left track coordinates x=101, y=33, width=350,
height=18. The live title anchor is x=276, y=84, also from the frame's bottom-left.
The track is deliberately a little larger than the alpha opening so the painted
inner bevel masks its edges. The portrait is not stretched separately from the
frame. A 960×180 destination gives about 814×165 pixels of visible artwork.

## Exact extraction prompt

```text
Use case: background-extraction / precise-object-edit.
Input image 1 is the approved boss HUD concept, serving as the EDIT TARGET.
Extract ONLY its top illustrated boss health frame as a separate production game UI sprite. Preserve the exact approved identity: the menacing hollow-eyed crowned sovereign funerary mask on the LEFT, its deep blue-black weathered iron hood, aged gold broken regal crown, and the long iron/gold bar frame that extends to the right and its shaped endcap. Keep the original broad painterly worn-metal shading, gold edge lights, silhouette and proportions, not a redesign.
Remove absolutely everything else: stone arena, screenshot, gameplay, bottom HUD, every word and letter including THE HOLLOW SOVEREIGN, and the red health fill.
Make the entire outside of the isolated frame GENUINELY TRANSPARENT with clean antialiased alpha. Also make the long enclosed health reservoir opening GENUINELY TRANSPARENT, an empty hole for a live fill behind it. Preserve the dark iron inner bevel around this opening. No baked health, no red fill, no red glow in the opening, no text, no numbers, no new symbols. The mask remains fully opaque and richly shaded.
Place the entire isolated mask-and-frame across the middle of a wide transparent image. It should be about five times wider than high, with only small even transparent padding. Keep the whole crown tip, mask lower tip, frame top ridge and right endcap, none cropped.
Do not add any surrounding panel, border or background. Deliver only the original painted frame and left death-mask as one alpha sprite, without text or health fill.
```

## Exact width-correction prompt

```text
Use case: precise-object-edit.
Input image 1 is the isolated approved painted boss-frame sprite, EDIT TARGET.
Make ONE layout correction only: lengthen the horizontal iron/gold bar section to the RIGHT of the death-mask, while preserving the death-mask itself exactly in its current shape and proportions. The complete visible mask-and-frame object must have a bounding-box aspect ratio of approximately 5.3:1 (five point three times wider than tall). The mask height is the unit: complete object length = 5.3 mask-heights. Present it centered on a larger transparent canvas if necessary; transparent padding is not counted in this ratio.
Keep the original left mask, dark iron hood, broken gold crown, menacing face, gold/iron materials, broad edge highlights, bar thickness, inner bevels and shaped endcap. Extend only the straight bar sections between the left mask and central ridge, and between the central ridge and right endcap, so ornaments and mask remain undistorted.
The exterior background and the enclosed long health reservoir opening must stay genuinely transparent. No new background. No text, no health fill, no numbers. This must remain the same approved art, simply a longer horizontal frame relative to its unchanged mask height. No crop of crown or endcap.
```
