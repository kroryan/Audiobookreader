cmake_minimum_required(VERSION 3.22)
project(bookreader_desktop_pocket LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
find_package(JNI REQUIRED)
find_package(Threads REQUIRED)
set(APP_NATIVE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../app/src/main")
include(FetchContent)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_NFKC_COMPILE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(sentencepiece GIT_REPOSITORY https://github.com/google/sentencepiece.git GIT_TAG v0.2.1 GIT_SHALLOW TRUE)
FetchContent_Declare(dr_libs GIT_REPOSITORY https://github.com/mackron/dr_libs.git GIT_TAG 50bb723e6a459dbb781e26cefee4fd9ca6714d6a)
FetchContent_MakeAvailable(sentencepiece dr_libs)
add_library(pockettts_jni SHARED
    "${APP_NATIVE}/cpp/vendor/PocketTTS.cpp/pocket_tts.cpp"
    "${APP_NATIVE}/cpp/jni_bridge.cpp")
target_compile_definitions(pockettts_jni PRIVATE PTT_SHARED_LIB)
target_include_directories(pockettts_jni PRIVATE ${JNI_INCLUDE_DIRS}
    "${APP_NATIVE}/third_party/onnxruntime/include/onnxruntime/core/session"
    "${sentencepiece_SOURCE_DIR}/src" "${dr_libs_SOURCE_DIR}")
target_link_libraries(pockettts_jni PRIVATE "${ORT_LIBRARY}" sentencepiece-static Threads::Threads)
set_target_properties(pockettts_jni PROPERTIES BUILD_RPATH "$ORIGIN" BUILD_WITH_INSTALL_RPATH TRUE INSTALL_RPATH "$ORIGIN")
