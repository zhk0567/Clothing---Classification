#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
用真实测试图像验证 ONNX 与 PyTorch 推理一致性。

用法（项目根目录）:
    python scripts/test_onnx_model.py
    python scripts/test_onnx_model.py path/to/image.jpg
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms
from torchvision.models import resnet18

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from category_labels import ENGLISH_NAMES  # noqa: E402

DEFAULT_ONNX = ROOT / "deepfashion_classifier.onnx"
ANDROID_ONNX = ROOT / "DeepFashionClassifier/app/src/main/assets/models/deepfashion_classifier.onnx"
EVAL_JSON = ROOT / "evaluation_results.json"

VAL_TRANSFORM = transforms.Compose([
    transforms.Resize((256, 256)),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


def build_model():
    model = resnet18(weights=None)
    nf = model.fc.in_features
    model.fc = nn.Sequential(
        nn.Dropout(0.6),
        nn.Linear(nf, 512),
        nn.ReLU(inplace=True),
        nn.Dropout(0.5),
        nn.Linear(512, 50),
    )
    return model


def load_pytorch_model():
    path = ROOT / "deepfashion_best_model.pth"
    model = build_model()
    ckpt = torch.load(path, map_location="cpu", weights_only=False)
    sd = ckpt.get("model_state_dict", ckpt) if isinstance(ckpt, dict) else ckpt
    model.load_state_dict(sd, strict=True)
    model.eval()
    return model


def pick_test_image() -> Path | None:
    if EVAL_JSON.exists():
        data = json.loads(EVAL_JSON.read_text(encoding="utf-8"))
        ex = data.get("misclassified_examples", [])
        if ex:
            p = Path(ex[0]["image_path"])
            if p.is_file():
                return p
            p2 = ROOT / ex[0]["image_path"]
            if p2.is_file():
                return p2
    test_list = ROOT / "Category and Attribute Prediction Benchmark/Anno_fine/test.txt"
    if test_list.exists():
        rel = test_list.read_text(encoding="utf-8").splitlines()[0].strip()
        if rel.startswith("img/"):
            rel = rel.replace("img/", "Img/img/")
        full = ROOT / "Category and Attribute Prediction Benchmark" / rel.replace("/", os.sep)
        if full.is_file():
            return full
    return None


def preprocess_pil(path: Path) -> np.ndarray:
    img = Image.open(path).convert("RGB")
    t = VAL_TRANSFORM(img).unsqueeze(0)
    return t.numpy().astype(np.float32)


def softmax(x):
    e = np.exp(x - x.max())
    return e / e.sum()


def run_onnx_verification(onnx_path: Path, image_path: Path) -> tuple[list[str], bool]:
    """运行 ONNX 与 PyTorch 对比，返回报告用文本行与是否一致。"""
    try:
        import onnxruntime as ort
    except ImportError:
        return ["请安装: pip install onnxruntime"], False

    if not onnx_path.exists():
        return [f"找不到 ONNX: {onnx_path}"], False
    if not image_path.exists():
        return [f"找不到图像: {image_path}"], False

    arr = preprocess_pil(image_path)
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    inp_name = session.get_inputs()[0].name
    onnx_out = session.run(None, {inp_name: arr})[0][0]

    pt_model = load_pytorch_model()
    with torch.no_grad():
        pt_out = pt_model(torch.from_numpy(arr)).numpy()[0]

    onnx_idx = int(np.argmax(onnx_out))
    pt_idx = int(np.argmax(pt_out))
    onnx_prob = float(softmax(onnx_out)[onnx_idx])
    pt_prob = float(softmax(pt_out)[pt_idx])
    max_diff = float(np.max(np.abs(onnx_out - pt_out)))

    lines = [
        f"模型路径: {onnx_path}",
        f"测试图像: {image_path}",
        f"输入 shape: {arr.shape}",
        f"输出 shape: {onnx_out.shape}",
        f"PyTorch Top-1: {ENGLISH_NAMES[pt_idx]} ({pt_prob:.4f})",
        f"ONNX     Top-1: {ENGLISH_NAMES[onnx_idx]} ({onnx_prob:.4f})",
        f"logits 最大绝对差: {max_diff:.6f}",
    ]

    ok = True
    if onnx_idx != pt_idx:
        lines.append("警告: PyTorch 与 ONNX Top-1 不一致")
        ok = False
    elif max_diff > 1e-3:
        lines.append("警告: logits 差异偏大，请检查导出")
        ok = False
    else:
        lines.append("ONNX 与 PyTorch 真实样本推理一致")
    return lines, ok


def test_onnx_model(onnx_path: Path, image_path: Path) -> bool:
    try:
        import onnxruntime as ort  # noqa: F401
    except ImportError:
        print("请安装: pip install onnxruntime")
        return False

    lines, ok = run_onnx_verification(onnx_path, image_path)
    for line in lines:
        print(line)
    return ok


def main():
    os.chdir(ROOT)
    onnx_path = DEFAULT_ONNX if DEFAULT_ONNX.exists() else ANDROID_ONNX
    if len(sys.argv) > 1:
        image_path = Path(sys.argv[1])
    else:
        image_path = pick_test_image()
        if image_path is None:
            print("无法自动找到测试图，请传入图片路径")
            sys.exit(1)

    ok = test_onnx_model(onnx_path, image_path)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
