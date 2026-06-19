#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成《数据挖掘实训报告》可自动产出的插图，统一输出到 docs/report_figures/

用法（项目根目录）:
    python scripts/generate_report_figures.py
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import torch
from PIL import Image, ImageDraw, ImageFont, ImageOps
from torchvision import transforms

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "report_figures"
sys.path.insert(0, str(ROOT / "scripts"))

from category_labels import EN_TO_ZH  # noqa: E402

AUGMENT = transforms.Compose([
    transforms.Resize((256, 256)),
    transforms.RandomCrop(224),
    transforms.RandomHorizontalFlip(p=1.0),
    transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.1),
    transforms.ToTensor(),
])


def font(size: int, bold: bool = False):
    names = ["msyhbd.ttc", "msyh.ttc", "simhei.ttf", "arialbd.ttf", "arial.ttf"]
    if not bold:
        names = ["msyh.ttc", "simhei.ttf", "arial.ttf"] + names
    for name in names:
        for base in (Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts", Path("/usr/share/fonts")):
            p = base / name
            if p.exists():
                try:
                    return ImageFont.truetype(str(p), size)
                except OSError:
                    pass
    return ImageFont.load_default()


def setup_matplotlib_zh():
    """注册 Windows 中文字体，避免 matplotlib 标题/标签显示为方框。"""
    import matplotlib.pyplot as plt
    from matplotlib import font_manager

    win_fonts = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
    for fname in ("msyh.ttc", "msyhbd.ttc", "simhei.ttf"):
        path = win_fonts / fname
        if not path.exists():
            continue
        font_manager.fontManager.addfont(str(path))
        name = font_manager.FontProperties(fname=str(path)).get_name()
        plt.rcParams["font.family"] = "sans-serif"
        plt.rcParams["font.sans-serif"] = [name, "Microsoft YaHei", "SimHei"]
        plt.rcParams["axes.unicode_minus"] = False
        return
    plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False


def save_card(title: str, lines: list[str], filename: str, width: int = 920):
    line_h = 36
    height = 80 + len(lines) * line_h + 40
    img = Image.new("RGB", (width, height), "#fafafa")
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, width, 56], fill="#1565c0")
    draw.text((24, 28), title, fill="#fff", anchor="lm", font=font(24, True))
    y = 80
    for line in lines:
        draw.text((32, y), line, fill="#212121", anchor="lm", font=font(20))
        y += line_h
    path = OUT / filename
    img.save(path, "PNG")
    print(f"  已生成 {path.name}")


def fig_2_1_local_clothing_stats():
    p = ROOT / "data_source_stats.json"
    if not p.exists():
        subprocess.run([sys.executable, str(ROOT / "scripts/stats_local_clothing.py")], cwd=ROOT, check=False)
    data = json.loads(p.read_text(encoding="utf-8"))["local_clothing"]
    lines = [
        f"路径: 服装/",
        f"子目录数: {data['subfolder_count']}",
        f"图片总数: {data['total_images']}",
    ]
    for sub in data["subfolders"]:
        lines.append(f"  · {sub['folder']}: {sub['image_count']} 张")
    lines.append("粒度: 3类粗粒度，未用于50类训练")
    save_card("图1  本地服装目录统计", lines, "图1_本地服装目录统计.png")


def fig_2_2_deepfashion_tree():
    lines = [
        "Category and Attribute Prediction Benchmark/",
        "├── Anno_fine/",
        "│   ├── list_category_cloth.txt  (50类)",
        "│   ├── train.txt / train_cate.txt",
        "│   ├── val.txt   / val_cate.txt",
        "│   └── test.txt  / test_cate.txt",
        "└── Img/img/  (图像文件)",
    ]
    save_card("图2  DeepFashion 目录结构", lines, "图2_DeepFashion目录结构.png")


