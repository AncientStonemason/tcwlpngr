#pragma once
#include <windows.h>
#include <atomic>
using DiagnosticSink = void (*)(const char*, const char*) noexcept;
inline std::atomic<DiagnosticSink> gDiagnosticSink{nullptr};
inline void DevLog(const char* category, const char* message) noexcept {
    if (auto sink = gDiagnosticSink.load(std::memory_order_acquire)) sink(category, message);
}
