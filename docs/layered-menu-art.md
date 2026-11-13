# Shared layered menu crypt

Approved direction: 2026-11-13. The user requested simpler, consistent menu backgrounds based on Home, without fire or baked fog, and approved matte blue-grey stone, simplified hooded guardians, a charcoal central arch and two warm ivory soul orbs.

This replaces the individual Home/Victory/Defeat environment paintings at runtime. Existing logos, title wordmarks, panel skins, buttons, summary values, remote navigation and detailed builds remain live and unchanged. Pause retains dimmed frozen gameplay as proposed.

## Original artwork and provenance

Created with the built-in image generation tool, using the original Home scene as the first edit target. No downloaded source-game artwork, CLI generation or Python image editing was used. Source masters are copied intact into this checkout; runtime baking crops/downsamples them once.

| File | Size and format | Role |
| --- | --- | --- |
| `docs/art/menu-crypt-concept.png` | 1672 × 941 RGB | Composite design reference; not loaded by the game |
| `assets/art/menu-crypt-base.png` | 1672 × 941 RGB | Empty architecture and matte floor |
| `assets/art/menu-crypt-guardians.png` | 1672 × 941 RGBA | Two aligned foreground groups, with empty bronze cradles |
| `assets/art/menu-crypt-orb.png` | 1254 × 1254 RGBA | Shared ivory orb and small transparent halo |

Generated master: `exec-a7eaaf3c-b37d-45f7-8103-04b34933b8c1.png`; base: `exec-7253b945-6ed6-4928-84b7-c50c06966667.png`; guardians: `exec-d85066dc-e7bf-4666-9d1f-d6fd75220134.png`; orb: `exec-218a653f-94bd-456d-8a92-fc3c7fa8fd8b.png`. Originals are under `/Users/eduardoleale/.codex/generated_images/01a1d497-c79b-7ba3-a0ee-7cd5741841ae/`.

## Composition and resource contract

The base is baked to 1536 × 864 RGBA. Guardian halves are independently alpha-cropped, packed with the orb into one 512 × 1024 RGBA atlas, and drawn using their original normalized canvas positions. Left and right are separate render layers even though they share a source PNG/atlas. The combined scene is 7.0625 MiB GPU and the same retained managed CPU pixels for Android restoration. Full-resolution decoded sources are released during startup; no baking, layout rebuilding, pixel scans or allocations happen in the draw loop.

Statue alpha ≥64 bounds: left `(57,126)-(258,826)`, right `(1414,126)-(1617,826)`, exclusive right/bottom. The orb alpha ≥8 bounds are `(312,312)-(935,939)`; its strong core (alpha ≥240) is `(388,399)-(857,875)`. Source alpha below 8 is cleared during baking to avoid near-transparent colored noise.

Orb centers correspond to master pixels approximately `(198,608)` and `(1473,608)`, top-left origin. The visible core is approximately 36 source pixels wide. An eight-second, slightly out-of-phase brightness change animates only the two light sprites. Small orb brightness/tint changes distinguish results; the room does not change. No particles, fire frames, fog layers, moving shadows, post-processing or framebuffer effects are used.

Home, Lobby, Invite, Character Info, both results and Run Details share this scene. Dialogs retain their existing scrim/panel. Phones receive a one-time 640 × 360 CPU composite of these same layers as a static PNG, with the existing CSS readability gradient. Their legacy `/art/crypt.png` URL remains an internal route to this composed image.

The original four environment paintings remain as provenance assets but are no longer selected by current menus. Result titles are still prewarmed; no outcome-background worker is started by the current screen flow and no scene upload is needed at the result transition.

## Generation prompts

### Simplified master

