# Lobby art direction

Current environment direction (approved 2026-11-13): menus share the simplified layered crypt with warm soul orbs, without fire or fog. See [layered menu artwork and prompts](layered-menu-art.md). Earlier paintings and their prompts below remain as provenance; the panel/button theme is retained.

PVGames' [Other Worlds: Core Tiles](https://pvgames.itch.io/other-worlds-core-tiles) is a mood and material reference: detailed medieval environments, weathered stone, grounded fantasy, and an elevated RPG camera. No PVGames assets are included or copied.

The user authorized original generated art from the first build. The bundled images were created with the built-in image generation tool on 2026-11-12:

- `assets/art/crypt.png`: original opaque crypt backdrop, 1672 x 941.
- `assets/art/adventurers.png`: original transparent knight/ranger portrait sheet, 1774 x 887; its halves share one texture.

These are lobby illustrations, not animated gameplay sprites. The character portraits do not commit the next milestone to character classes or abilities. All artwork is local; no generation or asset downloads occur at runtime.

## Background prompt

Use case: stylized-concept. Asset type: original dark-fantasy game lobby background, wide 16:9 landscape. Create a beautifully detailed medieval underground crypt entrance with a slightly elevated isometric RPG environment aesthetic, tactile weathered stone, broken pillars, tiled flagstones, iron braziers, amber torchlight, cold desaturated blue shadows, faint drifting mist, a distant arched doorway glowing softly. Painterly pre-rendered 2D fantasy environment, rich material detail, grounded proportions, evocative and readable like a premium classic dungeon crawler. The user's reference is the general medieval/isometric dark fantasy direction of PVGames Other Worlds Core Tiles; create a wholly original scene, not a reproduction of any exact image. Composition: strong architectural portal in upper center, braziers at outer sides, foreground floor in lower third. Keep middle and lower center relatively uncluttered and dark to support overlay lobby panels; details concentrated toward edges. No characters, no interface, no text, no symbols that read as letters, no logo, no watermark. Full bleed background, opaque.

## Adventurer sheet prompt

Use case: stylized-concept. Asset type: a single game character selection sprite sheet with TWO full-body original medieval dark fantasy adventurers, transparent background, landscape 2:1 canvas. Left half: a stoic armored knight in weathered iron plate, muted burgundy tabard, closed helmet, a longsword pointing downward. Right half: a hooded female ranger with a dark desaturated teal cloak, leather armor and a bow held at her side. Both standing at rest, entire bodies including feet visible, same scale, slightly elevated three-quarter RPG camera, premium detailed painterly pre-rendered 2D classic dungeon crawler art, realistic anatomy, subtle warm rim light and cool shadows. General material/mood inspiration: detailed isometric medieval fantasy environments like PVGames Other Worlds, but original character designs. Each figure centered in their own half, ample clear separation at the center seam and space around edges. No ground, no environment, no floor, no cast shadow background, no UI, no panels, no lettering or labels, no logo, no watermark. Genuine transparent alpha around the characters.

## UI treatment

Warm ivory text, muted gold borders and obvious remote focus contrast against cool, subdued stone. Connection state uses words in addition to color. The QR has a solid white quiet zone. Cinzel and Lato are bundled with their SIL Open Font License notices under assets/fonts.

## Resource policy

Both illustrations stay below a 2048-pixel edge. No mipmaps, post-processing, framebuffer effects, animation strips, remote font loads, or duplicate per-screen artwork. Fonts share a small atlas; UI resources are reused and disposed at shutdown. Keep total resident lobby art/font textures below 32 MiB; retain only the small CPU atlas needed for Android context restoration. Profile the actual target hardware before claiming sustained 60 FPS.

## Initial interface art pass

The original backdrop and characters are now complemented by original painted stone/brass panels and iron button plates. These sources were created with the built-in image generation tool on 2026-11-12:

- `assets/art/ui-panel.png`: 1254 x 1254 source painting. The native UI bakes it once into a 512 x 512 managed texture, then uses nine-slice corners/edges to support player cards and dialogs at different sizes.
- `assets/art/ui-button.png`: 2172 x 724 source painting, including transparent margins. The native UI reads the plate at (8,132) with size 2156 x 457 once and bakes a 1024 x 224 managed texture. Its nine-slice keeps rivets/corners intact as buttons resize.

