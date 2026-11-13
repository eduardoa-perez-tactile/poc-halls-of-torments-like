#!/usr/bin/env python3
"""Bake the actual canonical gameplay PNGs into three fixed managed pages.

No installation or runtime work is performed. Strict mode requires the complete
seven-actor / nineteen-region set. --preview deliberately writes different page
names and gameplay.partial.json; it can never produce a runtime-valid manifest.
Pixels are trimmed and copied losslessly, never rotated, mirrored or rescaled.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
EXPLORE = PROJECT / '.run/art-exploration'
WEEKEND = EXPLORE / 'weekend'
OWNED_OUTPUT = WEEKEND / 'packed'
FACINGS = ('SE', 'SW', 'NE', 'NW')
STATES = ('IDLE', 'MOVE', 'ATTACK', 'DODGE', 'HIT', 'DOWNED', 'WINDUP')
LOOPING = {'IDLE', 'MOVE', 'WINDUP'}
GUTTER = 2
GPU_LIMIT = 6 * 1024 * 1024
PAGES = (
    ('gameplay-actors-main.png', (1024, 1024)),
    ('gameplay-actors-common.png', (512, 512)),
    ('gameplay-environment.png', (512, 512)),
)
ALIASES = {
    'idle': 'IDLE', 'walk': 'MOVE', 'move': 'MOVE', 'drift': 'MOVE',
    'attack': 'ATTACK', 'shoot': 'ATTACK', 'cast_start': 'ATTACK', 'release': 'ATTACK',
    'dodge': 'DODGE', 'hit': 'HIT', 'downed': 'DOWNED',
    'windup': 'WINDUP', 'windup_hold': 'WINDUP', 'casterhold': 'WINDUP', 'caster_hold': 'WINDUP',
}
HERO_COUNTS = {'IDLE': 1, 'MOVE': 4, 'ATTACK': 4, 'DODGE': 1, 'HIT': 1, 'DOWNED': 1}
CONTACT_COUNTS = {'IDLE': 1, 'MOVE': 4}
CASTER_COUNTS = {'IDLE': 1, 'MOVE': 4, 'ATTACK': 1, 'WINDUP': 1}
ACTORS = (
    ('WARDEN', EXPLORE / 'warden-pilot-v2/kit.json', 0, ((96, 96),), 120, HERO_COUNTS),
    ('RANGER', WEEKEND / 'ranger/kit.json', 0, ((96, 96),), 120, {**HERO_COUNTS, 'ATTACK': 3}),
    ('GHOUL', WEEKEND / 'ghoul/kit.json', 1, ((64, 64),), 84, CONTACT_COUNTS),
    ('WRAITH', WEEKEND / 'wraith/kit.json', 1, ((64, 64),), 84, CONTACT_COUNTS),
    ('BRUTE', WEEKEND / 'brute/kit.json', 1, ((64, 64),), 84, CONTACT_COUNTS),
    ('CULTIST', WEEKEND / 'cultist/kit.json', 0, ((64, 64),), 84, CASTER_COUNTS),
    ('SOVEREIGN', WEEKEND / 'sovereign/kit.json', 0, ((128, 128), (96, 96)), 84, CASTER_COUNTS),
)
ENV_CAP_KEYS = {
    'floor-intact': 'floor1', 'floor-chipped': 'floor2', 'floor-fractured': 'floor3',
    'wall-ne': 'wall1', 'wall-nw': 'wall2', 'corner': 'corner1', 'arch': 'arch1',
    'pillar': 'pillar1', 'sarcophagus': 'sarcophagus1', 'rubble': 'rubble1', 'urn': 'urn1',
    'relic': 'relic1', 'obelisk': 'obelisk1', 'shrine': 'shrine1', 'orb': 'orb1',
    'shadow': 'shadow1', 'glow': 'glow1', 'soul': 'soul1', 'white': 'white1',
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT))
    except ValueError:
        return str(path)


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def finite_pair(value: Any, label: str, integer: bool = False) -> list:
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError(label + ' must have two values')
    result = []
    for n in value:
        if isinstance(n, bool) or not isinstance(n, (int, float)) or not math.isfinite(n):
            raise ValueError(label + ' must contain finite numbers')
        if integer and (int(n) != n or n <= 0):
            raise ValueError(label + ' must contain positive integers')
        result.append(int(n) if integer else n)
    return result


@dataclass
class Frame:
    key: str
    path: Path
    image: Image.Image
    source: list[int]
    pivot: list[float]
    page: int
    bounds: tuple[int, int, int, int]
    alpha_bounds: tuple[int, int, int, int]
    owner: str
    state: str | None
    world_height: float

    @property
    def outer_size(self) -> tuple[int, int]:
        l, t, r, b = self.bounds
        return r - l + 4, b - t + 4


class Inputs:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[dict] = []
        self.files: dict[Path, str] = {}
        self.file_roles: dict[Path, list[str]] = {}
        self.frames: list[Frame] = []
        self.actors: list[dict] = []
        self.regions: dict[str, dict] = {}

    def read(self, path: Path, role: str) -> bytes:
        data = path.read_bytes()
        old = self.files.setdefault(path, digest(data))
        if old != digest(data):
            raise ValueError('Input changed while being read: ' + display_path(path))
        self.file_roles.setdefault(path, []).append(role)
        return data

    def read_json(self, path: Path, role: str) -> dict:
        data = json.loads(self.read(path, role))
        if not isinstance(data, dict):
            raise ValueError('Manifest must be a JSON object: ' + display_path(path))
        status = str(data.get('status', '')).lower()
        if any(word in status for word in ('incomplete', 'placeholder', 'fallback', 'partial')):
            self.errors.append(f'{role}: manifest status is not complete: {status}')
        return data

    def frame(self, key: str, path: Path, source: list[int], pivot: list[float],
              page: int, owner: str, state: str | None, world_height: float) -> int:
        self.read(path, key)
        with Image.open(path) as original:
            image = original.convert('RGBA')
        if list(image.size) != source:
            raise ValueError(f'{key}: PNG {list(image.size)} differs from canonical source {source}; no rescaling allowed')
        if not (0 <= pivot[0] <= source[0] and 0 <= pivot[1] <= source[1]):
            raise ValueError(key + ': pivot lies outside canonical source')
        alpha_bounds = image.getchannel('A').getbbox()
        if alpha_bounds is None:
            raise ValueError(key + ': PNG has no nontransparent pixels')
        # Preserve invisible RGB as well as alpha. Alpha-only trimming would
        # discard nonzero RGB from the existing white glow's alpha-zero corners.
        bounds = image.getbbox(alpha_only=False)
        if bounds is None:
            raise ValueError(key + ': empty RGBA PNG')
        if owner != 'environment' and (alpha_bounds[0] == 0 or alpha_bounds[1] == 0
                                      or alpha_bounds[2] == source[0] or alpha_bounds[3] == source[1]):
            self.warnings.append({'kind': 'alpha_touches_canonical_edge', 'frame': key,
                                  'alphaBounds': list(alpha_bounds),
                                  'note': 'Packer preserves it exactly; source-art clipping cannot be inferred from PNG bounds alone.'})
        index = len(self.frames)
        self.frames.append(Frame(key, path, image, source, pivot, page, bounds,
                                 alpha_bounds, owner, state, world_height))
        return index

    def verify_unchanged(self) -> None:
        for path, expected in self.files.items():
            if not path.is_file() or digest(path.read_bytes()) != expected:
                raise ValueError('Input changed during packing: ' + display_path(path))


def state_name(name: str) -> str:
    if name in STATES:
        return name
    try:
        return ALIASES[name.lower()]
    except KeyError:
        raise ValueError('Unknown input state: ' + name) from None


def sequence_entries(value: Any, declared_timing: Any = None, declared_frame_ms: Any = None) -> list[dict]:
    durations = declared_timing
    if isinstance(value, dict):
        durations = value.get('durationsMs', durations)
        value = value.get('frames')
    if not isinstance(value, list):
        raise ValueError('Clip must be a frame list or an object with frames')
    if durations is None and declared_frame_ms is not None:
        durations = [declared_frame_ms] * len(value)
    if durations is not None and not isinstance(durations, list):
        raise ValueError('Declared clip timing must be a duration list')
    if durations is not None and len(durations) != len(value):
        raise ValueError('Clip durationsMs length differs from frame count')
    entries = []
    for i, entry in enumerate(value):
        if isinstance(entry, str):
            entry = {'path': entry}
        if not isinstance(entry, dict):
            raise ValueError('Frame must be an object or path')
        entry = dict(entry)
        if durations is not None:
            entry.setdefault('durationMs', durations[i])
        entries.append(entry)
    return entries


def load_actor(inputs: Inputs, spec: tuple) -> None:
    actor_id, path, page, canvases, world_height, expected = spec
    if not path.is_file():
        inputs.errors.append(f'{actor_id}: missing manifest {display_path(path)}')
        return
    try:
        data = inputs.read_json(path, actor_id)
        canvas = finite_pair(data.get('frameSize', data.get('canvas')), actor_id + ' canvas', True)
        pivot = finite_pair(data.get('anchor', data.get('pivot')), actor_id + ' pivot')
        if tuple(canvas) not in canvases:
            raise ValueError(f'{actor_id}: canonical canvas {canvas} must be one of {canvases}')
        directions = data.get('directions')
        if not isinstance(directions, dict):
            raise ValueError(actor_id + ': directions object is missing')
        if set(directions) != set(FACINGS):
            inputs.errors.append(f'{actor_id}: facing set {sorted(directions)} must be {list(FACINGS)}')
        actor = {'id': actor_id, 'canvas': canvas, 'worldHeight': world_height, 'clips': {}}
        for direction in FACINGS:
            raw_states = directions.get(direction)
            if not isinstance(raw_states, dict):
                inputs.errors.append(f'{actor_id}: missing facing {direction}')
                continue
            mapped = {}
            for raw_state, sequence in raw_states.items():
                try:
                    state = state_name(raw_state)
                    if state in mapped:
                        raise ValueError(f'multiple input states map to {state}; explicit producer resolution required')
                    timing = data.get('timing', {}).get(raw_state) if isinstance(data.get('timing'), dict) else None
                    mapped[state] = sequence_entries(sequence, timing, data.get('frameDurationMs'))
                except (ValueError, TypeError) as exc:
                    inputs.errors.append(f'{actor_id}/{direction}/{raw_state}: {exc}')
            if set(mapped) != set(expected):
                inputs.errors.append(f'{actor_id}/{direction}: clip set {sorted(mapped)} must be {sorted(expected)}; no clips will be silently dropped')
            for state in STATES:
                if state not in mapped:
                    continue
                entries = mapped[state]
                if state in expected and len(entries) != expected[state]:
                    inputs.errors.append(f'{actor_id}/{direction}/{state}: {len(entries)} frames, requires {expected[state]}')
                indices, durations = [], []
                for n, entry in enumerate(entries, 1):
                    key = f'{actor_id}/{state}/{direction}/{n:02}'
                    try:
                        file = entry.get('path', entry.get('file'))
                        if not isinstance(file, str) or not file:
                            raise ValueError('canonical PNG path is missing')
                        duration = entry.get('durationMs', entry.get('duration_ms'))
                        if isinstance(duration, bool) or not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration <= 0 or int(duration) != duration:
                            raise ValueError('positive integer durationMs is required; no timing defaults are invented')
                        if 'anchor' in entry and finite_pair(entry['anchor'], key + ' anchor') != pivot:
                            raise ValueError('per-frame anchor differs from the fixed actor pivot')
                        indices.append(inputs.frame(key, (path.parent / file).resolve(), canvas,
                                                    pivot, page, actor_id, state, world_height))
                        durations.append(int(duration))
                    except (OSError, ValueError, TypeError) as exc:
                        inputs.errors.append(f'{key}: {exc}')
                actor['clips'].setdefault(state, {})[direction] = {
                    'frames': indices, 'durationsMs': durations, 'loop': state in LOOPING}
        # Emit the exact ordered runtime state/facing schema, preserving every
        # supplied state in invalid previews instead of disguising omissions.
        actor['clips'] = {state: actor['clips'][state] for state in STATES if state in actor['clips']}
        inputs.actors.append(actor)
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        inputs.errors.append(f'{actor_id}: {exc}')


def load_environment(inputs: Inputs) -> None:
    path = WEEKEND / 'environment/environment.json'
    if not path.is_file():
        inputs.errors.append('environment: missing manifest ' + display_path(path))
        return
    try:
        data = inputs.read_json(path, 'environment')
        regions = data.get('regions')
        if not isinstance(regions, dict):
            raise ValueError('regions object is missing')
        if set(regions) != set(ENV_CAP_KEYS):
            inputs.errors.append(f'environment: expected nineteen regions; missing={sorted(set(ENV_CAP_KEYS)-set(regions))}, extra={sorted(set(regions)-set(ENV_CAP_KEYS))}')
        for name, entry in regions.items():
            try:
                source = finite_pair(entry.get('sourceSize'), name + ' sourceSize', True)
                pivot = finite_pair(entry.get('pivot'), name + ' pivot')
                world_height = entry.get('worldHeightHint')
                if not isinstance(world_height, (int, float)) or not math.isfinite(world_height) or world_height <= 0:
                    raise ValueError('worldHeightHint must be positive and finite')
                index = inputs.frame('environment/' + name, (path.parent / entry['path']).resolve(),
                                     source, pivot, 2, 'environment', None, world_height)
                inputs.regions[name] = {'frame': index, 'worldHeight': world_height}
            except (OSError, ValueError, TypeError, KeyError) as exc:
                inputs.errors.append(f'environment/{name}: {exc}')
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        inputs.errors.append(f'environment: {exc}')


def contained(a: tuple, b: tuple) -> bool:
    return a[0] >= b[0] and a[1] >= b[1] and a[0]+a[2] <= b[0]+b[2] and a[1]+a[3] <= b[1]+b[3]


def intersects(a: tuple, b: tuple) -> bool:
    return not (a[0]+a[2] <= b[0] or b[0]+b[2] <= a[0] or a[1]+a[3] <= b[1] or b[1]+b[3] <= a[1])


def pack_attempt(frames: list[Frame], size: tuple, ordering: str, heuristic: str) -> dict | None:
    free = [(0, 0, *size)]
    keys = {
        'max-side': lambda f: (-max(f.outer_size), -math.prod(f.outer_size), f.key),
        'area': lambda f: (-math.prod(f.outer_size), -max(f.outer_size), f.key),
        'height': lambda f: (-f.outer_size[1], -f.outer_size[0], f.key),
        'width': lambda f: (-f.outer_size[0], -f.outer_size[1], f.key),
    }
    placed = {}
    for frame in sorted(frames, key=keys[ordering]):
        w, h = frame.outer_size
        candidates = []
        for f in free:
            if w > f[2] or h > f[3]:
                continue
            short, long = min(f[2]-w, f[3]-h), max(f[2]-w, f[3]-h)
            score = {'short-side': (short, long, f[1], f[0]),
                     'area': (f[2]*f[3]-w*h, short, f[1], f[0]),
                     'bottom-left': (f[1]+h, f[0], short, long)}[heuristic]
            candidates.append((score, f))
        if not candidates:
            return None
        _, chosen = min(candidates)
        x, y = chosen[:2]
        used = (x, y, w, h)
        next_free = []
        for fx, fy, fw, fh in free:
            if not intersects(used, (fx, fy, fw, fh)):
                next_free.append((fx, fy, fw, fh))
                continue
            if x > fx: next_free.append((fx, fy, x-fx, fh))
            if x+w < fx+fw: next_free.append((x+w, fy, fx+fw-x-w, fh))
            if y > fy: next_free.append((fx, fy, fw, y-fy))
            if y+h < fy+fh: next_free.append((fx, y+h, fw, fy+fh-y-h))
        unique = list(dict.fromkeys(next_free))
        free = [a for i, a in enumerate(unique) if not any(i != j and contained(a, b) for j, b in enumerate(unique))]
        placed[frame.key] = used
    return placed


def pack_page(frames: list[Frame], size: tuple) -> tuple[dict, dict]:
    attempts = 0
    for ordering in ('max-side', 'area', 'height', 'width'):
        for heuristic in ('short-side', 'area', 'bottom-left'):
            attempts += 1
            result = pack_attempt(frames, size, ordering, heuristic)
            if result is not None:
                return result, {'ordering': ordering, 'heuristic': heuristic, 'attempts': attempts}
    raise ValueError(f'Actual rectangles do not pack into fixed page {size}; twelve bounded deterministic attempts exhausted. No page expansion or pose rescale allowed.')


def padded_content(content: Image.Image) -> Image.Image:
    w, h = content.size
    padded = Image.new('RGBA', (w+4, h+4))
    padded.paste(content, (2, 2))
    padded.paste(content.crop((0, 0, w, 1)), (2, 1))
    padded.paste(content.crop((0, h-1, w, h)), (2, h+2))
    padded.paste(content.crop((0, 0, 1, h)), (1, 2))
    padded.paste(content.crop((w-1, 0, w, h)), (w+2, 2))
    for x, y, cx, cy in [(1,1,0,0), (w+2,1,w-1,0), (1,h+2,0,h-1), (w+2,h+2,w-1,h-1)]:
        padded.putpixel((x, y), content.getpixel((cx, cy)))
    return padded


def cap_warnings(inputs: Inputs, plan: dict) -> list[dict]:
    warnings = []
    actors = {a['actor'].upper(): a for a in plan.get('actors', [])}
    actors['SOVEREIGN'] = actors.get('BOSS', {})
    common_caps = plan.get('enemy_max_outer_dimensions', {})
    ranger_caps = {state_name(s['state']): s['max_outer_dimensions'] for s in plan.get('ranger_state_rectangle_caps', [])}
    env_caps = {p['key'].split('/')[1]+p['key'].split('/')[2]: p['outer_rect'][2:]
                for p in plan.get('geometry_proof', {}).get('environment', {}).get('placements', [])}
    sums = {}
    for f in inputs.frames:
        sums[f.owner] = sums.get(f.owner, 0) + math.prod(f.outer_size)
        cap = None
        if f.owner == 'environment': cap = env_caps.get(ENV_CAP_KEYS.get(f.key.split('/')[-1], ''))
        elif f.owner == 'RANGER': cap = ranger_caps.get(f.state)
        elif f.owner == 'SOVEREIGN': cap = plan.get('optional_boss_128', {}).get('max_trimmed_outer_dimensions') if f.source == [128,128] else common_caps.get('boss')
        elif f.owner != 'WARDEN': cap = common_caps.get(f.owner.lower())
        if cap and (f.outer_size[0] > cap[0] or f.outer_size[1] > cap[1]):
            warnings.append({'kind':'planning_region_cap', 'frame':f.key, 'actualOuter':list(f.outer_size), 'proposedCap':cap})
    for name, total in sums.items():
        cap = actors.get(name, {}).get('outer_area_cap_pixels')
        if cap and total > cap:
            warnings.append({'kind':'planning_actor_area_cap', 'actor':name, 'actualOuterArea':total, 'proposedAreaCap':cap})
    return warnings


def source_report(inputs: Inputs) -> list[dict]:
    return [{'path':display_path(path),'sha256':sha,'roles':roles}
            for path, sha in sorted(inputs.files.items(), key=lambda x:str(x[0]))
            for roles in [sorted(set(inputs.file_roles[path]))]]


def scene_extents(inputs: Inputs) -> dict:
    out = {}
    for f in inputs.frames:
        l, t, r, b = f.alpha_bounds
        scale = f.world_height / f.source[1]
        values = {'left':(l-f.pivot[0])*scale,'right':(r-f.pivot[0])*scale,
                  'bottom':(f.pivot[1]-b)*scale,'top':(f.pivot[1]-t)*scale}
        # Actor maxima support culling across every pose; static props retain
        # individual extents so tall/wide scenery can be reviewed separately.
        group = f.key if f.owner == 'environment' else f.owner
        old = out.setdefault(group, dict(values))
        for key in ('left','bottom'): old[key] = min(old[key],values[key])
        for key in ('right','top'): old[key] = max(old[key],values[key])
    return out


def validate_page(image: Image.Image, frames: list[Frame], placements: dict) -> None:
    rectangles = []
    for f in frames:
        outer = placements[f.key]
        x, y, w, h = outer
        if x < 0 or y < 0 or x+w > image.width or y+h > image.height:
            raise ValueError('Packed rectangle outside page: ' + f.key)
        for key, other in rectangles:
            if intersects(outer,other): raise ValueError(f'Packed overlap: {f.key} / {key}')
        rectangles.append((f.key,outer))
        l,t,r,b = f.bounds
        expected = f.image.crop(f.bounds)
        actual = image.crop((x+2,y+2,x+2+r-l,y+2+b-t))
        restored = Image.new('RGBA', tuple(f.source))
        restored.paste(actual,(l,t))
        if restored.tobytes() != f.image.tobytes():
            raise ValueError('RGBA reconstruction failed: ' + f.key)
        if image.crop((x,y,x+w,y+h)).tobytes() != padded_content(expected).tobytes():
            raise ValueError('Two-pixel gutter / one-pixel extrusion failed: ' + f.key)


def run(args: argparse.Namespace) -> int:
    output = args.output.resolve()
    if output != OWNED_OUTPUT and OWNED_OUTPUT not in output.parents:
        raise ValueError('Output must remain under ' + display_path(OWNED_OUTPUT) + '; primary separately publishes approved runtime files')
    output.mkdir(parents=True,exist_ok=True)
    inputs = Inputs()
    plan_path = WEEKEND/'resource-plan.json'
    plan = inputs.read_json(plan_path,'resource plan')
    for spec in ACTORS: load_actor(inputs,spec)
    load_environment(inputs)
    caps = cap_warnings(inputs,plan)
    if args.enforce_caps and caps:
        inputs.errors.append(f'--enforce-caps: {len(caps)} proposed planning-cap violations')
    report = {'mode':'preview' if args.preview else 'strict','runtimeValid':False,
              'status':'incomplete' if inputs.errors else 'inputs-complete',
              'errors':inputs.errors,'planningCapViolations':caps,'sourceWarnings':inputs.warnings,
              'expectedActors':[s[0] for s in ACTORS],'presentActors':[a['id'] for a in inputs.actors],
              'frameCount':len(inputs.frames),'regionCount':len(inputs.regions),'sources':source_report(inputs),
              'sceneExtentsWorld':scene_extents(inputs),
              'rules':{'rotation':False,'mirroring':False,'rescaling':False,'gutterPerSide':2,
                       'extrudedPixels':1,'trim':'all nonzero RGBA channels, preserving invisible RGB',
                       'fixedGPUBytes':sum(w*h*4 for _,(w,h) in PAGES),'GPUByteLimit':GPU_LIMIT,
                       'capPolicy':'Proposed caps are advisory unless --enforce-caps; complete actual fixed-page packing is authoritative.'}}
    if inputs.errors and not args.preview:
        report['previousFinalManifestExists'] = (output/'gameplay.json').exists()
        write_json(output/'pack-report.incomplete.json',report)
        print(json.dumps({'status':'incomplete','runtimeValid':False,'errors':inputs.errors,'report':display_path(output/'pack-report.incomplete.json')}))
        return 2
    if not inputs.frames:
        raise ValueError('No actual input frames are available to pack')
    prefix = 'preview-' if args.preview else ''
    descriptor = {'version':1,'pages':[{'file':prefix+name,'size':list(size)} for name,size in PAGES],
                  'frames':[],'actors':inputs.actors,'regions':inputs.regions}
    if args.preview:
        descriptor['preview'] = True
    if sum(w*h*4 for _,(w,h) in PAGES) != GPU_LIMIT:
        raise ValueError('Fixed pages exceed or differ from the six-MiB contract')
    placements_by_page = {}
    images = []
    page_reports = []
    for n,(_,size) in enumerate(PAGES):
        frames = [f for f in inputs.frames if f.page == n]
        try:
            positions,algorithm = pack_page(frames,size)
        except ValueError as exc:
            report.update({'status':'packing-rejected','failedPage':n,
                           'failedPageSize':list(size),'failedPageOuterArea':sum(math.prod(f.outer_size) for f in frames),
                           'failedPageFrames':[{'key':f.key,'outerSize':list(f.outer_size)} for f in frames]})
            report['errors'].append(str(exc))
            write_json(output/'pack-report.failure.json',report)
            raise ValueError(str(exc)+' See '+display_path(output/'pack-report.failure.json')) from None
        page = Image.new('RGBA',size)
        for f in frames:
            page.paste(padded_content(f.image.crop(f.bounds)),positions[f.key][:2])
        validate_page(page,frames,positions)
        placements_by_page[n] = positions
        images.append(page)
        area = sum(math.prod(f.outer_size) for f in frames)
        page_reports.append({'page':n,'size':list(size),'frameCount':len(frames),
                             'outerArea':area,'fillFraction':area/math.prod(size),'algorithm':algorithm,
                             'placements':[{'key':f.key,'outerRect':list(positions[f.key])} for f in frames]})
    for f in inputs.frames:
        x,y,_,_ = placements_by_page[f.page][f.key]
        l,t,r,b = f.bounds
        descriptor['frames'].append({'page':f.page,'rect':[x+2,y+2,r-l,b-t],
                                     'source':f.source,'offset':[l,t],'pivot':f.pivot})
    inputs.verify_unchanged()
    report.update({'status':'preview-incomplete' if inputs.errors else ('preview-complete-inputs' if args.preview else 'complete'),
                   'runtimeValid':not args.preview and not inputs.errors,
                   'pages':page_reports,'validation':{'exactRGBAReconstruction':True,'boundsAndNonoverlap':True,
                       'twoPixelGutters':True,'onePixelEdgeExtrusion':True,'sourceFilesUnchanged':True,
                       'noDroppedInputFrames':not inputs.errors,'noRescaledOrRotatedFrames':True}})
    manifest_name = 'gameplay.partial.json' if args.preview else 'gameplay.json'
    report_name = 'pack-report.preview.json' if args.preview else 'pack-report.json'
    temporary = Path(tempfile.mkdtemp(prefix='.packing-',dir=output))
    try:
        for page,data in zip(images,descriptor['pages']):
            page.save(temporary/data['file'],compress_level=9)
            with Image.open(temporary/data['file']) as persisted:
                page_index = descriptor['pages'].index(data)
                validate_page(persisted.convert('RGBA'),[f for f in inputs.frames if f.page == page_index],placements_by_page[page_index])
        write_json(temporary/manifest_name,descriptor)
        report['outputHashes'] = {p.name:digest(p.read_bytes()) for p in sorted(temporary.iterdir())}
        write_json(temporary/report_name,report)
        for p in temporary.iterdir(): p.replace(output/p.name)
    finally:
        shutil.rmtree(temporary,ignore_errors=True)
    print(json.dumps({'status':report['status'],'runtimeValid':report['runtimeValid'],
                      'actors':len(inputs.actors),'frames':len(inputs.frames),'regions':len(inputs.regions),
                      'planningCapWarnings':len(caps),'GPUBytes':GPU_LIMIT,
                      'manifest':display_path(output/manifest_name),'report':display_path(output/report_name)}))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview',action='store_true',help='Allow explicitly invalid partial art preview; never writes gameplay.json')
    parser.add_argument('--enforce-caps',action='store_true',help='Reject proposed planning-cap violations as well as actual packing failures')
    parser.add_argument('--output',type=Path,default=OWNED_OUTPUT,help='Destination inside the owned weekend/packed folder')
    args = parser.parse_args()
    try:
        return run(args)
    except (OSError,ValueError,KeyError,TypeError,json.JSONDecodeError) as exc:
        print('Gameplay atlas rejected: '+str(exc),file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
