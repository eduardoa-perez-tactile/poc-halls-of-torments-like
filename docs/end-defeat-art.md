# Approved Defeat screen layers

The original environment below is superseded at runtime by the [shared layered crypt](layered-menu-art.md), approved 2026-11-13. The separate Defeat title remains in use; results retain their live summary and remote controls.

The user approved the original Defeat concept on 2026-11-13. These separate image layers derive from that concept, leaving summary values and remote controls live in LibGDX.

Reference concept: `/Users/eduardoleale/.codex/generated_images/01a1d415-2530-73a3-8474-f4251518c11c/exec-d189ae33-49dd-4145-8eb9-8beee8d216da.png`.

All generation and image edits used the built-in imagegen tool. No source-game art or third-party UI assets were copied. The existing original home crypt and adventurer paintings informed the approved concept. Python/Pillow was used only to read dimensions/alpha bounds; it did not modify pixels.

## Assets

- `assets/art/end-defeat-background.png`: 1672 × 941 RGB opaque scene; cold crypt, kneeling Warden and slumped Ranger, with all title, subtitle, summary, buttons and Run Details removed. Visually inspected: no interface residue and the approved composition is preserved.
- `assets/art/end-defeat-title.png`: 2172 × 724 RGBA transparent crimson DEFEAT wordmark. Alpha extrema 0–255, all corners alpha0; alpha >32 bounds `(57,166)-(2116,573)`. Runtime should trim alpha bounds before fitting the title without distorting its glyph proportions.

The runtime can bake only the active outcome background to 1536 × 864 and its title into a bounded texture, releasing the source pixmaps. Large source masters are not intended to remain resident on the GPU.

## Original concept prompt

Create one polished 16:9 Android TV dark-fantasy DEFEAT screen concept matching the original crypt/adventurer style references. Cold blue-black ruined cathedral crypt stairs, enormous ominous dark arch, dying embers and small candles, subtle crimson light and low mist. Armored Warden kneels exhausted on a grounded sword beside a slumped teal Ranger and her bow; no blood or gore. Characters roughly25% of screen height, rich edge architecture and dark negative space for controls. Large weathered crimson Cinzel-like DEFEAT title; subtitle THE CRYPT CLAIMS ANOTHER SOUL. Small unboxed run summary, two carved pale stone buttons TRY AGAIN and RETURN TO LOBBY (retry selected in gold), and a modest RUN DETAILS action. No statistic cards, table, build lists, HUD, health/XP, QR, pause hint, logo or extra text.

## Background extraction prompt

Use case: precise-object-edit. Extract the approved background scene for a layered game end screen. Edit target is the approved Defeat mockup. Preserve exactly the cold blue-black gothic cathedral crypt, both stone hooded statues, ruined blue architecture upper left, ominous red doorway upper right, wet stone staircase, candles, dying embers, mist, Warden kneeling on sword at lower center-left and slumped teal Ranger beside her bow at left. Keep the same identities, poses, positions, scale, lighting, palette, composition and camera. Remove all interface: large DEFEAT word, subtitle, summary numbers/words/separators, both stone buttons including text/ruby ends and golden focus glow, and RUN DETAILS. Reconstruct natural dark crypt/stone/mist and wet stone steps behind removed UI. Keep calm dark negative space upper center and lower18% for live UI. No added words, symbols, UI, frames, panels or objects. Opaque clean16:9 scene only.

## Title extraction prompt

Use case: background-extraction. Extract only the exact large crimson DEFEAT title from the approved mockup as a separate transparent layer. Keep the six glyph shapes D E F E A T, Cinzel-like gothic serif, worn dark crimson metal/red stone material, dimensional bevel, chips and subtle highlights. No redesign, icons, flourishes, subtitle, scene, buttons, glow field, shadow backdrop or other text. All areas around and between letters are true alpha transparency. Center DEFEAT on a wide approximately4:1 transparent canvas with narrow padding, preserving original title proportions and readable spelling, clean antialiased edges and real alpha.
