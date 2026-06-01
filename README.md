# 服饰识别（Android）

面向 50 类服饰的离线识别应用。提供训练脚本、模型导出与 Android 端集成，主打“简约、稳定、可落地”。

## 当前版本（v1.2）
- 应用名：服饰识别
- **v1.2 新增**：
  - 相机连拍模式（最高置信度 / 全部批量结果）+ 拍摄分辨率选择
  - 分享图片水印、对比页长图拼接
  - 推理单例与 LRU 缓存、线程自适应、协程异步分类
  - 崩溃捕获与日志查看、**本地** ZIP/JSONL 备份导入/导出
  - armeabi-v7a 分包、无障碍 contentDescription、Espresso 冒烟测试
- **导航架构**：底部 5 Tab（首页 / 识别 / 类别 / 历史 / 更多）+ 详情 Activity
- **新增页面**：
  - 首页摘要（本周统计、最近识别）
  - 识别入口（拍照 / 相册 / 批量识别）
  - 50 类类别百科 + 类别详情页
  - 批量识别 + 批量结果列表
  - 裁剪重识别 / 增强重识别
  - 结果页 Top-3 切换与置信度条形图
  - 数据统计 / 收藏夹 / 对比 / 导出
  - 首次引导 / 帮助中心 / 更新日志 / 模型信息
  - 手动画框裁剪 / 批量保存历史 / 7·30天统计与饼图 / 对比描述 / 类别历史筛选 / 图像锐化
- 核心特性：
  - 50 类服饰识别、离线 ONNX 推理、Material 3 现代化界面
  - 设置即时生效：语言切换（中文/English）、性能优先（多线程）、主题模式（跟随系统/浅色/深色）、结果展示密度、默认摄像头选择
  - 相机功能：前后摄切换、从相册选择图片、手势对焦（双指缩放、双击对焦）
  - 历史记录：结果保存、收藏功能、筛选（类别/置信度/时间）、搜索、分享（文字/图片）
  - 其他：保存图片到相册、页面切换动画、历史记录管理
- 训练与模型：PyTorch 训练 → 导出 ONNX，应用内使用 ONNX Runtime 推理

## 目录结构
```
Clothing-Classification/
├── DeepFashionClassifier/            # Android 应用（应用名：服饰识别）
│   ├── app/src/main/java/com/deepfashion/classifier/
│   │   ├── MainContainerActivity.kt  # 主框架（底部导航 + Fragment）
│   │   ├── HomeFragment.kt           # 首页
│   │   ├── ClassifyFragment.kt       # 识别入口
│   │   ├── CategoryListFragment.kt # 类别百科
│   │   ├── HistoryFragment.kt      # 历史记录
│   │   ├── MoreFragment.kt           # 更多工具
│   │   ├── CategoryDetailActivity.kt # 类别详情
│   │   ├── BatchClassifyActivity.kt  # 批量识别
│   │   ├── BatchResultActivity.kt    # 批量结果
│   │   ├── ImageCropActivity.kt      # 裁剪重识别
│   │   ├── ImageEnhanceActivity.kt   # 增强重识别
│   │   ├── StatisticsActivity.kt     # 数据统计
│   │   ├── FavoritesActivity.kt      # 收藏夹
│   │   ├── CompareActivity.kt        # 对比记录
│   │   ├── ExportActivity.kt         # 导出数据
│   │   ├── OnboardingActivity.kt     # 首次引导
│   │   ├── HelpCenterActivity.kt     # 帮助中心
│   │   ├── ChangelogActivity.kt      # 更新日志
│   │   ├── ModelInfoActivity.kt      # 模型信息
│   │   ├── MainActivity.kt           # 兼容转发
│   │   ├── CameraActivity.kt         # 拍摄页
│   │   ├── ResultActivity.kt         # 结果页（Top-3）
│   │   ├── SettingsActivity.kt       # 设置
│   │   ├── CategoryRepository.kt     # 50类数据
│   │   ├── StatisticsRepository.kt   # 统计计算
│   │   └── DeepFashionClassifier.kt  # ONNX 推理
│   ├── app/src/main/assets/models/deepfashion_classifier.onnx
│   └── build.gradle
├── scripts/
│   ├── train_deepfashion_complete.py  # 训练（断点续训、过拟合防护）
│   ├── update_model_for_android.py    # .pth → ONNX 并复制到 app
│   ├── generate_launcher_icons.py     # 从 image.png 生成图标
│   └── convert_*                      # 相关转换工具
├── image.png                          # 图标源文件
├── deepfashion_best_model.pth         # 最佳模型
├── deepfashion_checkpoint_latest.pth  # 训练断点
├── TRAINING_STATUS.md                 # 训练/继续说明
└── README.md
```

