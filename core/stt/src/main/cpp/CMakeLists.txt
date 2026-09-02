cmake_minimum_required(VERSION 3.22.1)
project(mozhi_whisper CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

set(WHISPER_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp")
if(NOT EXISTS "${WHISPER_SRC_DIR}/CMakeLists.txt")
    include(FetchContent)
    FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG        v1.7.5
    )
    FetchContent_MakeAvailable(whispercpp)
    set(WHISPER_INCLUDE_DIR "${whispercpp_SOURCE_DIR}/include")
else()
    add_subdirectory(${WHISPER_SRC_DIR} whisper)
    set(WHISPER_INCLUDE_DIR "${WHISPER_SRC_DIR}/include")
endif()

add_library(mozhi-whisper SHARED whisper_jni.cpp)
target_include_directories(mozhi-whisper PRIVATE ${WHISPER_INCLUDE_DIR})
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(mozhi-whisper PRIVATE whisper ${log-lib} ${android-lib})
