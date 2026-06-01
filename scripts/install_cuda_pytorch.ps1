# 自动安装CUDA版本的PyTorch
# 用于data_preprocessing conda环境

# 修复OpenMP冲突问题
$env:KMP_DUPLICATE_LIB_OK = "TRUE"

Write-Host "检查conda环境..." -ForegroundColor Cyan

# 检查是否在conda环境中
$condaEnv = $env:CONDA_DEFAULT_ENV
if ($null -eq $condaEnv) {
    Write-Host "错误: 未检测到conda环境，请先激活data_preprocessing环境" -ForegroundColor Red
    Write-Host "运行: conda activate data_preprocessing" -ForegroundColor Yellow
    exit 1
}

Write-Host "当前conda环境: $condaEnv" -ForegroundColor Green

# 检查PyTorch版本
Write-Host "`n检查当前PyTorch安装..." -ForegroundColor Cyan
python -c "import torch; print('PyTorch version:', torch.__version__); print('CUDA available:', torch.cuda.is_available())"

# 卸载CPU版本
Write-Host "`n正在卸载CPU版本的PyTorch..." -ForegroundColor Cyan
conda uninstall pytorch torchvision torchaudio -y

# 安装CUDA版本
Write-Host "`n正在安装CUDA 12.1版本的PyTorch..." -ForegroundColor Cyan
Write-Host "这可能需要几分钟，请耐心等待..." -ForegroundColor Yellow

conda install pytorch torchvision torchaudio pytorch-cuda=12.1 -c pytorch -c nvidia -y

# 重新安装Pillow以确保依赖正确（避免DLL加载错误）
Write-Host "`n重新安装Pillow以确保依赖正确..." -ForegroundColor Cyan
conda install pillow -y

# 验证安装
Write-Host "`n验证CUDA安装..." -ForegroundColor Cyan
python -c "import torch; print('PyTorch version:', torch.__version__); print('CUDA available:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"
python -c "from PIL import Image; print('Pillow import successful')"

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ CUDA PyTorch安装成功！" -ForegroundColor Green
    Write-Host "现在可以重新运行训练脚本，将自动使用GPU加速" -ForegroundColor Green
} else {
    Write-Host "`n✗ 安装可能失败，请检查错误信息" -ForegroundColor Red
}