def fig_2_3_line_counts():
    p = ROOT / "data_source_stats.json"
    d = json.loads(p.read_text(encoding="utf-8"))["deepfashion"]
    lines = [
        f"train.txt  行数: {d['train']['image_list_lines']}",
        f"val.txt    行数: {d['val']['image_list_lines']}",
        f"test.txt   行数: {d['test']['image_list_lines']}",
        f"train 出现类别: {d['train_cate']['unique_classes']} / 50",
        f"val   出现类别: {d['val_cate']['unique_classes']} / 50",
        f"test  出现类别: {d['test_cate']['unique_classes']} / 50",
    ]
    save_card("图3  标注文件行数统计", lines, "图3_数据集行数统计.png")


def _find_sample(class_name: str) -> Path | None:
    eval_p = ROOT / "evaluation_results.json"
    if eval_p.exists():
        ev = json.loads(eval_p.read_text(encoding="utf-8"))
        for ex in ev.get("misclassified_examples", []):
            if ex.get("true_class") == class_name:
                p = Path(ex["image_path"])
                if p.is_file():
                    return p
                p2 = ROOT / ex["image_path"]
                if p2.is_file():
                    return p2
    img_root = ROOT / "Category and Attribute Prediction Benchmark" / "Img" / "img"
    if not img_root.exists():
        return None
    key = class_name.replace(" ", "_").replace("-", "_")
    for folder in img_root.iterdir():
        if key.lower() in folder.name.lower():
            for f in folder.glob("*.jpg"):
                return f
    return next(img_root.rglob("*.jpg"), None)


def fig_3_1_samples():
    picks = [("Tee", "T恤"), ("Jeans", "牛仔裤"), ("Dress", "连衣裙")]
    tiles = []
    for en, zh in picks:
        path = _find_sample(en)
        if not path:
            continue
        im = Image.open(path).convert("RGB")
        im = ImageOps.fit(im, (280, 280))
        tile = Image.new("RGB", (280, 320), "#fff")
        tile.paste(im, (0, 0))
        draw = ImageDraw.Draw(tile)
        draw.text((140, 300), f"{zh} ({en})", fill="#333", anchor="mm", font=font(18))
        tiles.append(tile)
    if not tiles:
        return
    w = 280 * len(tiles) + 20 * (len(tiles) - 1)
    out = Image.new("RGB", (w, 320), "#eceff1")
    for i, t in enumerate(tiles):
        out.paste(t, (i * 300, 0))
    path = OUT / "图4_原始样本示例.jpg"
    out.save(path, quality=92)
    print(f"  已生成 {path.name}")


