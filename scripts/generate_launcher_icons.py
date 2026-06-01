# -*- coding: utf-8 -*-
"""
Generate Android launcher icons from a source image for the DeepFashionClassifier app.
- Uses image.png at project root by default
- Writes adaptive foreground (drawable/ic_launcher_foreground.png)
- Writes legacy mipmap icons (ic_launcher.png and ic_launcher_round.png)
"""

import os
import argparse
from PIL import Image, ImageOps

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_RES = os.path.join(PROJECT_ROOT, 'DeepFashionClassifier', 'app', 'src', 'main', 'res')
SRC_IMAGE = os.path.join(PROJECT_ROOT, 'image.png')

# Legacy mipmap sizes (px)
LEGACY_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

# Adaptive foreground recommended size
ADAPTIVE_SIZE = 432  # px canvas size for adaptive foreground


def ensure_dir(path: str) -> None:
    if not os.path.exists(path):
        os.makedirs(path, exist_ok=True)


def generate(scale: float = 0.8):
    if not os.path.exists(SRC_IMAGE):
        raise FileNotFoundError(f'Source image not found: {SRC_IMAGE}')

    img = Image.open(SRC_IMAGE).convert('RGBA')

    # Make square by padding with transparent background
    w, h = img.size
    side = max(w, h)
    square = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - w) // 2, (side - h) // 2))

    # Adaptive foreground (apply scale within 432x432 canvas)
    drawable_dir = os.path.join(APP_RES, 'drawable')
    ensure_dir(drawable_dir)
    # scale content by factor and center within canvas
    inner = int(ADAPTIVE_SIZE * max(0.1, min(scale, 1.0)))
    fg_scaled = square.resize((inner, inner), Image.LANCZOS)
    fg = Image.new('RGBA', (ADAPTIVE_SIZE, ADAPTIVE_SIZE), (0, 0, 0, 0))
    offset = ((ADAPTIVE_SIZE - inner) // 2, (ADAPTIVE_SIZE - inner) // 2)
    fg.paste(fg_scaled, offset, fg_scaled)
    fg_path = os.path.join(drawable_dir, 'ic_launcher_foreground.png')
    fg.save(fg_path, format='PNG')
    print(f'[OK] Adaptive foreground written: {fg_path}')

    # Legacy icons (square and round)
    for folder, size in LEGACY_SIZES.items():
        target_dir = os.path.join(APP_RES, folder)
        ensure_dir(target_dir)

        # add padding around content to visually shrink (same scale factor)
        inner = int(size * max(0.1, min(scale, 1.0)))
        icon_inner = square.resize((inner, inner), Image.LANCZOS)
        icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        off = ((size - inner) // 2, (size - inner) // 2)
        icon.paste(icon_inner, off, icon_inner)
        icon_path = os.path.join(target_dir, 'ic_launcher.png')
        icon.save(icon_path, format='PNG')

        # round: add circular mask
        mask = Image.new('L', (size, size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        rounded = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        rounded.paste(icon, (0, 0), mask)
        round_path = os.path.join(target_dir, 'ic_launcher_round.png')
        rounded.save(round_path, format='PNG')

        print(f'[OK] {folder}: {size}x{size} written')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--scale', type=float, default=0.8, help='Visible content scale within the icon canvas (0.1~1.0). Default: 0.8')
    args = parser.parse_args()
    generate(scale=args.scale)
