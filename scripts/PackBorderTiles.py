#!/usr/bin/env python3
"""Pack review-only border PNGs and render maps from the exported pixels.

This script never writes to assets/ or invokes image generation. Input artwork,
anchors and geometry come exclusively from tiles.json and its PNG files.
"""
import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
REQUIRED = ("interior", "edge-nw", "edge-ne", "edge-sw", "edge-se",
            "corner-n", "corner-w", "corner-e", "corner-s")
PADDING = 2


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(folder):
    manifest = json.loads((folder / "tiles.json").read_text())
    width, height = manifest["tileWidth"], manifest["tileHeight"]
    if width != height * 2 or width % 2 or height % 2:
        raise ValueError("Expected an even, exact 2:1 floor footprint")
    records = {tile["id"]: tile for tile in manifest["tiles"]}
    if set(records) != set(REQUIRED):
        raise ValueError("tiles.json must contain exactly the nine supported tile IDs")
    images = {}
    for tile_id in REQUIRED:
        path = (folder / records[tile_id]["file"]).resolve()
        if not path.is_relative_to(folder.resolve()):
            raise ValueError("Tile file escapes its source folder")
        with Image.open(path) as image:
            if image.mode != "RGBA":
                raise ValueError(f"Expected RGBA PNG: {path}")
            images[tile_id] = image.copy()
        if not images[tile_id].getchannel("A").getbbox():
            raise ValueError(f"Empty tile: {tile_id}")
    return manifest, records, images


def tile_at(i, j, columns, rows):
    if columns < 2 or rows < 2:
        raise ValueError("The nine-tile kit represents rooms at least 2 x 2 cells")
    if i == 0 and j == 0:
        return "corner-n"
    if i == columns - 1 and j == 0:
        return "corner-e"
    if i == 0 and j == rows - 1:
        return "corner-w"
    if i == columns - 1 and j == rows - 1:
        return "corner-s"
    if i == 0:
        return "edge-nw"
    if j == 0:
        return "edge-ne"
    if i == columns - 1:
        return "edge-se"
    if j == rows - 1:
        return "edge-sw"
    return "interior"