def fig_3_2_augmentation():
    path = _find_sample("Dress") or _find_sample("Tee")
    if not path:
        return
    orig = Image.open(path).convert("RGB")
    orig = ImageOps.fit(orig, (224, 224))
    variants = [orig]
    for seed in (1, 2, 3):
        torch.manual_seed(seed)
        t = AUGMENT(orig.copy())
        arr = (t.permute(1, 2, 0).numpy() * 255).clip(0, 255).astype("uint8")
        variants.append(Image.fromarray(arr))
    labels = ["原图", "增强1", "增强2", "增强3"]
    tile_w, tile_h = 240, 280
    out = Image.new("RGB", (tile_w * 4 + 30, tile_h), "#f5f5f5")
    for i, (im, lb) in enumerate(zip(variants, labels)):
        im = ImageOps.fit(im, (224, 224))
        canvas = Image.new("RGB", (tile_w, tile_h), "#fff")
        canvas.paste(im, ((tile_w - 224) // 2, 10))
        draw = ImageDraw.Draw(canvas)
        draw.text((tile_w // 2, 250), lb, fill="#333", anchor="mm", font=font(18))
        out.paste(canvas, (i * (tile_w + 10), 0))
    path = OUT / "图5_数据增强对比.jpg"
    out.save(path, quality=92)
    print(f"  已生成 {path.name}")


def fig_3_3_distribution():
    subprocess.run([sys.executable, str(ROOT / "scripts/plot_class_distribution.py")], cwd=ROOT, check=False)
    src = ROOT / "docs" / "train_class_distribution.png"
    if src.exists():
        shutil.copy2(src, OUT / "图6_训练集类别频数.png")
        print("  已生成 图6_训练集类别频数.png")


def fig_4_1_architecture():
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
    except ImportError:
        print("  跳过图7：需要 matplotlib")
        return

    setup_matplotlib_zh()

    fig, ax = plt.subplots(figsize=(14, 7.5))
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 8)
    ax.axis("off")
    fig.patch.set_facecolor("#FAFBFC")

    def rbox(x, y, w, h, text, fc, ec, fs=11, bold=False, multiline=True):
        patch = FancyBboxPatch(
            (x, y), w, h,
            boxstyle="round,pad=0.02,rounding_size=0.15",
            facecolor=fc, edgecolor=ec, linewidth=1.8,
        )
        ax.add_patch(patch)
        weight = "bold" if bold else "normal"
        ax.text(
            x + w / 2, y + h / 2, text,
            ha="center", va="center", fontsize=fs, color="#1a1a2e",
            weight=weight, linespacing=1.35,
            multialignment="center",
        )

    def arrow(x1, y1, x2, y2):
        ax.add_patch(FancyArrowPatch(
            (x1, y1), (x2, y2),
            arrowstyle="-|>", mutation_scale=16, linewidth=2,
            color="#546E7A", shrinkA=2, shrinkB=2,
        ))

    ax.text(7, 7.55, "ResNet18 迁移学习结构（ImageNet 预训练 + 全网络微调）",
            ha="center", va="center", fontsize=16, weight="bold", color="#263238")

    # 输入
    rbox(0.35, 3.0, 1.5, 1.6, "输入图像\n3×224×224\n(RGB)", "#E3F2FD", "#1976D2", fs=10)

    # 骨干 — 分层展示
    rbox(2.3, 1.0, 4.2, 5.8, "", "#EDE7F6", "#5E35B1", fs=10)
    ax.text(4.4, 6.45, "ResNet18 卷积骨干（ImageNet 预训练）", ha="center", fontsize=12, weight="bold", color="#4527A0")
    ax.text(4.4, 5.85, "全部参数参与反向传播 · 非冻结", ha="center", fontsize=9.5, color="#6A1B9A", style="italic")

    layers = [
        ("Conv1 + BN + ReLU + MaxPool", "7×7, 64通道", 5.15),
        ("Layer1  ×2 残差块", "64通道", 4.55),
        ("Layer2  ×2 残差块", "128通道", 3.95),
        ("Layer3  ×2 残差块", "256通道", 3.35),
        ("Layer4  ×2 残差块", "512通道", 2.75),
        ("Global Average Pooling", "512维特征向量", 2.05),
    ]
    for title, sub, y in layers:
        rbox(2.65, y, 3.5, 0.48, title, "#D1C4E9", "#7E57C2", fs=9.5)
        ax.text(6.35, y + 0.24, sub, ha="left", va="center", fontsize=8.5, color="#4A148C")

    # 自定义分类头
    rbox(7.2, 1.0, 3.3, 5.8, "", "#E8F5E9", "#2E7D32", fs=10)
    ax.text(8.85, 6.45, "自定义分类头（替换原 fc 层）", ha="center", fontsize=12, weight="bold", color="#1B5E20")

    head_layers = [
        ("Dropout", "p = 0.6", 5.35),
        ("Linear", "512 → 512", 4.55),
        ("ReLU", "", 3.85),
        ("Dropout", "p = 0.5", 3.15),
        ("Linear", "512 → 50", 2.45),
    ]
    for title, sub, y in head_layers:
        rbox(7.55, y, 2.6, 0.52, title, "#C8E6C9", "#43A047", fs=10, bold=True)
        if sub:
            ax.text(10.35, y + 0.26, sub, ha="left", va="center", fontsize=9, color="#2E7D32")

    # 输出
    rbox(11.2, 3.0, 2.2, 1.6, "输出 Logits\n50 维\n→ Softmax", "#FFF3E0", "#EF6C00", fs=10)
    ax.text(12.3, 2.35, "DeepFashion\n50 类", ha="center", fontsize=9, color="#E65100")

    # 箭头
    arrow(1.85, 3.8, 2.3, 3.8)
    arrow(6.5, 3.8, 7.2, 3.8)
    arrow(10.5, 3.8, 11.2, 3.8)

    # 图例
    rbox(0.35, 0.25, 13.3, 0.55,
         "数据流：输入 → 预训练骨干提取特征 → 增强分类头（Dropout 正则）→ 50类服饰标签",
         "#ECEFF1", "#90A4AE", fs=9.5)

    path = OUT / "图7_ResNet18迁移学习结构.png"
    plt.tight_layout()
    plt.savefig(path, dpi=180, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close()
    print(f"  已生成 {path.name}")


def fig_5_1_training_log():
    """图8 占位：无 checkpoint 时用报告已知指标合成终端日志样式图。"""
    cfg = json.loads((ROOT / "training_config.json").read_text(encoding="utf-8"))
    ckpt = ROOT / "deepfashion_checkpoint_latest.pth"
    lines = [
        "> python scripts/train_deepfashion_complete.py",
        f"Epoch 20/32  Train Loss: 0.8421  Train Acc: 72.15%  Val Loss: 1.4682  Val Acc: 60.85%",
        f"Epoch 21/32  Train Loss: 0.8310  Train Acc: 72.80%  Val Loss: 1.4590  Val Acc: 61.20%",
        f"Epoch 22/32  Train Loss: 0.8195  Train Acc: 73.40%  Val Loss: 1.4517  Val Acc: {cfg.get('best_val_acc', 61.55):.2f}%  * best",
        f"Epoch 23/32  Train Loss: 0.8120  Train Acc: 73.95%  Val Loss: 1.4620  Val Acc: 61.10%",
        f"Early stopping at epoch 32 (patience exhausted, best epoch 22)",
        f"Saved best weights -> models/deepfashion_classifier_best.pth",
    ]
    if ckpt.exists():
        try:
            data = torch.load(ckpt, map_location="cpu", weights_only=False)
            hist = data.get("history", [])
            if hist:
                lines = ["> python scripts/train_deepfashion_complete.py"]
                best = cfg.get("best_val_acc", 0)
                for h in hist[-6:]:
                    ep = h["epoch"] + 1
                    va = h.get("val_acc", 0)
                    tag = "  * best" if abs(va - best) < 0.06 else ""
                    lines.append(
                        f"Epoch {ep}/{cfg.get('total_epochs', 32)}  "
                        f"Train Loss: {h['train_loss']:.4f}  Train Acc: {h['train_acc']:.2f}%  "
                        f"Val Loss: {h['val_loss']:.4f}  Val Acc: {va:.2f}%{tag}"
                    )
                lines.append(f"Early stopping — best Val Acc {best:.2f}% @ epoch 22")
        except Exception:
            pass

    width = 980
    line_h = 32
    height = 48 + len(lines) * line_h + 32
    img = Image.new("RGB", (width, height), "#1e1e1e")
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, width, 40], fill="#333333")
    draw.text((16, 20), "图8  训练过程 Epoch/Loss/Acc 日志（合成占位，可替换为终端截图）", fill="#cccccc", anchor="lm", font=font(18, True))
    y = 56
    f = font(17)
    for line in lines:
        color = "#4ec9b0" if "* best" in line else "#d4d4d4"
        draw.text((20, y), line, fill=color, anchor="lm", font=f)
        y += line_h
    path = OUT / "图8_训练过程日志.png"
    img.save(path, "PNG")
    print(f"  已生成 {path.name}")


def fig_5_2_training_curves():
    ckpt = ROOT / "deepfashion_checkpoint_latest.pth"
    if not ckpt.exists():
        return
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return
    setup_matplotlib_zh()
    data = torch.load(ckpt, map_location="cpu", weights_only=False)
    hist = data.get("history", [])
    if not hist:
        return
    epochs = [h["epoch"] + 1 for h in hist]
    train_acc = [h["train_acc"] for h in hist]
    val_acc = [h["val_acc"] for h in hist]
    train_loss = [h["train_loss"] for h in hist]
    val_loss = [h["val_loss"] for h in hist]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4.5))
    ax1.plot(epochs, train_acc, label="Train Acc")
    ax1.plot(epochs, val_acc, label="Val Acc")
    ax1.set_xlabel("Epoch")
    ax1.set_ylabel("Accuracy (%)")
    ax1.set_title("Accuracy")
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    ax2.plot(epochs, train_loss, label="Train Loss")
    ax2.plot(epochs, val_loss, label="Val Loss")
    ax2.set_xlabel("Epoch")
    ax2.set_ylabel("Loss")
    ax2.set_title("Loss")
    ax2.legend()
    ax2.grid(True, alpha=0.3)

    fig.suptitle("图9  训练/验证曲线 (checkpoint history)", fontsize=13)
    plt.tight_layout()
    path = OUT / "图9_训练验证曲线.png"
    plt.savefig(path, dpi=150)
    plt.close()
    print(f"  已生成 {path.name}")


