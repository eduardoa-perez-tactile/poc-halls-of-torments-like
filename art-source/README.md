# Gameplay art sources

`gameplay-canonical.zip` preserves the exact selected canonical PNGs, actor/environment manifests, original creative prompts, normalization/selection records and source hashes for the current seven-actor crypt kit. Artwork came from built-in ImageGen; installed Pillow performs deterministic exports and atlas packing. No image API, installation or dependency download is needed for a rebuild.

The archive is **1,945,905 bytes**, with **427 entries**. `CANONICAL-SOURCES.json` indexes the other 426 entries by SHA-256 and byte count and records the four expected runtime output hashes. Historical review statuses are preserved; final inclusion is established by the complete strict pack. Raw generated posters and review media remain local in `.run/art-exploration/` and are excluded from this compact archive.

## Rebuild

From the repository root, when `.run/art-exploration/` has no local files to preserve, use the installed Python/Pillow environment:

```sh
python3 -m zipfile -e art-source/gameplay-canonical.zip .run/art-exploration
python3 scripts/BakeGameplayAtlas.py
```

The strict baker writes `.run/art-exploration/weekend/packed/`, leaving `assets/art/` unchanged. It requires all seven actors and nineteen regions, preserves canonical pixels/timing/roots, and validates the fixed 6 MiB page layout, saved RGBA reconstruction, nonoverlap and gutters. Confirm exact agreement with the shipped output:

```sh
for file in gameplay-actors-main.png gameplay-actors-common.png gameplay-environment.png gameplay.json; do
  cmp ".run/art-exploration/weekend/packed/$file" "assets/art/$file" || exit 1
done
```

Independent QA already verified all indexed source hashes/byte counts, confined all baker input reads to an isolated archive extraction, and rebuilt all four runtime files byte for byte against both `assets/art/` and the archive index hashes. The strict pack contains **208 actor frames + 19 environment regions = 227 records**. Local verification evidence is in `.run/art-exploration/weekend/archive-rebuild-qa/`; temporary extracted inputs and rebuilt outputs were cleaned after success. This proves reproducibility, not visual quality or TV performance.

`scripts/ArchiveGameplaySources.py` recreates the compact archive from a complete strict local pack plus provenance and refuses incomplete input. It is an archival step for changed approved sources, not part of a normal rebuild.

`legacy-gameplay/` retains the four superseded original `game-*.png` masters outside runtime assets. Their exact initial prompts remain in [the historical art record](../docs/gameplay-art.md). See [current integration and verification](../docs/gameplay-art-integration.md) for resource accounting and known limits.
