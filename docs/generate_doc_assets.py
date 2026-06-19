# -*- coding: utf-8 -*-
"""Generate placeholder screenshots and architecture PNG for soft-copyright HTML doc."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SHOTS = ROOT / "screenshots"
IMAGES = ROOT / "images"

SCREENSHOTS = [
    ("01-onboarding.png", "首次引导"),
    ("02-home.png", "首页 Tab"),
    ("03-classify.png", "识别 Tab"),
    ("04-camera-single.png", "相机 · 单拍"),
    ("05-camera-burst.png", "相机 · 连拍"),
    ("06-result-top3.png", "识别结果 Top-3"),
    ("07-categories.png", "类别百科"),
    ("08-category-detail.png", "类别详情"),
    ("09-history.png", "历史记录"),
    ("11-statistics.png", "数据统计"),
    ("12-compare.png", "对比记录"),
    ("13-export.png", "导出与备份"),
    ("14-more.png", "更多"),
    ("15-settings.png", "设置"),
    ("16-crop.png", "裁剪重识别"),
    ("17-enhance.png", "增强重识别"),
]


def font(size: int):
    for name in ("msyh.ttc", "msyhbd.ttc", "simhei.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def make_phone_placeholder(filename: str, title: str):
    w, h = 540, 960
    img = Image.new("RGB", (w, h), "#eceff1")
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, w, 48], fill="#6200ee")
    draw.text((w // 2, 24), "服饰识别 V1.2", fill="#fff", anchor="mm", font=font(22))
    draw.rectangle([24, 72, w - 24, h - 120], fill="#ffffff", outline="#b0bec5", width=2)
    draw.text((w // 2, h // 2 - 40), title, fill="#212121", anchor="mm", font=font(28))
    draw.text((w // 2, h // 2 + 20), "界面示意图", fill="#757575", anchor="mm", font=font(18))
    draw.text((w // 2, h // 2 + 60), "可替换为实机截图", fill="#9e9e9e", anchor="mm", font=font(14))
    draw.rectangle([0, h - 72, w, h], fill="#fafafa", outline="#cfd8dc")
    for i, label in enumerate(["首页", "识别", "类别", "历史", "更多"]):
        x = 54 + i * 108
        draw.text((x, h - 36), label, fill="#6200ee" if title.startswith(label[:2]) else "#757575", anchor="mm", font=font(14))
    img.save(SHOTS / filename, "PNG")


def make_architecture_png():
    w, h = 1840, 1440
    img = Image.new("RGB", (w, h), "#ede7f6")
    draw = ImageDraw.Draw(img)
    f_title = font(36)
    f_box = font(22)
    f_small = font(18)

    draw.text((w // 2, 50), "服饰识别软件 V1.2 — 系统架构图", fill="#3700b3", anchor="mm", font=f_title)

    def box(x, y, bw, bh, text, fill="#6200ee", tc="#fff"):
        draw.rounded_rectangle([x, y, x + bw, y + bh], radius=16, fill=fill, outline="#4527a0", width=2)
        draw.text((x + bw // 2, y + bh // 2), text, fill=tc, anchor="mm", font=f_box)

    box(120, 100, 1600, 100, "MainContainerActivity  |  首页 · 识别 · 类别 · 历史 · 更多", "#7c4dff")
    y = 260
    xs = [160, 420, 680, 940, 1200]
    names = ["HomeFragment", "ClassifyFragment", "CategoryList", "HistoryFragment", "MoreFragment"]
    for x, n in zip(xs, names):
        box(x, y, 220, 70, n, "#9575cd")
    box(420, 380, 260, 70, "CameraActivity", "#3949ab")
    box(420, 490, 260, 70, "ResultActivity", "#3949ab")
    box(760, 490, 320, 70, "Crop / Enhance 重识别", "#5c6bc0")
    box(680, 380, 200, 70, "CategoryDetail", "#7986cb")
    box(1180, 380, 420, 100, "Statistics / Export\nCompare / Logs / Settings", "#3949ab")
    box(420, 600, 320, 60, "ClassifierProvider", "#546e7a")
    box(200, 710, 1440, 90, "DeepFashionClassifier · ONNX Runtime\nassets/models/deepfashion_classifier.onnx", "#00897b")

    for x1, y1, x2, y2 in [
        (270, 330, 540, 380), (540, 450, 540, 490), (650, 525, 760, 525),
        (540, 560, 540, 600), (540, 670, 540, 710), (790, 330, 780, 380),
        (1310, 330, 1310, 380),
    ]:
        draw.line([x1, y1, x2, y2], fill="#455a64", width=4)

    img.save(IMAGES / "architecture-diagram.png", "PNG")


def main():
    SHOTS.mkdir(parents=True, exist_ok=True)
    IMAGES.mkdir(parents=True, exist_ok=True)
    for fn, title in SCREENSHOTS:
        make_phone_placeholder(fn, title)
    make_architecture_png()
    logo_src = ROOT.parent / "image.png"
    if logo_src.exists():
        Image.open(logo_src).save(IMAGES / "app-logo.png")
    print(f"Generated {len(SCREENSHOTS)} screenshots + architecture PNG")


if __name__ == "__main__":
    main()
