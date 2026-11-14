@echo off
setlocal
cd /d "%~dp0"
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (echo Visual Studio C++ Build Tools are required. & exit /b 1)
for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSROOT=%%i"
if not defined VSROOT (echo Visual Studio C++ toolchain not found. & exit /b 1)
call "%VSROOT%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1
set "EXTRA="
set "LINKEXTRA="
if /i "%~1"=="trt" (
  if not defined TENSORRT_ROOT (echo Set TENSORRT_ROOT to TensorRT 10.11. & exit /b 1)
  if not defined CUDA_ROOT (echo Set CUDA_ROOT to CUDA 11.8. & exit /b 1)
  set EXTRA=/DVISION_ENABLE_TRT /DVISION_ENABLE_D3D_INTEROP /I"%TENSORRT_ROOT%\include" /I"%CUDA_ROOT%\include"
  set LINKEXTRA=/LIBPATH:"%TENSORRT_ROOT%\lib" /LIBPATH:"%CUDA_ROOT%\lib\x64"
)
if not exist build mkdir build
cl /nologo /std:c++20 /EHsc /utf-8 /MD /O2 /W3 /DNOMINMAX /DWIN32_LEAN_AND_MEAN /DVISION_ENABLE_WGC /I. %EXTRA% /c ScreenCapture.cpp ObjectDetector.cpp /Fo:build\
if errorlevel 1 exit /b 1
lib /nologo /OUT:build\VisionCore.lib build\ScreenCapture.obj build\ObjectDetector.obj
if errorlevel 1 exit /b 1
cl /nologo /std:c++20 /EHsc /utf-8 /MD /O2 /W3 /DNOMINMAX /DWIN32_LEAN_AND_MEAN /DVISION_ENABLE_WGC /I. %EXTRA% tests\smoke.cpp build\VisionCore.lib /Fo:build\smoke.obj /Fe:build\core_smoke.exe /link user32.lib %LINKEXTRA%
exit /b %errorlevel%
