#include "support/desktop_srv_cache.hpp"
#include "ScreenCapture.hpp"
#include "ObjectDetector.hpp"
#include "HidInput.hpp"
#include <iostream>

static void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
static LetterboxInfo Process(FrameProcessor& processor, ID3D11DeviceContext* context,
                             const ScreenCapture::FrameView& frame, std::vector<float>& tensor) {
    auto submitted = processor.Submit(context, frame.pixels, frame.textureWidth, frame.textureHeight,
        frame.sampleRegion, frame.outputLeft, frame.outputTop, frame.id, frame.capturedMs,
        PerfNowMs(), frame.presentedMs, 0, 64, frame.acquiredMs, frame.accumulatedFrames);
    Require(submitted.net == 64, "Frame submission failed");
    context->Flush();
    LetterboxInfo result;
    for (unsigned i = 0; i < 500; ++i) {
        if (processor.TryFetch(context, tensor.data(), result)) return result;
        Sleep(10);
    }
    throw std::runtime_error("GPU readback timed out");
}
static void PreprocessingTest() {
    ComPtr<ID3D11Device> device;
    ComPtr<ID3D11DeviceContext> context;
    HR(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0, nullptr, 0,
        D3D11_SDK_VERSION, &device, nullptr, &context), "Test D3D11 device");
    FrameProcessor processor;
    processor.Init(device.Get());
    std::array<std::uint32_t, 8> pixels{};
    pixels.fill(0xffff0000u);
    D3D11_TEXTURE2D_DESC desc{};
    desc.Width = 4; desc.Height = 2; desc.MipLevels = desc.ArraySize = 1;
    desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM; desc.SampleDesc.Count = 1;
    desc.Usage = D3D11_USAGE_IMMUTABLE; desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
    D3D11_SUBRESOURCE_DATA data{pixels.data(), 4 * sizeof(std::uint32_t), 0};
    ComPtr<ID3D11Texture2D> texture;
    ComPtr<ID3D11ShaderResourceView> view;
    HR(device->CreateTexture2D(&desc, &data, &texture), "Test texture");
    HR(device->CreateShaderResourceView(texture.Get(), nullptr, &view), "Test view");
    ScreenCapture::FrameView frame;
    frame.pixels = view.Get(); frame.textureWidth = 4; frame.textureHeight = 2;
    frame.sampleRegion = {0, 0, 4, 2}; frame.id = 1;
    frame.outputLeft = -100; frame.outputTop = 25;
    std::vector<float> tensor(64 * 64 * 3);
    auto lb = Process(processor, context.Get(), frame, tensor);
    Require(lb.offsetX == -100 && lb.offsetY == 25 && lb.padY == 16, "Letterbox metadata mismatch");
    const auto middle = 32 * 64 + 32;
    Require(std::abs(tensor[middle] - 1.f) < 0.001f, "Red channel mismatch");
    Require(std::abs(tensor[4096 + middle]) < 0.001f && std::abs(tensor[8192 + middle]) < 0.001f,
        "Planar RGB channels mismatch");
    Require(std::abs(tensor[0] - 114.f / 255.f) < 0.001f, "Padding mismatch");
    std::cout << "PASS GPU preprocessing, planar RGB, letterbox and coordinates\n";
}
static void CaptureTest(ScreenCapture::Backend backend) {
    ScreenCapture capture;
    capture.Open(160, backend);
    FrameProcessor processor;
    processor.Init(capture.Device());
    std::vector<float> tensor(64 * 64 * 3);
    bool captured = false;
    for (int i = 0; i < 200 && !captured; ++i) {
        if (!capture.CaptureFrame(16)) { Sleep(10); continue; }
        auto frame = capture.View();
        Require(frame.pixels && frame.id, "Invalid captured frame");
        Process(processor, capture.Context(), frame, tensor);
        for (float v : tensor) Require(std::isfinite(v) && v >= 0 && v <= 1.001f, "Invalid captured tensor");
        captured = true;
    }
    Require(captured, "No desktop frame received");
    capture.Close();
    capture.Open(160, backend, ScreenCapture::CaptureMethod::DirtyRects);
    bool restarted = false;
    for (int i = 0; i < 200 && !restarted; ++i) {
        restarted = capture.CaptureFrame(16); if (!restarted) Sleep(10);
    }
    Require(restarted && capture.View().pixels, "Capture restart failed");
    std::cout << "PASS capture, preprocessing, close and restart\n";
}
static void DetectorTest(const std::wstring& path) {
    ObjectDetector detector;
    detector.Start(path);
    for (int i = 0; i < 3000 && !detector.Ready() && detector.Loading(); ++i) Sleep(10);
    if (!detector.Ready()) throw std::runtime_error("Model load failed: " + detector.Error());
    std::vector<float> input(64 * 64 * 3, 0.5f);
    LetterboxInfo lb{}; lb.net = 64; lb.frameId = 7; lb.ratio = 1;
    lb.offsetX = 10; lb.offsetY = 20; lb.roiWidth = lb.roiHeight = 64;
    lb.capturedMs = lb.submittedMs = PerfNowMs();
    detector.Push(input, lb);
    for (int i = 0; i < 3000 && !detector.CompletedCount() && detector.Ready(); ++i) Sleep(10);
    Require(detector.CompletedCount() > 0, "Inference did not complete");
    std::uint64_t version = 0; std::vector<Detection> detections;
    DetectionTiming timing; BackendStageTiming stages;
    Require(detector.CopyLatestIfChanged(version, detections, timing, stages), "No detection publication");
    Require(detections.size() == 1, "NMS result count mismatch");
    const auto& d = detections.front();
    Require(d.cls == 0 && std::abs(d.score - .9f) < .001f, "Class or score mismatch");
    Require(std::abs(d.x - 34) < .001f && std::abs(d.y - 44) < .001f &&
        std::abs(d.w - 16) < .001f && std::abs(d.h - 16) < .001f, "Decoded coordinates mismatch");
    Require(timing.frameId == 7, "Detection frame metadata mismatch");
    detector.Stop();
    Require(!detector.Ready(), "Detector stop failed");
    std::cout << "PASS model inference, decoding, NMS, coordinates, metadata and stop\n";
}
int wmain(int argc, wchar_t** argv) {
    try {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
        auto region = MakeCenteredRegion(1920, 1080, 320);
        Require(region.left == 640 && region.top == 220 && region.width == 640, "ROI mismatch");
        { HidInput device; Require(!device.Connected(), "Unexpected HID auto-connection"); device.Disconnect(); }
        ModelOutputContract contract; std::string error;
        Require(ModelOutputContract::ParseText("format=yolo8\naxes=channels_first\ncoordinates=pixels\n", contract, error), "Model contract failed");
        PreprocessingTest();
        if (argc > 1 && std::wstring(argv[1]) == L"--capture") CaptureTest(ScreenCapture::Backend::DesktopDuplication);
        if (argc > 1 && std::wstring(argv[1]) == L"--wgc") CaptureTest(ScreenCapture::Backend::WindowsGraphicsCapture);
        if (argc > 2 && std::wstring(argv[1]) == L"--model") DetectorTest(argv[2]);
        std::cout << "PASS region, model contract and passive HID lifecycle\n";
        return 0;
    } catch (const winrt::hresult_error& e) {
        std::cerr << winrt::to_string(e.message()) << "\n"; return 1;
    } catch (const std::exception& e) { std::cerr << e.what() << "\n"; return 1; }
}
