"""Deterministic, hand directed pixel fur; preserves the artist's cream/slate palette."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

root = Path(__file__).resolve().parents[1]
rng = random.Random(21019)
image = Image.new('RGBA', (128, 128), (255, 255, 255, 255))
draw = ImageDraw.Draw(image)
for y in range(96):
    # Warm undercoat, pale surface. A deliberately small palette, without smooth noise.
    base = (250, 240, 226) if y < 19 else (253, 245, 233) if y < 67 else (255, 248, 239)
    draw.line((0, y, 127, y), fill=base + (255,))
for i in range(250):
    x, y = rng.randrange(128), rng.randrange(93)
    length = rng.choice((2, 3, 4, 5))
    color = rng.choice(((232, 218, 201), (240, 225, 208), (246, 234, 219), (255, 252, 244)))
    draw.line((x, y, x + 1, min(94, y + length)), fill=color + (255,), width=1)
    if i % 3 == 0:
        draw.point((x - 1, min(94, y + length - 1)), fill=color + (255,))
for center in (30, 94):
    for y in (22, 43, 64):
        # Broken tapered edges read as fur, while the three markings stay legible.
        poly = [(center-10,y-5),(center+10,y),(center-7,y+7),(center-5,y+3),
                (center-9,y+5),(center-7,y+1),(center-11,y+2)]
        draw.polygon(poly, fill=(69,70,84,255))
        draw.line((center-8,y-3,center+3,y),fill=(89,89,103,255),width=1)
        draw.line((center-5,y+3,center+5,y),fill=(59,61,76,255),width=1)
        draw.point((center-7,y+5), fill=(239,225,209,255))
image.save(root / 'src/main/resources/assets/useless_stretcher/textures/entity/two_i_tail.png')
