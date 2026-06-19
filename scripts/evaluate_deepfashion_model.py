#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DeepFashion 模型评估：验证/测试 Top-1、Top-3、混淆矩阵、错例（softmax 概率）。

用法（项目根目录）:
    python scripts/evaluate_deepfashion_model.py
"""

from __future__ import annotations

import json
import os
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision.models import resnet18

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from category_labels import EN_TO_ZH  # noqa: E402
from train_deepfashion_complete import DeepFashionDataset, get_data_transforms  # noqa: E402

try:
    from torchvision.models import ResNet18_Weights
except ImportError:
    ResNet18_Weights = None


def build_model(num_classes: int = 50) -> nn.Module:
    model = resnet18(weights=None)
    num_features = model.fc.in_features
    model.fc = nn.Sequential(
        nn.Dropout(0.6),
        nn.Linear(num_features, 512),
        nn.ReLU(inplace=True),
        nn.Dropout(0.5),
        nn.Linear(512, num_classes),
    )
    return model


def load_weights(model: nn.Module, path: Path) -> None:
    checkpoint = torch.load(path, map_location="cpu", weights_only=False)
    if isinstance(checkpoint, dict):
        state_dict = checkpoint.get("model_state_dict", checkpoint.get("state_dict", checkpoint))
    else:
        state_dict = checkpoint
    model.load_state_dict(state_dict, strict=True)


def softmax_np(logits: np.ndarray) -> np.ndarray:
    e = np.exp(logits - logits.max(axis=1, keepdims=True))
    return e / e.sum(axis=1, keepdims=True)


def top_k_accuracy(logits: np.ndarray, labels: np.ndarray, k: int) -> float:
    topk = np.argsort(logits, axis=1)[:, -k:]
    hits = sum(int(labels[i] in topk[i]) for i in range(len(labels)))
    return 100.0 * hits / len(labels)


def compute_metrics(all_labels: np.ndarray, all_preds: np.ndarray, num_classes: int):
    cm = np.zeros((num_classes, num_classes), dtype=np.int64)
    for t, p in zip(all_labels, all_preds):
        cm[int(t), int(p)] += 1

    per_class = []
    present = []
    for c in range(num_classes):
        tp = cm[c, c]
        fp = cm[:, c].sum() - tp
        fn = cm[c, :].sum() - tp
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
        support = int(cm[c, :].sum())
        row = {
            "class_idx": c,
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": support,
        }
        per_class.append(row)
        if support > 0:
            present.append(row)

    macro_all = {
        "macro_precision": round(float(np.mean([x["precision"] for x in per_class])), 4),
        "macro_recall": round(float(np.mean([x["recall"] for x in per_class])), 4),
        "macro_f1": round(float(np.mean([x["f1"] for x in per_class])), 4),
    }
    macro_present = {
        "macro_precision_present": round(float(np.mean([x["precision"] for x in present])), 4),
        "macro_recall_present": round(float(np.mean([x["recall"] for x in present])), 4),
        "macro_f1_present": round(float(np.mean([x["f1"] for x in present])), 4),
        "num_classes_with_support": len(present),
    }

    pairs = []
    for i in range(num_classes):
        for j in range(num_classes):
            if i != j and cm[i, j] > 0:
                pairs.append((int(cm[i, j]), i, j))
    pairs.sort(reverse=True)
    top_confusions = [
        {
            "true_idx": i,
            "pred_idx": j,
            "count": cnt,
            "true_class": "",
            "pred_class": "",
        }
        for cnt, i, j in pairs[:10]
    ]

    return {
        "confusion_matrix": cm.tolist(),
        "per_class": per_class,
        **macro_all,
        **macro_present,
        "micro_accuracy_top1": round(100.0 * np.trace(cm) / cm.sum(), 2),
        "top_confusions": top_confusions,
    }


def save_confusion_figure(
    cm: np.ndarray, class_names: list[str], out_path: Path, title: str, chinese: bool = False
) -> None:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("未安装 matplotlib，跳过混淆矩阵图")
        return

    if chinese:
        plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
        plt.rcParams["axes.unicode_minus"] = False

    fig_size = max(12, len(class_names) * 0.22)
    fig, ax = plt.subplots(figsize=(fig_size, fig_size))
    im = ax.imshow(cm, interpolation="nearest", cmap="Blues")
    ax.set_title(title)
    plt.colorbar(im, ax=ax, fraction=0.046)
    ticks = np.arange(len(class_names))
    ax.set_xticks(ticks)
    ax.set_yticks(ticks)
    ax.set_xticklabels(class_names, rotation=90, fontsize=6)
    ax.set_yticklabels(class_names, fontsize=6)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    plt.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(out_path, dpi=150)
    plt.close()
    print(f"混淆矩阵图: {out_path}")


@torch.no_grad()
def evaluate_split(model, loader, device):
    model.eval()
    all_logits, all_labels = [], []
    total_loss = 0.0
    criterion = nn.CrossEntropyLoss()
    for images, labels in loader:
        images, labels = images.to(device), labels.to(device)
        outputs = model(images)
        total_loss += criterion(outputs, labels).item()
        all_logits.append(outputs.cpu().numpy())
        all_labels.append(labels.cpu().numpy())
    logits = np.concatenate(all_logits, axis=0)
    labels = np.concatenate(all_labels, axis=0)
    preds = logits.argmax(axis=1)
    return {
        "loss": round(total_loss / max(len(loader), 1), 4),
        "top1_acc": round(100.0 * (preds == labels).mean(), 2),
        "top3_acc": round(top_k_accuracy(logits, labels, 3), 2),
        "preds": preds,
        "labels": labels,
        "logits": logits,
    }


def count_category_distribution(cate_path: Path) -> dict:
    counter = Counter()
    with open(cate_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                counter[int(line)] += 1
    return {str(k): v for k, v in sorted(counter.items())}


def load_training_best_acc() -> float | None:
    cfg = ROOT / "training_config.json"
    if cfg.exists():
        return json.loads(cfg.read_text(encoding="utf-8")).get("best_val_acc")
    return None


def main():
    os.chdir(ROOT)
    data_dir = "Category and Attribute Prediction Benchmark"
    model_path = Path("deepfashion_best_model.pth")
    if not model_path.exists():
        print(f"错误: 找不到 {model_path}")
        sys.exit(1)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"设备: {device}")

    _, val_transform = get_data_transforms()
    category_file = "Anno_fine/list_category_cloth.txt"

    val_ds = DeepFashionDataset(data_dir, "Anno_fine/val.txt", category_file, val_transform, "val")
    test_ds = DeepFashionDataset(data_dir, "Anno_fine/test.txt", category_file, val_transform, "test")

    val_loader = DataLoader(val_ds, batch_size=32, shuffle=False, num_workers=0)
    test_loader = DataLoader(test_ds, batch_size=32, shuffle=False, num_workers=0)

    model = build_model().to(device)
    load_weights(model, model_path)

    val_result = evaluate_split(model, val_loader, device)
    test_result = evaluate_split(model, test_loader, device)

    class_names = [val_ds.idx_to_category[i] for i in range(val_ds.num_classes)]
    class_names_zh = [EN_TO_ZH.get(n, n) for n in class_names]
    test_metrics = compute_metrics(test_result["labels"], test_result["preds"], val_ds.num_classes)

    for item in test_metrics["top_confusions"]:
        item["true_class"] = class_names[item["true_idx"]]
        item["pred_class"] = class_names[item["pred_idx"]]
        item["true_class_zh"] = EN_TO_ZH.get(item["true_class"], item["true_class"])
        item["pred_class_zh"] = EN_TO_ZH.get(item["pred_class"], item["pred_class"])

    probs = softmax_np(test_result["logits"])
    misclassified = []
    wrong_idx = np.where(test_result["preds"] != test_result["labels"])[0]
    pred_conf = probs[np.arange(len(probs)), test_result["preds"].astype(int)]
    wrong_sorted = wrong_idx[np.argsort(-pred_conf[wrong_idx])][:8]
    for idx in wrong_sorted:
        ti, pi = int(test_result["labels"][idx]), int(test_result["preds"][idx])
        misclassified.append(
            {
                "sample_index": int(idx),
                "image_path": test_ds.image_paths[idx],
                "true_class": class_names[ti],
                "pred_class": class_names[pi],
                "true_class_zh": EN_TO_ZH.get(class_names[ti], class_names[ti]),
                "pred_class_zh": EN_TO_ZH.get(class_names[pi], class_names[pi]),
                "pred_confidence": round(float(pred_conf[idx]), 4),
            }
        )

    train_cate = Path(data_dir) / "Anno_fine/train_cate.txt"
    class_distribution = count_category_distribution(train_cate) if train_cate.exists() else {}
    training_best = load_training_best_acc()

    out = {
        "model": str(model_path),
        "device": str(device),
        "notes": {
            "training_config_best_val_acc": training_best,
            "val_acc_rerun_note": "独立评估脚本复现值，与训练日志 best 可能差 0.05 个百分点以内",
        },
        "validation": {
            "size": len(val_ds),
            "loss": val_result["loss"],
            "top1_acc": val_result["top1_acc"],
            "top3_acc": val_result["top3_acc"],
        },
        "test": {
            "size": len(test_ds),
            "loss": test_result["loss"],
            "top1_acc": test_result["top1_acc"],
            "top3_acc": test_result["top3_acc"],
            **{k: v for k, v in test_metrics.items() if k != "confusion_matrix"},
        },
        "class_names": class_names,
        "class_names_zh": class_names_zh,
        "misclassified_examples": misclassified,
        "train_class_distribution_1based": class_distribution,
    }

    json_path = ROOT / "evaluation_results.json"
    json_path.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")

    cm = np.array(test_metrics["confusion_matrix"])
    docs = ROOT / "docs"
    save_confusion_figure(
        cm, class_names, docs / "evaluation_confusion_matrix.png", "Confusion Matrix (Test, EN)"
    )
    save_confusion_figure(
        cm, class_names_zh, docs / "evaluation_confusion_matrix_zh.png", "混淆矩阵（测试集）", chinese=True
    )

    print(json.dumps(out, indent=2, ensure_ascii=False))
    print(f"\n结果: {json_path}")


if __name__ == "__main__":
    main()
