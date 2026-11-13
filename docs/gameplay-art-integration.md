# Gameplay art integration

The 13 November 2026 weekend milestone replaces the temporary arena art with the approved reusable crypt kit and seven distinct actors. The Warden pilot's corrected northeast walk and northwest sword strike are the starting point. Ranger retains the approved crossbow appearance without a reload delay; simulation damage, firing cadence, collision and controls remain unchanged.

## Resource preparation

`scripts/BakeRuntimePortrait.py` exports the unchanged approved `assets/art/adventurers.png` master (1774×887) to `adventurers-runtime.png` (1024×512). `UiArt` loads the runtime copy as a managed file-backed texture. The two square hero regions, transparent margins and existing normalized portrait crop calculations are preserved. The largest current portrait height is 318 pixels; each runtime half remains 512 pixels high.

This lowers portrait GPU allocation from 6,294,152 to 2,097,152 bytes, saving **4.002571 MiB**. The original painting is retained. Matched maximum-size portrait comparisons showed only minor fine-detail softening, without material crop, silhouette or readability changes. Core and Android Java compilation passed offline with the installed JDK 21; actual game screenshots and total-allocation checks follow during renderer integration.

## Planned atlas envelope

Replace the existing 3 MiB gameplay bank, rather than loading it alongside the new pages:

| Page | Dimensions | RGBA allocation |
| --- | --- | --- |
| Heroes and casting enemies | 1024×1024 | 4 MiB |
| Common enemies | 512×512 | 1 MiB |
| Environment and shared masks | 512×512 | 1 MiB |

With the portrait export, the current one-font-page allocation and a live 360×360 QR texture, the planned combined total is **30.807446 MiB**, leaving **1.192554 MiB** below the 32 MiB ceiling. A nonrotating pack of actual Warden rectangles and proposed remaining-actor caps fits these pages; final artwork still requires an actual full-pack check. This is a planning envelope, not a measured integrated runtime result.

New gameplay pages must be file-backed managed textures without retained decoded Pixmaps, use cached frame/trim/pivot metadata and separate shared ground shadows. Source masters and review boards are not runtime textures. Four authored facings preserve asymmetric equipment; both cameras read the same simulation-owned animation clocks, which freeze during skill/loot choices and all existing pause/disconnect gates.

Use only installed tools and cached dependencies. Builds use the existing Gradle 8.14.3 distribution with `--offline` and `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; no toolchain or dependency installation is authorized.

## Simulation-owned presentation state

The world now records actual movement, committed player attack age/aim, damage-only hit time, enemy locomotion/cast phase and the exact Cultist/boss projectile-release event. These snapshots advance only in combat ticks, after both skill and loot pause gates. Fresh participants and reused enemy slots reset them; reconnecting participants retain them. Dodge travel direction is exposed read-only, so later phone input cannot rotate an existing dodge pose.

`ActorAnimation` selects an actor, authored facing, action and phase without advancing any clock. Attacks retain their committed aim, including while dodging/moving. Boss bolts use an immediate release pose followed by the existing warning hold; Cultists use their existing windup and actual release. Retreating Cultists reverse the locomotion phase while keeping their aim toward the player; the frame sampler must support negative looping phases. Contact enemies retain their existing hit flash and death effects without acquiring a misleading attack windup.

Projectiles capture their launch aim; hostile shots also capture caster kind and elite status at actual emission. The renderer can align their visual height with the painted release pose after a hero turns, an enemy dies or its pool slot is reused. These value snapshots do not change shot position, velocity, lifetime, damage, collision or release timing. Reusing a projectile initializes the snapshots again. An exhausted bolt pool retains its existing gameplay cooldown but does not invent a new firing pose when no bolt was created.

Sixteen focused event/selection tests pass, including damage/projectile timing, two-camera read safety, actual movement versus wall/stun, choice freezes, reset/reuse/reconnect, projectile-emitter pooling, full-pool behavior and dodge/attack overlap. Android Java compilation also passes offline. The renderer still uses the temporary art until the complete new atlas is packed and connected; these checks do not establish final visual quality or TV performance.

## Offline packing and frame metadata

`scripts/BakeGameplayAtlas.py` copies the canonical actor and environment PNGs into the three fixed pages without rotation, mirroring or rescaling. It preserves exact RGBA pixels, records original canvas, trim offset and ground pivot, and checks saved-page reconstruction, bounds, overlaps and two-pixel gutters. Proposed trim caps are advisory when the actual complete set fits; the fixed page dimensions and 6 MiB limit are mandatory.

Strict mode requires all seven actors, their class-specific actions in four facings and all nineteen environment regions. Missing inputs cannot produce `gameplay.json`. `--preview` produces separately named pages and `gameplay.partial.json`; its `preview:true` flag is rejected by the runtime parser. The current 131-frame partial pack (Warden, Ranger, Wraith and environment) passes reconstruction and deterministic-hash checks; four actor kits remain outstanding.

`GameplayAtlas` validates the versioned metadata before GL textures load. It derives world-space trim geometry and culling extents from stable canvases and pivots. Looping clips support negative retreat phases; one-shot clips hold their final pose. Five focused metadata tests cover trim roots, timing, missing actors/facings/regions, malformed geometry and the memory envelope.

`scripts/PreviewGameplayAtlas.py` builds a self-contained HTML animation kit, four-facing walk GIFs and a bounded cast contact sheet from the packed pixels. It preserves source offsets, ground pivots, timing and existing enemy size modifiers. A partial proof must be requested explicitly with `--partial` and is visibly labeled. Browser checks on the current three-actor proof verified pause, facing/action changes and 40 ms stepping without console errors. This is art inspection, not gameplay verification.

After the strict full pack, `scripts/ArchiveGameplaySources.py` preserves its exact canonical inputs and text provenance in `art-source/gameplay-canonical.zip`. It checks input hashes and archive reconstruction, and refuses an incomplete pack. Large generated posters and review media remain in the local exploration directory; the compact archive is intended to reproduce the runtime atlas exactly. Historical review statuses are retained as provenance, alongside the final integration evidence.
