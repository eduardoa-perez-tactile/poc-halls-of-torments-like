# Approved Grave King artwork

On 24 November 2026 the user approved `approved-gameplay-proposal.png`: a large
floating ivory skull with a broken black-iron crown, worn brass edges and small
amber eye lights. The ornate shrouded Regent and the subsequent plain hooded
ghost were rejected. The approved image is a generated screenshot mockup, not a
capture of executable gameplay. Runtime evidence is recorded separately.

`generated-facing-sheet.png` was made with the built-in `image_gen` tool, using
that approved proposal as the identity/style reference. The exact production
prompt is in `generation-prompt.txt`. It contains four authored diagonal facings
(SW, SE, NE, NW) in the top idle row and the same four casting poses in the bottom
row. Back views show the rear cranium and crown, without a face painted onto the
back. No external art pack or purchased artwork was used.

Reproduce the runtime export with:

```sh
python3 scripts/BakeGraveKing.py
python3 scripts/BakeGraveKing.py --check
```

Run this stage after the existing gameplay, attack, border and floor stages.
The source can also be passed explicitly with `--source` (directory, PNG or ZIP).
The baker applies one global scale and shared idle/cast crops per facing, keeps
the crown fixed while the jaw opens, and uses a 128x144 logical canvas with the
ground pivot at (64,144). It adds eight frames to `gameplay.json` and one managed
512x256 RGBA `gameplay-grave-king.png` page (0.5 MiB), with two-pixel gutters and
one-pixel edge extrusion. No existing frame, actor or region is repacked, and
the three previous PNG pages remain byte-identical.

Runtime reuses the idle pose during movement and switches to the painted cast
pose during windup/release. A small hover reads the frozen combat clock; shadow,
targeting and depth sorting remain at the ground root. Hit/death feedback reuses
resident effects. The floating skull has no walking animation, fog, trailing
debris or new effect texture. The combined texture budget is preserved by ending
the unused legacy full-body portrait GPU load, while retaining that source art.
