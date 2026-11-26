# Isometric skill readability — 26 November 2026

`approved-proposal.png` is the original built-in ImageGen concept approved by the
user before implementation. It edits the user's all-skills Warden screenshot,
then brightens the active ground auras following their feedback. It is a design
reference, **not an in-game capture or a runtime texture**.

The direction is projected/foreshortened moving weapons, short real-trajectory
trails, floor-aligned painted strikes, grounded collectible relics and brighter
active auras. The existing painted art style and gameplay remain authoritative.
The user explicitly required actual game screenshots after implementation.

Production reuses the existing Sentinel/Glaive/relic/Consecration artwork from
`art-source/survivor-expansion/` and the approved painted crescent from
`art-source/warden-melee-wave/`. No generated pixels from this board enter the
runtime atlases. No texture size or decoded-pixmap allocation increases.

Generation provenance: built-in `image_gen.imagegen`, 26 November 2026.
Initial board: `exec-5c162925-0dcb-4676-9be5-e1123f202812.png`.
Approved brighter-aura edit: `exec-461641e0-580a-4893-b210-b87bb9e9ca39.png`.
The input and boards are original Couch Crypt imagery, without downloaded art.

See `docs/combat-vfx.md` for the runtime implementation and actual verification.
