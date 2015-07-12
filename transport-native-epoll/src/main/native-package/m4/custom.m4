dnl ---------------------------------------------------------------------------
dnl  Copyright 2015 The Netty Project
dnl  
dnl  The Netty Project licenses this file to you under the Apache License,
dnl  version 2.0 (the "License"); you may not use this file except in compliance
dnl  with the License. You may obtain a copy of the License at:
dnl  
dnl    http://www.apache.org/licenses/LICENSE-2.0
dnl  
dnl  Unless required by applicable law or agreed to in writing, software
dnl  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
dnl  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
dnl  License for the specific language governing permissions and limitations
dnl  under the License.
dnl ---------------------------------------------------------------------------

AC_DEFUN([CUSTOM_M4_SETUP],
[

  dnl 
  dnl  libaio is optional.. so if we have both the header and lib
  dnl  installed, the build will #define HAVE_LIB_AIO 1
  dnl
  AC_CHECK_HEADER([libaio.h],[
    dnl Lets link against libaio
    LDFLAGS="$LDFLAGS -laio"
    AC_CHECK_LIB([aio], [io_queue_init], [
        AC_DEFINE([HAVE_LIB_AIO], [1], [Define to 1 if you have the aio library.])
        AC_SUBST(LDFLAGS)
    ])
  ])

  dnl
  dnl epoll is required.  Fail if we can't find it's headers.
  dnl
  AC_CHECK_HEADER([sys/epoll.h],,[AC_MSG_ERROR([cannot find sys/epoll.h headers])])

])
