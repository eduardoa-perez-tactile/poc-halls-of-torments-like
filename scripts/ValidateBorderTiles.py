#!/usr/bin/env python3
"""Independently validate exported border tile pixels, atlas sampling and saved maps.

Reads exported tile PNGs and manifests only. Texture continuity metrics are
descriptive; coverage and exact-reconstruction assertions do not certify art style.
"""
import argparse
import hashlib
import json
import statistics
import subprocess
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MASKS = {0: "interior", 1: "edge-nw", 2: "edge-ne", 4: "edge-se", 8: "edge-sw",
         3: "corner-n", 6: "corner-e", 9: "corner-w", 12: "corner-s"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def same_pixels(first, second, message):
    if first.size != second.size or first.tobytes() != second.tobytes():
        raise AssertionError(message)


def summary(values):
    values = sorted(values)
    return {"samples": len(values), "meanRgbAbsoluteDifference": round(statistics.mean(values), 4),
            "p95RgbAbsoluteDifference": round(values[int((len(values)-1)*.95)], 4),
            "maximumRgbAbsoluteDifference": round(values[-1], 4)}


def atlas_check(path, images):
    data = json.loads(path.read_text())
    page_path = path.parent / data["file"]
    assert digest(page_path) == data["sha256"], "Atlas file hash differs from metadata"
    page = Image.open(page_path).convert("RGBA")
    assert list(page.size) == data["size"]
    assert len(data["tiles"]) == len(images)
    occupied = []
    for record in data["tiles"]:
        x, y, w, h = record["rect"]
        padding = data["padding"]
        region = (x-padding, y-padding, x+w+padding, y+h+padding)
        assert region[0] >= 0 and region[1] >= 0 and region[2] <= page.width and region[3] <= page.height
        assert not any(region[0] < old[2] and region[2] > old[0]
                       and region[1] < old[3] and region[3] > old[1] for old in occupied)
        occupied.append(region)
        original = images[record["id"]]
        if data["scale"] != 1:
            # Source ownership remains hard and aligned after downsampling.
            # Colors may be filtered, but independent fractional mask edges
            # must not combine into semitransparent seams.
            alpha = original.getchannel("A").resize(tuple(record["sourceSize"]), Image.Resampling.NEAREST)
            original = original.resize(tuple(record["sourceSize"]), Image.Resampling.LANCZOS)
            original.putalpha(alpha)
            original = Image.composite(original, Image.new("RGBA", original.size), alpha)
        rebuilt = Image.new("RGBA", tuple(record["sourceSize"]))
        crop = page.crop((x, y, x+w, y+h))
        rebuilt.paste(crop, tuple(record["trimOffset"]))
        same_pixels(rebuilt, original, "Atlas pixel reconstruction failed: " + record["id"])
        for actual, expected in (
            (page.crop((x, y-1, x+w, y)), crop.crop((0, 0, w, 1))),
            (page.crop((x, y+h, x+w, y+h+1)), crop.crop((0, h-1, w, h))),
            (page.crop((x-1, y, x, y+h)), crop.crop((0, 0, 1, h))),
            (page.crop((x+w, y, x+w+1, y+h)), crop.crop((w-1, 0, w, h)))):
            same_pixels(actual, expected, "Atlas extrusion differs: " + record["id"])
        for dx, dy, cx, cy in ((-1, -1, 0, 0), (w, -1, w-1, 0),
                               (-1, h, 0, h-1), (w, h, w-1, h-1)):
            assert page.getpixel((x+dx, y+dy)) == crop.getpixel((cx, cy))
        # The second gutter must remain empty, rather than borrow a neighbor.
        for box in ((x-2, y-2, x+w+2, y-1), (x-2, y+h+1, x+w+2, y+h+2),
                    (x-2, y-1, x-1, y+h+1), (x+w+1, y-1, x+w+2, y+h+1)):
            assert page.crop(box).getchannel("A").getbbox() is None
    return {"file": data["file"], "size": data["size"], "rgbaGpuBytes": page.width*page.height*4,
            "exactTileReconstruction": True, "paddedRectanglesNonoverlapping": True,
            "onePixelExtrusionMatches": True, "secondGutterTransparent": True}


def render_and_check(layout_path, manifest, images, fixture=None):
    if fixture is None:
        layout = json.loads(layout_path.read_text())
        image_path = layout_path.with_suffix(".png")
        saved = Image.open(image_path).convert("RGBA")
        label = image_path.name
    else:
        layout, saved = fixture
        label = f"independent-{layout['columns']}x{layout['rows']}"
    rebuilt = Image.new("RGBA", saved.size)
    columns, rows = layout["columns"], layout["rows"]
    tw, th = manifest["tileWidth"], manifest["tileHeight"]
    ax, ay = manifest["anchor"]
    ox, oy = layout["farOrigin"]
    seen = set()
    previous = (-1, -1)
    for placement in layout["placements"]:
        i, j = placement["i"], placement["j"]
        assert 0 <= i < columns and 0 <= j < rows and (i, j) not in seen
        seen.add((i, j))
        depth = (i+j, i)
        assert depth >= previous
        previous = depth
        mask = (1 if i == 0 else 0) | (2 if j == 0 else 0) | (4 if i == columns-1 else 0) | (8 if j == rows-1 else 0)
        assert placement["id"] == MASKS[mask]
        x, y = (i-j)*tw//2-ax+ox, (i+j)*th//2-ay+oy
        assert placement["x"] == x and placement["y"] == y
        rebuilt.alpha_composite(images[placement["id"]], (int(x), int(y)))
    assert len(seen) == columns*rows
    same_pixels(rebuilt, saved, "Saved map differs from PNG-only independent reassembly")
    def project(u, v):
        return (ox+(u-v)*tw/2, oy+(u+v)*th/2)
    # Inset avoids attributing expected silhouette anti-aliasing to an interior hole.
    inset = .04
    polygon = [project(inset, inset), project(columns-inset, inset),
               project(columns-inset, rows-inset), project(inset, rows-inset)]
    floor_mask = Image.new("L", saved.size)
    ImageDraw.Draw(floor_mask).polygon(polygon, fill=255)
    missing = ImageChops.multiply(floor_mask, ImageChops.invert(saved.getchannel("A")))
    histogram = missing.histogram()
    missing_count = sum(histogram[1:])
    transparent_count = histogram[255]
    assert missing_count == 0, f"Nonopaque pixels inside assembled footprint: {missing_count}"
    result = {"file": label, "size": list(saved.size), "cells": columns*rows,
              "exactPngOnlyReassembly": True, "positionsAndMasksCorrect": True,
              "nonopaqueInteriorPixels": missing_count, "transparentInteriorPixels": transparent_count,
              "footprintInsetWorldCells": inset}
    if columns >= 8 and rows >= 6:
        # Adjacent raster pixels across the two families of floor-cell seams.
        # Control lines sit 0.2 cells away and use the identical sampling operation.
        seam, control = [], []
        for axis in (0, 1):
            limit, length = (columns, rows) if axis == 0 else (rows, columns)
            for boundary in range(2, limit-1):
                for step in range(80, int((length-1)*40)):
                    along = step/40
                    for offset, output in ((0, seam), (.2, control)):
                        coordinate = boundary+offset
                        u, v = (coordinate, along) if axis == 0 else (along, coordinate)
                        x, y = project(u, v)
                        first = saved.getpixel((round(x-1), round(y)))
                        second = saved.getpixel((round(x+1), round(y)))
                        output.append(sum(abs(first[c]-second[c]) for c in range(3))/3)
        result["floorSeamTexture"] = summary(seam)
        result["floorControlTexture"] = summary(control)
        result["textureMetricMeaning"] = "Descriptive two-pixel RGB differences; grout, wear and color variation are not transparency gaps. No universal aesthetic pass threshold."
    return result


def thin_fixture(columns, rows, manifest, images):
    """Exercise the smallest rectangles and extended runs without invoking packer code."""
    tw, th = manifest["tileWidth"], manifest["tileHeight"]
    ax, ay = manifest["anchor"]
    ox, oy = rows*tw//2+ax+24, ay+24
    layout = {"columns": columns, "rows": rows, "farOrigin": [ox, oy], "placements": []}
    image = Image.new("RGBA", ((columns+rows)*tw//2+ax*2+48,
                               (columns+rows)*th//2+manifest["canvasHeight"]+48))
    for depth in range(columns+rows-1):
        for i in range(columns):
            j = depth-i
            if not 0 <= j < rows:
                continue
            mask = (1 if i == 0 else 0) | (2 if j == 0 else 0) | (4 if i == columns-1 else 0) | (8 if j == rows-1 else 0)
            tile_id = MASKS[mask]
            x, y = ox+(i-j)*tw//2-ax, oy+(i+j)*th//2-ay
            image.alpha_composite(images[tile_id], (x, y))
            layout["placements"].append({"id": tile_id, "i": i, "j": j, "x": x, "y": y})
    return render_and_check(None, manifest, images, (layout, image))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT/"art-source/border-tiles-v1")
    parser.add_argument("--review", type=Path, default=ROOT/".run/border-tiles-review")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    manifest_path = args.input/"tiles.json"
    manifest = json.loads(manifest_path.read_text())
    assert manifest["tileWidth"] == manifest["tileHeight"]*2
    assert manifest["logicalWorldCell"] == 120
    assert digest(args.input/manifest["source"]["file"]) == manifest["source"]["sha256"]
    assert len(manifest["tiles"]) == 9
    images, tile_results = {}, []
    for tile in manifest["tiles"]:
        path = args.input/tile["file"]
        assert digest(path) == tile["sha256"], "Tile hash mismatch: " + tile["id"]
        image = Image.open(path).convert("RGBA")
        assert image.size == (manifest["canvasWidth"], manifest["canvasHeight"])
        assert tile["id"] == MASKS[tile["mask"]] and tile["id"] not in images
        alpha = image.getchannel("A")
        histogram = alpha.histogram()
        assert not sum(histogram[1:255]), "Unexpected translucent tile interior/exterior pixels"
        bounds = alpha.getbbox()
        assert bounds and bounds[0] > 0 and bounds[1] > 0 and bounds[2] < image.width and bounds[3] < image.height
        images[tile["id"]] = image
        tile_results.append({"id": tile["id"], "size": list(image.size), "alphaBounds": list(bounds),
                             "sha256Matches": True, "binaryAlpha": True, "canvasHasTransparentMargin": True})
    assert set(images) == set(MASKS.values())
    atlases = [atlas_check(path, images) for path in sorted(args.review.glob("border-tiles*atlas.json"))]
    assert len(atlases) == 2
    maps = [render_and_check(args.review/f"assembled-{columns}x{rows}.json", manifest, images)
            for columns, rows in ((3, 3), (8, 6), (20, 15))]
    fixtures = [thin_fixture(columns, rows, manifest, images)
                for columns, rows in ((2, 2), (2, 15), (20, 2))]
    half_images = {tile_id: Image.open(args.review/"half-tiles"/f"{tile_id}.png").convert("RGBA")
                   for tile_id in images}
    half_atlas = json.loads((args.review/"border-tiles-half-atlas.json").read_text())
    half_page = Image.open(args.review/half_atlas["file"]).convert("RGBA")
    for record in half_atlas["tiles"]:
        x, y, w, h = record["rect"]
        rebuilt = Image.new("RGBA", tuple(record["sourceSize"]))
        rebuilt.paste(half_page.crop((x, y, x+w, y+h)), tuple(record["trimOffset"]))
        same_pixels(rebuilt, half_images[record["id"]], "Half exported tile differs from saved atlas")
    half_manifest = dict(manifest)
    for key in ("tileWidth", "tileHeight", "canvasWidth", "canvasHeight"):
        half_manifest[key] //= 2
    half_manifest["anchor"] = [value//2 for value in manifest["anchor"]]
    half_map = render_and_check(args.review/"assembled-20x15-half.json", half_manifest, half_images)
    changed = subprocess.check_output(["git", "diff", "--name-only", "develop"], cwd=ROOT, text=True).splitlines()
    changed += subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT, text=True).splitlines()
    runtime = [name for name in changed if name.startswith(("core/", "android/", "desktop/", "assets/"))]
    assert not runtime, "Runtime code/assets changed: " + repr(runtime)
    report = {"status": "passed-structural-and-pixel-checks", "independent": True,
              "renderSource": "Exported tile PNGs only, never painted master", "tiles": tile_results,
              "atlases": atlases, "maps": maps, "smallAndThinFixtures": fixtures, "halfScaleMap": half_map,
              "halfTileFilesMatchSavedAtlas": True,
              "runtimeChangesAgainstDevelop": runtime,
              "visualReview": {"reviewed": ["assembled-8x6.jpg", "assembled-20x15.jpg", "join-rear-straight.jpg", "join-far-corner.jpg", "join-near-corner.jpg"],
                               "finding": "Continuous wall silhouette, coping and footing; no open corner gaps or doubled pillars seen. Repeated floor texture is still discernible.",
                               "limits": "Art-only PNG checks do not verify runtime layer sorting, collision alignment, filtering/zoom on GPU, TV readability or performance."}}
    output = args.output or args.review/"independent-qa.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps({"status": report["status"], "tiles": len(images), "atlases": len(atlases),
                      "maps": len(maps), "smallAndThinFixtures": len(fixtures),
                      "coverageGaps": sum(m["nonopaqueInteriorPixels"] for m in maps+fixtures+[half_map]),
                      "report": str(output)}, indent=2))


if __name__ == "__main__":
    main()
