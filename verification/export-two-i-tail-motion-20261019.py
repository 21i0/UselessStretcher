"""Export a repeatable contact sheet and animated view from real client screenshots."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
source = root / 'build/story-tail-contact-20261019/screenshots'
target = root / 'verification/tail-lively-20261019'
target.mkdir(exist_ok=True)
frames = [Image.open(p).convert('RGB') for p in sorted(source.glob('motion-*.png'))]
assert len(frames) == 96, len(frames)
palette = frames[0].quantize(colors=240)
images = [f.quantize(palette=palette) for f in frames]
images[0].save(target / 'tail-motion.gif', save_all=True, append_images=images[1:],
               duration=250, loop=0, optimize=True)
sheet = Image.new('RGB', (1280, 800), '#cad4e0')
draw = ImageDraw.Draw(sheet)
for index, tick in enumerate((0, 60, 120, 180, 240, 300, 360, 420)):
    frame = frames[tick // 5]
    # Readable rear detail and actual palm at both sweep extremes.
    tile = frame.crop((190, 165, 1090, 380)).resize((600, 143))
    x, y = (index % 2) * 640 + 20, (index // 2) * 195 + 20
    sheet.paste(tile, (x, y))
    draw.text((x, y+149), f't={tick}: seated back / side / actual palm', fill='#24364c')
sheet.save(target / 'motion-contact-sheet.png')
Image.open(source / 'skin-tail-5.png').save(target / 'side-back-seated.png')
Image.open(source / 'skin-tail-0.png').save(target / 'forms.png')
