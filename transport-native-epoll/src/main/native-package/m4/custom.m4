dnl ---------------------------------------------------------------------------
dnl  Copyright 2014 The Netty Project
dnl
dnl  Licensed under the Apache License, Version 2.0 (the "License");
dnl  you may not use this file except in compliance with the License.
dnl  You may obtain a copy of the License at
dnl
dnl     http://www.apache.org/licenses/LICENSE-2.0
dnl
dnl  Unless required by applicable law or agreed to in writing, software
dnl  distributed under the License is distributed on an "AS IS" BASIS,
dnl  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
dnl  See the License for the specific language governing permissions and
dnl  limitations under the License.
dnl ---------------------------------------------------------------------------

AC_DEFUN([CUSTOM_M4_SETUP],
[
  AC_MSG_CHECKING(which arch to build for)
  AC_ARG_WITH([arch],
  [AS_HELP_STRING([--with-arch@<:@=ARCH@:>@],
    [Build for the specified architecture. Pick from: i386, x86_64.])],
  [
    AS_IF(test -n "$withval", [
      ARCH="$withval"
      AC_MSG_RESULT([yes, archs: $ARCH])
    ])
  ],[
    ARCH=""
    AC_MSG_RESULT([no])
  ])
  AS_IF(test "$ARCH" = "i386", [
    FLAGS="-m32"
  ], test "ARCH" = "x86_64", [
    FLAGS="-m64"
  ], [
    FLAGS=""
  ])
  AS_IF(test -n "$FLAGS", [
    CFLAGS="$FLAGS $CFLAGS"
    CXXFLAGS="$FLAGS $CXXFLAGS"
    LDFLAGS="$FLAGS $ARCH $LDFLAGS"
    AC_SUBST(CFLAGS)
    AC_SUBST(CXXFLAGS)
    AC_SUBST(LDFLAGS)
  ])
])