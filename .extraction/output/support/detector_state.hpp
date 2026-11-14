#pragma once

namespace vision
{
    constexpr bool DetectorIsLoading(bool exited, bool ready, bool failed) noexcept
    {
        return !exited && !ready && !failed;
    }
}
