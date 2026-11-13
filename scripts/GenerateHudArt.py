#!/usr/bin/env python3
"""Bake the approved code-native orb HUD gradients/shapes into one bounded RGBA atlas.

Source: approved .run/hud-proposal/proposal.html SVG/CSS, 2026-11-13.
Requires Pillow only for this offline asset-generation step; runtime has no dependency.
"""
from pathlib import Path
from math import hypot
from PIL import Image, ImageColor, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SCALE = 4
GOLD = [(0, '#fff0bf'), (.16, '#a58c55'), (.42, '#302b25'), (.64, '#ccb179'), (.82, '#54442e'), (1, '#201d1a')]
TEAL = [(0, '#d3eee0'), (.2, '#7fa89f'), (.45, '#29383a'), (.67, '#acd2c4'), (1, '#27322f')]
BLOOD = [(0, '#ff7567'), (.26, '#d62e30'), (.62, '#80101e'), (1, '#190610')]
GLASS = [(0, '#2c3031'), (.45, '#080c0e'), (1, '#010406')]
REFLECTION = [(0, '#ffffff77'), (.17, '#ffffff00'), (.8, '#00000000'), (1, '#000000cc')]


def blank(size=128):
    return Image.new('RGBA', (size * SCALE, size * SCALE))


def points(values):
    return [(round(x * SCALE), round(y * SCALE)) for x, y in values]


def gradient(width, height, stops, radial=None, vertical=False, horizontal=False):
    stops = [(offset, ImageColor.getcolor(color, 'RGBA')) for offset, color in stops]
    pixels = []
    for y in range(height * SCALE):
        for x in range(width * SCALE):
            px, py = (x + .5) / SCALE, (y + .5) / SCALE
            if radial:
                cx, cy, radius = radial
                amount = hypot(px - cx, py - cy) / radius
            else:
                amount = py / height if vertical else px / width if horizontal else (px / width * .7 + py / height) / 1.7
            amount = max(0, min(1, amount))
            for index in range(1, len(stops)):
                end, right = stops[index]
                start, left = stops[index - 1]
                if amount <= end:
                    fraction = (amount - start) / (end - start)
                    pixels.append(tuple(round(a + (b - a) * fraction) for a, b in zip(left, right)))
                    break
    image = Image.new('RGBA', (width * SCALE, height * SCALE))
    image.putdata(pixels)
    return image


def circle_mask(size, radius, width=0):
    mask = Image.new('L', (size * SCALE, size * SCALE))
    center = size / 2
    draw = ImageDraw.Draw(mask)
    bounds = tuple(round(value * SCALE) for value in (center - radius, center - radius, center + radius, center + radius))
    draw.ellipse(bounds, fill=255 if not width else None, outline=255 if width else None, width=max(1, round(width * SCALE)))
    return mask


def overlay(target, image, mask):
    # Multiply, rather than replace, the source alpha so translucent reflections stay translucent.
    from PIL import ImageChops
    image = image.copy()
    image.putalpha(ImageChops.multiply(image.getchannel('A'), mask))
    target.alpha_composite(image)


def orb_back():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.ellipse((13*SCALE, 13*SCALE, 115*SCALE, 115*SCALE), fill='#0b0d0e')
    overlay(image, gradient(128, 128, GLASS, radial=(49.9, 34.9, 79.9)), circle_mask(128, 47))
    draw.ellipse((17*SCALE, 17*SCALE, 111*SCALE, 111*SCALE), outline='#050809', width=2*SCALE)
    return image


def orb_blood():
    image = blank()
    overlay(image, gradient(128, 128, BLOOD, radial=(55.4, 41.6, 73.1)), circle_mask(128, 43))
    return image


def orb_reflection():
    image = blank()
    overlay(image, gradient(128, 128, REFLECTION, radial=(48.5, 38.2, 64.5)), circle_mask(128, 43))
    highlight = blank()
    ImageDraw.Draw(highlight).ellipse((36*SCALE, 34*SCALE, 60*SCALE, 44*SCALE), fill=(255, 255, 255, 158))
    highlight = highlight.rotate(40, resample=Image.Resampling.BICUBIC, center=(48*SCALE, 39*SCALE))
    image.alpha_composite(highlight)
    ImageDraw.Draw(image).ellipse((80*SCALE, 81*SCALE, 88*SCALE, 97*SCALE), fill=(255, 181, 160, 31))
    return image


