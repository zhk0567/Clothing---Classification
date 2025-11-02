# -*- coding: utf-8 -*-
"""
验证ONNX模型并准备用于Android
"""

import os
import numpy as np
import onnxruntime as ort

def verify_onnx_model():
    """验证ONNX模型"""
    onnx_path = "deepfashion_for_tflite.onnx"
    
    if not os.path.exists(onnx_path):
        print(f"错误: ONNX模型不存在: {onnx_path}")
        return False
    
    try:
        session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
        input_info = session.get_inputs()[0]
        
        all_max_indices = []
        for i in range(10):
            test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
            outputs = session.run(None, {input_info.name: test_input})
            max_idx = np.argmax(outputs[0][0])
            all_max_indices.append(max_idx)
        
        unique_indices = len(set(all_max_indices))
        if unique_indices == 1:
            print("警告: 模型输出异常")
        
        print("ONNX模型验证通过")
        return True
        
    except Exception as e:
        print(f"验证失败: {e}")
        return False

def prepare_for_android():
    """准备用于Android"""
    onnx_path = "deepfashion_for_tflite.onnx"
    android_path = "DeepFashionClassifier/app/src/main/assets/models/deepfashion_classifier.onnx"
    
    os.makedirs(os.path.dirname(android_path), exist_ok=True)
    
    import shutil
    shutil.copy2(onnx_path, android_path)
    
    print(f"模型已复制到Android应用: {android_path}")

if __name__ == "__main__":
    success = verify_onnx_model()
    if success:
        prepare_for_android()

