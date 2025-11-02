#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DeepFashion 50分类训练脚本 - 支持断点续训
"""

import os
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import torchvision.transforms as transforms
from PIL import Image
import json
import time
from collections import Counter

class DeepFashionDataset(Dataset):
    """DeepFashion数据集类"""
    
    def __init__(self, img_dir, image_list_file, label_file, transform=None):
        self.img_dir = img_dir
        self.transform = transform
        
        # 读取图片列表
        self.image_paths = []
        with open(image_list_file, 'r', encoding='utf-8') as f:
            for line in f:
                img_path = line.strip()
                if img_path:
                    full_path = os.path.join(self.img_dir, img_path)
                    if os.path.exists(full_path):
                        self.image_paths.append(full_path)
        
        # 读取标签
        self.labels = []
        with open(label_file, 'r', encoding='utf-8') as f:
            for line in f:
                label = int(line.strip()) - 1  # 转换为0-based索引
                self.labels.append(label)
        
        # 确保图片和标签数量匹配
        min_count = min(len(self.image_paths), len(self.labels))
        self.image_paths = self.image_paths[:min_count]
        self.labels = self.labels[:min_count]
        
        print(f"加载了 {len(self.image_paths)} 张图片")
        print(f"标签数量: {len(self.labels)}")
    
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
            print(f"加载图片失败: {image_path}, 错误: {e}")
            # 返回一个空白图片
            blank_image = Image.new('RGB', (224, 224), (0, 0, 0))
            if self.transform:
                blank_image = self.transform(blank_image)
            return blank_image, label

class DeepFashionClassifier(nn.Module):
    """DeepFashion分类器"""
    
    def __init__(self, num_classes=50):
        super(DeepFashionClassifier, self).__init__()
        
        # 使用预训练的ResNet18作为骨干网络
        import torchvision.models as models
        self.backbone = models.resnet18(pretrained=False)
        
        # 加载本地预训练模型
        pretrained_path = 'models/resnet18-f37072fd.pth'
        if os.path.exists(pretrained_path):
            print(f"加载本地预训练模型: {pretrained_path}")
            state_dict = torch.load(pretrained_path, map_location='cpu')
            self.backbone.load_state_dict(state_dict)
        else:
            print("警告: 未找到本地预训练模型，使用随机初始化")
        
        # 替换最后的分类层
        num_features = self.backbone.fc.in_features
        self.backbone.fc = nn.Linear(num_features, num_classes)
        
    def forward(self, x):
        return self.backbone(x)

def get_data_transforms():
    """获取数据变换"""
    train_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomRotation(degrees=10),
        transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.1),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    
    val_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    
    return train_transform, val_transform

def save_checkpoint(model, optimizer, scheduler, epoch, best_acc, checkpoint_path):
    """保存检查点"""
    checkpoint = {
        'epoch': epoch,
        'model_state_dict': model.state_dict(),
        'optimizer_state_dict': optimizer.state_dict(),
        'scheduler_state_dict': scheduler.state_dict(),
        'best_acc': best_acc,
        'timestamp': time.time()
    }
    torch.save(checkpoint, checkpoint_path)
    print(f"检查点已保存: {checkpoint_path}")

def load_checkpoint(checkpoint_path, model, optimizer, scheduler):
    """加载检查点"""
    if os.path.exists(checkpoint_path):
        print(f"加载检查点: {checkpoint_path}")
        checkpoint = torch.load(checkpoint_path, map_location='cpu')
        
        model.load_state_dict(checkpoint['model_state_dict'])
        optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        scheduler.load_state_dict(checkpoint['scheduler_state_dict'])
        
        start_epoch = checkpoint['epoch'] + 1
        best_acc = checkpoint['best_acc']
        
        print(f"从第 {start_epoch} 个epoch继续训练")
        print(f"当前最佳准确率: {best_acc:.4f}")
        
        return start_epoch, best_acc
    else:
        # 检查是否有现有的最佳模型
        best_model_path = 'models/deepfashion_classifier_best.pth'
        if os.path.exists(best_model_path):
            print(f"未找到检查点，但找到现有最佳模型: {best_model_path}")
            print("从现有最佳模型开始训练...")
            model.load_state_dict(torch.load(best_model_path, map_location='cpu'))
            return 0, 0.0  # 从头开始训练，但使用现有模型权重
        else:
            print("未找到检查点和现有模型，从头开始训练")
            return 0, 0.0

def train_model(model, train_loader, val_loader, num_epochs=20, learning_rate=0.001, resume_from_checkpoint=True):
    """训练模型 - 支持断点续训"""
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"使用设备: {device}")
    
    model = model.to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=learning_rate)
    scheduler = optim.lr_scheduler.StepLR(optimizer, step_size=7, gamma=0.1)
    
    # 检查点路径
    checkpoint_path = 'models/deepfashion_checkpoint.pth'
    
    # 尝试加载检查点
    if resume_from_checkpoint:
        start_epoch, best_acc = load_checkpoint(checkpoint_path, model, optimizer, scheduler)
    else:
        start_epoch, best_acc = 0, 0.0
    
    # 如果从现有模型开始，设置一个合理的起始准确率
    if start_epoch == 0 and best_acc == 0.0 and os.path.exists('models/deepfashion_classifier_best.pth'):
        best_acc = 0.4562  # 使用之前测试的准确率
        print(f"使用现有模型，设置起始最佳准确率: {best_acc:.4f}")
    
    print(f"开始训练: 从第 {start_epoch} 个epoch到第 {num_epochs} 个epoch")
    
    for epoch in range(start_epoch, num_epochs):
        print(f'\nEpoch {epoch+1}/{num_epochs}')
        print('-' * 50)
        
        # 训练阶段
        model.train()
        running_loss = 0.0
        running_corrects = 0
        
        for batch_idx, (inputs, labels) in enumerate(train_loader):
            inputs = inputs.to(device)
            labels = labels.to(device)
            
            optimizer.zero_grad()
            outputs = model(inputs)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            
            running_loss += loss.item()
            _, preds = torch.max(outputs, 1)
            running_corrects += torch.sum(preds == labels.data)
            
            if batch_idx % 100 == 0:
                print(f'Batch {batch_idx}, Loss: {loss.item():.4f}')
        
        epoch_loss = running_loss / len(train_loader)
        epoch_acc = running_corrects.double() / len(train_loader.dataset)
        
        print(f'Train Loss: {epoch_loss:.4f}, Train Acc: {epoch_acc:.4f}')
        
        # 验证阶段
        model.eval()
        val_loss = 0.0
        val_corrects = 0
        
        with torch.no_grad():
            for inputs, labels in val_loader:
                inputs = inputs.to(device)
                labels = labels.to(device)
                
                outputs = model(inputs)
                loss = criterion(outputs, labels)
                
                val_loss += loss.item()
                _, preds = torch.max(outputs, 1)
                val_corrects += torch.sum(preds == labels.data)
        
        val_loss = val_loss / len(val_loader)
        val_acc = val_corrects.double() / len(val_loader.dataset)
        
        print(f'Val Loss: {val_loss:.4f}, Val Acc: {val_acc:.4f}')
        
        # 保存最佳模型
        if val_acc > best_acc:
            best_acc = val_acc
            torch.save(model.state_dict(), 'models/deepfashion_classifier_best.pth')
            print(f'保存最佳模型，验证准确率: {val_acc:.4f}')
        
        # 保存检查点
        save_checkpoint(model, optimizer, scheduler, epoch, best_acc, checkpoint_path)
        
        scheduler.step()
    
    # 保存最终模型
    torch.save(model.state_dict(), 'models/deepfashion_classifier_final.pth')
    print("训练完成！")
    
    return model

def main():
    """主函数"""
    print("DeepFashion 50分类训练 - 支持断点续训")
    print("=" * 50)
    
    # 数据集路径
    data_dir = 'Category and Attribute Prediction Benchmark/Img'
    train_image_file = 'Category and Attribute Prediction Benchmark/Anno_fine/train.txt'
    train_label_file = 'Category and Attribute Prediction Benchmark/Anno_fine/train_cate.txt'
    val_image_file = 'Category and Attribute Prediction Benchmark/Anno_fine/val.txt'
    val_label_file = 'Category and Attribute Prediction Benchmark/Anno_fine/val_cate.txt'
    
    # 检查文件是否存在
    required_files = [data_dir, train_image_file, train_label_file, val_image_file, val_label_file]
    
    for file_path in required_files:
        if not os.path.exists(file_path):
            print(f"错误: 找不到文件 {file_path}")
            return
    
    # 获取数据变换
    train_transform, val_transform = get_data_transforms()
    
    # 创建数据集
    print("加载数据集...")
    train_dataset = DeepFashionDataset(data_dir, train_image_file, train_label_file, train_transform)
    val_dataset = DeepFashionDataset(data_dir, val_image_file, val_label_file, val_transform)
    
    if len(train_dataset) == 0:
        print("错误: 训练数据集为空")
        return
    
    if len(val_dataset) == 0:
        print("错误: 验证数据集为空")
        return
    
    # 创建数据加载器
    train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True, num_workers=2)
    val_loader = DataLoader(val_dataset, batch_size=32, shuffle=False, num_workers=2)
    
    # 创建模型
    model = DeepFashionClassifier(num_classes=50)
    
    # 创建模型保存目录
    os.makedirs('models', exist_ok=True)
    
    # 训练模型
    print("开始训练...")
    trained_model = train_model(model, train_loader, val_loader, num_epochs=20, resume_from_checkpoint=True)
    
    print("训练完成！")

if __name__ == "__main__":
    main()