def orb_rim(teal=False):
    metal = gradient(128, 128, TEAL if teal else GOLD)
    mask = circle_mask(128, 51, 5)
    rim = blank()
    shapes = [[(13,39),(5,25),(22,28)], [(115,39),(123,25),(106,28)],
              [(30,107),(23,119),(42,114)], [(98,107),(105,119),(86,114)]]
    draw = ImageDraw.Draw(rim)
    edge = ImageDraw.Draw(mask)
    for shape in shapes:
        draw.polygon(points(shape), fill='#242824')
        edge.line(points(shape + [shape[0]]), fill=255, width=2*SCALE, joint='curve')
    overlay(rim, metal, mask)
    overlay(rim, metal, circle_mask(128, 44, 1.5))
    draw = ImageDraw.Draw(rim)
    draw.polygon(points([(64,7),(69,13),(64,20),(59,13)]), fill='#9bcbbf' if teal else '#e3c58b', outline='#29241b', width=SCALE)
    diamond = Image.new('L', rim.size)
    ImageDraw.Draw(diamond).polygon(points([(64,112),(69,119),(64,124),(59,119)]), fill=255)
    overlay(rim, metal, diamond)
    shadow = blank()
    alpha = rim.getchannel('A').filter(ImageFilter.GaussianBlur(2*SCALE)).point(lambda value: round(value * .65))
    shadow.putalpha(alpha)
    result = blank()
    result.alpha_composite(shadow, (0, 2*SCALE))
    result.alpha_composite(rim)
    return result


def skill_rim(teal=False):
    image = blank(64)
    draw = ImageDraw.Draw(image)
    draw.ellipse((2*SCALE,2*SCALE,62*SCALE,62*SCALE), outline='#141b1a' if teal else '#171817', width=3*SCALE)
    draw.ellipse((4*SCALE,4*SCALE,60*SCALE,60*SCALE), outline='#273b3a' if teal else '#433a2d', width=3*SCALE)
    overlay(image, gradient(64,64,TEAL if teal else GOLD), circle_mask(64,29,1.5))
    draw.ellipse((7*SCALE,7*SCALE,57*SCALE,57*SCALE), outline=(255,255,255,75), width=SCALE)
    return image


def dodge_back():
    image = blank(64)
    overlay(image, gradient(64,64,[(0,'#263338'),(1,'#080f13')],radial=(24,20,45)),circle_mask(64,27))
    ImageDraw.Draw(image).ellipse((4*SCALE,4*SCALE,60*SCALE,60*SCALE),outline='#3c4645',width=3*SCALE)
    return image


def dodge_glyph():
    image = blank(64)
    ImageDraw.Draw(image).line(points([(22,40),(32,24),(40,24),(30,40)]), fill='white', width=3*SCALE, joint='curve')
    ImageDraw.Draw(image).line(points([(27,28),(20,40)]), fill='white', width=3*SCALE)
    return image


def main():
    atlas = Image.new('RGBA',(512*SCALE,256*SCALE))
    for image,x,y in [(orb_back(),0,0),(orb_blood(),128,0),(orb_reflection(),256,0),
                      (orb_rim(),384,0),(orb_rim(True),0,128),
                      (skill_rim(),128,128),(skill_rim(True),192,128),
                      (dodge_back(),256,128),(dodge_glyph(),320,128)]:
        atlas.alpha_composite(image,(x*SCALE,y*SCALE))
    health = gradient(32,16,[(0,'#f0836b'),(.25,'#d52d30'),(.55,'#a21626'),(1,'#4a0b16')],vertical=True)
    atlas.alpha_composite(health,(256*SCALE,192*SCALE))
    for x,colors in [(288,[(0,'#886738'),(1,'#e5cf92')]),(320,[(0,'#385f5c'),(1,'#a2d3c3')])]:
        atlas.alpha_composite(gradient(32,8,colors,horizontal=True),(x*SCALE,192*SCALE))
    ImageDraw.Draw(atlas).rectangle((352*SCALE,192*SCALE,356*SCALE-1,196*SCALE-1),fill="white")
    output=ROOT/'assets/art/hud-atlas.png'
    atlas.resize((512,256),Image.Resampling.LANCZOS).save(output,optimize=True)
    print(f'{output}:512x256RGBA,524288GPUbytes,staticapprovedSVG-derivedart')


if __name__ == '__main__':
    main()
