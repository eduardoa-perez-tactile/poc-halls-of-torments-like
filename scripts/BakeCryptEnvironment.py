#!/usr/bin/env python3
"""Repack the original open-crypt environment inside the existing 512x512 page.

Reads only the tracked canonical input ZIP; does not generate artwork, change
actor pages, modify projectile pixels or depend on local exploration caches.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'art-source/crypt-environment-v2/canonical-inputs.zip'
NAMES = ('wall-x', 'wall-x-broken', 'wall-y', 'wall-y-broken', 'buttress', 'sarcophagus-x', 'sarcophagus-y',
         'altar', 'guardian', 'broken-column', 'rubble', 'sealed-gate')
UTILITIES = ('shadow', 'glow', 'soul', 'white')
SIZE = 512
PADDING = 2


def intersects(a, b):
    return a[0] < b[0]+b[2] and b[0] < a[0]+a[2] and a[1] < b[1]+b[3] and b[1] < a[1]+a[3]


def contains(a, b):
    return a[0] <= b[0] and a[1] <= b[1] and a[0]+a[2] >= b[0]+b[2] and a[1]+a[3] >= b[1]+b[3]


def subtract(free, used):
    result = []
    for x,y,w,h in free:
        if not intersects((x,y,w,h), used):
            result.append((x,y,w,h)); continue
        ux,uy,uw,uh = used
        if ux > x: result.append((x,y,ux-x,h))
        if ux+uw < x+w: result.append((ux+uw,y,x+w-ux-uw,h))
        if uy > y: result.append((x,y,w,uy-y))
        if uy+uh < y+h: result.append((x,uy+uh,w,y+h-uy-uh))
    result = sorted(set(result))
    return [r for i,r in enumerate(result) if not any(i != j and contains(s,r) for j,s in enumerate(result))]


def pack(cells):
    orders = {
        'max-side': lambda c: (-max(c['image'].size), -c['image'].width*c['image'].height,c['key']),
        'area': lambda c: (-c['image'].width*c['image'].height,-max(c['image'].size),c['key']),
        'height': lambda c: (-c['image'].height,-c['image'].width,c['key']),
        'width': lambda c: (-c['image'].width,-c['image'].height,c['key']),
    }
    for order,key in orders.items():
        for heuristic in ('short-side','area','bottom-left'):
            free=[(0,0,SIZE,SIZE)]; placed={}
            for c in sorted(cells,key=key):
                w,h=c['image'].size; rw,rh=w+PADDING*2,h+PADDING*2
                candidates=[]
                for r in free:
                    if rw>r[2] or rh>r[3]:continue
                    short,long=min(r[2]-rw,r[3]-rh),max(r[2]-rw,r[3]-rh)
                    score={'short-side':(short,long,r[1],r[0]),'area':(r[2]*r[3]-rw*rh,short,r[1],r[0]),'bottom-left':(r[1]+rh,r[0],short,long)}[heuristic]
                    candidates.append((score,r))
                if not candidates:break
                _,r=min(candidates); used=(r[0],r[1],rw,rh);placed[c['key']]=(r[0]+PADDING,r[1]+PADDING,w,h)
                free=subtract(free,used)
            if len(placed)==len(cells):return placed,order+'/'+heuristic
    raise ValueError('Complete crypt sprites do not fit the fixed 512x512 environment page')


def extrude(page, image, x, y):
    page.paste(image,(x,y));w,h=image.size
    page.paste(image.crop((0,0,w,1)),(x,y-1));page.paste(image.crop((0,h-1,w,h)),(x,y+h))
    page.paste(image.crop((0,0,1,h)),(x-1,y));page.paste(image.crop((w-1,0,w,h)),(x+w,y))
    for dx,dy,sx,sy in [(-1,-1,0,0),(w,-1,w-1,0),(-1,h,0,h-1),(w,h,w-1,h-1)]:
        page.putpixel((x+dx,y+dy),image.getpixel((sx,sy)))


def bake(archive:Path, output:Path):
    with zipfile.ZipFile(archive) as z:
        index=json.loads(z.read('SOURCES.json'))
        for name,expected in index.items():
            data=z.read(name)
            if len(data)!=expected['bytes'] or hashlib.sha256(data).hexdigest()!=expected['sha256']:
                raise ValueError('Canonical input changed: '+name)
        manifest=json.loads(z.read('environment.json'))
        base=json.loads(z.read('base-gameplay.json'));magic=json.loads(z.read('base-magic-projectiles.json'))
        if tuple(manifest['regions'])!=NAMES:raise ValueError('Twelve named crypt sprites are required in canonical order')
        if len(magic['frames'])!=24:raise ValueError('All24 missile frames are required')
        actor_frames=copy.deepcopy(base['frames'][:208])
        if len(actor_frames)!=208 or any(f['page'] not in (0,1) for f in actor_frames):raise ValueError('Fixed actor prefix changed')
        cells=[]
        for name in NAMES:
            m=manifest['regions'][name];image=Image.open(io.BytesIO(z.read('sprites/'+name+'.png'))).convert('RGBA')
            if list(image.size)!=m['sourceSize']:raise ValueError('Crypt source canvas changed: '+name)
            bounds=image.getchannel('A').getbbox()
            if not bounds:raise ValueError('Empty crypt sprite: '+name)
            frame={'page':2,'source':list(image.size),'offset':list(bounds[:2]),'pivot':m['pivot']}
            cells.append({'key':'env/'+name,'image':image.crop(bounds),'frame':frame,'name':name})
        for name in UTILITIES:
            image=Image.open(io.BytesIO(z.read('sprites/'+name+'.png'))).convert('RGBA')
            frame=copy.deepcopy(manifest['utilities'][name]['frame'])
            if image.size != tuple(frame['rect'][2:]):raise ValueError('Utility pixels changed: '+name)
            cells.append({'key':'env/'+name,'image':image,'frame':frame,'name':name})
        for i,frame in enumerate(magic['frames']):
            image=Image.open(io.BytesIO(z.read(f'projectiles/{i:02d}.png'))).convert('RGBA')
            if hashlib.sha256(image.tobytes()).hexdigest()!=manifest['projectileRgbaSha256'][i]:raise ValueError('Projectile pixels changed')
            cells.append({'key':f'projectile/{i:02d}','image':image,'frame':copy.deepcopy(frame),'index':i})
    placement,method=pack(cells);page=Image.new('RGBA',(SIZE,SIZE));gameplay=copy.deepcopy(base)
    gameplay['frames']=actor_frames;gameplay['regions']={};magic_frames=[None]*24;rectangles=[]
    for cell in cells:
        x,y,w,h=placement[cell['key']];outer=(x-PADDING,y-PADDING,w+PADDING*2,h+PADDING*2)
        if any(intersects(outer,other) for other in rectangles):raise ValueError('Packing gutters overlap')
        if min(x,y)<PADDING or x+w+PADDING>SIZE or y+h+PADDING>SIZE:raise ValueError('Packing out of bounds')
        rectangles.append(outer);extrude(page,cell['image'],x,y)
        f=copy.deepcopy(cell['frame']);f['rect']=[x,y,w,h]
        if cell['key'].startswith('env/'):
            name=cell['name'];frame_index=len(gameplay['frames']);gameplay['frames'].append(f)
            if name in NAMES:
                m=manifest['regions'][name];fw=float(m['projectedFootprintWidth']);wx=m['footprintWorldX'];wy=m['footprintWorldY']
                scale=(wx+wy)*2**-.5/fw
                region={'frame':frame_index,'worldHeight':round(f['source'][1]*scale,6),'footprintWidth':fw,'footprintWorldX':wx,'footprintWorldY':wy}
                if 'renderShearY' in m:
                    region['renderShearY']=m['renderShearY']
            else:
                height=manifest['utilities'][name]['worldHeight'];region={'frame':frame_index,'worldHeight':height,'footprintWidth':f['source'][0],'footprintWorldX':height,'footprintWorldY':height}
            gameplay['regions'][name]=region
        else:magic_frames[cell['index']]=f
    if gameplay['frames'][:208]!=base['frames'][:208] or gameplay['actors']!=base['actors']:raise ValueError('Actor metadata changed')
    magic['frames']=magic_frames
    output.mkdir(parents=True,exist_ok=True);page.save(output/'gameplay-environment.png',optimize=True)
    saved=Image.open(output/'gameplay-environment.png').convert('RGBA')
    for cell in cells:
        x,y,w,h=placement[cell['key']]
        if saved.crop((x,y,x+w,y+h)).tobytes()!=cell['image'].tobytes():raise ValueError('Saved pixel reconstruction failed: '+cell['key'])
    (output/'gameplay.json').write_text(json.dumps(gameplay,indent=2)+'\n')
    (output/'magic-projectiles.json').write_text(json.dumps(magic,indent=2)+'\n')
    report={'version':1,'sourceArchive':str(archive.name),'environmentSize':[SIZE,SIZE], 'environmentTextureBytes':SIZE*SIZE*4,
        'addedTextureBytes':0,'newEnvironmentSprites':len(NAMES),'utilitiesPreserved':len(UTILITIES),'projectileFramesPreserved':24,
        'actorFramesPreserved':208,'padding':PADDING,'edgeExtrusion':1,'packer':method,'savedRgbaReconstruction':True,
        'actorPageSha256':manifest['actorPageSha256'],'outputs':{name:hashlib.sha256((output/name).read_bytes()).hexdigest() for name in ['gameplay-environment.png','gameplay.json','magic-projectiles.json']}}
    print(json.dumps(report,indent=2))
    return report


def apply_projection_calibration(output:Path):
    """Final metadata-only step, after the additive floor/border/actor bakes.

    Those historical bake archives retain their original baseline hashes. Keep
    them reproducible, then apply the measured architecture correction to the
    completed bank without repacking or changing any PNG.
    """
    sources=json.loads((ROOT/'art-source/crypt-environment-v2/environment.json').read_text())
    target=output/'gameplay.json';gameplay=json.loads(target.read_text())
    for name in ('altar','sealed-gate'):
        calibration=sources['regions'][name];region=gameplay['regions'][name]
        frame=gameplay['frames'][region['frame']]
        if frame['source']!=calibration['sourceSize'] or any(
                region[key]!=calibration[key] for key in ('footprintWorldX','footprintWorldY')):
            raise ValueError('Projection calibration belongs to different source geometry: '+name)
        sy=calibration['renderScaleY'];shear=calibration['renderShearY']
        if not .85<=sy<=1.2 or not -.1<=shear<=.1:
            raise ValueError('Unbounded projection correction: '+name)
        frame['pivot']=calibration['pivot']
        region['renderScaleY']=sy;region['renderShearY']=shear
    target.write_text(json.dumps(gameplay,indent=2)+'\n')
    print('Applied measured gate/altar projection metadata; no PNGs changed.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=ARCHIVE)
    parser.add_argument('--output',type=Path,default=ROOT/'assets/art')
    parser.add_argument('--projection-only',action='store_true',help='Apply final gate/altar metadata calibration to a completed bank, without repacking pixels')
    args=parser.parse_args()
    if args.projection_only:apply_projection_calibration(args.output)
    else:bake(args.source,args.output)

if __name__=='__main__':main()
