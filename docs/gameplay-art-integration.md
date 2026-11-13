# Gameplay art integration

The 13 November 2026 art milestone replaces the temporary arena kit with the selected original crypt environment and seven actors. Four separately authored diagonal facings preserve asymmetric equipment. Warden uses the corrected northeast walk and northwest sword strike; Ranger retains the compact crossbow without introducing a reload delay. Existing combat cadence, damage, collision, controls and playable bounds remain unchanged.

## Shipped kit and resources

`assets/art/gameplay.json` describes **208 actor frames plus 19 environment regions**, packed without rotation, mirroring or rescaling. Runtime frame selection preserves each canonical canvas, trim offset, ground pivot and authored timing.

| Actor | Canonical canvas | Frames | Actions in each facing |
| --- | --- | ---: | --- |
| Warden | 96×96 | 48 | Idle, four-step walk, four-frame attack, dodge, hit, downed |
| Ranger | 96×96 | 44 | Idle, four-step walk, three-frame attack, dodge, hit, downed |
| Ghoul / Wraith / Brute | 64×64 | 20 each | Idle, four-pose locomotion (Wraith drifts) |
| Cultist | 64×64 | 28 | Idle, four-step walk, release, windup hold |
| Sovereign | 128×128 | 28 | Idle, four-step walk, release, windup hold |

| Managed runtime page | Dimensions | RGBA GPU bytes |
| --- | --- | ---: |
| `gameplay-actors-main.png` | 1024×1024 | 4,194,304 |
| `gameplay-actors-common.png` | 512×512 | 1,048,576 |
| `gameplay-environment.png` | 512×512 | 1,048,576 |
| **Gameplay total** | | **6,291,456 (6 MiB)** |

`GameArt` loads these three file-backed managed pages, caches regions/metadata, and retains **zero decoded gameplay Pixmap bytes**. Partial constructor failure disposes already loaded pages. Legacy `game-*.png` masters moved to `art-source/legacy-gameplay/`; neither those masters nor review media are runtime textures.

`scripts/BakeRuntimePortrait.py` exports the unchanged approved `adventurers.png` painting (1774×887) to `adventurers-runtime.png` (1024×512). The shared crop math and originals are preserved. Each hero retains 512 pixels of source height against a largest current 318-pixel portrait draw height. Matched maximum-size comparisons showed minor fine-detail softening without material crop, silhouette or readability changes. The export saves **4.002571 MiB GPU allocation**.

Native verification reported a maximum **32,303,948 texture bytes (30.8074455 MiB), including the live QR**, leaving **1,250,484 bytes (1.1925545 MiB)** below the combined 32 MiB ceiling. Reported retained decoded Pixmaps across the whole application were **20,245,324 bytes (19.3074455 MiB)**; that total belongs to existing resources, while the new gameplay pages retain none. These are accounted texture/Pixmap totals, not a measurement of all process or driver memory.

## Rendering and animation

Both cameras read the same simulation-owned movement, attack, hurt and casting snapshots. Skill/loot choices, manual pause, disconnect and background gates freeze combat animation with combat. Reconnect preserves snapshots; fresh participants and reused enemy slots reset them. Contact enemies gain no invented attack windup. Cultist retreat plays locomotion backward while retaining aim; boss immediate release and Cultist delayed release retain their existing mechanics.

Projectiles capture launch aim; hostile shots also capture caster kind and elite status by value. Measured visual emitter offsets therefore survive actor turns, death and pool reuse without changing projectile physics or emission timing. Bodies sort by ground root; culling uses all-pose extents and canonical trim geometry. Ground shadows remain separate.

An eight-cell static floor border removes exposed black camera edges. The fixed cache contains **1,116 quads**, with **102,672 vertex/index bytes**; it reuses existing floor textures and does not expand playable bounds. Cached metadata, fixed pools and primitive render loops avoid new per-frame allocation paths.

## Provenance and reproducibility

Original artwork was generated with the **built-in ImageGen tool**, then deterministically normalized with installed Pillow. No API image-generation workflow or tool/dependency installation was used. Exact creative prompts, source hashes, normalization/selection records and canonical pack inputs are preserved in [`art-source/gameplay-canonical.zip`](../art-source/gameplay-canonical.zip). Large generated posters and review videos remain local under `.run/art-exploration/`; the archive explicitly excludes them. Historical exploration statuses remain intact as provenance.

The archive is **1,945,905 bytes / 427 entries**: 426 indexed source/provenance files plus `CANONICAL-SOURCES.json`. Independent archive-only rebuild QA verified every indexed hash and byte count, confined all 236 baker input reads to extraction, and reproduced all three shipped PNGs and `gameplay.json` **byte for byte**, also matching the index's runtime output hashes. Temporary extracted/rebuilt copies were removed afterward. See [source rebuild instructions](../art-source/README.md).

`scripts/BakeGameplayAtlas.py` enforces the complete seven-actor/nineteen-region set and fixed 6 MiB pages. It validates exact saved-page RGBA reconstruction, bounds, nonoverlap, two-pixel gutters and one-pixel edge extrusion. Proposed trim caps are advisory; actual complete fixed-page packing passed. Preview outputs have separate names and are rejected by the runtime parser. `GameplayAtlas` validates the runtime schema before textures load.

## Verification and remaining limits

All **55 focused tests** passed across animation selection, presentation snapshots, atlas metadata, arena combat, late joining, skill pauses and loot pauses. The boss fixture now resolves legitimately triggered timed loot and asserts its freeze before killing the boss; production combat was unchanged by that test correction. Installed JDK 21 and cached Gradle dependencies were used offline.

Brief native TV-emulator checks covered solo/co-op rendering, independent cameras, movement/Dodge, skill-choice freeze and phone acknowledgments, results/Run Details, remote Invite Player 2, and ordinary Home/resume. Fresh Player 2 catch-up granted **17 skill ranks and eight gear rewards**, spawned at **200/200 effective health**, and inherited no offers. Home/resume preserved the server session; forced GL-context loss was not tested.

Local evidence is under `.run/art-exploration/weekend/test-evidence/`, `tv-verification/` and `archive-rebuild-qa/`. Minor cloth occlusion and pose/costume variation remain in generated animation frames. These checks do not establish balance or sustained physical-TV 60 FPS. A final seven-second co-op fixture after floor padding and visibility corrections reported 301 frames: interval p95/p99/max 19.10/20.99/22.56 ms and CPU submission p95/p99/max 5.71/9.99/12.93 ms. There were 159 intervals above 16.7 ms and none above 33.4 ms; the reported frame used 56 draws. The strict displayed-frame budget remains unproven and was exceeded in this emulator window. See [performance evidence and limits](performance.md). The completed feature is merged into develop; the tagged TV playtest handoff is described in [v0.1.0 playtest notes](playtest-v0.1.0.md).
