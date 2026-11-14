#pragma once
#include "platform.hpp"
#include "handle.hpp"
inline void HR(HRESULT hr, const char* what)
{
    if (FAILED(hr))
    {
        char buf[32];
        sprintf_s(buf, "0x%08X", unsigned(hr));
        throw std::runtime_error(std::string(what) + " failed, hr=" + buf);
    }
}

inline std::wstring Utf8ToWide(const std::string& s)
{
    if (s.empty()) return {};
    if (s.size() > size_t((std::numeric_limits<int>::max)())) return {};
    const int inputLength = static_cast<int>(s.size());
    const int len = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS,
        s.data(), inputLength, nullptr, 0);
    if (len <= 0) return {};
    std::wstring result(size_t(len), L'\0');
    if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS,
        s.data(), inputLength, result.data(), len) != len) return {};
    return result;
}

inline std::string WideToUtf8(const std::wstring& w)
{
    if (w.empty()) return {};
    if (w.size() > size_t((std::numeric_limits<int>::max)())) return {};
    const int inputLength = static_cast<int>(w.size());
    const int len = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
        w.data(), inputLength, nullptr, 0, nullptr, nullptr);
    if (len <= 0) return {};
    std::string result(size_t(len), '\0');
    if (WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
        w.data(), inputLength, result.data(), len, nullptr, nullptr) != len) return {};
    return result;
}

inline bool ReadTextFileUtf8(const std::wstring& path, std::string& out)
{
    out.clear();
    vision::Handle h(CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_DELETE, nullptr,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_SEQUENTIAL_SCAN, nullptr));
    if (!h.Valid()) return false;
    LARGE_INTEGER size{};
    if (!GetFileSizeEx(h.Get(), &size) || size.QuadPart < 0 || size.QuadPart > 4 * 1024 * 1024)
    {
        return false;
    }
    out.resize(size_t(size.QuadPart));
    size_t offset = 0;
    bool ok = true;
    while (offset < out.size())
    {
        const DWORD chunk = DWORD(std::min<size_t>(out.size() - offset, 1u << 20));
        DWORD readBytes = 0;
        if (!ReadFile(h.Get(), out.data() + offset, chunk, &readBytes, nullptr) || readBytes == 0)
        {
            ok = false;
            break;
        }
        offset += readBytes;
    }
    if (!ok) out.clear();
    return ok;
}