The runtime textures add only 1.875 MiB, with the same amount of small CPU pixmap storage for Android context recovery. Full-resolution source pixmaps are disposed immediately after baking. Original source paintings remain unchanged on disk. No textures are rebuilt in the frame loop. Remote focus adds a crisp gold rim, and disabled controls use a muted plate tint. The phone page uses the same local source art through CSS border-image/background cropping.

These initial blue/iron skins and background have been superseded at runtime by the approved shared Home theme described in [home-menu-art.md](home-menu-art.md). The original files and prompts below are retained as source-art provenance; current screens use the matching warm panel and separate normal/focused stone plates.

### Panel prompt

Use case: stylized-concept. Asset type: one production-ready original game UI panel background for LibGDX nine-slice scaling. Square canvas. A flat, orthographic, front-facing medieval crypt interface panel, NOT a rendered object in a room. Fill almost the entire canvas with a crisp rectangular panel. Frame: thin chipped blue-black slate stone, narrow inset aged brass bevel, small restrained medieval corner engravings, tactile hand-painted detail. Keep frame detail confined to the outer 10% on each edge, corners wholly inside the outer 15%; straight consistent edges between corners. Interior is an opaque, very dark charcoal-blue slate surface with extremely faint even texture, nearly flat at the center so it can stretch without distortion and remain readable under text. The reference direction is the general weathered stone, grounded dark-fantasy mood of isometric medieval dungeon RPGs, but this must be original UI artwork. Premium restrained aesthetic, not ornate baroque, no bright glows. A narrow fully transparent margin only outside the frame, and opaque interior. No text, no lettering, no symbols resembling letters, no objects inside, no icons, no drop shadow outside the canvas. The center must not be transparent. Designed as a functional reusable UI asset, centered symmetrical rectangle with straight sides.

### Button prompt

Use case: stylized-concept. Asset type: one reusable original medieval dark-fantasy game UI button plate for nine-slice scaling. Wide landscape canvas, 4:1 aspect ratio. Flat orthographic front view, perfectly horizontal long rectangular button, centered and almost filling the canvas, a thin transparent margin outside only. Aged dark iron and charcoal slate in the interior, subtle tactile brushed material, restrained tarnished brass edge with small rivet details at the extreme left and right corners, tiny chips and weathering. Outer border is thin and consistent along straight horizontal edges, all detail confined to outer 10% and corners, no central emblem. The center 70% is a nearly uniform opaque very dark slate surface for light interface lettering that will be added in code. Sophisticated practical dungeon crawler interface, grounded medieval craftsmanship, matching weathered crypt stone and warm bronze, no baroque flourishes, no bright glow. No text, no lettering, no logo, no symbols, no character, no environment, no perspective or bevel extruding dramatically. It must be a functional resizable button sprite with opaque interior and genuine alpha only outside its thin silhouette.

## Approved orb HUD — 2026-11-13

The user approved the compact orb/rim concept: live red health orbs with gold/teal forged rims, adjacent health and shared-XP rails, acquired skill emblems/ranks and Dodge status. The layout stays at the lower edges, with separate compact boss and notification lanes. Character portraits and large panels remain outside the combat HUD.

`assets/art/hud-atlas.png` is a code-native derivative of that approved mockup, built from vector shapes, gradients, glass highlights and forged-rim details. The orb background, blood, reflection and player rims remain separate layers so the renderer can fill live health without rebuilding artwork. Skill illustrations reuse the existing normalized skill atlas; text, ranks, timers and stat values remain live UI.

Regenerate the atlas with `python3 scripts/GenerateHudArt.py`. The script preserves the approved SVG/CSS-derived gradient stops and vector ornament coordinates, with offline antialiasing; Pillow is an offline generation dependency only.

The single 512 × 256 RGBA atlas adds exactly 524,288 bytes (0.5 MiB) of GPU storage and retains no decoded CPU pixmap. `HudArt` uses a file-backed managed texture, restoring from the small baked PNG after Android context loss. It is loaded once, shared between both players and disposed at shutdown, with the combined 32 MiB GPU texture budget unchanged. It adds no framebuffer or runtime art generation. Temporary gameplay floor/actor art remains a separate later visual pass.
