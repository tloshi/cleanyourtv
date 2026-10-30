#!/usr/bin/env python3
"""Draw the Modern theme's wallpaper: a dark mesh gradient, three soft colour blooms on near-black.

    modern_wallpaper.py                       writes themes/modern/wallpaper.jpg
    modern_wallpaper.py some/other/path.jpg   writes it there instead

No picture, no illustration: the kind of quiet gradient a 2026 TV home screen sits on, dark enough for white
text and the app icons to carry the colour. Computed small and scaled up (a gradient has no edges to lose),
with a whisper of grain so TV panels do not band it. Saved as a JPEG under 300 KB, the size themes/README.md
asks for. Needs Pillow: python3 -m venv tools/venv && tools/venv/bin/pip install pillow.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops

WIDTH, HEIGHT = 1920, 1080
SMALL = (240, 135)
MAX_BYTES = 300 * 1024
BASE = (10, 12, 18)
# Each bloom: centre x and y as fractions, radius as a fraction of the width, colour, strength 0 to 1.
BLOOMS = (
    (0.12, 0.18, 0.62, (58, 70, 168), 0.60),      # indigo, top left, behind the clock
    (0.88, 0.92, 0.55, (22, 112, 122), 0.55),     # teal, bottom right, under the row
    (0.62, 0.08, 0.40, (96, 52, 132), 0.35),      # violet, top centre
    (0.45, 0.60, 0.30, (28, 44, 92), 0.30),       # a cooler lift in the middle
)


def bloom_weight(x, y, cx, cy, radius):
    """Smooth falloff: 1 at the centre, 0 at the radius, no visible edge."""
    d = math.hypot(x - cx, (y - cy) * HEIGHT / WIDTH) / radius
    if d >= 1.0:
        return 0.0
    t = 1.0 - d
    return t * t * (3 - 2 * t)


def render_small():
    w, h = SMALL
    img = Image.new("RGB", (w, h))
    px = img.load()
    for j in range(h):
        for i in range(w):
            x, y = i / (w - 1), j / (h - 1)
            r, g, b = BASE
            for cx, cy, radius, colour, strength in BLOOMS:
                k = bloom_weight(x, y, cx, cy, radius) * strength
                r += (colour[0] - r) * k
                g += (colour[1] - g) * k
                b += (colour[2] - b) * k
            # A touch darker towards the bottom edge, where the icons' names sit.
            shade = 1.0 - 0.18 * max(0.0, y - 0.7) / 0.3
            px[i, j] = (int(r * shade), int(g * shade), int(b * shade))
    return img


def grain(img):
    noise = Image.effect_noise(img.size, 3.0).convert("RGB")
    return ImageChops.add(img, noise, scale=1.0, offset=-128)


def render():
    return grain(render_small().resize((WIDTH, HEIGHT), Image.BICUBIC))


def save(img, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    for quality in (90, 86, 82, 78, 74):
        img.save(out, "JPEG", quality=quality, optimize=True, subsampling=1)
        if out.stat().st_size <= MAX_BYTES:
            return quality
    return quality


def main(argv):
    out = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parent.parent / "themes" / "modern" / "wallpaper.jpg"
    quality = save(render(), out)
    print(f"{out} ({out.stat().st_size // 1024} KB, JPEG quality {quality})")


if __name__ == "__main__":
    main(sys.argv)
