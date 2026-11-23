#!/usr/bin/env python3
"""Bake four original sprite effects into one bounded RGBA page, offline (<=512²)."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from types import SimpleNamespace
import zipfile
from PIL import Image
from BakeGameplayAtlas import pack_page, padded_content

ROOT = Path(__file__).resolve().parents[1]

def bake(archive, output):
    frames=[]; clips=[]; normalized=[]
    with zipfile.ZipFile(archive) as z:
        source=json.loads(z.read('SOURCES.json'))
        for name,expected in source['hashes'].items():
            if hashlib.sha256(z.read(name)).hexdigest()!=expected:
                raise ValueError('Source hash differs: '+name)
        for animation in source['animations']:
            image=Image.open(io.BytesIO(z.read(animation['source']))).convert('RGBA')
            cw,ch=animation['cell']; columns,rows=animation['grid']
            if image.size!=(cw*columns,ch*rows): raise ValueError('Source grid differs')
            sx,sy=animation['scale']; size=[round(cw*sx),round(ch*sy)]
            pivot=[animation['pivot'][0]*sx,animation['pivot'][1]*sy]
            references=[]
            for i in range(columns*rows):
                x,y=(i%columns)*cw,(i//columns)*ch
                cell=image.crop((x,y,x+cw,y+ch))
                cell.putalpha(cell.getchannel('A').point(lambda a:0 if a<=4 else round((a-4)*255/251)))
                cell=cell.resize(tuple(size),Image.Resampling.LANCZOS)
                bounds=cell.getchannel('A').getbbox() or (0,0,1,1)
                key=f'{animation["name"]}/{i}'
                references.append(len(frames));normalized.append(cell)
                frames.append(SimpleNamespace(key=key,outer_size=(bounds[2]-bounds[0]+4,bounds[3]-bounds[1]+4),
                    bounds=bounds,source=size,pivot=pivot))
            clips.append({'name':animation['name'],'frames':references,'durationsMs':animation['durationsMs']})
    for height in (256,288,320,384,512):
        try:
            positions,method=pack_page(frames,(512,height)); break
        except ValueError:
            if height==512: raise
    page=Image.new('RGBA',(512,height)); metadata=[]
    for frame,cell in zip(frames,normalized):
        x,y,_,_=positions[frame.key];l,t,r,b=frame.bounds
        page.paste(padded_content(cell.crop(frame.bounds)),(x,y))
        metadata.append({'rect':[x+2,y+2,r-l,b-t],'source':frame.source,'offset':[l,t],'pivot':frame.pivot})
    output.mkdir(parents=True,exist_ok=True)
    page.save(output/'combat-vfx.png')
    saved=Image.open(output/'combat-vfx.png').convert('RGBA')
    for frame,cell,meta in zip(frames,normalized,metadata):
        x,y,w,h=meta['rect']
        if saved.crop((x,y,x+w,y+h)).tobytes()!=cell.crop(frame.bounds).tobytes():
            raise ValueError('Packed pixels differ: '+frame.key)
    manifest={'version':1,'file':'combat-vfx.png','size':[512,height],'frames':metadata,'animations':clips}
    (output/'combat-vfx.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return {'frames':len(frames),'dimensions':[512,height],'textureBytes':512*height*4,'retainedPixmapBytes':0,
        'outerPixels':sum(w*h for _,_,w,h in positions.values()),'pack':method,
        'outputHashes':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [output/'combat-vfx.png',output/'combat-vfx.json']}}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=ROOT/'art-source/combat-vfx-canonical.zip')
    parser.add_argument('--output',type=Path,default=ROOT/'assets/art')
    args=parser.parse_args()
    print(json.dumps(bake(args.source,args.output),indent=2))
