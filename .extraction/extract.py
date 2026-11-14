from pathlib import Path
import re
src=Path('.extraction/source'); dst=Path('.extraction/output')
dst.mkdir(parents=True,exist_ok=True)
def read(n):return (src/n).read_text(encoding='utf-8-sig')
mapping={
'modules/core/platform.hpp':'support/platform.hpp','modules/core/runtime.hpp':'support/runtime.hpp',
'modules/core/detection.hpp':'support/detection.hpp','modules/core/timing.hpp':'support/timing.hpp',
'modules/core/clock.hpp':'support/clock.hpp','modules/core/utilities.hpp':'support/utilities.hpp',
'modules/inference/detector.hpp':'ObjectDetector.hpp','modules/inference/detector_state.hpp':'support/detector_state.hpp',
'gpu_preprocessor.hpp':'FrameProcessor.hpp','gpu_infer.hpp':'support/inference_backend.hpp',
'model_contract.hpp':'support/model_contract.hpp','dev_diagnostics.hpp':'diagnostics.hpp',
'ort_backend.hpp':'ort_backend.hpp','worker_guard.hpp':'worker_guard.hpp',
'win32_overlapped_io.hpp':'overlapped_io.hpp','atomic_file.hpp':'handle.hpp',
'desktop_srv_cache.hpp':'support/desktop_srv_cache.hpp','wgc_capture.hpp':'support/wgc_capture.hpp',
'latest_frame.hpp':'latest_frame.hpp'}
def neutral(s):
 for old,new in sorted(mapping.items(),key=lambda x:-len(x[0])):s=s.replace('"'+old+'"','"'+new+'"')
 s=re.sub(r'\bDetector\b','ObjectDetector',s)
 s=s.replace('GpuPreprocessor','FrameProcessor').replace('ArduinoHidMouse','HidInput')
 s=s.replace('ALBION','VISION').replace('Albion','Vision').replace('albion','vision')
 s=s.replace('AIM_','VISION_').replace('Aim','Backend')
 # Drop application commentary; retain copyright/license notices verbatim.
 s='\n'.join(l for l in s.splitlines() if not l.lstrip().startswith('//') or re.search('copyright|license|spdx',l,re.I))+'\n'
 s=s.replace('use recovery in F8','restart capture').replace('use F8 recovery','restart capture')
 return s
def write(n,s,convert=True):
 q=dst/n;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(neutral(s) if convert else s,encoding='utf-8')
for a,b in mapping.items():
 if a in ['modules/core/platform.hpp','modules/core/utilities.hpp','dev_diagnostics.hpp','atomic_file.hpp']:continue
 write(b,read(a))
# Backend-local includes and public headers need their own platform declarations.
for name in ['FrameProcessor.hpp','support/inference_backend.hpp']:
 q=dst/name;s=q.read_text();s=s.replace('#pragma once','#pragma once\n#include "'+('support/' if '/' not in name else '')+'platform.hpp"');q.write_text(s)
platform='''#pragma once
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <unknwn.h>
#include <d3d11_1.h>
#include <d3d10.h>
#include <dxgi1_6.h>
#include <d3dcompiler.h>
#include <wrl/client.h>
#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.AI.MachineLearning.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <climits>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cwctype>
#include <cwchar>
#include <iterator>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>
#include "worker_guard.hpp"
#include "diagnostics.hpp"
#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "d3dcompiler.lib")
#pragma comment(lib, "windowsapp.lib")
using Microsoft::WRL::ComPtr;
namespace WinML = winrt::Windows::AI::MachineLearning;
struct __declspec(uuid("5b0d3235-4dba-4d44-865e-8f1d0e4fd04d")) __declspec(novtable)
IMemoryBufferByteAccess : ::IUnknown {
    virtual HRESULT __stdcall GetBuffer(uint8_t** value, uint32_t* capacity) = 0;
};
'''
write('support/platform.hpp',platform,False)
handle=read('atomic_file.hpp');handle=handle[handle.index('class Handle {'):handle.index('// FIX-V11.1-LOCKFILES')]
write('support/handle.hpp','#pragma once\n#include <windows.h>\n#include <utility>\nnamespace vision {\n'+handle+'}\n')
write('support/diagnostics.hpp','''#pragma once
#include <windows.h>
#include <atomic>
using DiagnosticSink = void (*)(const char*, const char*) noexcept;
inline std::atomic<DiagnosticSink> gDiagnosticSink{nullptr};
inline void DevLog(const char* category, const char* message) noexcept {
    if (auto sink = gDiagnosticSink.load(std::memory_order_acquire)) sink(category, message);
}
''',False)
u=read('modules/core/utilities.hpp')
def func(s,name):
 a=s.index('inline ',s.index(name)-30);b=s.index('{',a);depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]+'\n'
