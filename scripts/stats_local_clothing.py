#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
统计本地 服装/ 目录与 DeepFashion 标注行数，供实训报告引用。

用法（项目根目录）:
    python scripts/stats_local_clothing.py
"""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def count_images(folder: Path) -> int:
    exts = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
    return sum(1 for p in folder.rglob("*") if p.suffix.lower() in exts)


def main():
    local_dir = ROOT / "服装"
    stats = {"local_clothing": {}, "deepfashion": {}}

    if local_dir.exists():
        subs = []
        total = 0
        for sub in sorted(local_dir.iterdir()):
            if sub.is_dir():
                n = count_images(sub)
                total += n
                subs.append({"folder": sub.name, "image_count": n})
        stats["local_clothing"] = {
            "root": str(local_dir),
            "subfolder_count": len(subs),
            "total_images": total,
            "subfolders": subs,
            "granularity": "3类粗粒度（上衣/下装/连身装），非DeepFashion 50类",
        }
    else:
        stats["local_clothing"] = {"error": "目录不存在"}

    df_root = ROOT / "Category and Attribute Prediction Benchmark" / "Anno_fine"
    for name in ("train", "val", "test"):
        p = df_root / f"{name}.txt"
        cate = df_root / f"{name}_cate.txt"
        if p.exists():
            lines = [ln.strip() for ln in p.read_text(encoding="utf-8").splitlines() if ln.strip()]
            stats["deepfashion"][name] = {"image_list_lines": len(lines)}
        if cate.exists():
            labels = [int(ln.strip()) for ln in cate.read_text(encoding="utf-8").splitlines() if ln.strip()]
            stats["deepfashion"][f"{name}_cate"] = {
                "label_lines": len(labels),
                "unique_classes": len(set(labels)),
            }

    train_cate = df_root / "train_cate.txt"
    if train_cate.exists():
        labels = [int(ln.strip()) for ln in train_cate.read_text(encoding="utf-8").splitlines() if ln.strip()]
        ctr = Counter(labels)
        stats["deepfashion"]["train_class_freq"] = {
            "min_per_class": min(ctr.values()),
            "max_per_class": max(ctr.values()),
            "mean_per_class": round(sum(ctr.values()) / len(ctr), 1),
        }

    out = ROOT / "data_source_stats.json"
    out.write_text(json.dumps(stats, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(stats, indent=2, ensure_ascii=False))
    print(f"\n已写入: {out}")


if __name__ == "__main__":
    main()
