# Vision Core

Reusable C++20 components extracted from the supplied Windows source. The original application files are not modified.

## Components

- **ScreenCapture.hpp / ScreenCapture.cpp**: DXGI Desktop Duplication, optional Windows Graphics Capture, direct texture views, region-copy fallback, dirty rectangles, timestamps, and recovery.
- **ObjectDetector.hpp / ObjectDetector.cpp**: asynchronous model loading/inference, output-layout contracts, coordinate decoding, confidence filtering, NMS, result snapshots, and timing.
- **FrameProcessor.hpp**: D3D11 compute-shader resize/letterbox conversion to planar RGB tensors, GPU timing, CPU staging, and shared GPU buffers.
- **HidInput.hpp**: Arduino Leonardo/Micro RawHID enumeration, connection lifecycle, movement/button reports, bounded overlapped I/O, and reconnect handling.

Keep the support directory with these files. The two header-only components retain inline implementations. Application UI, authentication, configuration storage, and automatic detection-to-input connections are not included.

## Build

Windows x64, Visual Studio C++ Build Tools, and a recent Windows SDK with C++/WinRT are required. Run **build.cmd**. It discovers the installed toolchain and produces build/VisionCore.lib and build/core_smoke.exe.

The default uses Windows ML and enables Windows Graphics Capture. No third-party SDK is needed for this build. Define VISION_ENABLE_WGC consistently in application and library, or omit it consistently. Link user32, d3d11, dxgi, d3dcompiler, windowsapp, hid, and setupapi as required by the components used.

### Optional inference backends

The backend implementations are retained, not replaced with stubs:

- TensorRT 10.11 + CUDA 11.8: set TENSORRT_ROOT and CUDA_ROOT, then run **build.cmd trt** (outputs go to build-trt). Put matching runtime DLL directories on PATH. This enables VISION_ENABLE_TRT and VISION_ENABLE_D3D_INTEROP.
- ONNX Runtime: integrate with VISION_ENABLE_ORT, its SDK include/library directories, onnxruntime.lib, and matching DLL. VISION_ENABLE_DML additionally enables DirectML.

Third-party SDKs, runtime binaries, and trained models are not redistributed. Use your existing SDKs and their licenses. TensorRT engine files are hardware/runtime-specific.

## Capture and preprocessing

Initialize a WinRT multithreaded apartment on the owner thread. The following is one iteration; continue polling readback before submitting another CPU-path frame:

    winrt::init_apartment(winrt::apartment_type::multi_threaded);
    ScreenCapture capture;
    capture.Open(320);
    FrameProcessor processor;
    processor.Init(capture.Device());
    std::vector<float> tensor(640 * 640 * 3);
    if (capture.CaptureFrame(8)) {
        const auto frame = capture.View();
        processor.Submit(capture.Context(), frame.pixels,
            frame.textureWidth, frame.textureHeight, frame.sampleRegion,
            frame.outputLeft, frame.outputTop, frame.id, frame.capturedMs,
            PerfNowMs(), frame.presentedMs, 0, 640,
            frame.acquiredMs, frame.accumulatedFrames);
        capture.Context()->Flush();
    }
    LetterboxInfo result;
    if (processor.TryFetch(capture.Context(), tensor.data(), result)) {
    }

Submit GPU work before the next capture, close, or restart: a retained COM view does not extend a desktop frame lease. View contains a borrowed pixel pointer valid only until the next capture, close, or restart. Use View only after successful capture. TryFetch is nonblocking; its destination must hold net * net * 3 floats. Supported square model input sides are 64–640.

View.sampleRegion is texture-local; outputLeft/outputTop map decoded boxes to virtual-desktop coordinates, including negative monitor origins. VISION_CAPTURE_MONITOR selects a Windows display name; the default is the primary monitor. Rotated displays are rejected. Native HDR tone mapping is not implemented.

For shared input, configure detector buffers before Start, check SharedInput/SharedSlotCount, and never overwrite a slot while inference owns it. These components do not supply an application scheduling thread. Capture and preprocessing are owner-thread APIs; do not call them concurrently.

## Detection

Call Start(modelPath), wait for Ready while checking Loading and Error, then use WantsFrame and Push(tensor, metadata). Push swaps the caller's vector with the worker's buffer. Read results through CopyLatestIfChanged. Stop the detector before destroying shared GPU buffers.

Defaults remain class 0, confidence 0.35, NMS IoU 0.45, and at most 100 results. Shared runtime configuration is in support/runtime.hpp; use one active detector configuration per process. Set kDecodeClass to -1 to decode all supported classes.

Optional model semantics use a sidecar named <model path>.model-contract:

    format=yolo8
    axes=channels_first
    coordinates=pixels
    verified_nms=0

Supported formats: auto, yolo8, yolo5, nms_xyxy. Existing sidecar contents can be reused under this new suffix. Backend tuning environment variables now use the VISION_ prefix. Set gDiagnosticSink to receive diagnostics; application-specific log files and telemetry were removed.

## HID

Construction starts an idle worker. Call Connect explicitly to enumerate/open hardware:

    HidInput device(firmwareGreeting);
    device.Connect();

The greeting is caller-supplied, not a renamed protocol constant. Pass the exact string expected by your firmware. An empty greeting skips the handshake and remains unverified. The expected reply still contains OK. A connection can be open without a verified handshake, matching the source behavior; callers can require HandshakeOk before sending reports.

VID 0x2341, PIDs 0x8036/0x8037, usage page 0xFFC0, usage 0x0C00, report ID, button bits and signed movement layout are unchanged. This is a specific RawHID transport, not a universal HID abstraction. No firmware is included.

## Checks

    build.cmd
    build\core_smoke.exe
    build\core_smoke.exe --capture
    build\core_smoke.exe --wgc
    python tests\make_model.py
    build\core_smoke.exe --model tests\fixture.onnx

Fixture generation uses Python with onnx and numpy. It creates a small synthetic model with known boxes, not a trained model. Checks cover actual inference, decoding, NMS, coordinates, metadata, GPU preprocessing, capture/restart, and passive HID lifecycle. No check sends HID movement or button reports. See VERIFICATION.md for results actually observed.

## Source and rights

These files derive from user-supplied source, not a newly licensed clean-room implementation. Existing notices in the extracted source are retained. No new license grant is implied; redistribution remains subject to the rights and dependency licenses applicable to the original source.