write('support/utilities.hpp','#pragma once\n#include "platform.hpp"\n#include "handle.hpp"\n'+
 '\n'.join(func(u,n) for n in ['void HR','std::wstring Utf8ToWide','std::string WideToUtf8','bool ReadTextFileUtf8']))
d=read('modules/inference/detector.cpp')
d=re.sub(r'        albion::performance::telemetry.Result\(inferenceMs,.*?timing.accumulatedFrames\);\n','',d,flags=re.S)
d=d.replace('AlbionVariant::Get().maxDetections','100u').replace('AlbionVariant::Get().nmsIou','0.45f')
d=d.replace('AppendTextFileUtf8(AlbionDataPath(L"winml_errors.txt"), record);','DevLog("INFERENCE", record.c_str());')
d=d.replace('.albion-model','.model-contract')
write('ObjectDetector.cpp',d)
# Neutral namespace, unchanged RawHID framing; firmware greeting is explicit.
h=read('arduino_hid.hpp')
h=h.replace('#include "win32_overlapped_io.hpp"','#include "support/overlapped_io.hpp"').replace('#include "worker_guard.hpp"','#include "support/worker_guard.hpp"').replace('#include "dev_diagnostics.hpp"','#include "support/diagnostics.hpp"')
h=h.replace('#include <windows.h>','#include "support/platform.hpp"')
h=h.replace('ArduinoHidMouse() {','explicit ArduinoHidMouse(std::string handshake = {}) : mGreeting(std::move(handshake)) {')
h=h.replace('const char hello[] = "ALBION";','if (mGreeting.empty()) return false;\n        const auto& hello = mGreeting;')
h=h.replace('i < sizeof(hello) - 1','i < hello.size()')
h=h.replace('HANDLE mDevice = INVALID_HANDLE_VALUE;','const std::string mGreeting;\n    HANDLE mDevice = INVALID_HANDLE_VALUE;')
write('HidInput.hpp',h)
# Region geometry is independent of application behavior.
s=read('basic_winapi_aim.hpp');a=s.index('struct CaptureRegion');b=s.index('\n};',a)+3
region=s[a:b]+'\n'+func(s,'CaptureRegion MakeCenteredRegion')
write('support/capture_region.hpp','#pragma once\n#include "platform.hpp"\n'+region)
# Detach capture from the application and return an owned view to its caller.
c=read('modules/capture/capture.cpp').replace('#include "modules/app/app.hpp"','#include "ScreenCapture.hpp"')
c=c.replace('App::','ScreenCapture::').replace('mAim.Config().radius','mRadius').replace('const double acquiredAt=PerfNowMs();','const double acquiredAt=NowMs();')
start=c.index('UINT ScreenCapture::CaptureTimeoutMs() const')
end=c.index('ID3D11ShaderResourceView* ScreenCapture::DesktopSrv',start)
c=c[:start]+c[end:]
start=c.index('#ifdef AIM_ENABLE_WGC\nbool ScreenCapture::CaptureWgcFrame()')
end=c.index('\nbool ScreenCapture::CaptureFrame',start)
c=c[:start]+'''#ifdef VISION_ENABLE_WGC
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
''' +c[end:]
start=c.index('                    if (mDetectEnabled')
end=c.index('\n                }\n                else',start)
c=c[:start]+'                    mCurrentSrv = direct;'+c[end:]
c=c.replace('                upd = true;','                if (!mDirectCapture) mCurrentSrv = mFrameSRV;\n                upd = true;')
c=c.replace('        static std::vector<uint8_t> meta;','        auto& meta = mMetadata;')
c=c.replace('        ReleaseHeldFrame();\n        ComPtr<IDXGIResource>', '        mCurrentSrv.Reset();\n        ReleaseHeldFrame();\n        ComPtr<IDXGIResource>')
write('ScreenCapture.cpp',c)
print('Generated extracted files:',len(list(dst.rglob('*.*'))))
