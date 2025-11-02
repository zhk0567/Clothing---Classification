# 安装CUDA版本PyTorch（用于GPU加速）

## 前提条件
- 已安装NVIDIA驱动（已检测到RTX 4060）
- CUDA驱动版本：13.0
- Conda环境：data_preprocessing

## 安装步骤

### 方法1：使用Conda（推荐）

```bash
# 激活conda环境
conda activate data_preprocessing

# 卸载CPU版本的PyTorch（如果已安装）
conda uninstall pytorch torchvision torchaudio

# 安装CUDA 12.1版本的PyTorch
conda install pytorch torchvision torchaudio pytorch-cuda=12.1 -c pytorch -c nvidia
```

### 方法2：使用pip

```bash
# 激活conda环境
conda activate data_preprocessing

# 卸载CPU版本的PyTorch
pip uninstall torch torchvision torchaudio

# 安装CUDA 12.1版本的PyTorch
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
```

## 验证安装

安装完成后，运行以下命令验证CUDA是否可用：

```bash
python -c "import torch; print('CUDA available:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"
```

预期输出：
```
CUDA available: True
GPU: NVIDIA GeForce RTX 4060 ...
```

## 注意事项

- RTX 4060有8GB显存，训练脚本会自动设置batch_size=64
- 安装完成后重新运行训练脚本，将自动检测并使用GPU
- 使用GPU训练速度将提升约60-100%

