# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("spectrum")

# Treat most warnings as errors.
add_compile_options("-Wall" "-Werror")
add_compile_options("-Wno-unused-variable")
add_compile_options("-Wno-unused-but-set-variable")
add_compile_options("-Wno-unused-const-variable")
add_compile_options("-Wno-unused-function")
add_compile_options("-Wno-unused-label")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
file(GLOB_RECURSE VULKAN_SOURCES vulkan/*.cpp)
file(GLOB_RECURSE TETRA_SOURCES osmo-tetra/*.c)

add_library(${CMAKE_PROJECT_NAME} SHARED
        GLES.cpp
        Vulkan.cpp VulkanShiftDecimator.cpp ${VULKAN_SOURCES}
        Tetra.cpp ${TETRA_SOURCES})

# Disable Vulkan prototypes.
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE "VK_NO_PROTOTYPES")
# Additional include directories.
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE "${CMAKE_SOURCE_DIR}" "osmo-tetra")

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME} android log EGL GLESv3 vulkan-loader)

# Vulkan loader.
add_library(vulkan-loader SHARED vulkan-loader/Loader.cpp)
target_link_libraries(vulkan-loader android log)

# Vulkan loader hook.
if (CMAKE_ANDROID_ARCH STREQUAL "arm64")
    add_library(vulkan-loader-hook SHARED vulkan-loader/Hook.c)
    target_link_libraries(vulkan-loader-hook android log vulkan-loader)
    target_link_options(vulkan-loader-hook PRIVATE -z global)
endif()
