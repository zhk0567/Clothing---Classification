# -*- coding: utf-8 -*-
"""
更新Android应用的DeepFashion模型
将最新的训练好的PyTorch模型转换为ONNX并复制到Android应用
"""

import os
import torch
import torch.nn as nn
from torchvision.models import resnet18

class DeepFashionClassifier(nn.Module):
    """DeepFashion分类器 - 匹配训练脚本的模型结构（增强正则化版本）"""
    
    def __init__(self, num_classes=50):
        super(DeepFashionClassifier, self).__init__()
        # 使用与训练脚本相同的结构（ResNet18，直接替换fc层）
        try:
            from torchvision.models import ResNet18_Weights
            model = resnet18(weights=None)
        except:
            model = resnet18(pretrained=False)
        
        # 替换最后的分类层 - 增强正则化版本（匹配训练脚本）
        num_features = model.fc.in_features
        model.fc = nn.Sequential(
            nn.Dropout(0.6),  # 匹配训练脚本的Dropout率
            nn.Linear(num_features, 512),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),  # 额外的Dropout层
            nn.Linear(512, num_classes)
        )
        
        # 注意：实际保存的模型可能没有backbone前缀，直接使用model
        self.backbone = model
        # 为了兼容，也直接保存model的引用
        self.model = model
    
    def forward(self, x):
        # 兼容两种情况：有backbone前缀或没有
        if hasattr(self, 'backbone'):
            return self.backbone(x)
        else:
            return self.model(x)

def update_model():
    """更新Android应用的模型"""
    pytorch_model_path = "deepfashion_best_model.pth"
    onnx_path = "deepfashion_classifier.onnx"
    android_onnx_path = "DeepFashionClassifier/app/src/main/assets/models/deepfashion_classifier.onnx"
    
    if not os.path.exists(pytorch_model_path):
        print(f"错误: 找不到训练好的模型 {pytorch_model_path}")
        return False
    
    try:
        model = DeepFashionClassifier(num_classes=50)
        checkpoint = torch.load(pytorch_model_path, map_location='cpu', weights_only=False)
        
        if isinstance(checkpoint, dict):
            if 'model_state_dict' in checkpoint:
                state_dict = checkpoint['model_state_dict']
            elif 'state_dict' in checkpoint:
                state_dict = checkpoint['state_dict']
            else:
                state_dict = checkpoint
        else:
            state_dict = checkpoint
        
        if any(k.startswith('backbone.') for k in state_dict.keys()):
            model.load_state_dict(state_dict, strict=False)
        else:
            new_state_dict = {}
            for k, v in state_dict.items():
                new_key = f'backbone.{k}' if not k.startswith('backbone.') else k
                new_state_dict[new_key] = v
            model.load_state_dict(new_state_dict, strict=False)
        
        model.eval()
        
        dummy_input = torch.randn(1, 3, 224, 224)
        torch.onnx.export(
            model,
            dummy_input,
            onnx_path,
            input_names=['input'],
            output_names=['output'],
            export_params=True,
            do_constant_folding=True,
            opset_version=13,
            dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}}
        )
        
        import onnx
        onnx_model = onnx.load(onnx_path)
        onnx.checker.check_model(onnx_model)
        
        android_assets_dir = os.path.dirname(android_onnx_path)
        os.makedirs(android_assets_dir, exist_ok=True)
        
        import shutil
        shutil.copy2(onnx_path, android_onnx_path)
        
        print(f"模型已更新: {android_onnx_path}")
        return True
        
    except Exception as e:
        print(f"错误: {e}")
        return False

if __name__ == "__main__":
    import sys
    # 切换到项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    os.chdir(project_root)
    
    success = update_model()
    sys.exit(0 if success else 1)

