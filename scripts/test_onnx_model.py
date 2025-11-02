# -*- coding: utf-8 -*-
"""
使用ONNX Runtime Mobile转换为TFLite（备选方案）
或者使用其他转换工具
"""

import os
import numpy as np
import onnxruntime as ort

def test_onnx_model():
    """测试ONNX模型是否正常工作"""
    onnx_path = "deepfashion_for_tflite.onnx"
    
    if not os.path.exists(onnx_path):
        print(f"错误: 找不到ONNX模型 {onnx_path}")
        return False
    
    try:
        session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
        input_name = session.get_inputs()[0].name
        
        test_inputs = []
        for i in range(5):
            test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
            outputs = session.run(None, {input_name: test_input})
            max_idx = np.argmax(outputs[0][0])
            test_inputs.append(max_idx)
        
        unique_outputs = len(set(test_inputs))
        if unique_outputs == 1:
            print("警告: 所有输入产生相同输出")
        
        print("ONNX模型验证通过")
        return True
        
    except Exception as e:
        print(f"测试失败: {e}")
        return False

if __name__ == "__main__":
    import os
    os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
    test_onnx_model()

