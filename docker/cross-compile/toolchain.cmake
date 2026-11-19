# ----------------------------------------------------------------------------
# Copyright 2026 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
#
# CMake toolchain file for the zig based cross compile toolchain, see zig-toolchain.sh.
# docker/cross-compile/build.sh exports it as CMAKE_TOOLCHAIN_FILE (which CMake >= 3.21 respects) together with
# NETTY_CROSS_TRIPLE, the autoconf style triple of the target (for example x86_64-apple-darwin).
set(_netty_triple "$ENV{NETTY_CROSS_TRIPLE}")
if(NOT _netty_triple)
  message(FATAL_ERROR "NETTY_CROSS_TRIPLE must be set when using ${CMAKE_CURRENT_LIST_FILE}")
endif()

if(_netty_triple MATCHES "-apple-darwin$")
  set(CMAKE_SYSTEM_NAME Darwin)
  # Don't look for an SDK, zig brings its own copy of the libSystem headers.
  set(CMAKE_OSX_SYSROOT "" CACHE STRING "" FORCE)
  # CMake requires an install_name_tool for macOS targets, see zig-toolchain.sh
  find_program(CMAKE_INSTALL_NAME_TOOL NAMES "${_netty_triple}-install_name_tool" REQUIRED)
  if(DEFINED ENV{MACOSX_DEPLOYMENT_TARGET})
    set(CMAKE_OSX_DEPLOYMENT_TARGET "$ENV{MACOSX_DEPLOYMENT_TARGET}" CACHE STRING "")
  endif()
elseif(_netty_triple MATCHES "-mingw32$")
  set(CMAKE_SYSTEM_NAME Windows)
else()
  set(CMAKE_SYSTEM_NAME Linux)
endif()

if(NOT CMAKE_SYSTEM_PROCESSOR)
  string(REGEX MATCH "^[^-]+" CMAKE_SYSTEM_PROCESSOR "${_netty_triple}")
endif()

set(CMAKE_C_COMPILER "${_netty_triple}-gcc")
set(CMAKE_CXX_COMPILER "${_netty_triple}-g++")
set(CMAKE_AR "${_netty_triple}-ar" CACHE FILEPATH "")
set(CMAKE_RANLIB "${_netty_triple}-ranlib" CACHE FILEPATH "")

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
