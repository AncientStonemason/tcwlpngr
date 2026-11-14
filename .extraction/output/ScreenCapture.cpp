#include "ScreenCapture.hpp"

void ScreenCapture::CreateDevice()
{
        ComPtr<IDXGIFactory1> factory; HR(CreateDXGIFactory1(IID_PPV_ARGS(&factory)), "CreateDXGIFactory1");
        ComPtr<IDXGIAdapter1> chosenAdapter; ComPtr<IDXGIOutput> chosenOutput;
        DXGI_OUTPUT_DESC chosen{};
        wchar_t requested[128]{};
        const DWORD n = GetEnvironmentVariableW(L"VISION_CAPTURE_MONITOR", requested, 128);
        if (n >= 128) throw std::runtime_error("VISION_CAPTURE_MONITOR is too long");
        const HMONITOR primary = MonitorFromPoint(POINT{0,0}, MONITOR_DEFAULTTOPRIMARY);
        for (UINT ai = 0;; ++ai) {
            ComPtr<IDXGIAdapter1> adapter;
            const HRESULT ah = factory->EnumAdapters1(ai, &adapter);
            if (ah == DXGI_ERROR_NOT_FOUND) break; HR(ah, "EnumAdapters1");
            for (UINT oi = 0;; ++oi) {
                ComPtr<IDXGIOutput> output;
                const HRESULT oh = adapter->EnumOutputs(oi, &output);
                if (oh == DXGI_ERROR_NOT_FOUND) break; HR(oh, "EnumOutputs");
                DXGI_OUTPUT_DESC desc{}; HR(output->GetDesc(&desc), "Output description");
                if (!desc.AttachedToDesktop) continue;
                const bool matches = n ? CompareStringOrdinal(desc.DeviceName, -1, requested, -1, TRUE) == CSTR_EQUAL : desc.Monitor == primary;
                if (matches) { chosenAdapter = adapter; chosenOutput = output; chosen = desc; break; }
            }
            if (chosenOutput) break;
        }
        if (!chosenOutput) throw std::runtime_error("Requested/primary capture monitor is not available");
        if (chosen.Rotation != DXGI_MODE_ROTATION_IDENTITY && chosen.Rotation != DXGI_MODE_ROTATION_UNSPECIFIED)
            throw std::runtime_error("Portrait/rotated capture is not supported yet; select a landscape capture monitor");
        UINT flags = D3D11_CREATE_DEVICE_BGRA_SUPPORT;
#ifdef _DEBUG
        flags |= D3D11_CREATE_DEVICE_DEBUG;
#endif
        D3D_FEATURE_LEVEL levels[] = {D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0};
        HRESULT hr = D3D11CreateDevice(chosenAdapter.Get(), D3D_DRIVER_TYPE_UNKNOWN, nullptr, flags,
            levels, _countof(levels), D3D11_SDK_VERSION, &mDev, nullptr, &mCtx);
#ifdef _DEBUG
        if (hr == DXGI_ERROR_SDK_COMPONENT_MISSING) hr = D3D11CreateDevice(chosenAdapter.Get(), D3D_DRIVER_TYPE_UNKNOWN,
            nullptr, flags & ~D3D11_CREATE_DEVICE_DEBUG, levels, _countof(levels), D3D11_SDK_VERSION, &mDev, nullptr, &mCtx);
#endif
        HR(hr, "D3D11CreateDevice on capture adapter");
        ComPtr<ID3D10Multithread> mt; if (SUCCEEDED(mDev.As(&mt))) mt->SetMultithreadProtected(TRUE);
        HR(chosenOutput.As(&mOutput1), "QI IDXGIOutput1");
        chosenOutput.As(&mOutput5);
        mOutputLeft = chosen.DesktopCoordinates.left; mOutputTop = chosen.DesktopCoordinates.top;
        DXGI_ADAPTER_DESC1 adapter{}; HR(chosenAdapter->GetDesc1(&adapter), "Adapter description");
        char identity[256]{};
        std::snprintf(identity, sizeof(identity), "vendor=0x%04X device=0x%04X LUID=%08X:%08X output-origin=%ld,%ld",
            unsigned(adapter.VendorId), unsigned(adapter.DeviceId), unsigned(adapter.AdapterLuid.HighPart),
            unsigned(adapter.AdapterLuid.LowPart), chosen.DesktopCoordinates.left, chosen.DesktopCoordinates.top);
        mCaptureDeviceDetails = WideToUtf8(adapter.Description) + "; monitor=" + WideToUtf8(chosen.DeviceName) + "; " + identity;
    }

