// Based on https://github.com/bylaws/libadrenotools

#include "Loader.h"
#include "../vulkan/Utils.h"

#include <android/dlext.h>
#include <asm/unistd.h>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <string>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static std::string nativeLibPath;
static android_namespace_t *loaderNamespace;
static Vulkan::Loader::LibrarySubstitutions libSubstitutions;

static bool initialize();
static void *load(const char *name, android_namespace_t *ns, bool memoryMapped);

namespace Vulkan::Loader {
    void *loadVulkan(LibrarySubstitutions &&driverSubstitutions) {
#ifdef __aarch64__
        if (!driverSubstitutions.empty()) {
            if (initialize()) {
                libSubstitutions = std::move(driverSubstitutions);
                load("libvulkan-loader-hook.so", loaderNamespace, false);
                return load("/system/lib64/libvulkan.so", loaderNamespace, true);
            }
        }
#endif
        return dlopen("libvulkan.so", RTLD_LAZY);
    }
}

static constexpr uint64_t ANDROID_NAMESPACE_TYPE_SHARED = 2;

using loader_android_get_exported_namespace_t = android_namespace_t *(*)(const char *name);
using loader_android_create_namespace_t = android_namespace_t *(*)(const char *name, const char *ld_library_path, const char *default_library_path,
                                                                   uint64_t type, const char *permitted_when_isolated_path, void *parent_namespace,
                                                                   const void *caller);

static loader_android_get_exported_namespace_t loader_android_get_exported_namespace;
static loader_android_create_namespace_t loader_android_create_namespace;

static void *align(void *ptr) {
    return (void *) ((uintptr_t) ptr & ~(getpagesize() - 1));
}

static bool initialize() {
    // ARM64 specific function walking to locate the internal dlopen handler
    union BranchLinked {
        uint32_t raw;

        struct {
            int32_t offset: 26; //!< 26-bit branch offset
            uint8_t sig: 6;  //!< 6-bit signature
        };

        bool verify() const {
            return sig == 0x25;
        }
    };

    static_assert(sizeof(BranchLinked) == 4, "BranchLinked is wrong size");

    // Some devices ship with --X mapping for exexecutables so work around that
    mprotect(align((void *) &dlopen), getpagesize(), PROT_WRITE | PROT_READ | PROT_EXEC);

    // dlopen is just a wrapper for loader_dlopen that passes the return address
    // as the third arg hence we can just walk it to find loader_dlopen
    auto bl = (BranchLinked *) &dlopen;
    while (!bl->verify()) { bl++; }

    using loader_dlopen_t = void *(*)(const char *, int, const void *);
    auto loader_dlopen = (loader_dlopen_t) (bl + bl->offset);
    VK_CHECK(loader_dlopen != nullptr);

    // Unprotect the loader_dlopen function to remove the BTI attribute
    // (since this is an internal function that isn't intended to be jumped indirectly to)
    mprotect(align(&loader_dlopen), getpagesize(), PROT_WRITE | PROT_READ | PROT_EXEC);

    // Passing dlopen as a caller address tricks the linker into using the internal unrestricted namespace,
    // letting us access libraries that are normally forbidden in the classloader namespace imposed on apps
    auto ldHandle = loader_dlopen("ld-android.so", RTLD_LAZY, (void *) &dlopen);
    VK_CHECK(ldHandle != nullptr);

    using loader_android_link_namespaces_all_libs_t = bool (*)(void *, void *);
    auto loader_android_link_namespaces_all_libs = (loader_android_link_namespaces_all_libs_t) dlsym(ldHandle, "__loader_android_link_namespaces_all_libs");
    VK_CHECK(loader_android_link_namespaces_all_libs != nullptr);

    auto libdlHandle = loader_dlopen("libdl_android.so", RTLD_LAZY, (void *) &dlopen);
    VK_CHECK(libdlHandle != nullptr);

    loader_android_create_namespace = (loader_android_create_namespace_t) dlsym(libdlHandle, "__loader_android_create_namespace");
    VK_CHECK(loader_android_create_namespace != nullptr);

    loader_android_get_exported_namespace = (loader_android_get_exported_namespace_t) dlsym(libdlHandle, "__loader_android_get_exported_namespace");
    VK_CHECK(loader_android_get_exported_namespace != nullptr);

    Dl_info info;
    dladdr((void *) initialize, &info);
    const std::string fname(info.dli_fname);
    nativeLibPath = fname.substr(0, fname.rfind('/'));

    const char *namespaceName = "vulkan-loader";
    LOGD("Creating namespace %s with library path %s", namespaceName, nativeLibPath.c_str());

    loaderNamespace = loader_android_create_namespace(namespaceName, nativeLibPath.c_str(), nullptr, ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr, nullptr);
    VK_CHECK(loaderNamespace != nullptr);

    void *defaultNamespace = loader_android_create_namespace("default0", nullptr, nullptr, ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr, (void *) &dlopen);
    VK_CHECK(loader_android_link_namespaces_all_libs(loaderNamespace, defaultNamespace));

    return true;
}

