#!/usr/bin/env python3
"""Pack the approved brighter elite chest into resident Grave King atlas space.

No existing frame moves. The one-pixel extrusion occupies only verified empty
space inside a two-pixel reservation. Run after the existing gameplay art stages.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "assets/art"
SOURCE = ROOT / "art-source/elite-loot-chest/v2"
PAGE = "gameplay-grave-king.png"
REGION = "elite-chest"
X, Y, WIDTH = 464, 4, 46
GUTTER = 2
WORLD_HEIGHT = 52
GROUND_PIVOT_Y = 35


def sha(data):
    return hashlib.sha256(data).hexdigest()


def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def pack():
    metadata_path = ART / "gameplay.json"
    metadata = json.loads(metadata_path.read_text())
    page_index = next(i for i, page in enumerate(metadata["pages"]) if page["file"] == PAGE)
    old_region = metadata["regions"].get(REGION)
    old_index = old_region["frame"] if old_region else None
    before = Image.open(ART / PAGE).convert("RGBA")
    assert before.size == (512, 256)

    # Supplemental page indices are local to each metadata file. In particular,
    # engraved-floor page 3 is arena-border.png, not gameplay page 3.
    supplemental = {}
    for name in ("enemy-attacks.json", "magic-projectiles.json", "painted-borders.json", "engraved-floor.json"):
        data = json.loads((ART / name).read_text())
        names = [p["file"] for p in data.get("pages", [])]
        if "file" in data:
            names.append(data["file"])
        assert PAGE not in names, f"Supplemental art now uses {PAGE}: {name}"
        supplemental[name] = names

    master = Image.open(SOURCE / "generated-master.png").convert("RGBA")
    # Ignore only near-transparent generation specks when choosing crop bounds;
    # retain the generated alpha inside that crop, including antialiased edges.
    bounds = master.getchannel("A").point(lambda a: 255 if a >= 32 else 0).getbbox()
    assert bounds
    crop = master.crop(bounds)
    height = round(crop.height * WIDTH / crop.width)
    sprite = crop.resize((WIDTH, height), Image.Resampling.LANCZOS)
    patch = (X - 1, Y - 1, X + WIDTH + 1, Y + height + 1)
    reservation = (X - GUTTER, Y - GUTTER, X + WIDTH + GUTTER, Y + height + GUTTER)
    old_patch = patch
    if old_index is not None:
        old_x, old_y, old_w, old_h = metadata["frames"][old_index]["rect"]
        old_patch = (old_x - 1, old_y - 1, old_x + old_w + 1, old_y + old_h + 1)
    union_patch = (min(patch[0], old_patch[0]), min(patch[1], old_patch[1]),
                   max(patch[2], old_patch[2]), max(patch[3], old_patch[3]))
    assert reservation[2] <= before.width and reservation[3] <= before.height
    checked_frames = 0
    for i, frame in enumerate(metadata["frames"]):
        if frame["page"] != page_index or i == old_index:
            continue
        x, y, w, h = frame["rect"]
        reserved = (max(0, x - GUTTER), max(0, y - GUTTER), min(before.width, x + w + GUTTER), min(before.height, y + h + GUTTER))
        assert not intersects(reservation, reserved), f"Chest overlaps existing frame {i}"
        checked_frames += 1
    available = before.copy()
    if old_index is not None:
        available.paste((0, 0, 0, 0), old_patch)
    assert available.crop(patch).getchannel("A").getbbox() is None, "Chest target is not empty"

    after = before.copy()
    # Clear the complete old sprite/gutter before a replacement, including any
    # rows left behind by a future smaller source. Never touch adjacent art.
    after.paste((0, 0, 0, 0), union_patch)
    for py in range(-1, height + 1):
        for px in range(-1, WIDTH + 1):
            after.putpixel((X + px, Y + py), sprite.getpixel((min(WIDTH - 1, max(0, px)), min(height - 1, max(0, py)))))

    # Exact RGBA preservation, including invisible RGB, for every pixel outside
    # the added sprite and its one-pixel sampling neighbours.
    outside_before = before.copy()
    outside_after = after.copy()
    outside_before.paste((0, 0, 0, 0), union_patch)
    outside_after.paste((0, 0, 0, 0), union_patch)
    assert outside_before.tobytes() == outside_after.tobytes()

    frame = {"page": page_index, "rect": [X, Y, WIDTH, height], "source": [WIDTH, height], "offset": [0, 0], "pivot": [WIDTH / 2, GROUND_PIVOT_Y]}
    if old_index is None:
        old_index = len(metadata["frames"])
        metadata["frames"].append(frame)
    else:
        metadata["frames"][old_index] = frame
    metadata["regions"][REGION] = {"frame": old_index, "worldHeight": WORLD_HEIGHT, "footprintWidth": 44, "footprintWorldX": 48, "footprintWorldY": 32}
    after.save(ART / PAGE)
    sprite.save(SOURCE / "chest-runtime.png")
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
    report = {"source": "generated-master.png", "sourceSha256": sha((SOURCE / "generated-master.png").read_bytes()), "approvedMockupSha256": sha((SOURCE / "approved-in-arena-mockup.png").read_bytes()), "alphaCropThreshold": 32, "sourceCrop": list(bounds), "page": PAGE, "pageIndex": page_index, "pageSize": list(after.size), "frame": old_index, "rect": frame["rect"], "pivot": frame["pivot"], "worldHeight": WORLD_HEIGHT, "gutter": GUTTER, "edgeExtrusion": 1, "reservedRect": list(reservation), "clearedUnionPatch": list(union_patch), "existingPageFramesChecked": checked_frames, "supplementalPageFilesChecked": supplemental, "outsidePatchRgbaSha256": sha(outside_after.tobytes()), "outsidePatchPixels": "unchanged", "addedTextureBytes": 0, "retainedDecodedPixmapBytes": 0}
    (SOURCE / "pack-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    pack()
