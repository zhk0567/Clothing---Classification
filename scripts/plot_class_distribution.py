#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
绘制训练集类别样本频数柱状图（图3-3）。

用法（项目根目录）:
    python scripts/plot_class_distribution.py
"""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_id_to_name() -> dict[int, str]:
    path = ROOT / "Category and Attribute Prediction Benchmark/Anno_fine/list_category_cloth.txt"
    result = {}
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()
    for idx, line in enumerate(lines[2:], start=1):
        name = line.strip().split()[0]
        result[idx] = name
    return result


def main():
    cate_path = ROOT / "Category and Attribute Prediction Benchmark/Anno_fine/train_cate.txt"
    if not cate_path.exists():
        print("找不到 train_cate.txt")
        return

    labels = [int(ln.strip()) for ln in cate_path.read_text(encoding="utf-8").splitlines() if ln.strip()]
    counter = Counter(labels)
    id_to_name = load_id_to_name()

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("请安装 matplotlib")
        return

    ids = sorted(counter.keys())
    counts = [counter[i] for i in ids]
    names = [id_to_name.get(i, str(i)) for i in ids]

    fig, ax = plt.subplots(figsize=(14, 5))
    ax.bar(range(len(ids)), counts, color="#4A90D9")
    ax.set_xticks(range(len(ids)))
    ax.set_xticklabels(names, rotation=90, fontsize=7)
    ax.set_ylabel("Sample Count")
    ax.set_title("DeepFashion Train Set Class Frequency")
    plt.tight_layout()

    out = ROOT / "docs" / "train_class_distribution.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(out, dpi=150)
    plt.close()
    print(f"已保存: {out}")
    print(json.dumps({
        "min": min(counts),
        "max": max(counts),
        "mean": round(sum(counts) / len(counts), 1),
        "classes_present": len(ids),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
