cmake_minimum_required(VERSION 3.22.1)
project(bookreader_pockettts LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BOOKREADER_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../..")
set(POCKET_SOURCE "${CMAKE_CURRENT_LIST_DIR}/vendor/PocketTTS.cpp/pocket_tts.cpp")
set(ORT_LIBRARY "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so")
set(ORT_HEADERS "${CMAKE_CURRENT_LIST_DIR}/../third_party/onnxruntime/include")

if(NOT EXISTS "${ORT_LIBRARY}" OR NOT EXISTS "${ORT_HEADERS}/onnxruntime/core/session/onnxruntime_cxx_api.h")
    message(FATAL_ERROR "PocketTTS native dependencies are missing. Run scripts/prepare_pockettts_native_deps.sh.")
endif()

include(FetchContent)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_PROTOBUF_PROVIDER "internal" CACHE STRING "" FORCE)
set(SPM_ABSL_PROVIDER "internal" CACHE STRING "" FORCE)
set(SPM_ENABLE_NFKC_COMPILE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    sentencepiece
    GIT_REPOSITORY https://github.com/google/sentencepiece.git
    GIT_TAG v0.2.1
    GIT_SHALLOW TRUE
)
FetchContent_Declare(
    dr_libs
    GIT_REPOSITORY https://github.com/mackron/dr_libs.git
    GIT_TAG 50bb723e6a459dbb781e26cefee4fd9ca6714d6a
)
FetchContent_MakeAvailable(sentencepiece dr_libs)

foreach(tool spm_decode spm_encode spm_export_vocab spm_normalize spm_train)
    if(TARGET ${tool})
        target_link_libraries(${tool} log)
    endif()
endforeach()

add_library(pockettts_jni SHARED
    vendor/PocketTTS.cpp/pocket_tts.cpp
    jni_bridge.cpp
)
target_compile_definitions(pockettts_jni PRIVATE PTT_SHARED_LIB)
target_compile_options(pockettts_jni PRIVATE -O3 -ffile-prefix-map=${BOOKREADER_ROOT}=.)
target_link_options(pockettts_jni PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_include_directories(pockettts_jni PRIVATE
    "${ORT_HEADERS}"
    "${ORT_HEADERS}/onnxruntime/core/session"
    "${sentencepiece_SOURCE_DIR}/src"
    "${dr_libs_SOURCE_DIR}"
)
target_link_libraries(pockettts_jni PRIVATE "${ORT_LIBRARY}" sentencepiece-static log android)