## 使用说明（Android）
1) 导入工程：Android Studio 打开 `DeepFashionClassifier`
2) 放置模型：`app/src/main/assets/models/deepfashion_classifier.onnx`
3) 构建安装：
```powershell
cd "DeepFashionClassifier"
./gradlew.bat assembleDebug
```
4) 可选图标生成：
```bash
python scripts/generate_launcher_icons.py --scale 0.60
```

## 训练与导出
- 训练：`python scripts/train_deepfashion_complete.py`（断点续训、日志与检查点保存至当前目录）
- 导出并更新 App：`python scripts/update_model_for_android.py`

## 依赖（训练/工具）
```bash
pip install torch torchvision onnx onnxruntime pillow
```

## 设置与历史
- 设置：语言/性能优先，修改后立即生效（推理前自动重建 Session）
- 历史：结果页"保存结果"，首页/拍摄页进入历史查看

### 模型转换

将PyTorch模型转换为ONNX格式：

**DeepFashion分类器转换**
```bash
python scripts/update_model_for_android.py
```

### Android 应用（服饰识别）
1. 打开 Android Studio
2. 导入 `DeepFashionClassifier` 目录
3. 确保 ONNX 模型位于 `app/src/main/assets/models/deepfashion_classifier.onnx`
4. 生成/替换启动图标（可选）：
   ```bash
   python scripts/generate_launcher_icons.py --scale 0.75
   ```
5. 构建与安装：
   ```powershell
   cd "DeepFashionClassifier"
   .\gradlew.bat assembleDebug
   ```
6. 应用内功能：
   - 首页现代卡片、右上角直接"设置"按钮、查看历史
   - 拍摄页极简 UI、从相册选择/翻转摄像头/手势对焦、结果页保存到历史
   - 历史记录支持分享文字和图片卡片
   - 设置项即时生效：语言（中文/English）、性能优先（多线程）

## 🔧 核心功能

### 数据处理
- **图片去重**：使用感知哈希算法去除重复图片
- **格式统一**：将所有图片转换为JPEG格式
- **数据增强**：随机裁剪、翻转、颜色调整等

### 模型架构
- **骨干网络**：ResNet18
- **迁移学习**：使用ImageNet预训练权重
- **分类头**：50类分类输出层

### 模型转换
- **PyTorch → ONNX**：使用 `torch.onnx.export()`
- **ONNX Runtime**：应用内使用 ONNX Runtime 进行推理
- **优化**：多线程推理支持，性能优先设置

## 📱 Android应用特性

### 应用特性
- **50 分类支持**：面向 50 类服饰识别
- **实时分类**：使用相机实时识别服装类型
- **相册选择**：支持从相册选择图片进行识别
- **中文界面**：完全中文化的用户体验
- **Material Design 3**：现代化 UI 设计
- **权限管理**：相机和存储权限处理
- **异步处理**：后台模型推理，流畅用户体验
- **手势操作**：双指缩放、双击对焦（黄色对焦框）

### 历史与设置
- **历史记录**：结果页一键保存，首页可查看
- **历史分享**：支持分享文字信息和卡片图片（不保存到设备）
- **设置**：语言切换/性能优先，均即时生效

## 📊 数据集统计

### DeepFashion数据集分布
- **训练集**：14,000张图片，50个类别
- **验证集**：2,000张图片，50个类别
- **测试集**：4,000张图片，50个类别
- **总规模**：20,000张图片

### 类别详情
DeepFashion数据集包含50个细分类别，包括：
- **上衣类**：风衣、西装外套、女式衬衫、飞行员夹克、纽扣衬衫、开襟毛衣、法兰绒衬衫、露背上衣、亨利衫、连帽衫、夹克、运动衫、派克大衣、双排扣外套、斗篷、毛衣、背心、T恤、上衣、高领毛衣
- **下装类**：七分裤、休闲裤、短裤、宽松裤、牛仔裤、紧身牛仔裤、马裤、慢跑裤、紧身裤、背带裤、长裤、长袍、裙子、运动裤等
- **连身装类**：长袍、连衣裙、连体衣等

完整50类：Anorak, Blazer, Blouse, Bomber, Button-Down, Cardigan, Flannel, Halter, Henley, Hoodie, Jacket, Jersey, Parka, Peacoat, Poncho, Sweater, Tank, Tee, Top, Turtleneck, Capris, Chinos, Culottes, Cutoffs, Gauchos, Jeans, Jeggings, Jodhpurs, Joggers, Leggings, Sarong, Shorts, Skirt, Sweatpants, Sweatshorts, Trunks, Caftan, Cape, Coat, Coverup, Dress, Jumpsuit, Kaftan, Kimono, Nightdress, Onesie, Robe, Romper, Shirtdress, Sundress

## 🛠️ 技术栈

