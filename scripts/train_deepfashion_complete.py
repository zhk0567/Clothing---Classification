# -*- coding: utf-8 -*-
"""
DeepFashion 50分类训练脚本 - 完整版本
支持断点续训、过拟合防护、兼容性、准确率监控
"""

import os
import sys

# 修复OpenMP冲突问题（如果有多个OpenMP库被链接）
os.environ.setdefault('KMP_DUPLICATE_LIB_OK', 'TRUE')

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import torchvision.transforms as transforms
from torchvision.models import resnet18
from PIL import Image
import numpy as np
import time
from datetime import datetime
import json
try:
    from torch.amp import autocast, GradScaler  # PyTorch 2.0+
except ImportError:
    from torch.cuda.amp import autocast, GradScaler  # 旧版本兼容

class DeepFashionDataset(Dataset):
    """DeepFashion数据集类 - 支持DeepFashion标准格式"""
    
    def __init__(self, root_dir, split_file=None, category_file=None, transform=None, split='train'):
        """
        Args:
            root_dir: 数据集根目录（如 'Category and Attribute Prediction Benchmark'）
            split_file: 分割文件路径（如 'Anno_fine/train.txt'）
            category_file: 类别文件路径（如 'Anno_fine/list_category_cloth.txt'）
            transform: 数据变换
            split: 数据集分割类型 ('train', 'val', 'test')
        """
        self.root_dir = root_dir
        self.transform = transform
        
        # 加载类别映射
        self.category_to_idx = {}
        self.idx_to_category = {}
        self.num_classes = 50  # 默认50个类别
        
        if category_file:
            category_path = os.path.join(root_dir, category_file)
            if os.path.exists(category_path):
                with open(category_path, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                    self.num_classes = int(lines[0].strip())  # 从文件读取类别数
                    for i in range(2, len(lines)):  # 跳过标题行
                        parts = lines[i].strip().split()
                        if len(parts) >= 2:
                            category_name = parts[0]
                            category_idx = i - 2  # 0-based索引
                            self.category_to_idx[category_name] = category_idx
                            self.idx_to_category[category_idx] = category_name
                print(f"加载了 {len(self.category_to_idx)} 个类别")
        
        # 从类别文件夹名推断类别
        img_dir = os.path.join(root_dir, 'Img', 'img')
        if not os.path.exists(img_dir):
            # 检查是否有解压的图片目录
            img_dir = os.path.join(root_dir, 'Img')
        
        # 加载图片路径和标签
        self.image_paths = []
        self.labels = []
        
        if split_file:
            # 使用标注文件
            split_path = os.path.join(root_dir, split_file)
            category_label_file = split_file.replace('.txt', '_cate.txt')
            category_label_path = os.path.join(root_dir, category_label_file)
            
            if os.path.exists(split_path):
                print(f"从标注文件加载: {split_path}")
                
                # 读取图片路径
                img_paths_list = []
                with open(split_path, 'r', encoding='utf-8') as f:
                    for line in f:
                        img_rel_path = line.strip()
                        if img_rel_path:
                            img_paths_list.append(img_rel_path)
                
                # 读取类别标签
                labels_list = []
                if os.path.exists(category_label_path):
                    print(f"从标签文件加载: {category_label_path}")
                    with open(category_label_path, 'r', encoding='utf-8') as f:
                        for line in f:
                            label_str = line.strip()
                            if label_str:
                                # DeepFashion标签是1-based，转换为0-based
                                label_idx = int(label_str) - 1
                                labels_list.append(label_idx)
                
                # 匹配图片路径和标签
                min_count = min(len(img_paths_list), len(labels_list)) if labels_list else len(img_paths_list)
                found_count = 0
                missing_paths = []
                
                for i in range(min_count):
                    img_rel_path = img_paths_list[i]
                    # 修复路径：标注文件路径是 img/xxx.jpg，实际文件在 Img/img/xxx.jpg
                    # 将 img/ 转换为 Img/img/
                    if img_rel_path.startswith('img/'):
                        img_rel_path = img_rel_path.replace('img/', 'Img/img/')
                    img_full_path = os.path.join(root_dir, img_rel_path.replace('/', os.sep))
                    
                    if os.path.exists(img_full_path):
                        found_count += 1
                        if labels_list:
                            # 使用标签文件中的类别
                            label_idx = labels_list[i]
                            if 0 <= label_idx < self.num_classes:
                                self.image_paths.append(img_full_path)
                                self.labels.append(label_idx)
                        else:
                            # 从路径推断类别
                            path_parts = img_rel_path.split('/')
                            if len(path_parts) >= 2:
                                category_folder = path_parts[1]
                                category_name = self._infer_category(category_folder)
                                
                                if category_name and category_name in self.category_to_idx:
                                    label_idx = self.category_to_idx[category_name]
                                    self.image_paths.append(img_full_path)
                                    self.labels.append(label_idx)
                    else:
                        if len(missing_paths) < 5:  # 只记录前5个缺失路径作为示例
                            missing_paths.append(img_full_path)
                
                if found_count == 0 and len(img_paths_list) > 0:
                    print(f"警告: 检查了 {min_count} 个图片路径，但未找到任何图片文件")
                    if missing_paths:
                        print("示例缺失路径:")
                        for mp in missing_paths[:3]:
                            print(f"  - {mp}")
                    print("\n提示: 请确保已解压图片文件")
                    print(f"  1. 解压 'Category and Attribute Prediction Benchmark/Img/img.zip'")
                    print(f"  2. 解压后应该有目录: {os.path.join(root_dir, 'Img', 'img')}")
        
        # 如果没有从标注文件加载，尝试从目录结构加载
        if len(self.image_paths) == 0:
            print("从目录结构加载图片...")
            img_base_dir = os.path.join(root_dir, 'Img', 'img')
            if not os.path.exists(img_base_dir):
                img_base_dir = os.path.join(root_dir, 'Img')
            
            if os.path.exists(img_base_dir):
                # 遍历所有子目录
                for category_dir in os.listdir(img_base_dir):
                    category_path = os.path.join(img_base_dir, category_dir)
                    if os.path.isdir(category_path):
                        # 尝试推断类别
                        category_name = self._infer_category(category_dir)
                        
                        if category_name and category_name in self.category_to_idx:
                            label_idx = self.category_to_idx[category_name]
                            
                            # 收集图片
                            for filename in os.listdir(category_path):
                                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                                    img_path = os.path.join(category_path, filename)
                                    self.image_paths.append(img_path)
                                    self.labels.append(label_idx)
        
        # num_classes已在加载类别文件时设置，如果未加载类别文件则保持默认值50
        if not self.category_to_idx:
            self.num_classes = 50
        
        print(f"数据集加载完成:")
        print(f"  - 总图片数: {len(self.image_paths)}")
        print(f"  - 类别数: {self.num_classes}")
        if len(self.image_paths) > 0:
            sample_labels = [self.idx_to_category[l] for l in set(self.labels[:10])]
            print(f"  - 样本类别: {sample_labels}")
    
    def _infer_category(self, folder_name):
        """从文件夹名推断类别名"""
        # DeepFashion类别名列表
        categories = [
            'Anorak', 'Blazer', 'Blouse', 'Bomber', 'Button-Down', 'Cardigan', 'Flannel', 'Halter',
            'Henley', 'Hoodie', 'Jacket', 'Jersey', 'Parka', 'Peacoat', 'Poncho', 'Sweater', 'Tank',
            'Tee', 'Top', 'Turtleneck', 'Capris', 'Chinos', 'Culottes', 'Cutoffs', 'Gauchos', 'Jeans',
            'Jeggings', 'Jodhpurs', 'Joggers', 'Leggings', 'Sarong', 'Shorts', 'Skirt', 'Sweatpants',
            'Sweatshorts', 'Trunks', 'Caftan', 'Cape', 'Coat', 'Coverup', 'Dress', 'Jumpsuit', 'Kaftan',
            'Kimono', 'Nightdress', 'Onesie', 'Robe', 'Romper', 'Shirtdress', 'Sundress'
        ]
        
        # 检查文件夹名是否包含类别名
        folder_name_lower = folder_name.lower()
        for cat in categories:
            if cat.lower() in folder_name_lower:
                return cat
        
        return None
    
    def __len__(self):
        return len(self.image_paths)
    
    def __getitem__(self, idx):
        image_path = self.image_paths[idx]
        label = self.labels[idx]
        
        try:
            image = Image.open(image_path).convert('RGB')
            if self.transform:
                image = self.transform(image)
            return image, label
        except Exception as e:
            # 返回一个空白图片（仅用于测试）
            blank_image = Image.new('RGB', (224, 224), (128, 128, 128))
            if self.transform:
                blank_image = self.transform(blank_image)
            return blank_image, label

def get_model():
    """创建DeepFashion分类模型"""
    # 使用新版本API加载预训练权重
    try:
        from torchvision.models import ResNet18_Weights
        model = resnet18(weights=ResNet18_Weights.IMAGENET1K_V1)
    except:
        # 兼容旧版本
        model = resnet18(pretrained=True)
    
    # 替换最后的分类层 - 增强正则化
    num_features = model.fc.in_features
    model.fc = nn.Sequential(
        nn.Dropout(0.6),  # 增加Dropout率从0.5到0.6
        nn.Linear(num_features, 512),
        nn.ReLU(inplace=True),
        nn.Dropout(0.5),  # 添加额外的Dropout层
        nn.Linear(512, 50)  # 50个类别
    )
    
    return model

def get_data_transforms():
    """获取数据变换 - 增强版（更强的数据增强以对抗过拟合）"""
    train_transform = transforms.Compose([
        transforms.Resize((256, 256)),
        transforms.RandomCrop(224),
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomRotation(degrees=15),  # 随机旋转±15度
        transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.1),  # 颜色增强
        transforms.RandomAffine(degrees=0, translate=(0.1, 0.1), scale=(0.9, 1.1)),  # 随机仿射变换
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                           std=[0.229, 0.224, 0.225])
    ])
    
    val_transform = transforms.Compose([
        transforms.Resize((256, 256)),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                           std=[0.229, 0.224, 0.225])
    ])
    
    return train_transform, val_transform

