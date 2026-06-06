cmake_minimum_required(VERSION 3.22.1)
project("ryzix" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build Ryzix as an executable named "libryzix.so".
# Android's package installer extracts any .so from the APK into
# nativeLibraryDir with execute permissions — this is the same trick used by
# Stockfish on Android and other chess apps.
add_executable(ryzix Ryzix.cpp)

target_compile_options(ryzix PRIVATE
    -O2
    -fno-rtti
    -fno-exceptions
    -DNDEBUG
)

set_target_properties(ryzix PROPERTIES
    OUTPUT_NAME "libryzix"
    SUFFIX      ".so"
)
