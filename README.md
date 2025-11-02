# 服饰识别（Android）

面向 50 类服饰的离线识别应用。提供训练脚本、模型导出与 Android 端集成，主打“简约、稳定、可落地”。

## 当前版本（v1.0）
- 应用名：服饰识别
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
│   │   ├── MainActivity.kt           # 首页
│   │   ├── CameraActivity.kt         # 拍摄页（底部：历史 | 相册 | 拍照 | 翻转）
│   │   ├── ResultActivity.kt         # 结果页（保存到历史）
│   │   ├── HistoryActivity.kt        # 历史列表
│   │   ├── SettingsActivity.kt       # 设置（即时生效）
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

### 模型优化
- **量化**：FP32 → INT8量化
- **剪枝**：移除冗余参数
- **知识蒸馏**：小模型学习大模型

### 移动端优化
- **异步推理**：避免UI阻塞
- **模型缓存**：减少加载时间
- **内存管理**：优化内存使用
- **代码优化**：简化调试信息，减少日志输出

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

## 📌 开发计划（下一个版本 v1.1）

### 识别功能增强

- **多结果展示**
  - Top-3 结果展示与切换（显示前3个最可能的分类结果，支持快速切换查看）
  - 结果置信度可视化（进度条或图表展示各分类的置信度对比）

- **批量识别**
  - 从相册多选图片进行批量识别
  - 批量识别结果列表展示（支持单个查看详情）
  - 批量保存到历史记录

- **类别信息**
  - 类别详情说明（离线缓存类别信息，支持关键词检索）
  - 类别属性展示（如适用场景、季节、风格等）

- **图像处理增强**
  - 结果页支持图像裁剪区域预览与重识别
  - 图像增强选项（亮度、对比度、锐化等基础调整）

### 历史记录功能增强

- **批量操作**
  - 批量删除功能（多选模式，支持全选/反选）
  - 批量导出（支持导出为JSONL/CSV格式）
  - 按日期范围批量操作

- **分享增强**
  - 历史记录水印/署名（分享图片时可选择添加应用标识）
  - 分享格式优化（支持长图拼接、详情卡片等）

### 相机与拍摄功能

- **拍摄辅助**
  - 网格参考线开关（三分线/网格线，辅助构图）
  - 快门音开关（静音拍摄选项）
  - 连拍模式（自动保留置信度最高的一张，或全部保存）
  - 分辨率选择（支持不同拍摄质量，平衡速度与精度）

### 用户体验优化

- **引导与帮助**
  - 首次启动引导（快速上手教程，核心功能演示）
  - 应用内更新日志（本地查看版本更新记录）
  - 帮助中心（常见问题与使用技巧）

- **界面优化**
  - 深色模式视觉细节优化（进度条、分隔线、阴影对比度调整）
  - 动画效果优化（更流畅的过渡动画）
  - 手势操作提示（首次使用时引导手势功能）

### 性能与优化

- **性能提升**
  - ONNX Runtime 线程策略自适应（根据设备CPU核心配置自动调整）
  - 异步 I/O 优化（历史记录写入不阻塞主线程）
  - 内存优化（减少峰值内存占用，优化大图处理）
  - 推理缓存机制（相似图片复用识别结果）

- **体积控制**
  - 资源裁剪（进一步压缩图片和资源文件）
  - 控制 APK 体积 ≤ 25 MB

### 模型与训练

- **模型优化**
  - 继续训练 DeepFashion 50 类模型（采用早停、余弦退火、Mixup/CutMix等技术）
  - 类别不均衡处理（重采样策略优化，提升少数类别识别率）
  - 指标监控扩展（F1分数、Top-3/Top-5准确率等）
  - ONNX模型更新与回归测试（确保模型转换一致性）
  - 模型量化优化（INT8量化以减小体积、提升速度）

### 质量与稳定

- **错误处理**
  - 崩溃捕获与本地日志（设备内记录，不上传）
  - 异常处理优化（更友好的错误提示，提供解决建议）
  - 网络异常处理（如有在线功能时）

- **测试与验证**
  - UI自动化测试（启动/拍摄/保存/历史/设置等核心流程）
  - 多语言完整性检查（中英文资源覆盖率100%）
  - 多设备兼容性测试（不同Android版本和屏幕尺寸）
  - 性能测试（内存占用、CPU使用率、识别速度基准测试）

### 可选项（低优先级）

- 多 ABI 构建变体（arm64/armeabi-v7a），支持更多设备
- 无障碍功能支持（内容描述、焦点顺序优化）
- 分享功能增强（支持更多分享渠道和格式）
- 云端备份（用户可选，本地优先）

## 💝 用爱发电

如果这个项目对您有帮助，欢迎扫码支持：

![微信支付收款码](mm_facetoface_collect_qrcode_1762047813586.png)

## 许可

MIT License