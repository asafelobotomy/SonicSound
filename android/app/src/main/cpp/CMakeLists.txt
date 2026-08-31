cmake_minimum_required(VERSION 3.22.1)
project(vlc_pcm_tap)

add_library(vlc_pcm_tap SHARED vlc_pcm_tap.cpp)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(vlc_pcm_tap ${log-lib} ${dl-lib})

target_compile_options(vlc_pcm_tap PRIVATE -Wall -Wextra -fvisibility=hidden)
