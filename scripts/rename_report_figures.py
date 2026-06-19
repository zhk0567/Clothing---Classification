#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一次性将 docs/report_figures 内旧编号文件名改为全文连续编号。"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "report_figures"

# 先长前缀后短前缀，避免误替换
PREFIX_MAP = [
    ("图7-5", "图18"),
    ("图7-4", "图17"),
    ("图7-3", "图16"),
    ("图7-2", "图15"),
    ("图7-1", "图14"),
    ("图6-3", "图13"),
    ("图6-2", "图12"),
    ("图6-1", "图11"),
    ("图5-3", "图10"),
    ("图5-2", "图9"),
    ("图5-1", "图8"),
    ("图4-1", "图7"),
    ("图3-3", "图6"),
    ("图3-2", "图5"),
    ("图3-1", "图4"),
    ("图2-3", "图3"),
    ("图2-2", "图2"),
    ("图2-1", "图1"),
]


def main() -> None:
    if not OUT.is_dir():
        print(f"目录不存在: {OUT}")
        return
    renamed = 0
    for path in sorted(OUT.iterdir()):
        if not path.is_file() or path.name == "README.txt":
            continue
        name = path.name
        for old, new in PREFIX_MAP:
            if name.startswith(old):
                target = OUT / (new + name[len(old) :])
                if target.exists() and target != path:
                    print(f"跳过（目标已存在）: {name} -> {target.name}")
                else:
                    path.rename(target)
                    print(f"{name} -> {target.name}")
                    renamed += 1
                break
    print(f"共重命名 {renamed} 个文件")


if __name__ == "__main__":
    main()
