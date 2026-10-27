cmake_minimum_required(VERSION 3.22.1)
project(mapcontrol_hab)

add_library(mapcontrol_hab SHARED hab_native.cpp)

# Kare donusumu/kopyasi debug derlemede de akici kalsin.
target_compile_options(mapcontrol_hab PRIVATE -O2)

find_library(log-lib log)
target_link_libraries(mapcontrol_hab ${log-lib} dl EGL GLESv2)
