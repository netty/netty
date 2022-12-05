cmake_minimum_required (VERSION 3.8)

project ("netty_resolver_dns_native_windows")

find_package(JNI)
include_directories(${JNI_INCLUDE_DIRS})

set(CMAKE_COMPILE_WARNING_AS_ERROR ON)

add_library (netty_resolver_dns_native_windows SHARED  "src/netty_window_dns_native.c"  "src/netty_window_dns_native.h")

set_target_properties(netty_resolver_dns_native_windows
                       PROPERTIES OUTPUT_NAME netty_resolver_dns_native_windows_${JAVA_ARCH}
)

if (CMAKE_VERSION VERSION_GREATER 3.12)
  set_property(TARGET netty_resolver_dns_native_windows PROPERTY CXX_STANDARD 11)
endif()