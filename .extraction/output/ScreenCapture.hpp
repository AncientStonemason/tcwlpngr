#pragma once
#include "support/platform.hpp"
#include "support/capture_region.hpp"
#include "support/desktop_srv_cache.hpp"
#include "support/clock.hpp"
#include "support/utilities.hpp"
#ifdef VISION_ENABLE_WGC
#include "support/wgc_capture.hpp"
#endif

class ScreenCapture {
public:
    enum class Backend { DesktopDuplication, WindowsGraphicsCapture };
    enum class CaptureMethod { DirtyRects, DirectXFull };
    struct FrameView {
        ID3D11ShaderResourceView* pixels = nullptr;
        UINT textureWidth = 0, textureHeight = 0;
        CaptureRegion sampleRegion{};
        LONG outputLeft = 0, outputTop = 0;
        std::uint64_t id = 0;
        double capturedMs = 0, presentedMs = 0, acquiredMs = 0;
        std::uint32_t accumulatedFrames = 0;
    };

    ScreenCapture() = default;
    ~ScreenCapture() { Close(); }
    ScreenCapture(const ScreenCapture&) = delete;
    ScreenCapture& operator=(const ScreenCapture&) = delete;
    void Open(UINT radius = 320, Backend backend = Backend::DesktopDuplication,
              CaptureMethod method = CaptureMethod::DirectXFull);
    void Close() noexcept;
    bool CaptureFrame(UINT timeoutMs = 8);
    // The view borrows its pixels until the next capture, close, or restart.
    // Submit all GPU work before that operation.
    FrameView View() const;
    ID3D11Device* Device() const { return mDev.Get(); }
    ID3D11DeviceContext* Context() const { return mCtx.Get(); }
    const std::string& DeviceDetails() const { return mCaptureDeviceDetails; }
    double CaptureMs() const { return mCaptureMs.load(); }
    std::uint64_t Timeouts() const { return mDxgiTimeouts.load(); }
    std::uint64_t MissedFrames() const { return mDxgiMissedFrames.load(); }

private:
    static double NowMs() { return PerfNowMs(); }
    void CreateDevice();
    void EnsureCaptureTexture();
    HRESULT CreateDuplication();
    void ReleaseHeldFrame() noexcept;
    void StartDuplication();
    ID3D11ShaderResourceView* DesktopSrv(ID3D11Texture2D*);
    void CopyDirty(const DXGI_OUTDUPL_FRAME_INFO&, ID3D11Texture2D*);
    void CopyRegionFull(ID3D11Texture2D*);
#ifdef VISION_ENABLE_WGC
    bool CaptureWgcFrame();
    WgcCapture mWgc;
    std::shared_ptr<WgcCapture::Frame> mWgcFrame;
    bool mUseWgc = false;
#endif
    UINT mRadius = 320;
    CaptureMethod mWorkerCaptureMethod = CaptureMethod::DirectXFull;
    ComPtr<ID3D11Device> mDev;
    ComPtr<ID3D11DeviceContext> mCtx;
    ComPtr<IDXGIOutput1> mOutput1;
    ComPtr<IDXGIOutput5> mOutput5;
    ComPtr<IDXGIOutputDuplication> mDupl;
    ComPtr<ID3D11Texture2D> mFrameTex;
    ComPtr<ID3D11ShaderResourceView> mFrameSRV, mCurrentSrv;
    DesktopSrvCache mDesktopSrvs;
    CaptureRegion mCaptureRegion{};
    DXGI_FORMAT mDuplFormat = DXGI_FORMAT_B8G8R8A8_UNORM;
    UINT mDeskW = 0, mDeskH = 0, mFrameW = 0, mFrameH = 0;
    LONG mOutputLeft = 0, mOutputTop = 0;
    bool mFrameHeld = false, mDirectSrvSupported = true, mDirectCapture = false;
    bool mFrameNeedsFullCopy = true;
    unsigned mCapFailures = 0;
    static constexpr unsigned kMaxCaptureFailures = 5;
    std::uint64_t mCapturedFrameId = 0;
    double mCapturedFrameMs = 0, mPresentedFrameMs = 0, mAcquiredFrameMs = 0;
    std::uint32_t mAccumulatedFrames = 0;
    std::atomic<double> mCaptureMs{0};
    std::atomic<std::uint64_t> mDxgiTimeouts{0}, mDxgiMissedFrames{0};
    std::string mCaptureDeviceDetails;
    std::vector<uint8_t> mMetadata;
};
