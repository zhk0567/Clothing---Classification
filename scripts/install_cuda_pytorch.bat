@echo off
REM 自动安装CUDA版本的PyTorch
REM 用于data_preprocessing conda环境

REM 修复OpenMP冲突问题
set KMP_DUPLICATE_LIB_OK=TRUE

echo 检查conda环境...
call conda activate data_preprocessing
if errorlevel 1 (
    echo 错误: 无法激活data_preprocessing环境
    echo 请先创建环境: conda create -n data_preprocessing python=3.10
    pause
    exit /b 1
)

echo 当前conda环境: %CONDA_DEFAULT_ENV%

echo.
echo 检查当前PyTorch安装...
python -c "import torch; print('PyTorch version:', torch.__version__); print('CUDA available:', torch.cuda.is_available())"

echo.
echo 正在卸载CPU版本的PyTorch...
conda uninstall pytorch torchvision torchaudio -y

echo.
echo 正在安装CUDA 12.1版本的PyTorch...
echo 这可能需要几分钟，请耐心等待...
conda install pytorch torchvision torchaudio pytorch-cuda=12.1 -c pytorch -c nvidia -y

echo.
echo 重新安装Pillow以确保依赖正确...
conda install pillow -y

echo.
echo 验证CUDA安装...
python -c "import torch; print('PyTorch version:', torch.__version__); print('CUDA available:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"
python -c "from PIL import Image; print('Pillow import successful')"

echo.
echo 安装完成！
echo 现在可以重新运行训练脚本，将自动使用GPU加速
pause