void ScreenCapture::EnsureCaptureTexture()
{
        const UINT width = mCaptureRegion.width;
        const UINT height = mCaptureRegion.height;
        if (!width || !height) return;
        if (mFrameTex && mFrameSRV && mFrameW == width && mFrameH == height) return;

        mFrameSRV.Reset();
        mFrameTex.Reset();

        D3D11_TEXTURE2D_DESC td{};
        td.Width = width; td.Height = height; td.MipLevels = td.ArraySize = 1;
        td.Format = mDuplFormat; td.SampleDesc = { 1,0 };
        td.Usage = D3D11_USAGE_DEFAULT; td.BindFlags = D3D11_BIND_SHADER_RESOURCE;
        HR(mDev->CreateTexture2D(&td, nullptr, &mFrameTex), "Create capture texture");
        HR(mDev->CreateShaderResourceView(mFrameTex.Get(), nullptr, &mFrameSRV),
            "Create capture SRV");
        mFrameW = width;
        mFrameH = height;
        mFrameNeedsFullCopy = true;
    }

HRESULT ScreenCapture::CreateDuplication()
{
        if (mOutput5) {
            const DXGI_FORMAT formats[] = { DXGI_FORMAT_B8G8R8A8_UNORM };
            const HRESULT hr = mOutput5->DuplicateOutput1(mDev.Get(), 0,
                _countof(formats), formats, &mDupl);
            if (SUCCEEDED(hr) || hr == E_ACCESSDENIED) return hr;
        }
        return mOutput1->DuplicateOutput(mDev.Get(), &mDupl);
    }

void ScreenCapture::ReleaseHeldFrame() noexcept
{
        if (mFrameHeld && mDupl) mDupl->ReleaseFrame();
        mFrameHeld = false;
    }

void ScreenCapture::StartDuplication()
{
        ReleaseHeldFrame();
        mDesktopSrvs.Reset();
        mDupl.Reset();
        DXGI_OUTPUT_DESC output{}; HR(mOutput1->GetDesc(&output), "Current output description");
        if (!output.AttachedToDesktop ||
            (output.Rotation != DXGI_MODE_ROTATION_IDENTITY && output.Rotation != DXGI_MODE_ROTATION_UNSPECIFIED))
            throw std::runtime_error("Capture output changed/rotated; select a landscape monitor and restart capture");
        mOutputLeft = output.DesktopCoordinates.left; mOutputTop = output.DesktopCoordinates.top;
        const HRESULT hr = CreateDuplication();
        if (hr == E_ACCESSDENIED) { Sleep(50); return; }
        HR(hr, "DuplicateOutput");

        DXGI_OUTDUPL_DESC d{}; mDupl->GetDesc(&d);
        if (d.ModeDesc.Width != mDeskW || d.ModeDesc.Height != mDeskH
            || d.ModeDesc.Format != mDuplFormat)
        {
            mDeskW = d.ModeDesc.Width; mDeskH = d.ModeDesc.Height;
            mDuplFormat = d.ModeDesc.Format;
            mFrameSRV.Reset(); mFrameTex.Reset();
            mFrameW = mFrameH = 0;
        }
        mDirectCapture = false;
        mFrameNeedsFullCopy = true;
        mDirectSrvSupported = true;
        mCaptureRegion = MakeCenteredRegion(mDeskW, mDeskH, mRadius);
        EnsureCaptureTexture();
    }

ID3D11ShaderResourceView* ScreenCapture::DesktopSrv(ID3D11Texture2D* src)
{
        return mDesktopSrvs.Get(mDev.Get(), src, mDirectSrvSupported);
    }

#ifdef VISION_ENABLE_WGC
bool ScreenCapture::CaptureWgcFrame()
{
    const double begin = NowMs();
    auto frame = mWgc.Take();
    if (!frame) { mCaptureMs = NowMs() - begin; return false; }
    mWgcFrame = std::move(frame);
    ++mCapturedFrameId;
    mCapturedFrameMs = mWgcFrame->receivedMs;
    mPresentedFrameMs = mWgcFrame->presentedMs;
    mAcquiredFrameMs = NowMs();
    auto* src = mWgcFrame->texture.Get();
    mCurrentSrv = DesktopSrv(src);
    mDirectCapture = bool(mCurrentSrv);
    if (!mCurrentSrv) {
        EnsureCaptureTexture(); CopyRegionFull(src);
        mFrameNeedsFullCopy = false; mCurrentSrv = mFrameSRV;
    }
    mCaptureMs = NowMs() - begin;
    return true;
}
#endif