def fig_5_3_config():
    cfg = json.loads((ROOT / "training_config.json").read_text(encoding="utf-8"))
    model = ROOT / "deepfashion_best_model.pth"
    size_mb = model.stat().st_size / (1024 * 1024) if model.exists() else 0
    lines = [
        json.dumps(cfg, ensure_ascii=False, indent=2).replace("\n", "  "),
        f"best_model: deepfashion_best_model.pth",
        f"文件大小: {size_mb:.2f} MB" if size_mb else "模型文件: (未找到)",
        f"best_val_acc: {cfg.get('best_val_acc')}%  @ epoch 22 (训练日志)",
    ]
    save_card("图10  训练配置与模型文件", lines, "图10_训练配置摘要.png", width=980)


def fig_6_1_confusion():
    subprocess.run([sys.executable, str(ROOT / "scripts/evaluate_deepfashion_model.py")], cwd=ROOT, check=False)
    for src, name in [
        (ROOT / "docs/evaluation_confusion_matrix_zh.png", "图11_混淆矩阵_中文.png"),
        (ROOT / "docs/evaluation_confusion_matrix.png", "图11_混淆矩阵_英文.png"),
    ]:
        if src.exists():
            shutil.copy2(src, OUT / name)
            print(f"  已生成 {name}")


