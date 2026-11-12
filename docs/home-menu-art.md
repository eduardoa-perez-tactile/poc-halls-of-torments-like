# Approved home-menu artwork

The user approved the centered Couch Crypt main-menu direction on 2026-11-12. These are original runtime components derived from that approved concept: a clean environment, a separate transparent wordmark, and one blank resizable stone button. The concept's buttons and labels are not flattened into the environment.

Created on 2026-11-12 with the built-in image-generation tool. No CLI/API fallback was used. The approved concept was supplied as the visual reference in each of three independent calls. Existing lobby/launcher artwork was not overwritten.

## Source assets

| Asset | Source size | Format | File bytes |
|---|---:|---|---:|
| `assets/art/home-background.png` | 1672 x 941 | RGB, opaque | 2,518,645 |
| `assets/art/home-logo.png` | 2172 x 724 | RGBA | 1,631,499 |
| `assets/art/home-button.png` | 2172 x 724 | RGBA | 1,865,840 |

The source paintings are retained intact. Native code can bake bounded textures once at startup and reuse them. Planned limits are 1536 x 864 for the environment, 1536 x 384 for the wordmark, and 1024 x 192 for the button, keeping the complete art/font footprint within the project's 32 MiB GPU budget. The parent implementation owns final sizing and measured totals.

## Alpha bounds for startup baking

Bounds use `(left, top, right-exclusive, bottom-exclusive)` source pixels, measured by reading the original PNG alpha channel without modifying it:

- Logo, alpha > 16: `(13, 9, 2159, 695)`; alpha > 128: `(14, 10, 2158, 693)`. The tight artwork is approximately 3.13:1 including its crest. Preserve complete lettering and crest when fitting it into the title slot.
- Button, alpha > 16: `(3, 160, 2170, 543)`; alpha > 128: `(4, 160, 2169, 542)`. There are very faint alpha specks elsewhere in the original canvas. A conservative plate crop is `(0, 152, 2172, 552)`, or origin/size `[0, 152, 2172, 400]`, including a small safety margin around the visible slab.

The button's pale stone center is intentionally blank for black text drawn by the UI. Brass corner ornaments and red gem endpoints belong in preserved nine-slice end regions. Focus glow, labels, and remote interaction belong to native UI code. These assets require no frame-loop image generation, network downloads, or animation strips.

## Background prompt

Use case: precise-object-edit. Asset type: clean original 16:9 game main-menu background derived from the approved reference image. Input image is the approved Couch Crypt menu concept and serves as the architectural/composition reference. Produce ONLY the environment, without any interface. Faithfully preserve the dramatic medieval ruined crypt: huge dark stone arches, hooded guardian statues at left and right, burning iron braziers at the lower outer edges, moss, cracked wet flagstones, cold blue side corridor and distant broken architecture at upper right, faint low mist. Remove ALL branding, logo, tagline, menu buttons, text, ornaments belonging to UI, and footer. Reconstruct natural dark quiet architectural space behind the removed UI. The central upper and middle area must remain subdued/uncluttered so the real game can overlay a large title and a centered column of buttons. Tactile richly detailed original painterly pre-rendered dungeon RPG art, amber fire against blue-grey shadows, restrained atmosphere, no characters moving through the room. Full-bleed opaque environment, landscape 16:9. Do not include a title, letters, wordmarks, menu, panels, button plates, HUD, symbols resembling text, or watermark.

## Wordmark prompt

Use case: background-extraction. Asset type: original transparent main-menu title wordmark extracted faithfully from the approved reference. Input image is the approved Couch Crypt menu concept. Isolate ONLY its large forged-gold "COUCH CRYPT" title and the restrained medieval crest/iron bar immediately attached above/behind the title. Preserve the dramatic original medieval serif letter shapes, chipped aged gold/brass material, warm ember-like bevel edges, slight dark red fabric behind the central crest, and small skull crest centered above. Exact lettering (verbatim): "COUCH CRYPT". Wide horizontal arrangement like the reference, approximately 4:1 canvas, complete title visible with a modest transparent safety margin. Make the lettering legible at TV distance. Genuine fully transparent background around the entire logo, including behind/under the letters; no solid rectangle. Do not include the tagline, any other words, menu buttons, environment, floor, statues, border frame, UI, or watermark. This is one usable RGBA title sprite, not a logo on a presentation canvas.

## Blank button prompt

Use case: background-extraction. Asset type: one original blank game menu button plate based closely on the approved Couch Crypt reference image. Input image is the approved main-menu concept. Isolate/recreate a SINGLE neutral unselected carved silver-grey stone button matching the reference HOW TO JOIN and SETTINGS button materials: a long horizontal slab of pale weathered stone, fine fractures, aged brass/bronze sculpted corners, one small deep red diamond gem at each end, thin dark stone lower bevel. No fiery gold focus glow: focus will be drawn by the game. Front-facing perfectly straight horizontal orthographic interface sprite, not perspective. Wide approximately 5:1 shape, plate nearly fills the canvas with only a thin transparent safety margin. Its center must be flat opaque light-grey stone, with low-contrast cracks and sufficient quiet area for large BLACK text added at runtime. Keep endpoint gems/corner ornaments near the outer ends and all decoration within outer 15%, so this can be nine-sliced. Remove ALL letters and labels: completely blank plate. Genuine transparent alpha only OUTSIDE the button silhouette, opaque interior. Do not include multiple buttons, any text, title, environment, frame around the whole canvas, watermark, heavy drop shadow, or colored focus glow.