bool ScreenCapture::CaptureFrame(UINT ms)
{
#ifdef VISION_ENABLE_WGC
        if(mUseWgc)return CaptureWgcFrame();
#endif
        if (!mDev) throw std::logic_error("Capture is not open");
        const double captureStart = NowMs();
        auto finishCapture = [this, captureStart]() {
            mCaptureMs.store(NowMs() - captureStart, std::memory_order_relaxed);
            };
        if (!mDupl) { StartDuplication(); finishCapture(); return false; }
        mCurrentSrv.Reset();
        ReleaseHeldFrame();
        ComPtr<IDXGIResource> res; DXGI_OUTDUPL_FRAME_INFO info{};
        HRESULT hr = mDupl->AcquireNextFrame(ms, &info, &res);
        const double acquiredAt=NowMs();
        if (hr == DXGI_ERROR_WAIT_TIMEOUT)
        {
            mDxgiTimeouts.fetch_add(1, std::memory_order_relaxed);
            finishCapture();
            return false;
        }
        if (hr == DXGI_ERROR_ACCESS_LOST || hr == DXGI_ERROR_INVALID_CALL)
        {
            if (++mCapFailures >= kMaxCaptureFailures) mCapFailures = 0;
            StartDuplication();
            finishCapture();
            return false;
        }
        mCapFailures = 0;
        HR(hr, "AcquireNextFrame");
        mFrameHeld = true;
        bool upd = false;
        if (info.LastPresentTime.QuadPart != 0)
        {
            if (info.AccumulatedFrames > 1)
                mDxgiMissedFrames.fetch_add(info.AccumulatedFrames - 1, std::memory_order_relaxed);
            ComPtr<ID3D11Texture2D> src;
            if (SUCCEEDED(res.As(&src)))
            {
                ++mCapturedFrameId;
                mAcquiredFrameMs=acquiredAt; mAccumulatedFrames=info.AccumulatedFrames;
                mCapturedFrameMs = NowMs();
                static const double qpcToMs=[] {
                    LARGE_INTEGER f{};QueryPerformanceFrequency(&f);
                    return f.QuadPart>0 ? 1000.0/double(f.QuadPart) : 0.0;
                }();
                mPresentedFrameMs=double(info.LastPresentTime.QuadPart)*qpcToMs;
                ID3D11ShaderResourceView* direct = DesktopSrv(src.Get());
                if (direct)
                {
                    mDirectCapture = true;
                    mCurrentSrv = direct;
                }
                else
                {
                    mDirectCapture = false;
                    EnsureCaptureTexture();
                    if (mWorkerCaptureMethod == CaptureMethod::DirectXFull || mFrameNeedsFullCopy)
                    {
                        CopyRegionFull(src.Get());
                        mFrameNeedsFullCopy = false;
                    }
                    else
                    {
                        CopyDirty(info, src.Get());
                    }
                }
                if (!mDirectCapture) mCurrentSrv = mFrameSRV;
                upd = true;
            }
        }
        finishCapture();
        return upd;
    }

void ScreenCapture::CopyDirty(const DXGI_OUTDUPL_FRAME_INFO& info, ID3D11Texture2D* src)
{
        UINT metaSize = info.TotalMetadataBufferSize;
        bool full = (metaSize == 0);
        auto& meta = mMetadata;
        if (!full)
        {
            if (meta.size() < metaSize) meta.resize(metaSize);
            UINT mb = 0, db = 0;
            if (FAILED(mDupl->GetFrameMoveRects(metaSize,
                reinterpret_cast<DXGI_OUTDUPL_MOVE_RECT*>(meta.data()), &mb))) full = true;
            if (!full && mb > 0) full = true;
            if (!full)
            {
                if (FAILED(mDupl->GetFrameDirtyRects(metaSize,
                    reinterpret_cast<RECT*>(meta.data()), &db))) full = true;
                else
                {
                    const RECT* rects = reinterpret_cast<const RECT*>(meta.data());
                    for (UINT i = 0; i < db / sizeof(RECT); ++i)
                    {
                        const LONG cl = mCaptureRegion.left;
                        const LONG ct = mCaptureRegion.top;
                        const LONG cr = cl + LONG(mCaptureRegion.width);
                        const LONG cb = ct + LONG(mCaptureRegion.height);
                        const LONG l = std::max<LONG>(rects[i].left, cl);
                        const LONG t = std::max<LONG>(rects[i].top, ct);
                        const LONG r = std::min<LONG>(rects[i].right, cr);
                        const LONG b = std::min<LONG>(rects[i].bottom, cb);
                        if (r <= l || b <= t) continue;
                        D3D11_BOX box{ UINT(l),UINT(t),0,UINT(r),UINT(b),1 };
                        mCtx->CopySubresourceRegion(mFrameTex.Get(), 0,
                            UINT(l - cl), UINT(t - ct), 0, src, 0, &box);
                    }
                }
            }
        }
        if (full) CopyRegionFull(src);
    }

