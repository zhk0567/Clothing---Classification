# 软件著作权截图素材说明

将手机实机截图按下列文件名保存到本目录，HTML 文档会自动引用（刷新浏览器即可）。

| 文件名 | 对应界面 |
|--------|----------|
| 00-cover.png | 启动/桌面图标（可选） |
| 01-onboarding.png | 首次引导任一页 |
| 02-home.png | 首页 Tab |
| 03-classify.png | 识别 Tab |
| 04-camera-single.png | 相机-单拍模式 |
| 05-camera-burst.png | 相机-连拍模式 |
| 06-result-top3.png | 识别结果页（含 Top-3） |
| 07-categories.png | 类别百科列表 |
| 08-category-detail.png | 类别详情 |
| 09-history.png | 历史记录 |
| 10-history-filter.png | 历史筛选/多选（可选） |
| 11-statistics.png | 数据统计 |
| 12-compare.png | 对比记录 |
| 13-export.png | 导出/备份 |
| 14-more.png | 更多 |
| 15-settings.png | 设置 |
| 16-crop.png | 裁剪重识别 |
| 17-enhance.png | 增强重识别 |
| 18-batch.png | 批量识别结果 |

**占位图**：若尚未有实机截图，可在 `docs` 目录运行 `python generate_doc_assets.py` 生成占位 PNG，避免 HTML 打开时出现 404。

**截图建议**：1080×2400 或更高，竖屏，系统状态栏可见；导出 PDF 前替换占位图。

**ADB 批量截图示例**（手机连接后）：

```powershell
adb shell screencap -p /sdcard/sc.png
adb pull /sdcard/sc.png .\docs\screenshots\02-home.png
```
