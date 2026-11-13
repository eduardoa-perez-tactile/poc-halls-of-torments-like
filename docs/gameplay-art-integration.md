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
