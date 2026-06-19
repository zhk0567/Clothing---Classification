#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导出测试集错分样例图片（图6-2），附带标签文件名。

用法（项目根目录）:
    python scripts/export_misclassified_samples.py
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    eval_path = ROOT / "evaluation_results.json"
    if not eval_path.exists():
        print("请先运行: python scripts/evaluate_deepfashion_model.py")
        return

    data = json.loads(eval_path.read_text(encoding="utf-8"))
    examples = data.get("misclassified_examples", [])
    out_dir = ROOT / "docs" / "misclassified_samples"
    out_dir.mkdir(parents=True, exist_ok=True)

    for old in out_dir.glob("*"):
        if old.is_file():
            old.unlink()

    manifest = []
    for i, ex in enumerate(examples, start=1):
        src = Path(ex["image_path"])
        if not src.is_file():
            src = ROOT / ex["image_path"]
        if not src.is_file():
            print(f"跳过缺失: {ex.get('image_path')}")
            continue
        safe = f"{i:02d}_true-{ex['true_class']}_pred-{ex['pred_class']}_conf{ex['pred_confidence']:.2f}{src.suffix}"
        dst = out_dir / safe
        shutil.copy2(src, dst)
        manifest.append({"file": dst.name, **ex})
        print(f"导出: {dst.name}")

    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"\n共 {len(manifest)} 张，目录: {out_dir}")


if __name__ == "__main__":
    main()
