# Verification — 2026-11-14

Executed on the connected Windows x64 PC using Visual Studio 18 Build Tools.

## Passed

- Default C++20 build and link: Windows ML + Windows Graphics Capture.
- TensorRT 10.11 / CUDA 11.8 build and link, including D3D11 interoperability.
- Real D3D11 GPU preprocessing with a synthetic texture: RGB channel ordering, planar tensor layout, resize, letterbox padding, and coordinate metadata.
- Actual Windows ML inference with a validated synthetic ONNX model: confidence/class, overlapping-box NMS, decoded coordinate offsets, frame metadata, and detector shutdown.
- Live DXGI desktop capture through preprocessing, followed by close/reopen.
- Live Windows Graphics Capture through preprocessing, followed by close/reopen.
- Capture region geometry, model-contract parsing, and passive HID construction/disconnection/destruction.
- Extraction review: no retained application UI/authentication dependencies or application-specific identifiers in the delivered code.

## Not exercised

- Physical HID enumeration, firmware handshake, movement/button delivery, or reconnection to hardware. No input reports were sent.
- Inference using the user's trained model or TensorRT engine. TensorRT was compiled/linked, not exercised with an engine.
- Optional ONNX Runtime backend execution.
- Non-primary monitors, rotated/HDR displays, or device-loss recovery under hardware failure.
- The DXGI dirty-rectangle incremental fallback was compiled; the restart check does not prove that path was selected by the driver.

The synthetic model is a deterministic integration fixture, not a trained detection model. Build products and test logs remain in the extraction directory on the PC; the source archive contains code, documentation, and the fixture generator only.
