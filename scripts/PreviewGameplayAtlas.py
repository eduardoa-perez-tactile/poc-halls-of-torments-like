#!/usr/bin/env python3
"""Create a standalone animation viewer and bounded proofs from packed pixels."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SCALE = {'BRUTE': 1.4, 'WRAITH': .82, 'SOVEREIGN': 2.2}
FACINGS = ('SE', 'SW', 'NE', 'NW')

HTML = '''<!doctype html><html lang="en"><meta charset="utf-8"><title>Couch Crypt animation kit</title><link rel="icon" href="data:,">
<style>body{margin:24px;background:#111b20;color:#eee7d2;font:16px system-ui}h1{font-size:25px;margin:0 0 8px}p{color:#aebcbf}button,select{background:#26363d;color:#eee7d2;border:1px solid #8e7951;padding:8px;border-radius:4px;margin:4px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:14px;max-width:1400px}article{background:#1a282e;border:1px solid #455055;border-radius:6px;overflow:hidden}h2{margin:12px;font-size:18px}canvas{width:100%;display:block}small{display:block;margin:8px 12px;color:#b6c6c7}</style>
<h1>Couch Crypt · animation kit</h1><p id="notice"></p><button id="play">Pause</button><button id="step">Step 40 ms</button><button id="restart">Restart action</button><label><input id="roots" type="checkbox" checked> Ground roots</label><main></main>
<script>
const kit=__KIT__, sources=__SOURCES__, scales={BRUTE:1.4,WRAITH:.82,SOVEREIGN:2.2};
let playing=true, phase=0, last=0;
const views=[];
function sample(clip,ms){const total=clip.durationsMs.reduce((a,b)=>a+b,0); let t=clip.loop?((ms%total)+total)%total:Math.max(0,ms);for(let i=0;i<clip.frames.length;i++){if(t<clip.durationsMs[i])return clip.frames[i];t-=clip.durationsMs[i]}return clip.frames.at(-1)}
const images=sources.map(src=>{const im=new Image();im.src=src;return im});
document.querySelector('#notice').textContent=(kit.preview?'PARTIAL ART PROOF · ':'ART PROOF · ')+kit.actors.length+' of 7 actors. Packed PNG pixels and authored timing; this is not a gameplay recording. All cards use the same world scale, including existing enemy size modifiers.';
for(const actor of kit.actors){const el=document.createElement('article'), title=document.createElement('h2');title.textContent=actor.id;el.append(title);const facing=document.createElement('select');for(const f of ['SE','SW','NE','NW'])facing.add(new Option(f,f));const state=document.createElement('select');for(const s of Object.keys(actor.clips))state.add(new Option(s,s));state.value='MOVE';facing.setAttribute('aria-label',actor.id+' facing');state.setAttribute('aria-label',actor.id+' action');const canvas=document.createElement('canvas');canvas.width=640;canvas.height=400;const info=document.createElement('small');el.append(facing,state,canvas,info);document.querySelector('main').append(el);state.onchange=facing.onchange=()=>{phase=0};views.push({actor,facing,state,canvas,info})}
function draw(){for(const v of views){const {actor,canvas}=v,ctx=canvas.getContext('2d');ctx.fillStyle='#1a282e';ctx.fillRect(0,0,640,400);const clip=actor.clips[v.state.value][v.facing.value],index=sample(clip,phase),f=kit.frames[index],im=images[f.page],k=actor.worldHeight/actor.canvas[1]*(scales[actor.id]||1)*2;ctx.imageSmoothingEnabled=true;if(im.complete)ctx.drawImage(im,...f.rect,320+(f.offset[0]-f.pivot[0])*k,370+(f.offset[1]-f.pivot[1])*k,f.rect[2]*k,f.rect[3]*k);if(document.querySelector('#roots').checked){ctx.strokeStyle='#bc9f62';ctx.beginPath();ctx.moveTo(310,370);ctx.lineTo(330,370);ctx.moveTo(320,365);ctx.lineTo(320,375);ctx.stroke()}v.info.textContent='Frame '+(clip.frames.indexOf(index)+1)+' / '+clip.frames.length+' · '+(clip.loop?'loop':'one-shot, then held') }}
document.querySelector('#play').onclick=()=>{playing=!playing;document.querySelector('#play').textContent=playing?'Pause':'Play'};
document.querySelector('#step').onclick=()=>{playing=false;document.querySelector('#play').textContent='Play';phase+=40;draw()};
document.querySelector('#restart').onclick=()=>{phase=0;draw()};
function tick(now){if(last&&playing)phase+=Math.min(100,now-last);last=now;draw();requestAnimationFrame(tick)}requestAnimationFrame(tick);
</script></html>'''


def sample(clip, milliseconds):
    total = sum(clip['durationsMs'])
    t = milliseconds % total if clip['loop'] else max(0, milliseconds)
    for frame, duration in zip(clip['frames'], clip['durationsMs']):
        if t < duration:
            return frame
        t -= duration
    return clip['frames'][-1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--partial', action='store_true', help='Explicitly label an incomplete art-only proof')
    args = parser.parse_args()
    packed = ROOT / '.run/art-exploration/weekend/packed'
    manifest = packed / ('gameplay.partial.json' if args.partial else 'gameplay.json')
    kit = json.loads(manifest.read_text())
    if not args.partial and (kit.get('preview') or len(kit['actors']) != 7):
        raise ValueError('The full animation proof requires all seven actors')
    output = ROOT / '.run/art-exploration/weekend/animation-kit' / ('partial' if args.partial else 'final')
    output.mkdir(parents=True, exist_ok=True)
    sources, pages = [], []
    for p in kit['pages']:
        path = packed / p['file']
        sources.append('data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode())
        im = Image.open(path).convert('RGBA')
        if list(im.size) != p['size']:
            raise ValueError('Page dimensions disagree with manifest')
        pages.append(im)
    crops = [pages[f['page']].crop((f['rect'][0], f['rect'][1], f['rect'][0]+f['rect'][2], f['rect'][1]+f['rect'][3])) for f in kit['frames']]

    def draw_frame(target, actor, facing, state, milliseconds, root, world_scale):
        index = sample(actor['clips'][state][facing], milliseconds)
        frame, crop = kit['frames'][index], crops[index]
        k = actor['worldHeight'] / actor['canvas'][1] * SCALE.get(actor['id'], 1) * world_scale
        size = (max(1, round(crop.width*k)), max(1, round(crop.height*k)))
        x = round(root[0] + (frame['offset'][0]-frame['pivot'][0])*k)
        y = round(root[1] + (frame['offset'][1]-frame['pivot'][1])*k)
        target.alpha_composite(crop.resize(size, Image.Resampling.LANCZOS), (x, y))

    board = Image.new('RGBA', (960, 720), '#1a282e')
    pen = ImageDraw.Draw(board)
    for n, actor in enumerate(kit['actors']):
        x, y = n % 3 * 320, n // 3 * 240
        pen.text((x+12, y+8), actor['id']+' / SE + NE', fill='#e6ce9a')
        for col, facing in enumerate(('SE', 'NE')):
            draw_frame(board, actor, facing, 'MOVE', 0, (x+80+160*col, y+220), 1)
        clips = actor['clips']['MOVE']
        total = max(sum(c['durationsMs']) for c in clips.values())
        events = {0, total}
        for clip in clips.values():
            end = 0
            for duration in clip['durationsMs']:
                end += duration
                if end <= total:
                    events.add(end)
        events = sorted(events)
        frames, durations = [], []
        for start, end in zip(events, events[1:]):
            image = Image.new('RGBA', (768, 768), '#1a282e')
            label = ImageDraw.Draw(image)
            for i, facing in enumerate(FACINGS):
                ox, oy = i % 2 * 384, i // 2 * 384
                draw_frame(image, actor, facing, 'MOVE', start, (ox+192, oy+352), 2)
                label.text((ox+12, oy+12), actor['id']+' '+facing+' / ART PROOF', fill='#e6ce9a')
            frames.append(image.convert('RGB').quantize(colors=256, dither=Image.Dither.NONE))
            durations.append(end-start)
        frames[0].save(output/(actor['id'].lower()+'-walk.gif'), save_all=True, append_images=frames[1:], duration=durations, loop=0, disposal=2)
    board.convert('RGB').save(output/'cast-walk-contact-sheet.png')
    html = HTML.replace('__KIT__', json.dumps(kit).replace('</', '<\\/')).replace('__SOURCES__', json.dumps(sources))
    (output/'animation-kit.html').write_text(html)
    (output/'proof.json').write_text(json.dumps({'type':'art-proof-not-gameplay', 'actors':[a['id'] for a in kit['actors']], 'manifestSha256':hashlib.sha256(manifest.read_bytes()).hexdigest(), 'partial':args.partial, 'gifPalette':'256 colours; original PNG pixels embedded in HTML', 'rootGeometry':'same source offsets/pivots and existing enemy scale modifiers as ArenaRenderer'}, indent=2)+'\n')
    print(json.dumps({'output':str(output.relative_to(ROOT)), 'actors':len(kit['actors']), 'partial':args.partial}))


if __name__ == '__main__':
    main()
