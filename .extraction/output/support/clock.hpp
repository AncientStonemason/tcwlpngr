#pragma once
#include "support/platform.hpp"
inline double PerfNowMs()
{
    static const double scale = [] {
        LARGE_INTEGER f{}; QueryPerformanceFrequency(&f);
        return f.QuadPart ? 1000.0 / double(f.QuadPart) : 0.0;
        }();
    LARGE_INTEGER t{}; QueryPerformanceCounter(&t);
    return double(t.QuadPart) * scale;
}
