# Ranger bolts and stronger Warden contact — 26 November 2026

`approved-proposal.png` is the original built-in ImageGen review board approved
by the user. Its three Ranger panels show release, a sharp tapered projectile
and a radial contact bloom. Its two Warden panels compare the previous weak
contact with a broader ivory/gold cut and short sparks. The board is a generated
design reference, **not an actual game capture or a runtime texture**.

Provenance: built-in `image_gen.imagegen`, 26 November 2026,
`exec-162af14a-71bd-457b-9d6e-8e90fc95fa31.png`. Input references are actual
Couch Crypt solo/co-op native renders from the preceding isometric skill pass.
No external game artwork was downloaded.

The approved request preserves painted style, world perspective and weapon
height; damage, speed, collision sizes, paths and attack timing stay unchanged.
Ranger impacts are radial and Warden contacts remain directional. The Warden
primary ground wave is outside this contact change.

Production reuses the original painted Ranger projectile and contact/sword-hit
animations, plus the existing soft point and white utility texel. No board
pixels are shipped, and no runtime raster, atlas size or texture count changes.
See `docs/combat-vfx.md` for implementation and verification.
