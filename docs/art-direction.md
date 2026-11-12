# Lobby art direction

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