Use case: precise-object-edit. Asset type: original 16:9 dark fantasy game menu environment master, intended to be decomposed into compositing layers. Input image 1 is the EDIT TARGET and architectural identity reference, the existing Couch Crypt Home painting. Create its approved simplified replacement. Retain the recognizable large central gothic stone arch and broad quiet central space, two hooded stone guardian statues holding swords near the far left and right edges, broad flagstone floor. Composition must work behind centered menus: central 60% subdued dark quiet, guardians confined to outer 20% each, feet and lighting fixtures fully inside canvas. Simplify strongly: large readable blocks and silhouettes, restrained painterly stone texture, only occasional large chips, no dense cracks, no microdetail, matte blue-grey stone floor with broad simple slabs, no wet reflections. Replace both fire braziers with simple waist-high carved stone plinths bearing aged bronze cradles, each holding one small smooth warm ivory soul orb with a very restrained pale gold local halo. Left and right orbs at matching heights at x~12% and x~88%, y~65%. Lighting soft, even, neutral and subdued, sufficient to read the stone; no hard baked light pools. Central arch opens into a plain deep charcoal recess; simplify the right corridor to a single quiet recessed doorway. Remove all foliage, rubble, intricate distant ruined architecture, fog, mist, smoke, flames, fire, embers, candles, sunbeams, volumetric lighting, shiny floor and theatrical lighting. Palette matte desaturated blue-grey stone, charcoal center, small aged bronze and ivory-gold orb accents. Painterly grounded medieval material character, NOT photorealism, NOT cartoon, NOT highly detailed cinematic concept art. Lower visual complexity is the primary objective. Entire scene must be crisp and clear with no atmospheric haze. No people, no heroes, no UI, no branding, no lettering, no borders, no watermark. Opaque full bleed landscape 16:9. Do not include any fire anywhere.

### Empty base

Use case: precise-object-edit. Asset type: opaque base environment layer of a 16:9 layered game menu. Input image 1 is the EXACT EDIT TARGET, the approved simplified crypt master. Preserve the exact camera, canvas aspect and framing, every arch, column, wall, dark central recess, right-hand doorway and matte floor slab positions, geometry, muted palette and simple painterly finish. Remove ONLY the two hooded guardian statues including their swords, and the two tall narrow foreground orb pedestals with bronze cradles and orbs. Reconstruct plain dark empty stone niches behind the removed statues; retain the architectural niche arches and their low architectural steps. Reconstruct matching matte floor where orb pedestals stood. All remaining architecture MUST remain in the same canvas coordinates and retain exactly the same scale as the input. No redesign or additional details. Keep central negative space plain dark charcoal. No fog, mist, smoke, fire, glowing lights, orbs, people, text, UI, logo or watermark. Clean opaque full bleed 16:9 environment only. Flat soft neutral ambient lighting, no atmospheric effects. Intended to receive the same statues and pedestals as separate transparent layers.

### Transparent guardians and cradles

Use case: background-extraction. Asset type: transparent aligned foreground layer for a layered 16:9 game menu. Input image 1 is the EXACT EDIT TARGET master. Extract ONLY BOTH hooded stone guardian statues holding swords and BOTH separate tall narrow foreground stone orb pedestals including the small bronze orb cradles. REMOVE THE TWO GLOWING SPHERES from their cradles: leave the bronze bowls/cradles empty. Keep both statues and their separate orb pedestals EXACTLY at the same coordinates, scale and proportions on the SAME WIDE 16:9 CANVAS as the input master. Left group stays far left, right group stays far right. Preserve their muted blue-grey stone, simple painterly shading and small gold bronze cradles. Preserve the complete statue silhouettes and complete pedestal feet. Statues may include their own small support plinth but NO surrounding niche masonry, background steps, walls, architectural arches, floor, recess, shadows cast on floor, solid rectangle or lighting field. Everything except these four objects is true alpha zero, including the entire central 60% and gaps between objects. Actual transparent background, no checkerboard painting, no fog, no fire, no text. DO NOT rearrange objects into a tightly packed sheet; preserve full original 16:9 composition for exact layering over the matching empty scene. Empty orb cradles at x~12% and x~88%, y~65%.

### Shared orb

Use case: background-extraction. Asset type: one reusable isolated warm soul orb sprite for a dark-fantasy game menu. Input image 1 is the simplified crypt master, a visual/material reference. Extract/recreate ONLY ONE of its small smooth ivory-gold orb spheres: a round softly shaded warm pale ivory core with a restrained small warm gold halo that fades completely to transparent. No cradle, pedestal, metal, stone, room, ground, shadows, fire, sparks, particles, smoke or fog. Core occupies approximately center 40% of square canvas width; subtle soft halo extends at most to center 75% width then alpha zero for generous clean margin. Simple painterly smooth spherical form, not a sun, no rays, no swirling texture, no bloom field. Pale warm ivory highlight upper-left and faint warm-gold lower edge. Actual true transparent alpha everywhere outside sphere and its small halo, not a black or white backdrop and no painted checkerboard. Centered single sprite, square canvas. No lettering, symbols or watermark.

## Verification

Source images were visually inspected for simplified stone, clear negative space, absent flames/fog, transparent foregrounds and empty cradles. Dimensions and alpha bounds were read without modifying source pixels. Integration/build/render checks are recorded in the current performance notes after execution. Physical-TV frame pacing remains unverified.
