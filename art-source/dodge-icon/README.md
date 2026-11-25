# Original Dodge emblem

Created on 2026-11-23 after the user requested a new dedicated Dodge icon rather than the reused Shadow Step emblem. Generated with the built-in `image_gen` tool, with `transparent_background: true` and no reference images. No existing icon or third-party artwork was used as input.

- `dodge-source.png`: original generated RGBA master, 1254 × 1254; alpha includes fully transparent and opaque pixels.
- `dodge-128.png`: atlas input, 128 × 128 RGBA. Offline processing only crops the source alpha bounds, downsamples proportionally to fit 112 × 112 using Lanczos, and centers it on a transparent canvas. No repainting, masking or generated-background removal was applied.
- Runtime integration packs the normalized icon into the existing HUD atlas; the standalone master and normalized input are source assets, not additional runtime textures.

The original and normalized images were visually inspected. The output contains one leather boot, warm gold hardware and teal/ivory sidestep streaks, without text, a frame or surrounding medallion. The normalized PNG SHA-256 is `0b5929fc103198cb202d4c48eec2ce7a10b0a9257ac2761f7e2902917c002c98`.

## Exact generation prompt

```text
Use case: stylized-concept
Asset type: original game HUD Dodge ability icon, square isolated transparent bitmap.
Primary request: Paint one unmistakable single medieval leather boot performing a fast lateral evasive sidestep. The boot points diagonally toward the upper right, with two or three short teal and warm ivory motion streaks trailing behind it toward the lower left. Dark fantasy medieval painted game icon, polished but simple, strong chunky silhouette and broad lighting shapes readable when reduced to 64–80 pixels. Worn deep brown leather, muted aged-gold buckle and ankle reinforcement, restrained pale teal soul-light on the sole and motion streaks. One boot only, no person or extra feet. Center the compact subject with about 12 percent transparent padding on each side. Fully transparent background, clean alpha edge. No surrounding circle, border, frame or medallion; the game supplies its own rim. No text, numbers, arrows, logos, watermark, scenery, tiny filigree, or extra particles.
```