### 深度学习
- **PyTorch**：模型训练和推理
- **ResNet18**：DeepFashion分类器的骨干网络
- **迁移学习**：使用ImageNet预训练权重

### 移动端部署
- **ONNX Runtime**：移动端模型推理（已替代 TFLite）
- **Android**：原生Android应用
- **Kotlin**：Android开发语言

### 数据处理
- **PIL/Pillow**：图像处理
- **OpenCV**：计算机视觉
- **NumPy**：数值计算

## 🔄 模型转换流程

```
PyTorch模型 (.pth)
    ↓ torch.onnx.export()
ONNX模型 (.onnx)
    ↓ 部署到Android
ONNX Runtime 推理
```

### ✅ 转换成功状态
- **DeepFashion模型**: 成功转换为 ONNX 格式
- **Android部署**: 模型已部署到Android应用
- **功能验证**: DeepFashion 50分类模型测试通过
- **性能优化**: 支持多线程推理，设置即时生效

## 📈 性能优化

### 应用内已启用
- **异步推理**：协程在后台线程执行分类，避免阻塞 UI
- **模型单例与 LRU 缓存**：减少重复加载与重复推理
- **线程自适应**：按 CPU 核心数调整 ONNX Runtime 线程

### 模型侧（仓库脚本，非应用内功能）
- 训练与导出见项目 `scripts/` 目录；应用仅打包 FP32 ONNX 分类模型

## 📊 模型训练状态

### 最新训练结果（重新训练，已部署）
- **训练完成**: 32/100 epochs（Early Stopping触发）
- **最佳验证准确率**: 61.55%（Epoch 22）
- **最终训练准确率**: 74.29% (Loss: 1.5063)
- **最终验证准确率**: 60.40% (Loss: 1.9554)
- **训练-验证差距**: 13.89%（显著改善，之前的过拟合模型差距>27%）
- **状态**: ✅ 训练完成，模型已转换为ONNX并部署到Android应用

### 防过拟合措施
- ✅ 增强数据增强（随机旋转、颜色变换、仿射变换）
- ✅ 增强正则化（Dropout 0.6、权重衰减 2e-4、标签平滑 0.1）
- ✅ Early Stopping（连续10个epoch无提升自动停止）
- ✅ GPU加速训练（RTX 4060，批次大小48，混合精度训练）

### 模型性能
- 验证准确率：61.55%（最佳），较之前的60.90%略有提升
- 过拟合情况显著改善：训练-验证差距从27%+降低到14%
- 模型已部署：ONNX模型已更新到Android应用

## 🐛 已知问题

1. **模型准确率仍有提升空间**：当前验证准确率61.55%，可继续优化（如调整超参数、尝试不同架构等）
2. **Android权限**：需要手动授权相机和存储权限
3. **样本不平衡**：DeepFashion数据集中部分类别样本数量极少，可能影响识别准确率

## v1.1 已完成功能

### 导航与页面
- 底部 5 Tab 主框架（首页 / 识别 / 类别 / 历史 / 更多）
- 50 类类别百科、类别详情、数据统计、收藏、对比、导出
- 首次引导、帮助中心、更新日志、模型信息

### 识别增强
- Top-3 结果切换与置信度条形图
- 相册批量识别 + 批量结果列表 + **一键全部保存历史**
- **手动画框裁剪**重识别（CropOverlayView）
- 图像增强：亮度 / 对比度 / **锐化** 后重识别

### 历史与数据
- 历史多选批量删除 / 导出
- 统计页 **7/30 天趋势切换** + **类别饼图**
- 对比页展示百科描述与置信度差值
- 类别详情 **查看该类历史**（预填筛选）

### 相机
- 构图网格线、快门音、拍摄页相册入口

## v1.2 已完成功能

### 相机与分享
- 连拍模式（3/5/8 张，保留最高置信度或全部进批量结果）
- 拍摄分辨率（高/中/低）
- 分享水印、对比长图拼接

### 性能与稳定性
- ClassifierProvider 单例 + 推理缓存
- 协程异步推理、线程数自适应（核心数/2）
- CrashHandler + LogViewerActivity

### 数据
- ZIP 备份导出（history.jsonl + images/）
- JSONL/ZIP 导入（追加或覆盖）

### 质量
- armeabi-v7a + arm64-v8a 分包
- 权限 UX（Snackbar + 跳转系统设置）
- Espresso 冒烟测试

## 产品范围说明

本 Android 应用为**离线 50 类服饰分类**，不包含以下能力（且无相关入口）：

- 应用内模型训练、INT8 量化或在线更新权重
- 实时目标检测（如 yolov8 预览画框）
- 账号云端同步（数据仅支持本机 ZIP/JSONL 导入导出）

## 后续计划（可选增强）

- UI 自动化测试扩展、多设备兼容性测试
- 无障碍深度优化

## 许可

MIT License