#!/usr/bin/env python3
"""Draw the Dusk theme's wallpaper: a sky after sunset, a crescent moon, rolling hills and one tree.

    dusk_wallpaper.py                       writes themes/dusk/wallpaper.jpg
    dusk_wallpaper.py some/other/path.jpg   writes it there instead

Everything is drawn here, so the picture has no licence but the repository's. It is rendered at twice
the size and sampled down, which keeps the silhouettes smooth, and saved as a JPEG under 300 KB, the
size themes/README.md asks for. Needs Pillow: python3 -m venv tools/venv && tools/venv/bin/pip install pillow.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

WIDTH, HEIGHT = 1920, 1080
SUPERSAMPLE = 2
SEED = 7
MAX_BYTES = 300 * 1024

# The sky from the top of the picture down to the horizon: (fraction of the height, colour).
SKY = (
    (0.00, (6, 12, 34)),
    (0.28, (16, 34, 80)),
    (0.50, (52, 70, 122)),
    (0.62, (124, 102, 138)),
    (0.70, (212, 130, 110)),
    (0.76, (242, 160, 104)),
    (0.84, (240, 178, 122)),
    (1.00, (236, 192, 152)),
)
MOON = (0.79, 0.23, 0.052)          # centre x and y as fractions of the width and height, radius of the height
MOON_COLOUR = (234, 238, 246)
TREE_X = 0.80                       # as a fraction of the width, under the moon and clear of the calendar card


def mix(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def sky_at(t):
    """The sky colour at height fraction t, interpolated between the stops."""
    for (t0, c0), (t1, c1) in zip(SKY, SKY[1:]):
        if t0 <= t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0)
    return SKY[-1][1]


def paint_sky(img):
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for y in range(h):
        draw.line([(0, y), (w, y)], fill=sky_at(y / h))


def glow(img, cx, cy, rx, ry, colour, strength):
    """Blends a soft ellipse of colour over the picture, computed small and scaled up (fast, and smooth)."""
    w, h = img.size
    sw, sh = 192, 108
    mask = Image.new("L", (sw, sh))
    px = mask.load()
    for y in range(sh):
        for x in range(sw):
            d = math.hypot((x / sw - cx) / rx, (y / sh - cy) / ry)
            a = max(0.0, 1.0 - d) ** 2 * strength
            px[x, y] = int(a * 255)
    mask = mask.resize((w, h), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (w, h), colour), img, mask)


def paint_stars(img):
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for _ in range(170):
        x, y = random.uniform(0, w), random.uniform(0, h * 0.48)
        fade = 1.0 - y / (h * 0.5)                      # fewer and fainter near the glow
        if random.random() > fade + 0.15:
            continue
        r = random.choice((1, 1, 1, 2, 2, 3)) * SUPERSAMPLE / 2
        bright = int(140 + 110 * random.random() * fade)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(bright, bright, min(255, bright + 12)))


def paint_moon(img):
    w, h = img.size
    cx, cy, r = MOON[0] * w, MOON[1] * h, MOON[2] * h
    img = glow(img, MOON[0], MOON[1], 0.14, 0.26, (200, 212, 255), 0.26)
    # The lit disc, minus a second disc shifted to the right: a crescent lit on the left, and the sky with
    # its glow showing through the dark side instead of a flat darker circle.
    crescent = Image.new("L", (w, h))
    draw = ImageDraw.Draw(crescent)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    ox, oy, r2 = cx + r * 0.50, cy - r * 0.20, r * 0.93
    draw.ellipse([ox - r2, oy - r2, ox + r2, oy + r2], fill=0)
    return Image.composite(Image.new("RGB", (w, h), MOON_COLOUR), img, crescent)


def ridge(x, w, base, waves):
    """Height of a hill line at x: the baseline plus a few slow sine waves, in picture pixels."""
    y = base
    for amp, freq, phase in waves:
        y += amp * math.sin(x / w * freq * 2 * math.pi + phase)
    return y


def hill_layers(w, h):
    far_haze = mix((20, 28, 58), sky_at(0.72), 0.42)
    return (
        (0.655 * h, ((0.030 * h, 1.3, 0.4), (0.014 * h, 3.1, 2.0), (0.006 * h, 7.0, 1.0)), far_haze),
        (0.720 * h, ((0.034 * h, 1.1, 2.6), (0.012 * h, 2.7, 0.3), (0.005 * h, 6.3, 4.1)), mix((22, 30, 60), sky_at(0.72), 0.20)),
        (0.795 * h, ((0.040 * h, 0.9, 4.4), (0.012 * h, 2.2, 1.7), (0.004 * h, 5.5, 2.9)), (13, 19, 40)),
        (0.885 * h, ((0.030 * h, 0.8, 1.2), (0.010 * h, 2.5, 3.3)), (7, 11, 26)),
    )


def tree_ridge(x, w, h):
    """The third hill, with a gentle rise where the tree stands so it sits on a crest."""
    base, waves, _ = hill_layers(w, h)[2]
    bump = 0.065 * h * math.exp(-((x - TREE_X * w) / (0.11 * w)) ** 2)
    return ridge(x, w, base, waves) - bump


def paint_hills(img):
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for index, (base, waves, colour) in enumerate(hill_layers(w, h)):
        points = []
        for x in range(0, w + 8, 8):
            y = tree_ridge(x, w, h) if index == 2 else ridge(x, w, base, waves)
            points.append((x, y))
        points += [(w + 8, h + 8), (-8, h + 8)]
        draw.polygon(points, fill=colour)
        if index == 2:
            paint_tree(draw, TREE_X * w, tree_ridge(TREE_X * w, w, h) + 4, h, colour)


def paint_tree(draw, x0, y0, h, colour):
    """A lone deciduous tree in silhouette: a short trunk, forking branches, a broad crown of small leaf blobs."""
    tips = []

    def branch(x, y, angle, length, depth, width):
        x2, y2 = x + math.cos(angle) * length, y - math.sin(angle) * length
        draw.line([(x, y), (x2, y2)], fill=colour, width=max(2, int(width)))
        if depth == 0:
            tips.append((x2, y2))
            return
        forks = 2 if depth > 4 else random.choice((2, 2, 3))
        spread = 0.60 if depth >= 5 else 0.40       # the first forks open wide, the twigs less so
        for i in range(forks):
            a = angle + (i - (forks - 1) / 2) * spread * random.uniform(0.8, 1.25) + random.uniform(-0.1, 0.1)
            a = max(0.30, min(math.pi - 0.30, a))        # branches climb, never droop below the horizontal
            branch(x2, y2, a, length * random.uniform(0.58, 0.70), depth - 1, width * 0.68)

    trunk = 0.085 * h
    width = 0.020 * h
    # A slight flare at the foot of the trunk, so it grows out of the hill instead of standing on it.
    draw.polygon([(x0 - width * 0.95, y0 + 2), (x0 + width * 0.95, y0 + 2),
                  (x0 + width * 0.45, y0 - trunk * 0.5), (x0 - width * 0.45, y0 - trunk * 0.5)], fill=colour)
    branch(x0, y0, math.pi / 2 + random.uniform(-0.05, 0.05), trunk, 7, width)
    # Leaves: a few small blobs around every twig, which together make a broad, slightly ragged crown.
    for tx, ty in tips:
        for _ in range(4):
            r = random.uniform(0.006, 0.015) * h
            dx, dy = random.uniform(-r * 1.4, r * 1.4), random.uniform(-r, r * 0.6)
            draw.ellipse([tx + dx - r, ty + dy - r * 0.75, tx + dx + r, ty + dy + r * 0.75], fill=colour)


def vignette(img):
    w, h = img.size
    mask = Image.new("L", (96, 54))
    px = mask.load()
    for y in range(54):
        for x in range(96):
            d = math.hypot((x / 96 - 0.5) * 1.15, (y / 54 - 0.5))
            px[x, y] = int(max(0.0, d - 0.42) * 0.55 * 255)
    mask = mask.resize((w, h), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (w, h), (2, 4, 12)), img, mask)


def grain(img):
    """A whisper of noise: smooth gradients band on TV panels, and the noise breaks the bands up."""
    noise = Image.effect_noise(img.size, 3.0).convert("RGB")
    return ImageChops.add(img, noise, scale=1.0, offset=-128)


def render():
    random.seed(SEED)
    w, h = WIDTH * SUPERSAMPLE, HEIGHT * SUPERSAMPLE
    img = Image.new("RGB", (w, h))
    paint_sky(img)
    img = glow(img, 0.38, 0.75, 0.60, 0.26, (255, 196, 128), 0.38)   # the afterglow where the sun went down
    paint_stars(img)
    img = paint_moon(img)
    paint_hills(img)
    img = vignette(img)
    img = img.resize((WIDTH, HEIGHT), Image.LANCZOS)
    return grain(img)


def save(img, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    for quality in (88, 84, 80, 76, 72, 68):
        img.save(out, "JPEG", quality=quality, optimize=True, subsampling=1)
        if out.stat().st_size <= MAX_BYTES:
            return quality
    return quality


def main(argv):
    out = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parent.parent / "themes" / "dusk" / "wallpaper.jpg"
    quality = save(render(), out)
    print(f"{out} ({out.stat().st_size // 1024} KB, JPEG quality {quality})")


if __name__ == "__main__":
    main(sys.argv)
