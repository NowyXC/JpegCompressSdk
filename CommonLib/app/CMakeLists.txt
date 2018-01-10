# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)


#设置c的根目录
set(libjpeg_dir ${CMAKE_SOURCE_DIR}/src/main/cpp)



#指定要引用的libjpeg.so的头文件目录
set(INC_DIR ${libjpeg_dir}/include)
include_directories(${INC_DIR})

#设置生成的so动态库最后输出的路径
#set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})


#设置源文件路径
set(SOURCE_FILES src/main/cpp/native-lib.c)


add_library(compress SHARED ${SOURCE_FILES})



#导入libjpeg动态库
add_library(libjpeg SHARED IMPORTED)

set(libjpeg_lib_dir ${CMAKE_SOURCE_DIR}/src/main/jniLibs)
set_target_properties(libjpeg PROPERTIES IMPORTED_LOCATION ${libjpeg_lib_dir}/${ANDROID_ABI}/libjpeg.so)


set_target_properties(compress PROPERTIES OUTPUT_NAME "compress")


find_library(log-lib log)
find_library(graphics jnigraphics)

#项目关联
target_link_libraries(compress libjpeg ${graphics} ${log-lib})