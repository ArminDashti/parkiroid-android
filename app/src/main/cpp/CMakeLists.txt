cmake_minimum_required(VERSION 3.22.1)
project(dogan_ncnn)

set(NCNN_VERSION "20240820")
set(NCNN_DIR "${CMAKE_SOURCE_DIR}/ncnn-${NCNN_VERSION}-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn")

if(NOT EXISTS "${NCNN_DIR}/ncnnConfig.cmake")
    message(FATAL_ERROR "NCNN prebuilt not found at ${NCNN_DIR}. Run ./gradlew downloadNcnn first.")
endif()

set(ncnn_DIR "${NCNN_DIR}")
find_package(ncnn REQUIRED)

add_library(dogan_ncnn SHARED
    ncnn_jni.cpp
    yolov8_detector.cpp
)

find_library(log-lib log)

target_link_libraries(dogan_ncnn
    ncnn
    ${log-lib}
    android
)

target_compile_features(dogan_ncnn PRIVATE cxx_std_17)