## Checks

Visually inspected all three generated images against the approved concept. Confirmed that the background contains no title, buttons, or text; the transparent wordmark reads COUCH CRYPT; the button has no lettering and has an opaque stone interior with alpha outside. Read PNG dimensions/formats, file sizes, and alpha bounds. No emulator tests, gameplay tests, or repository branch/commit changes were performed by the art task.

## Distinct focus state and cohesive panel revision

Following user feedback on 2026-11-12, two additional original assets were created with the built-in image-generation tool. The normal button was used as the edit target for the selected state; the normal button and earlier panel were used as material and structure references for the revised panel. No existing image was overwritten.

- `assets/art/home-button-focused.png`: 2172 x 724 RGBA, 2,013,814 bytes. It preserves the normal plate's cracks, shape, gem placement, and canvas placement, with a distinctly brighter cream stone center, glowing warm gold carved-outline rim, and brighter ruby highlights. The glow follows the irregular ornamented silhouette rather than a rectangular bounding frame.
- `assets/art/ui-panel-v2.png`: 1254 x 1254 RGBA, 2,357,847 bytes. Neutral weathered stone/brass/ruby framing matches the menu buttons, with a dark charcoal-brown interior for ivory text. The center sample is RGBA (32,28,26,251), nearly opaque, and contains no blue/teal tint or lettering.

Focused-button alpha bounds: alpha > 16 `(0,130,2172,573)`; alpha > 128 `(2,144,2171,559)`; alpha > 240 `(6,152,2168,548)`. Very faint stray alpha appears elsewhere in the original canvas. A shared crop `[0,124,2172,456]` for both normal and focused button textures includes the focused halo and keeps the underlying slabs aligned. Do not independently fit each state to its glow-inclusive bounds, which would make the selected slab shrink or jump.

Revised-panel alpha bounds: alpha > 16 `(9,7,1245,1243)`; alpha > 128 `(10,8,1244,1243)`. Full-source baking preserves all corner ornaments. Planned native texture limits remain 1024 x 192 for the focused button and 512 x 512 for the revised panel. The focus art is static; it needs no animation, blur pass, framebuffer, or additional frame-loop work beyond switching the selected texture.

### Focused-button prompt

Use case: precise-object-edit. Asset type: SELECTED/FOCUSED state of the supplied original game menu button. The input is the exact NORMAL button sprite, and it is the EDIT TARGET. Preserve the SAME horizontal stone plate, silhouette, dimensions, placement, carved cracks, brass end ornaments, and two red diamond gems. Preserve the wide 2172 x 724 source canvas and the original plate position; do not redesign its geometry or invent a different button. Change ONLY its visual selection treatment: a powerful warm golden illuminated rim tightly following the actual irregular carved outline, bright gold light along the brass bevels, a clearly brighter warm ivory/cream stone center, and stronger ruby highlights/gleam on both end gems. This must look unmistakably selected at TV distance compared with the normal grey stone button. Keep the gold effect controlled around the existing plate rather than a rectangular bounding box. Blank interior: NO text or symbols. Genuine transparent alpha outside the silhouette and its soft gold edge light; no solid background. Do not add animation frames, a separate rectangular frame, drop shadow on a canvas, text, labels, logos, or other UI. Preserve alignment with the input normal plate so the game can swap normal and focused sprites without jumping.

### Cohesive-panel prompt

Use case: stylized-concept. Asset type: one functional square game UI panel skin for nine-slice scaling. Input image 1 is the approved stone/brass/red-gem BUTTON and is the material/style reference. Input image 2 is the previous square panel and is the STRUCTURE reference only; replace its blue palette. Produce an original square, flat, orthographic medieval dark-fantasy panel with a thin carved weathered neutral stone-grey frame, aged warm bronze/brass insets and sculpted corners, and one small dark ruby gem in each corner. Match the tactile craftsmanship and warm neutral stone/brass palette of input image1. The large interior is OPAQUE very dark charcoal-brown slate with an extremely faint even fine texture so light ivory UI text remains highly readable. NO blue or teal interior. Place all frame detail and corner ornament within the outer 10-15% of each edge; straight consistent edges between corners, and a nearly flat dark center that can stretch cleanly. Restrained premium gothic RPG interface, not a heavy ornate sculpture, no perspective. Panel nearly fills a square canvas with only a thin genuine transparent outside margin; interior must NOT be transparent. No letters, text, icons inside, character portraits, environment, multi-panel sheet, glow, watermark, or outer presentation background.

Both new assets were visually inspected and their source dimensions, PNG formats, file sizes, and alpha bounds were read. No code, Android resources, emulator state, branches, or commits were changed by this asset task.