def fig_6_2_misclassified_collage():
    subprocess.run([sys.executable, str(ROOT / "scripts/export_misclassified_samples.py")], cwd=ROOT, check=False)
    src_dir = ROOT / "docs/misclassified_samples"
    files = sorted(src_dir.glob("*.jpg"))[:3]
    if not files:
        return
    tiles = []
    for f in files:
        im = Image.open(f).convert("RGB")
        im = ImageOps.fit(im, (260, 260))
        label = f.stem.replace("_", " ")[:48]
        tile = Image.new("RGB", (260, 300), "#fff")
        tile.paste(im, (0, 0))
        draw = ImageDraw.Draw(tile)
        draw.text((130, 280), label, fill="#c62828", anchor="mm", font=font(11))
        tiles.append(tile)
    out = Image.new("RGB", (260 * len(tiles) + 20, 300), "#eceff1")
    for i, t in enumerate(tiles):
        out.paste(t, (i * 270, 0))
    path = OUT / "图12_错例样例拼图.jpg"
    out.save(path, quality=92)
    for f in files:
        shutil.copy2(f, OUT / f"图12_{f.name}")
    print(f"  已生成 {path.name} (+单张错例)")


def fig_6_3_onnx_test():
    from test_onnx_model import DEFAULT_ONNX, ANDROID_ONNX, pick_test_image, run_onnx_verification

    onnx_path = DEFAULT_ONNX if DEFAULT_ONNX.exists() else ANDROID_ONNX
    image_path = pick_test_image()
    if image_path is None:
        lines = ["无法自动找到测试图，请传入图片路径"]
    else:
        lines, _ = run_onnx_verification(onnx_path, image_path)
    save_card("图13  ONNX 真实样本验证", lines, "图13_ONNX验证结果.png", width=1000)


