# Approved Victory artwork layers

The original environment below is superseded at runtime by the [shared layered crypt](layered-menu-art.md), approved 2026-11-13. The separate Victory title remains in use; results retain their live summary and remote controls.

The user approved the Victory and Defeat concepts before these production layers were extracted. These original paintings retain the approved Couch Crypt menu's worn stone, black iron, aged gold and torchlight direction. No Halls of Torment or Diablo source artwork was used.

## Assets

- `assets/art/end-victory-background.png`: 1672 × 941 opaque RGB/RGBA scene. Opened crypt gates, dawn, Warden at left, Ranger at right and the defeated Sovereign's fallen crown, helm and blade. All text, controls, focus glows and UI ornaments are removed.
- `assets/art/end-victory-title.png`: 2172 × 724 RGBA transparent title cutout. Contains only forged-gold VICTORY and its thin ruby/gold divider. Source alpha is zero at the corners; strongest visible glyph opacity is 253. The generator leaves some almost invisible alpha 1–6 pixels around edges; these are not painted glow. Render with normal alpha blending, or discard alpha below 8 during the managed runtime bake. Do not use nonzero-alpha alone for crop bounds.

These sources are intended for a single active lazily loaded outcome scene. The runtime can bake the background to 1536 × 864 and fit the trimmed title into a 1024 × 256 transparent managed texture while preserving its aspect. Menu labels, run data and buttons remain real UI drawn separately, not a flattened screenshot.

## Provenance

Generated with the built-in `image_gen.imagegen` tool. No Python pixel editing, downloaded source-game assets or external image-generation API was used. Source concept:

`/Users/eduardoleale/.codex/generated_images/01a1d414-e4f5-75e1-b2a7-4503d88b1c3b/exec-30ac3f8b-7083-4672-b12e-fac3f45a750b.png`

The concept used these original local art references for material and character identity:

- `assets/art/home-background.png`
- `assets/art/adventurers.png`

## Generation prompts

### Background extraction

Use case: precise-object-edit. Asset type: production background layer for an approved dark-fantasy game end screen.

Edit this approved victory mockup by REMOVING ALL interface elements, leaving only the original background painting. Remove the large VICTORY lettering and its gold/ruby divider; remove the subtitle; remove the run summary and its small ornamental strokes; remove both PLAY AGAIN / RETURN TO LOBBY stone buttons and all their glowing borders; remove RUN DETAILS and its underline. Remove every text glyph and UI ornament completely. Reconstruct natural dark stone architecture, shadow, steps and ground in those exact places.

Preserve the image composition, same camera and 16:9 framing, exact character positions and identities, defeated ruler's fallen crown/helm/blade on the central steps, opened gates, dawn sky, light rays, torches, statues, stone walls, colors and painterly materials. The Warden remains left foreground and the Ranger right foreground. Keep the upper central region naturally dark and calm for future title placement. Keep the lower central ground calm for future real UI buttons. Opaque full-size 16:9 background painting. No lettering, no symbols from UI, no buttons, no glowing rectangular edges, no menu panels, no logos. Only the background scene, no redesign.

### Initial transparent title extraction

Use case: background-extraction. Asset type: transparent production title layer for an approved dark-fantasy end screen.

Extract ONLY the existing large forged-gold word VICTORY together with the single thin gold ornamental divider and small central red ruby directly beneath that word from this approved mockup. Preserve the exact VICTORY spelling, existing slender sharp high-contrast gothic Roman serif glyph design, chipped gold foil / forged metal shading and bevels, gold hues, letter proportions, and the delicate divider's shape and centered ruby. Remove absolutely everything else: all scene/background pixels, subtitle THE HOLLOW SOVEREIGN HAS FALLEN, characters, run statistics, action buttons, RUN DETAILS, stone, sky, flames and shadows belonging to the scene. Do not include any subtitle letters or extra word. Place the isolated title+divider centered on a wide roughly 4:1 canvas with transparent margins. The original word must be intact, each letter complete, no clipping. Divider sits below with same proportional spacing. Actual transparent alpha background, not white, black, checkerboard or simulated transparency. No new lettering or redesigned logo. This is a clean title cutout layer, not a full screen.

### One focused alpha-edge correction

Use case: background-extraction. Correct only the transparency edge quality of this title cutout. Preserve the existing forged-gold VICTORY letters and the centered gold divider with red ruby exactly. Do not redesign glyphs, move letters, alter proportions, create additional text, or change the gold-metal texture. The surrounding canvas and every hole between/inside letters must be truly transparent alpha zero. Remove all red/yellow/green background speckling, colored fringes, translucent haze, extraction residue or glow outside the actual gold metal edges. The only red pixels should belong to the small ruby centered on the divider. There is no colored aura or glow around the lettering. Clean smooth antialiased alpha edges, fully transparent background, high-quality isolated artwork suitable to composite over a dark stone scene. Keep complete word and divider centered with generous transparent padding, wide canvas. No black/white/checkerboard background.

## Checks

Background visually inspected: no retained UI/text or button glows; approved composition remains. Title visually inspected and alpha statistics read without image modification. Exact word and divider retained; subtitle absent. The transparent title needs threshold-based crop bounds because of near-zero alpha noise. Large sources are freed after the runtime bake; retaining both full-resolution outcome paintings as GPU textures is unnecessary.