def render(manifest, images, columns, rows, margin=48):
    tw, th = manifest["tileWidth"], manifest["tileHeight"]
    ax, ay = manifest["anchor"]
    placements = []
    for depth in range(columns + rows - 1):
        for i in range(columns):
            j = depth - i
            if not 0 <= j < rows:
                continue
            tile_id = tile_at(i, j, columns, rows)
            placements.append({"id": tile_id, "i": i, "j": j,
                               "x": (i-j)*tw//2-ax,
                               "y": (i+j)*th//2-ay})
    x0 = min(p["x"] for p in placements)
    y0 = min(p["y"] for p in placements)
    x1 = max(p["x"] + images[p["id"]].width for p in placements)
    y1 = max(p["y"] + images[p["id"]].height for p in placements)
    image = Image.new("RGBA", (x1-x0+2*margin, y1-y0+2*margin))
    for p in placements:
        p["x"] += margin-x0
        p["y"] += margin-y0
        image.alpha_composite(images[p["id"]], (p["x"], p["y"]))
    return image, {"columns": columns, "rows": rows,
                   "order": "ascending i+j, then ascending i",
                   "placements": placements,
                   "farOrigin": [margin-x0, margin-y0]}


def fit(rectangles, width, height):
    """Deterministic non-rotating free-rectangle pack, with contained splits removed."""
    free = [(0, 0, width, height)]
    placed = {}
    for tile_id, rw, rh in sorted(rectangles,
                                  key=lambda r: (-max(r[1], r[2]), -r[1]*r[2], r[0])):
        candidates = [(min(fw-rw, fh-rh), max(fw-rw, fh-rh), fy, fx, n)
                      for n, (fx, fy, fw, fh) in enumerate(free)
                      if rw <= fw and rh <= fh]
        if not candidates:
            return None
        *_, index = min(candidates)
        px, py, _, _ = free[index]
        placed[tile_id] = (px, py)
        split = []
        for fx, fy, fw, fh in free:
            if px >= fx+fw or px+rw <= fx or py >= fy+fh or py+rh <= fy:
                split.append((fx, fy, fw, fh))
                continue
            if px > fx:
                split.append((fx, fy, px-fx, fh))
            if px+rw < fx+fw:
                split.append((px+rw, fy, fx+fw-px-rw, fh))
            if py > fy:
                split.append((fx, fy, fw, py-fy))
            if py+rh < fy+fh:
                split.append((fx, py+rh, fw, fy+fh-py-rh))
        free = [a for n, a in enumerate(split) if not any(
            n != m and a[0] >= b[0] and a[1] >= b[1]
            and a[0]+a[2] <= b[0]+b[2] and a[1]+a[3] <= b[1]+b[3]
            and (a != b or n > m) for m, b in enumerate(split))]
    return placed


def half_tile(image):
    """Filter color but preserve the shared raster ownership of touching cells.

    Filtering each hard cell mask separately creates two partially transparent
    seam pixels whose source-over composition can never reach full opacity.
    Aligned nearest-neighbor alpha samples select the same source ownership as
    the full-size map; existing outer-edge alpha values are not fabricated.
    """
    size = (image.width//2, image.height//2)
    result = image.resize(size, Image.Resampling.LANCZOS)
    alpha = image.getchannel("A").resize(size, Image.Resampling.NEAREST)
    result.putalpha(alpha)
    nonzero = alpha.point(lambda a: 255 if a else 0)
    return Image.composite(result, Image.new("RGBA", size), nonzero)


def pack(manifest, images, output, name, scale=1, fixed=None):
    if scale == 1:
        resized = images
    else:
        resized = {key: half_tile(image) for key, image in images.items()}
    bounds = {key: image.getchannel("A").getbbox() for key, image in resized.items()}
    rectangles = [(key, box[2]-box[0]+2*PADDING, box[3]-box[1]+2*PADDING)
                  for key, box in bounds.items()]
    candidates = [fixed] if fixed else [(512, 256), (512, 512), (1024, 512),
                                       (1024, 1024), (2048, 1024)]
    placement = None
    for size in candidates:
        placement = fit(rectangles, *size)
        if placement is not None:
            break
    if placement is None:
        return {"packed": False, "requestedSize": fixed,
                "reason": "Trimmed sprites and 2px sampling gutters do not fit"}, None
    occupied = []
    for tile_id, rw, rh in rectangles:
        x, y = placement[tile_id]
        if x < 0 or y < 0 or x+rw > size[0] or y+rh > size[1]:
            raise AssertionError("Packed tile/gutter exceeds the page")
        if any(x < ox+ow and x+rw > ox and y < oy+oh and y+rh > oy
               for ox, oy, ow, oh in occupied):
            raise AssertionError("Packed tile/gutters overlap")
        occupied.append((x,y,rw,rh))
    page = Image.new("RGBA", size)
    records = []
    for tile_id in REQUIRED:
        box = bounds[tile_id]
        crop = resized[tile_id].crop(box)
        x, y = placement[tile_id]
        x += PADDING
        y += PADDING
        page.paste(crop, (x, y))
        w, h = crop.size
        # First gutter pixel extrudes RGBA edges; the second remains transparent.
        page.paste(crop.crop((0, 0, w, 1)), (x, y-1))
        page.paste(crop.crop((0, h-1, w, h)), (x, y+h))
        page.paste(crop.crop((0, 0, 1, h)), (x-1, y))
        page.paste(crop.crop((w-1, 0, w, h)), (x+w, y))
        for dx, dy, cx, cy in ((-1,-1,0,0), (w,-1,w-1,0),
                               (-1,h,0,h-1), (w,h,w-1,h-1)):
            page.putpixel((x+dx,y+dy), crop.getpixel((cx,cy)))
        records.append({"id": tile_id, "rect": [x,y,w,h],
                        "sourceSize": list(resized[tile_id].size),
                        "trimOffset": [box[0], box[1]],
                        "anchor": [v*scale for v in manifest["anchor"]]})
    path = output / f"{name}.png"
    page.save(path)
    rebuilt = {}
    with Image.open(path) as saved:
        for record in records:
            x,y,w,h = record["rect"]
            canvas = Image.new("RGBA", record["sourceSize"])
            canvas.paste(saved.crop((x,y,x+w,y+h)), tuple(record["trimOffset"]))
            if canvas.tobytes() != resized[record["id"]].tobytes():
                raise AssertionError(f"Atlas changes pixels: {record['id']}")
            rebuilt[record["id"]] = canvas
    result = {"packed": True, "file": path.name, "size": list(size),
              "rgbaGpuBytes": size[0]*size[1]*4,
              "scale": scale, "padding": PADDING, "edgeExtrusion": 1,
              "tileWidth": manifest["tileWidth"]*scale,
              "tileHeight": manifest["tileHeight"]*scale,
              "anchor": [v*scale for v in manifest["anchor"]],
              "tiles": records, "sha256": digest(path),
              "exactReconstruction": True}
    if scale != 1:
        result["resampling"] = {"color": "Lanczos", "alpha": "nearest shared ownership",
                                "zeroAlphaRgb": "zeroed"}
    (output / f"{name}.json").write_text(json.dumps(result, indent=2)+"\n")
    return result, rebuilt


def jpg(image, path, limit=2000):
    bg = Image.new("RGBA", image.size, "#11191f")
    bg.alpha_composite(image)
    bg = bg.convert("RGB")
    bg.thumbnail((limit, limit), Image.Resampling.LANCZOS)
    bg.save(path, quality=94, subsampling=0)


def contact(manifest, images, path):
    cw = max(im.width for im in images.values()) + 50
    ch = max(im.height for im in images.values()) + 64
    sheet = Image.new("RGBA", (cw*3, ch*3), "#11191f")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(str(ROOT/"assets/fonts/lato.ttf"), 18)
    except OSError:
        font = ImageFont.load_default()
    for n, tile_id in enumerate(REQUIRED):
        x, y = n%3*cw, n//3*ch
        tile = images[tile_id]
        sheet.alpha_composite(tile, (x+(cw-tile.width)//2, y+14))
        draw.text((x+22, y+ch-34), tile_id, fill="#d4b47c", font=font)
    sheet.save(path)
    jpg(sheet, path.with_suffix(".jpg"))


def footprint_alpha(manifest, image, layout):
    """Measure opacity in the assembled room, excluding its outer silhouette."""
    tw, th = manifest["tileWidth"], manifest["tileHeight"]
    columns, rows = layout["columns"], layout["rows"]
    x, y = layout["farOrigin"]
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).polygon(((x,y), (x+columns*tw//2,y+columns*th//2),
                                 (x+(columns-rows)*tw//2,y+(columns+rows)*th//2),
                                 (x-rows*tw//2,y+rows*th//2)), fill=255)
    mask = mask.filter(ImageFilter.MinFilter(5))
    histogram = image.getchannel("A").histogram(mask)
    return {"footprintInsetPixels": 2,
            "minimumAlpha": next((i for i,n in enumerate(histogram) if n), None),
            "transparentPixels": histogram[0],
            "partialAlphaPixels": sum(histogram[1:255]),
            "opaquePixels": histogram[255]}


def half_review(manifest, images, rebuilt, output):
    values = [manifest["tileWidth"],manifest["tileHeight"],*manifest["anchor"]]
    if any(v % 2 for v in values):
        raise ValueError("Half-scale review requires even footprint and anchor coordinates")
    half_manifest = dict(manifest)
    half_manifest.update(tileWidth=manifest["tileWidth"]//2,
                         tileHeight=manifest["tileHeight"]//2,
                         anchor=[v//2 for v in manifest["anchor"]])
    # Persist and reload the independently resized PNGs for the comparison.
    source_folder = output/"half-tiles"
    source_folder.mkdir(exist_ok=True)
    expected_tiles = {}
    legacy_tiles = {}
    for tile_id, image in images.items():
        path = source_folder/f"{tile_id}.png"
        half_tile(image).save(path)
        with Image.open(path) as saved:
            expected_tiles[tile_id] = saved.copy()
        legacy_tiles[tile_id] = image.resize((image.width//2,image.height//2),
                                             Image.Resampling.LANCZOS)
    actual, layout = render(half_manifest, rebuilt, 20, 15)
    expected, _ = render(half_manifest, expected_tiles, 20, 15)
    if actual.tobytes() != expected.tobytes():
        raise AssertionError("Half-scale atlas map differs from half-scale PNG assembly")
    legacy, legacy_layout = render(half_manifest, legacy_tiles, 20, 15)
    prefix = "assembled-20x15-half"
    actual.save(output/f"{prefix}.png")
    jpg(actual, output/f"{prefix}.jpg")
    layout["source"] = "Reconstructed half-scale atlas sprites"
    (output/f"{prefix}.json").write_text(json.dumps(layout,indent=2)+"\n")
    return {"file":prefix+".png", "layout":prefix+".json", "cells":300,
            "sha256":digest(output/f"{prefix}.png"),
            "expectedSource": "Exported and reloaded half-tiles/*.png",
            "exactHalfPngAssemblyMatch": True,
            "opacity": footprint_alpha(half_manifest,actual,layout),
            "unfixedLanczosOpacity":footprint_alpha(half_manifest,legacy,legacy_layout)}


def copy_package(input_folder, review_folder, package_folder, report):
    """Keep the distributable package limited to atlas files and its own report."""
    package_folder.mkdir(parents=True, exist_ok=True)
    copied = []
    for item in (report["atlas"],report["halfScaleCandidate"]):
        if not item["packed"]:
            continue
        for filename in (item["file"],Path(item["file"]).with_suffix(".json").name):
            source, target = review_folder/filename, package_folder/filename
            if source.resolve() != target.resolve():
                shutil.copyfile(source,target)
            copied.append(filename)
    packaged = {"source":os.path.relpath(input_folder/"tiles.json",package_folder),
                "renderSource":report["renderSource"], "atlas":report["atlas"],
                "halfScaleCandidate":report["halfScaleCandidate"],
                "contents":copied+["pack-report.json"],
                "verification":{
                    "maps":[{k:v for k,v in item.items() if k != "file"}
                            for item in report["maps"]],
                    "reviewImagesIncluded":False}}
    if "halfScaleMap" in report:
        packaged["verification"]["halfScaleMap"] = {
            k:v for k,v in report["halfScaleMap"].items()
            if k not in ("file","layout","expectedSource")}
    (package_folder/"pack-report.json").write_text(json.dumps(packaged,indent=2)+"\n")


def export_runtime(review_folder, output_folder, atlas):
    """Only the bounded half-size page enters the game's managed asset bank."""
    if not atlas["packed"] or atlas["size"] != [512, 256]:
        raise ValueError("Runtime border atlas must fit the 512x256 texture budget")
    output_folder.mkdir(parents=True, exist_ok=True)
    filename = "arena-border.png"
    shutil.copyfile(review_folder/atlas["file"], output_folder/filename)
    metadata = {"version": 1, "file": filename,
                **{key: atlas[key] for key in ("size", "tileWidth", "tileHeight", "anchor",
                                                "padding", "edgeExtrusion", "tiles")},
                "sourceAtlasSha256": atlas["sha256"]}
    (output_folder/"arena-border.json").write_text(json.dumps(metadata, indent=2)+"\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT/"art-source/border-tiles-v1")
    parser.add_argument("--output", type=Path, default=ROOT/".run/border-tiles-review")
    parser.add_argument("--package-output", type=Path,
                        help="Copy only atlas PNG/JSON files and a package-scoped report here")
    parser.add_argument("--runtime-output", type=Path,
                        help="Export the half-MiB managed border page and runtime metadata here")
    args = parser.parse_args()
    try:
        manifest, records, images = load(args.input)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"Cannot read border tile inputs: {error}\n")
    args.output.mkdir(parents=True, exist_ok=True)
    atlas, rebuilt = pack(manifest, images, args.output, "border-tiles-atlas")
    if not atlas["packed"]:
        parser.exit(2, "Full-size atlas exceeds maximum review page\n")
    half, half_rebuilt = pack(manifest, images, args.output, "border-tiles-half-atlas",
                              scale=.5, fixed=(512,256))
    contact(manifest, images, args.output/"tile-contact-sheet.png")
    report = {"source": str(args.input.resolve()),
              "renderSource": "Exported tile PNGs only; source master is never read",
              "atlas": atlas, "halfScaleCandidate": half, "maps": []}
    for columns, rows in ((3,3),(8,6),(20,15)):
        image, layout = render(manifest, images, columns, rows)
        packed_image, _ = render(manifest, rebuilt, columns, rows)
        if image.tobytes() != packed_image.tobytes():
            raise AssertionError("Map from reconstructed atlas differs from PNG map")
        prefix = f"assembled-{columns}x{rows}"
        image.save(args.output/f"{prefix}.png")
        jpg(image, args.output/f"{prefix}.jpg")
        (args.output/f"{prefix}.json").write_text(json.dumps(layout,indent=2)+"\n")
        report["maps"].append({"file": prefix+".png", "sha256":digest(args.output/f"{prefix}.png"),
                               "cells":columns*rows,"exactAtlasMatch":True})
        if (columns,rows)==(8,6):
            # These are crops of the real assembly, never separately repainted joins.
            samples = {"rear-straight": (0,2), "front-straight": (columns-1,2),
                       "far-corner": (0,0), "near-corner": (columns-1,rows-1)}
            for name, cell in samples.items():
                p = next(p for p in layout["placements"] if (p["i"],p["j"])==cell)
                tw,th = manifest["tileWidth"],manifest["tileHeight"]
                crop = image.crop((p["x"]-tw//3, p["y"]-th//3,
                                   p["x"]+tw+tw//3,p["y"]+images[p["id"]].height+th//3))
                jpg(crop.resize((crop.width*3,crop.height*3),Image.Resampling.NEAREST),
                    args.output/f"join-{name}.jpg")
    if half["packed"]:
        report["halfScaleMap"] = half_review(manifest,images,half_rebuilt,args.output)
    (args.output/"pack-report.json").write_text(json.dumps(report,indent=2)+"\n")
    if args.package_output:
        copy_package(args.input,args.output,args.package_output,report)
    if args.runtime_output:
        export_runtime(args.output, args.runtime_output, half)
    print(json.dumps({"output":str(args.output),"atlasBytes":atlas["rgbaGpuBytes"],
                      "halfScaleCandidate":{key:half[key] for key in
                          ("packed","size","rgbaGpuBytes","reason") if key in half},
                      "maps":report["maps"],
                      "halfScaleMap":report.get("halfScaleMap")},indent=2))


if __name__ == "__main__":
    main()
