from pathlib import Path
import numpy as np
import onnx
from onnx import helper, numpy_helper, TensorProto
p = Path(__file__).parent / "fixture.onnx"
boxes = np.zeros((1, 84, 2), dtype=np.float32)
boxes[0, :4, :] = np.array([[32, 32], [32, 32], [16, 16], [16, 16]], dtype=np.float32)
boxes[0, 4, :] = [.9, .8]
graph = helper.make_graph([
    helper.make_node("ReduceMean", ["input"], ["mean"], keepdims=0),
    helper.make_node("Mul", ["mean", "zero"], ["input_dependency"]),
    helper.make_node("Add", ["boxes", "input_dependency"], ["output"]),
], "detection_fixture",
    [helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, 64, 64])],
    [helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 84, 2])],
    initializer=[numpy_helper.from_array(boxes, "boxes"),
                 numpy_helper.from_array(np.array(0, dtype=np.float32), "zero")])
model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)], ir_version=8)
onnx.checker.check_model(model)
onnx.save(model, p)
Path(str(p) + ".model-contract").write_text("format=yolo8\naxes=channels_first\ncoordinates=pixels\n")
print("Created validated synthetic inference fixture")
