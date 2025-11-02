# -*- coding: utf-8 -*-
"""
将训练好的DeepFashion模型转换为TFLite格式
保证兼容性和格式正确性
"""

import torch
import torch.nn as nn
import numpy as np
import tensorflow as tf
import onnx
from torchvision.models import resnet18

class DeepFashionClassifier(nn.Module):
    """DeepFashion分类器 - 匹配训练脚本的模型结构"""
    
    def __init__(self, num_classes=50):
        super(DeepFashionClassifier, self).__init__()
        # 使用与训练脚本相同的结构（ResNet18，直接替换fc层）
        model = resnet18(pretrained=False)
        
        # 替换最后的分类层（匹配训练脚本）
        num_features = model.fc.in_features
        model.fc = nn.Sequential(
            nn.Dropout(0.5),
            nn.Linear(num_features, num_classes)
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

def convert_to_tflite():
    """转换训练好的模型到TFLite"""
    print("转换DeepFashion模型到TFLite...")
    print("=" * 60)
    
    # 优先使用当前目录的最佳模型，如果不存在则尝试models目录
    pytorch_model_path = "deepfashion_best_model.pth"  # 当前目录
    if not os.path.exists(pytorch_model_path):
        pytorch_model_path = "models/deepfashion_classifier_final.pth"  # 备用路径
    onnx_path = "deepfashion_for_tflite.onnx"  # 当前目录
    tflite_path = "DeepFashionClassifier/app/src/main/assets/models/deepfashion_classifier.tflite"
    
    if not os.path.exists(pytorch_model_path):
        print(f"错误: 找不到训练好的模型 {pytorch_model_path}")
        print("请先运行训练脚本: python scripts/train_deepfashion_complete.py")
        return
    
    try:
        # 加载训练好的模型
        print(f"加载训练好的模型: {pytorch_model_path}")
        model = DeepFashionClassifier(num_classes=50)
        
        # 加载权重（处理不同的保存格式）
        checkpoint = torch.load(pytorch_model_path, map_location='cpu')
        
        if isinstance(checkpoint, dict):
            if 'model_state_dict' in checkpoint:
                state_dict = checkpoint['model_state_dict']
            elif 'state_dict' in checkpoint:
                state_dict = checkpoint['state_dict']
            else:
                state_dict = checkpoint
        else:
            state_dict = checkpoint
        
        # 处理backbone前缀问题：如果state_dict没有backbone前缀，需要添加
        if any(k.startswith('backbone.') for k in state_dict.keys()):
            # 已经有backbone前缀，直接加载
            model.load_state_dict(state_dict, strict=False)
        else:
            # 没有backbone前缀，需要添加前缀或直接映射到model
            # 尝试直接加载（ResNet18的权重可以直接加载到backbone）
            try:
                # 先尝试直接加载到backbone
                new_state_dict = {}
                for k, v in state_dict.items():
                    new_key = f'backbone.{k}' if not k.startswith('backbone.') else k
                    new_state_dict[new_key] = v
                model.load_state_dict(new_state_dict, strict=False)
            except:
                # 如果失败，尝试直接加载到model（移除backbone前缀）
                model.model.load_state_dict(state_dict, strict=False)
        
        model.eval()
        
        print("模型加载成功")
        
        # 转换为ONNX
        print("\n转换为ONNX...")
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
        
        print(f"ONNX模型已保存: {onnx_path}")
        
        # 验证ONNX模型
        print("\n验证ONNX模型...")
        onnx_model = onnx.load(onnx_path)
        onnx.checker.check_model(onnx_model)
        print("ONNX模型验证通过")
        
        # 转换为TensorFlow Lite
        print("\n转换为TensorFlow Lite...")
        
        # 方法1: 使用tf2onnx
        try:
            import tf2onnx
            from onnx_tf.backend import prepare
            
            print("使用onnx_tf转换...")
            tf_rep = prepare(onnx_model)
            
            # 导出为TensorFlow SavedModel
            tf_model_path = "deepfashion_tf"  # 当前目录
            tf_rep.export_graph(tf_model_path)
            print(f"TensorFlow模型已保存: {tf_model_path}")
            
            # 转换为TFLite
            converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
            
            tflite_model = converter.convert()
            
            with open(tflite_path, 'wb') as f:
                f.write(tflite_model)
            
            print(f"TFLite模型已保存: {tflite_path}")
            print(f"模型大小: {len(tflite_model):,} bytes")
            
        except Exception as e:
            print(f"使用onnx_tf转换失败: {e}")
            print("\n尝试使用onnxruntime进行转换...")
            
            # 方法2: 使用onnxruntime-tf
            import onnxruntime as ort
            
            # 创建简单的转换（手动构建）
            print("使用替代方法: 通过ONNX进行推理测试")
            
            session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
            
            # 测试推理
            test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
            outputs = session.run(None, {'input': test_input})
            
            print(f"ONNX推理测试成功")
            print(f"  输入形状: {test_input.shape}")
            print(f"  输出形状: {outputs[0].shape}")
            print(f"  输出类别: {np.argmax(outputs[0][0])}, 概率: {np.max(outputs[0][0]):.4f}")
            
            print("\n注意: 需要手动将ONNX模型转换为TFLite")
            print(f"ONNX模型已保存: {onnx_path}")
            return
        
        # 测试TFLite模型
        print("\n测试TFLite模型...")
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        
        # 测试多个输入
        test_inputs = []
        for i in range(5):
            test_input = np.random.randn(1, 224, 224, 3).astype(np.float32)
            interpreter.set_tensor(interpreter.get_input_details()[0]['index'], test_input)
            interpreter.invoke()
            output = interpreter.get_tensor(interpreter.get_output_details()[0]['index'])
            
            max_idx = np.argmax(output[0])
            max_prob = output[0][max_idx]
            test_inputs.append(max_idx)
            
            print(f"  测试 {i+1}: 类别={max_idx}, 概率={max_prob:.4f}")
        
        unique_outputs = len(set(test_inputs))
        print(f"\n不同输出数量: {unique_outputs}/5")
        
        if unique_outputs > 1:
            print("模型会产生不同的输出")
        else:
            print("模型输出相同 - 可能需要重新训练")
        
        print("\n转换完成!")
        print(f"TFLite模型已保存到: {tflite_path}")
        print("可以用于Android部署")
        
    except Exception as e:
        print(f"转换失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    import os
    os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'  # 减少TensorFlow日志
    convert_to_tflite()

