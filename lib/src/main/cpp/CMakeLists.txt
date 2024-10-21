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
        Vulkan.cpp ${VULKAN_SOURCES} VulkanShiftDecimator.cpp
        Tetra.cpp ${TETRA_SOURCES})

# Disable Vulkan prototypes.
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE "VK_NO_PROTOTYPES")

# Additional include directories.
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE "${CMAKE_SOURCE_DIR}" "osmo-tetra")

# Treat most warnings as errors.
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wall" "-Werror")
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wno-unused-variable")
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wno-unused-but-set-variable")
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wno-unused-const-variable")
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wno-unused-function")
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wno-unused-label")

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME} android log EGL GLESv3)