void ScreenCapture::CopyRegionFull(ID3D11Texture2D* src)
{
        const LONG left = std::max<LONG>(mCaptureRegion.left, 0);
        const LONG top = std::max<LONG>(mCaptureRegion.top, 0);
        const LONG right = std::min<LONG>(mCaptureRegion.left + LONG(mCaptureRegion.width), LONG(mDeskW));
        const LONG bottom = std::min<LONG>(mCaptureRegion.top + LONG(mCaptureRegion.height), LONG(mDeskH));
        if (right <= left || bottom <= top) return;
        const D3D11_BOX box{ UINT(left), UINT(top), 0, UINT(right), UINT(bottom), 1 };
        mCtx->CopySubresourceRegion(mFrameTex.Get(), 0,
            UINT(left - mCaptureRegion.left), UINT(top - mCaptureRegion.top), 0, src, 0, &box);
    }

void ScreenCapture::Open(UINT radius, Backend backend, CaptureMethod method)
{
    if (!radius || radius > (std::numeric_limits<UINT>::max)() / 2)
        throw std::invalid_argument("Capture radius is out of range");
    Close();
    mRadius = radius;
    mWorkerCaptureMethod = method;
    try {
        CreateDevice();
        if (backend == Backend::WindowsGraphicsCapture) {
#ifdef VISION_ENABLE_WGC
            DXGI_OUTPUT_DESC desc{};
            HR(mOutput1->GetDesc(&desc), "Capture output");
            mWgc.Start(mDev.Get(), desc.Monitor, nullptr);
            mUseWgc = true;
            mDeskW = UINT(mWgc.Width()); mDeskH = UINT(mWgc.Height());
            mCaptureRegion = MakeCenteredRegion(mDeskW, mDeskH, mRadius);
            EnsureCaptureTexture();
#else
            throw std::runtime_error("Windows Graphics Capture was not enabled at build time");
#endif
        } else StartDuplication();
    } catch (...) { Close(); throw; }
}

void ScreenCapture::Close() noexcept
{
    mCurrentSrv.Reset();
#ifdef VISION_ENABLE_WGC
    mWgcFrame.reset(); mWgc.Stop(); mUseWgc = false;
#endif
    ReleaseHeldFrame();
    mDesktopSrvs.Reset();
    mDupl.Reset(); mFrameSRV.Reset(); mFrameTex.Reset();
    mOutput5.Reset(); mOutput1.Reset(); mCtx.Reset(); mDev.Reset();
    mDeskW = mDeskH = mFrameW = mFrameH = 0;
    mCaptureRegion = {}; mCapturedFrameId = 0;
    mCapturedFrameMs = mPresentedFrameMs = mAcquiredFrameMs = 0;
    mAccumulatedFrames = 0; mCapFailures = 0;
    mDirectCapture = false; mDirectSrvSupported = mFrameNeedsFullCopy = true;
}

ScreenCapture::FrameView ScreenCapture::View() const
{
    FrameView result;
    result.pixels = mCurrentSrv.Get();
    result.textureWidth = mDirectCapture ? mDeskW : mFrameW;
    result.textureHeight = mDirectCapture ? mDeskH : mFrameH;
    result.sampleRegion = mCaptureRegion;
    if (!mDirectCapture) result.sampleRegion.left = result.sampleRegion.top = 0;
    result.outputLeft = mOutputLeft + mCaptureRegion.left;
    result.outputTop = mOutputTop + mCaptureRegion.top;
    result.id = mCapturedFrameId;
    result.capturedMs = mCapturedFrameMs;
    result.presentedMs = mPresentedFrameMs;
    result.acquiredMs = mAcquiredFrameMs;
    result.accumulatedFrames = mAccumulatedFrames;
    return result;
}
