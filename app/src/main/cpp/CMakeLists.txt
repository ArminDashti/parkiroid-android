cmake_minimum_required(VERSION 3.22.1)
project(dogan_ncnn)

add_library(dogan_ncnn SHARED ncnn_jni.cpp)

find_library(log-lib log)

target_link_libraries(dogan_ncnn ${log-lib})
