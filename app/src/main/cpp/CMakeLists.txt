cmake_minimum_required(VERSION 3.22.1)

project(android_llama_jni LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

if(ANDROID)
    set(OPENCL_HEADERS_ROOT
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/OpenCL-Headers")
    set(OPENCL_ICD_LOADER_HEADERS_DIR
        "${OPENCL_HEADERS_ROOT}" CACHE PATH "" FORCE)
    set(OPENCL_ICD_LOADER_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(ENABLE_OPENCL_LAYERS OFF CACHE BOOL "" FORCE)
    set(ENABLE_OPENCL_LAYERINFO OFF CACHE BOOL "" FORCE)
    set(OPENCL_HEADERS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(OPENCL_HEADERS_BUILD_CXX_TESTS OFF CACHE BOOL "" FORCE)
    add_subdirectory(
        third_party/OpenCL-ICD-Loader
        build-opencl-loader
        EXCLUDE_FROM_ALL)

    list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)

    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    set(Vulkan_INCLUDE_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/Vulkan-Headers/include"
        CACHE PATH "" FORCE)
    set(SPIRV-Headers_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/cmake"
        CACHE PATH "" FORCE)
    if(CMAKE_HOST_WIN32)
        set(Vulkan_GLSLC_EXECUTABLE
            "${ANDROID_NDK}/shader-tools/windows-x86_64/glslc.exe"
            CACHE FILEPATH "" FORCE)
    elseif(CMAKE_HOST_APPLE)
        set(Vulkan_GLSLC_EXECUTABLE
            "${ANDROID_NDK}/shader-tools/darwin-x86_64/glslc"
            CACHE FILEPATH "" FORCE)
    else()
        set(Vulkan_GLSLC_EXECUTABLE
            "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc"
            CACHE FILEPATH "" FORCE)
    endif()

endif()

add_subdirectory(third_party/llama.cpp build-llama)

add_library(android_llama SHARED llama_jni.cpp)

target_link_options(android_llama PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(android_llama PRIVATE
    third_party/llama.cpp/include
    third_party/llama.cpp/ggml/include
)

target_link_libraries(android_llama PRIVATE llama android log)
