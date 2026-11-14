#pragma once
#include <exception>
#include <utility>
namespace vision {
template<class Work, class Failure, class Exit>
void GuardWorker(Work&& work, Failure&& failure, Exit&& exit) noexcept {
    try { work(); }
    catch (const std::exception& e) { try { failure(e.what()); } catch (...) {} }
    catch (...) { try { failure("Unknown worker exception"); } catch (...) {} }
    try { exit(); } catch (...) {}
}
}
