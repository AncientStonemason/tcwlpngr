#pragma once
#include <windows.h>
#include <utility>
namespace vision {
class Handle {
    HANDLE value_ = INVALID_HANDLE_VALUE;
public:
    explicit Handle(HANDLE h = INVALID_HANDLE_VALUE) noexcept : value_(h) {}
    ~Handle() { Reset(); }
    Handle(const Handle&) = delete; Handle& operator=(const Handle&) = delete;
    HANDLE Get() const noexcept { return value_; }
    HANDLE Release() noexcept { return std::exchange(value_, INVALID_HANDLE_VALUE); }
    bool Valid() const noexcept { return value_ && value_ != INVALID_HANDLE_VALUE; }
    void Reset(HANDLE h = INVALID_HANDLE_VALUE) noexcept {
        if (Valid()) CloseHandle(value_);
        value_ = h;
    }
};
}