def train_epoch(model, dataloader, criterion, optimizer, device, epoch, scaler=None):
    """训练一个epoch - 支持混合精度训练"""
    model.train()
    running_loss = 0.0
    correct = 0
    total = 0
    use_amp = scaler is not None
    
    for batch_idx, (images, labels) in enumerate(dataloader):
        images, labels = images.to(device, non_blocking=True), labels.to(device, non_blocking=True)
        
        # 前向传播（混合精度）
        optimizer.zero_grad()
        if use_amp:
            try:
                with autocast('cuda'):  # PyTorch 2.0+新API
                    outputs = model(images)
                    loss = criterion(outputs, labels)
            except TypeError:
                with autocast():  # 旧版本兼容
                    outputs = model(images)
                    loss = criterion(outputs, labels)
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
        else:
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
        
        # 统计
        running_loss += loss.item()
        _, predicted = outputs.max(1)
        total += labels.size(0)
        correct += predicted.eq(labels).sum().item()
        
        # 每200个batch打印一次（减少打印频率）
        if (batch_idx + 1) % 200 == 0:
            print(f'Epoch [{epoch+1}], Batch [{batch_idx+1}/{len(dataloader)}], '
                  f'Loss: {loss.item():.4f}, Acc: {100.*correct/total:.2f}%')
    
    epoch_loss = running_loss / len(dataloader)
    epoch_acc = 100. * correct / total
    
    return epoch_loss, epoch_acc