static void *load(const char *name, android_namespace_t *ns, bool memoryMapped) {
    LOGD("Loading %s", name);

    android_dlextinfo extInfo{
            .flags = ANDROID_DLEXT_USE_NAMESPACE,
            .library_namespace = ns
    };

    if (memoryMapped) {
        extInfo.flags |= ANDROID_DLEXT_USE_LIBRARY_FD;
        extInfo.library_fd = (int) syscall(__NR_memfd_create, name, 0);
        VK_CHECK_NULL(errno != ENOSYS);
        VK_CHECK_NULL(extInfo.library_fd > 0);
        struct stat fstat{};
        VK_CHECK_NULL(stat(name, &fstat) == 0);
        VK_CHECK_NULL(ftruncate(extInfo.library_fd, (off_t) fstat.st_size) == 0);
        auto mappedFile = (uint8_t *) mmap(nullptr, fstat.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, extInfo.library_fd, 0);
        VK_CHECK_NULL(mappedFile != MAP_FAILED);
        int file = open(name, O_RDONLY);
        VK_CHECK_NULL(file != -1);
        VK_CHECK_NULL(read(file, mappedFile, fstat.st_size) == fstat.st_size);
        close(file);

        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/proc/self/fd/%d", extInfo.library_fd);
        name = path;
    }

    return android_dlopen_ext(name, RTLD_GLOBAL, &extInfo);
}

extern "C" void *hook_android_dlopen_ext(const char *filename, int flags, const android_dlextinfo *extinfo) {
    LOGD("Loading %s", filename);

    for (const auto [source, destination]: libSubstitutions) {
        if (strcmp(filename, source) == 0) {
            LOGD("Loading %s instead of %s", destination, source);

            android_dlextinfo loaderInfo(*extinfo);
            loaderInfo.library_namespace = loader_android_create_namespace(filename, nativeLibPath.c_str(), nullptr,
                                                                           ANDROID_NAMESPACE_TYPE_SHARED, nullptr,
                                                                           extinfo->library_namespace, nullptr);
            VK_CHECK_NULL(loaderInfo.library_namespace);

            void *handle = android_dlopen_ext(destination, flags, &loaderInfo);
            if (handle == nullptr) {
                LOGD("%s", dlerror());
            }
            return handle;
        }
    }

    return android_dlopen_ext(filename, flags, extinfo);
}

extern "C" void *hook_android_load_sphal_library(const char *filename, int flags) {
    for (const char *name: {"sphal", "vendor", "default"}) {
        if (auto ns = loader_android_get_exported_namespace(name)) {
            const android_dlextinfo extinfo{
                    .flags = ANDROID_DLEXT_USE_NAMESPACE,
                    .library_namespace = ns,
            };
            return hook_android_dlopen_ext(filename, flags, &extinfo);
        }
    }
    return nullptr;
}
