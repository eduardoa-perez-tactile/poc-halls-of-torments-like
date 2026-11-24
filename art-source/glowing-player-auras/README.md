# Approved glowing player auras

The user approved this in-context visual proposal on 24 November 2026 after sharing
a screenshot of luminous red/violet double rings. The selected treatment uses
gold for Player 1 and teal for Player 2, two fine concentric ground rings, a narrow
soft glow and eight short radial ticks around the feet.

`approved-proposal.png` is a built-in ImageGen visual mockup, using the earlier
native game screenshot as the edit target and the user attachment as the style
reference. It is approval/provenance material, not runtime art. Original actors,
environment and UI assets remain unchanged.

Runtime `PlayerFootRing` reproduces the geometric treatment with cached gradients
over the existing white texel. It adds no textures, framebuffer, retained Pixmaps
or frame-loop allocations. Targeting, damage, progression, waves, healing,
equipment sustain and the Defeat transition remain unchanged. See
`docs/combat-vfx.md` for bounds and verification.
