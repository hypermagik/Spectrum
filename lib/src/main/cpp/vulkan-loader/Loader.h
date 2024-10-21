#pragma once

#include <map>

namespace Vulkan::Loader {
    using LibrarySubstitutions = std::map<const char *, const char *>;
    void *loadVulkan(LibrarySubstitutions &&driverSubstitutions);
}
