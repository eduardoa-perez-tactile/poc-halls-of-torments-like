#!/usr/bin/env python3
"""Add authored attack/aura sprites to unused actor-bank space, without new textures.

The tracked source archive contains normalized fixed-root frames plus original
ImageGen sheets/prompts/provenance. Existing gameplay frames/indices never move.
Run after BakeGameplayAtlas.py; --base-art may point to a clean rebuilt bank.
"""
from __future__ import annotations
import argparse, hashlib, io, json, zipfile
from pathlib import Path
from PIL import Image, ImageChops

ROOT=Path(__file__).resolve().parent.parent
GUTTER=2

def digest(b): return hashlib.sha256(b).hexdigest()
def intersect(a,b): return a[0]<b[0]+b[2] and b[0]<a[0]+a[2] and a[1]<b[1]+b[3] and b[1]<a[1]+a[3]
def subtract(free,used):
    out=[]
    for r in free:
        if not intersect(r,used):out.append(r);continue
        x,y,w,h=r;ux,uy,uw,uh=used
        if ux>x:out.append((x,y,ux-x,h))
        if ux+uw<x+w:out.append((ux+uw,y,x+w-ux-uw,h))
        if uy>y:out.append((x,y,w,uy-y))
        if uy+uh<y+h:out.append((x,uy+uh,w,y+h-uy-uh))
    out=list(dict.fromkeys(out))
    return [r for i,r in enumerate(out) if not any(i!=j and r[0]>=s[0] and r[1]>=s[1] and r[0]+r[2]<=s[0]+s[2] and r[1]+r[3]<=s[1]+s[3] for j,s in enumerate(out))]

def bake(source:Path,base:Path,output:Path):
    with zipfile.ZipFile(source) as z:
        source_index=json.loads(z.read('sources.json'))
        for path,sha in source_index['sha256'].items():
            if digest(z.read(path))!=sha:raise ValueError('Source checksum mismatch: '+path)
        config=json.loads(z.read('animation-source.json'))
        original=json.loads((base/'gameplay.json').read_text())
        pages=[Image.open(base/p['file']).convert('RGBA') for p in original['pages'][:2]]
        before=[im.copy() for im in pages];free=[];reserved=[[],[]]
        for page,im in enumerate(pages):
            f=[(0,0,*im.size)]
            for frame in original['frames']:
                if frame['page']!=page:continue
                x,y,w,h=frame['rect'];r=(max(0,x-GUTTER),max(0,y-GUTTER),min(im.width,x+w+GUTTER)-max(0,x-GUTTER),min(im.height,y+h+GUTTER)-max(0,y-GUTTER))
                reserved[page].append(r);f=subtract(f,r)
            free.append(f)
            # Clear only unused regions, making this stage idempotent after an earlier overlay.
            clean=Image.new('RGBA',im.size)
            for x,y,w,h in reserved[page]:clean.paste(im.crop((x,y,x+w,y+h)),(x,y))
            pages[page]=clean
        frames=[];images=[];dedup={};aliases={}
        for entry in config['frames']:
            full=Image.open(io.BytesIO(z.read(entry['sourceFile']))).convert('RGBA')
            box=full.getchannel('A').getbbox()
            if box is None:raise ValueError('Empty sprite '+entry['sourceFile'])
            image=full.crop(box);key=digest(image.tobytes()+str((full.size,box,entry['pivot'])).encode())
            if key in dedup:aliases[entry['id']]=dedup[key];continue
            index=len(frames);dedup[key]=index;aliases[entry['id']]=index
            frames.append({'id':entry['id'],'source':list(full.size),'offset':list(box[:2]),'pivot':entry['pivot']})
            images.append(image)
        for index in sorted(range(len(images)),key=lambda i:(-images[i].height,-images[i].width,i)):
            im=images[index];w,h=im.width+GUTTER*2,im.height+GUTTER*2
            choices=[]
            for page,bank in enumerate(free):
                for r in bank:
                    if w<=r[2] and h<=r[3]:choices.append((min(r[2]-w,r[3]-h),r[2]*r[3]-w*h,page,r[1],r[0],r))
            if not choices:raise ValueError('Actor bank has no room for '+frames[index]['id'])
            _,_,page,y,x,_=min(choices);free[page]=subtract(free[page],(x,y,w,h));x+=GUTTER;y+=GUTTER
            frames[index].update(page=page,rect=[x,y,im.width,im.height]);pages[page].paste(im,(x,y))
            # One-pixel edge extrusion inside the two-pixel sampling gutter.
            pages[page].paste(im.crop((0,0,im.width,1)),(x,y-1));pages[page].paste(im.crop((0,im.height-1,im.width,im.height)),(x,y+im.height))
            pages[page].paste(im.crop((0,0,1,im.height)),(x-1,y));pages[page].paste(im.crop((im.width-1,0,im.width,im.height)),(x+im.width,y))
            for xx,yy,sx,sy in [(x-1,y-1,0,0),(x+im.width,y-1,im.width-1,0),(x-1,y+im.height,0,im.height-1),(x+im.width,y+im.height,im.width-1,im.height-1)]:pages[page].putpixel((xx,yy),im.getpixel((sx,sy)))
        for page,rects in enumerate(reserved):
            for x,y,w,h in rects:
                if before[page].crop((x,y,x+w,y+h)).tobytes()!=pages[page].crop((x,y,x+w,y+h)).tobytes():raise ValueError('Original gameplay pixels changed')
        metadata={k:v for k,v in config.items() if k!='frames'}
        metadata.update(version=1,pages=original['pages'][:2],frames=frames,textureBytesAdded=0,gutter=GUTTER,edgeExtrusion=1)
        for actor in metadata['actors']:
            actor['clips']={f:[aliases[key] for key in refs] for f,refs in actor['clips'].items()}
        metadata['aura']['frames']=[aliases[key] for key in metadata['aura']['frames']]
        output.mkdir(parents=True,exist_ok=True)
        for i,p in enumerate(original['pages'][:2]):pages[i].save(output/p['file'])
        for f,im in zip(frames,images):
            x,y,w,h=f['rect'];check=Image.open(output/original['pages'][f['page']]['file']).convert('RGBA').crop((x,y,x+w,y+h))
            if check.tobytes()!=im.tobytes():raise ValueError('Saved-page reconstruction mismatch')
        (output/'enemy-attacks.json').write_text(json.dumps(metadata,indent=2)+'\n')
        print(json.dumps({'frames':len(frames),'textureBytesAdded':0,'originalFramePixels':'unchanged','output':str(output)}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,default=ROOT/'art-source/enemy-attacks-canonical.zip');parser.add_argument('--base-art',type=Path,default=ROOT/'assets/art');parser.add_argument('--output',type=Path,default=ROOT/'assets/art');args=parser.parse_args();bake(args.source,args.base_art,args.output)