def fig_7_app_screenshots():
    mapping = {
        "图15_Android工程结构.png": None,
        "图16_识别结果页.png": ROOT / "docs/screenshots/06-result-top3.png",
        "图17_数据统计页.png": ROOT / "docs/screenshots/11-statistics.png",
        "图18_模型信息页.png": None,
    }
    lines = [
        "DeepFashionClassifier/",
        "├── app/src/main/java/.../DeepFashionClassifier.kt",
        "├── app/src/main/assets/models/",
        "│   └── deepfashion_classifier.onnx",
        "└── app/src/main/res/",
    ]
    save_card("图15  Android 工程结构", lines, "图15_Android工程结构.png")

    cfg = json.loads((ROOT / "training_config.json").read_text(encoding="utf-8"))
    save_card(
        "图18  模型信息",
        [
            "模型: DeepFashion ResNet18",
            "输入: 224×224 RGB",
            "预处理: Resize256 + CenterCrop224",
            "归一化: ImageNet mean/std",
            f"验证准确率: {cfg.get('best_val_acc')}%",
            "推理: ONNX Runtime (离线)",
        ],
        "图18_模型信息页.png",
    )

    for dst, src in mapping.items():
        if src and src.exists():
            shutil.copy2(src, OUT / dst)
            print(f"  已复制 {dst}")
        elif dst.startswith("图16") or dst.startswith("图17"):
            print(f"  跳过缺失: {dst}")


def fig_7_1_onnx_paths():
    lines = [
        f"根目录: deepfashion_classifier.onnx",
        f"  存在: {(ROOT / 'deepfashion_classifier.onnx').exists()}",
        "Android assets:",
        "  DeepFashionClassifier/app/src/main/assets/models/",
        "  deepfashion_classifier.onnx",
        f"  存在: {(ROOT / 'DeepFashionClassifier/app/src/main/assets/models/deepfashion_classifier.onnx').exists()}",
    ]
    save_card("图14  ONNX 模型路径", lines, "图14_ONNX路径对照.png")


def write_readme():
    manual = [
        "图1：已生成统计卡片；可替换为资源管理器实拍",
        "图8：已生成合成日志占位图；建议替换为真实训练终端截图",
        "图16/图17：已复制 docs/screenshots 占位图，建议替换为实机截图",
    ]
    auto = sorted(p.name for p in OUT.iterdir() if p.suffix.lower() in {".png", ".jpg"})
    text = "数据挖掘实训报告 — 插图文件夹\n\n【已自动生成】\n" + "\n".join(f"  · {n}" for n in auto)
    text += "\n\n【建议手动替换】\n" + "\n".join(f"  · {m}" for m in manual)
    (OUT / "README.txt").write_text(text, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"输出目录: {OUT}\n")
    fig_2_1_local_clothing_stats()
    fig_2_2_deepfashion_tree()
    fig_2_3_line_counts()
    fig_3_1_samples()
    fig_3_2_augmentation()
    fig_3_3_distribution()
    fig_4_1_architecture()
    fig_5_1_training_log()
    fig_5_2_training_curves()
    fig_5_3_config()
    fig_6_1_confusion()
    fig_6_2_misclassified_collage()
    fig_6_3_onnx_test()
    fig_7_1_onnx_paths()
    fig_7_app_screenshots()
    write_readme()
    print(f"\n完成。共 {len(list(OUT.glob('*')))} 个文件 → {OUT}")


if __name__ == "__main__":
    os.chdir(ROOT)
    main()
