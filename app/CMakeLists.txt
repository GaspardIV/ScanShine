cmake_minimum_required(VERSION 3.4.1)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/libs/opencv_include/sdk/native/jni/include)
add_library( lib_opencv SHARED IMPORTED )
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libopencv_java3.so)

add_library(
             native-lib
             SHARED
             src/main/cpp/native-lib.cpp
             )
add_library(
            magic-lib
            SHARED
            src/main/cpp/magic-lib.cpp

)

target_link_libraries(
                       native-lib
                       lib_opencv
                       )
target_link_libraries(
                       magic-lib
                       lib_opencv
                       )