def validate(model, dataloader, criterion, device, scaler=None):
    """验证模型 - 支持混合精度"""
    model.eval()
    running_loss = 0.0
    correct = 0
    total = 0
    use_amp = scaler is not None
    
    with torch.no_grad():
        for images, labels in dataloader:
            images, labels = images.to(device, non_blocking=True), labels.to(device, non_blocking=True)
            
            if use_amp:
                try:
                    with autocast('cuda'):  # PyTorch 2.0+新API
                        outputs = model(images)
                        loss = criterion(outputs, labels)
                except TypeError:
                    with autocast():  # 旧版本兼容
                        outputs = model(images)
                        loss = criterion(outputs, labels)
            else:
                outputs = model(images)
                loss = criterion(outputs, labels)
            
            running_loss += loss.item()
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()
    
    epoch_loss = running_loss / len(dataloader)
    epoch_acc = 100. * correct / total
    
    return epoch_loss, epoch_acc

def train_with_resume():
    """支持断点续训的训练函数"""
    print("DeepFashion 50分类训练 - 完整版本")
    print("=" * 60)
    
    # 配置 - 保存到当前目录
    checkpoint_dir = "."  # 当前目录
    checkpoint_path = os.path.join(checkpoint_dir, "deepfashion_checkpoint_latest.pth")
    best_model_path = os.path.join(checkpoint_dir, "deepfashion_best_model.pth")
    config_path = os.path.join(checkpoint_dir, "training_config.json")
    
    # DeepFashion数据集目录
    data_dir = "Category and Attribute Prediction Benchmark"
    category_file = "Anno_fine/list_category_cloth.txt"
    train_file = "Anno_fine/train.txt"
    val_file = "Anno_fine/val.txt"
    
    # 优化配置：根据设备调整批次大小
    # 自动检测GPU，优先使用CUDA
    use_cuda = False
    gpu_memory = 0
    
    if torch.cuda.is_available():
        device = torch.device('cuda')
        gpu_name = torch.cuda.get_device_name(0)
        gpu_memory = torch.cuda.get_device_properties(0).total_memory / 1024**3
        print(f"✓ 检测到GPU: {gpu_name}")
        print(f"✓ GPU显存: {gpu_memory:.1f} GB")
        use_cuda = True
    else:
        device = torch.device('cpu')
        use_cuda = False
        print("⚠ 未检测到CUDA支持，使用CPU训练")
        print("\n提示: 检测到您有NVIDIA GPU（RTX 4060），但当前PyTorch是CPU版本")
        print("如需使用GPU加速训练（速度提升60-100%），请安装CUDA版本的PyTorch:")
        print("\n方法1: 运行自动安装脚本（推荐）")
        print("  PowerShell: .\\scripts\\install_cuda_pytorch.ps1")
        print("  或 CMD: scripts\\install_cuda_pytorch.bat")
        print("\n方法2: 手动安装")
        print("  conda activate data_preprocessing")
        print("  conda install pytorch torchvision torchaudio pytorch-cuda=12.1 -c pytorch -c nvidia")
        print("\n安装完成后重新运行此脚本，将自动检测并使用GPU")
        print("=" * 60)
    
    # 根据设备调整批次大小和数据加载器工作进程
    if use_cuda:
        # 根据GPU显存动态调整批次大小
        if gpu_memory >= 8:
            batch_size = 64  # 8GB以上显存使用64
        elif gpu_memory >= 4:
            batch_size = 48  # 4-8GB显存使用48
        else:
            batch_size = 32  # 4GB以下显存使用32
        
        num_workers = 8  # GPU环境下可以使用更多工作进程
        pin_memory = True  # 加速数据传输
    else:
        batch_size = 32  # CPU保持较小批次
        num_workers = 4  # CPU环境减少工作进程
        pin_memory = False
    
    num_epochs = 100
    learning_rate = 0.001
    use_amp = use_cuda  # GPU使用混合精度训练
    
    print(f"\n使用设备: {device}")
    print(f"批次大小: {batch_size}")
    print(f"学习率: {learning_rate}")
    print(f"数据加载工作进程: {num_workers}")
    print(f"混合精度训练: {'启用' if use_amp else '禁用'}")
    
    # 加载数据集
    print("\n加载数据集...")
    train_transform, val_transform = get_data_transforms()
    
    # 加载训练集
    train_dataset = DeepFashionDataset(
        root_dir=data_dir,
        split_file=train_file,
        category_file=category_file,
        transform=train_transform,
        split='train'
    )
    
    # 加载验证集
    val_dataset = DeepFashionDataset(
        root_dir=data_dir,
        split_file=val_file,
        category_file=category_file,
        transform=val_transform,
        split='val'
    )
    
    # 如果没有图片，提示用户
    if len(train_dataset) == 0 and len(val_dataset) == 0:
        print(f"错误: 在 '{data_dir}' 目录下没有找到图片数据")
        print("可能的原因:")
        print("  1. 图片文件尚未解压（需要解压 img.zip）")
        print("  2. 图片路径不正确")
        print(f"  请检查: {os.path.join(data_dir, 'Img', 'img')}")
        return
    
    # 如果验证集为空，使用训练集的20%作为验证集
    if len(val_dataset) == 0 and len(train_dataset) > 0:
        print("验证集为空，从训练集划分20%作为验证集")
        train_size = int(0.8 * len(train_dataset))
        indices = np.arange(len(train_dataset))
        np.random.seed(42)
        np.random.shuffle(indices)
        train_indices = indices[:train_size]
        val_indices = indices[train_size:]
        
        train_subset = torch.utils.data.Subset(train_dataset, train_indices)
        val_subset = torch.utils.data.Subset(train_dataset, val_indices)
        
        class TransformedDataset:
            def __init__(self, subset):
                self.subset = subset
            
            def __len__(self):
                return len(self.subset)
            
            def __getitem__(self, idx):
                return self.subset[idx]
        
        train_dataset = TransformedDataset(train_subset)
        val_dataset = TransformedDataset(val_subset)
    
    train_loader = DataLoader(
        train_dataset, 
        batch_size=batch_size, 
        shuffle=True, 
        num_workers=num_workers,
        pin_memory=pin_memory,
        persistent_workers=True if num_workers > 0 else False
    )
    val_loader = DataLoader(
        val_dataset, 
        batch_size=batch_size, 
        shuffle=False, 
        num_workers=num_workers,
        pin_memory=pin_memory,
        persistent_workers=True if num_workers > 0 else False
    )
    
    print(f"训练集: {len(train_dataset)} 张图片")
    print(f"验证集: {len(val_dataset)} 张图片")
    
    # 创建模型
    print("\n创建模型...")
    model = get_model().to(device)
    
    # 优化器和损失函数 - 增强正则化
    # 使用标签平滑的交叉熵损失（减少过拟合）
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)  # 标签平滑0.1
    optimizer = optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=2e-4)  # 增加权重衰减从1e-4到2e-4
    
    # 训练状态
    start_epoch = 0
    best_val_acc = 0.0
    train_history = []
    early_stop_patience = 10  # Early Stopping耐心值：10个epoch没有提升就停止
    no_improve_epochs = 0  # 记录没有提升的epoch数
    
    # 尝试加载checkpoint
    if os.path.exists(checkpoint_path):
        print(f"\n发现checkpoint: {checkpoint_path}")
        checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)
        
        model.load_state_dict(checkpoint['model_state_dict'])
        optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        start_epoch = checkpoint['epoch']
        best_val_acc = checkpoint['best_val_acc']
        train_history = checkpoint.get('history', [])
        
        print(f"从epoch {start_epoch+1} 继续训练")
        print(f"最佳验证准确率: {best_val_acc:.2f}%")
    else:
        print("\n开始新的训练")
    
    # 使用余弦退火学习率调度器（更平滑的学习率衰减）
    # 在加载checkpoint后初始化，以便正确计算剩余epoch数
    remaining_epochs = num_epochs - start_epoch
    T_max = max(remaining_epochs, 1)  # 确保至少为1
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=T_max, eta_min=1e-6)
    
    # 如果从checkpoint恢复，尝试加载scheduler状态
    if start_epoch > 0:
        checkpoint_for_scheduler = torch.load(checkpoint_path, map_location=device, weights_only=False)
        if 'scheduler_state_dict' in checkpoint_for_scheduler:
            try:
                scheduler.load_state_dict(checkpoint_for_scheduler['scheduler_state_dict'])
            except:
                # 如果加载失败，使用重新计算的scheduler
                print("  注意: scheduler状态加载失败，使用重新计算的scheduler")
                pass
    
    # 混合精度训练scaler
    if use_amp:
        try:
            scaler = GradScaler('cuda')  # PyTorch 2.0+新API
        except TypeError:
            scaler = GradScaler()  # 旧版本兼容
    else:
        scaler = None
    
    # 如果从checkpoint恢复，尝试加载scaler状态
    if scaler is not None:
        if os.path.exists(checkpoint_path) and 'scaler_state_dict' in checkpoint:
            try:
                scaler.load_state_dict(checkpoint['scaler_state_dict'])
            except:
                # 如果加载失败，创建新的scaler
                print("  注意: scaler状态加载失败，使用新的scaler")
                try:
                    scaler = GradScaler('cuda')  # PyTorch 2.0+新API
                except TypeError:
                    scaler = GradScaler()  # 旧版本兼容
    
    # 训练循环
    print("\n开始训练...")
    print("=" * 60)
    
    for epoch in range(start_epoch, num_epochs):
        start_time = time.time()
        
        # 训练
        train_loss, train_acc = train_epoch(model, train_loader, criterion, optimizer, device, epoch, scaler)
        
        # 验证
        val_loss, val_acc = validate(model, val_loader, criterion, device, scaler)
        
        # 更新学习率（余弦退火在epoch后更新）
        scheduler.step()
        
        # 记录历史
        train_history.append({
            'epoch': epoch,
            'train_loss': train_loss,
            'train_acc': train_acc,
            'val_loss': val_loss,
            'val_acc': val_acc,
            'lr': optimizer.param_groups[0]['lr']
        })
        
        # 打印结果
        epoch_time = time.time() - start_time
        print(f"\nEpoch [{epoch+1}/{num_epochs}] - 耗时: {epoch_time:.2f}s")
        print(f"  训练 - Loss: {train_loss:.4f}, Acc: {train_acc:.2f}%")
        print(f"  验证 - Loss: {val_loss:.4f}, Acc: {val_acc:.2f}%")
        print(f"  学习率: {optimizer.param_groups[0]['lr']:.6f}")
        
        # 保存最佳模型
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            no_improve_epochs = 0  # 重置计数器
            torch.save(model.state_dict(), best_model_path)
            print(f"  新的最佳模型已保存! 验证准确率: {best_val_acc:.2f}%")
        else:
            no_improve_epochs += 1  # 验证准确率没有提升
        
        # 保存checkpoint（包含scaler状态）
        checkpoint = {
            'epoch': epoch + 1,
            'model_state_dict': model.state_dict(),
            'optimizer_state_dict': optimizer.state_dict(),
            'scheduler_state_dict': scheduler.state_dict(),
            'best_val_acc': best_val_acc,
            'history': train_history
        }
        if scaler is not None:
            checkpoint['scaler_state_dict'] = scaler.state_dict()
        torch.save(checkpoint, checkpoint_path)
        
        # 检查过拟合
        if len(train_history) > 10:
            train_acc_avg = np.mean([h['train_acc'] for h in train_history[-10:]])
            val_acc_avg = np.mean([h['val_acc'] for h in train_history[-10:]])
            overfitting = train_acc_avg - val_acc_avg
            
            if overfitting > 25:
                print(f"  警告: 检测到严重过拟合! (训练-验证差距: {overfitting:.2f}%)")
        
        # Early Stopping: 如果验证准确率连续多个epoch没有提升，提前停止
        if no_improve_epochs >= early_stop_patience:
            print(f"\nEarly Stopping: 验证准确率连续{early_stop_patience}个epoch没有提升")
            print(f"最佳验证准确率: {best_val_acc:.2f}%")
            print(f"停止训练以避免过拟合")
            break
        
        print("=" * 60)
        
        # 达到目标准确率，停止训练
        if val_acc > 85.0 and train_acc > 90.0:
            print(f"\n达到目标准确率! 训练完成")
            print(f"最终验证准确率: {val_acc:.2f}%")
            break
    
    # 保存训练配置
    config = {
        'num_classes': 50,
        'model_type': 'ResNet18',
        'batch_size': batch_size,
        'learning_rate': learning_rate,
        'best_val_acc': best_val_acc,
        'total_epochs': epoch + 1
    }
    with open(config_path, 'w') as f:
        json.dump(config, f, indent=2)
    
    print("\n训练完成!")
    print(f"最佳模型已保存: {best_model_path}")
    print(f"最新checkpoint已保存: {checkpoint_path}")

if __name__ == "__main__":
    train_with_resume()

