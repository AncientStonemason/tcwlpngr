#pragma once
#include "platform.hpp"
struct CaptureRegion {
    LONG left = 0;
    LONG top = 0;
    UINT width = 0;
    UINT height = 0;

    float CenterX() const noexcept { return float(left) + float(width) * 0.5f; }
    float CenterY() const noexcept { return float(top) + float(height) * 0.5f; }
};
inline CaptureRegion MakeCenteredRegion(
    UINT desktopW,
    UINT desktopH,
    UINT radius) noexcept {

    const UINT wanted = std::max<UINT>(2u, radius * 2u);
    const UINT width = std::min<UINT>(wanted, desktopW);
    const UINT height = std::min<UINT>(wanted, desktopH);

    CaptureRegion r;
    r.width = width;
    r.height = height;
    r.left = LONG((desktopW - width) / 2u);
    r.top = LONG((desktopH - height) / 2u);
    return r;
}
