# Warden melee wave — 25 November 2026

The user selected a broad outward crescent, requested an actual sprite rather
than hand-drawn mockup art, corrected the first generated sheet to isometric,
then approved that sheet for replacement of the Warden's primary slash. They
authorized damage synchronized with the expanding wave and requested no device
testing while another agent uses the TV.

Both sheets are original raster artwork generated with the built-in `image_gen`
tool on 2026-11-25. `initial-sheet.png` is the first proposal;
`approved-isometric-sheet.png` is the approved edit. Exact tool prompts are in
`prompts.json`. No external artwork was downloaded. The approved source SHA-256
is `35665d167e7798dd3f362b5bc3eab9c36650cc9f5cd57c945b8148d2aea7269c`.

Production uses the full-reach third cell: source crop `[0,620,633,1241]` from the
1267×1241 master. `scripts/BakeMeleeWave.py` measures its ivory rim in the 2:1
ground plane, preserves its painted colour/alpha body and tapered edge, and
normalizes the generated angular span to the existing 110-degree attack cone.
The fitted source centre is `(329.22064,311.39679)`, reference horizontal radius
255.59042, angular span −87→141 degrees (image Y down). The local exported master
is 160×128 with fixed pivot `(72,64)`, radius 72 and SE heading. Its bright ridge
is the radius reference; faint antialias/bloom can extend slightly beyond it.

One master supplies continuous host-clock expansion and fade, avoiding visible
steps, generated anchor drift and unnecessary duplicate frames. The old eight
compact slash frames are removed from the resident bank; 64 unrelated frames
retain exact pixel and sampling-gutter content. The final bank has 65 frames in
the same managed 512×512 RGBA texture (1 MiB), no new texture allocation or
retained decoded Pixmap. Both public bakers produce this replacement:

```sh
python3 scripts/BakeMeleeWave.py
# Or rebuild the full current bank through its existing entry point:
python3 scripts/BakeSpriteEffects.py
```

Source generation is a preview/art-authoring step; these sheets are not a native
game capture. Device rendering and gameplay feel remain for the user's later
playtest.
