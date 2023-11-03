#!/bin/bash

source scl_source enable devtoolset-11

set -eux

cd $SOURCE_DIR

git clone -b $GCC_VERSION https://github.com/riscv/riscv-gnu-toolchain
pushd riscv-gnu-toolchain
./configure --prefix=/opt/riscv --disable-gdb
make linux -j --output-sync=target
popd

mkdir -p autoconf
curl -L https://ftp.gnu.org/gnu/autoconf/autoconf-2.71.tar.gz | tar -xvzf - -C autoconf --strip-components 1
pushd autoconf
./configure --prefix=/opt/riscv
make
make install
popd

mkdir -p automake
curl -L https://ftp.gnu.org/gnu/automake/automake-1.16.tar.gz | tar -xvzf - -C automake --strip-components 1
pushd automake
patch -p1 <<PATCH
diff -bur automake-1.16/bin/automake.in  automake-1.16.new/bin/automake.in
--- automake-1.16/bin/automake.in       2018-02-26 01:13:58.000000000 +1100
+++ automake-1.16.new/bin/automake.in   2018-03-04 10:28:25.357886554 +1100
@@ -73,7 +73,8 @@
 use Automake::Language;
 use File::Basename;
 use File::Spec;
-use List::Util 'none';
+use List::Util 'reduce';
+sub none (&@) { my \\\$code=shift; reduce { \\\$a && !\\\$code->(local \\\$_ = \\\$b) } 1, @_; }
 use Carp;
 
 ## ----------------------- ##
PATCH
./configure --prefix=/opt/riscv
make
make install
popd

mkdir -p libtool
curl -L https://ftp.gnu.org/gnu/libtool/libtool-2.4.7.tar.gz | tar -xvzf - -C libtool --strip-components 1
pushd libtool
./configure --prefix=/opt/riscv
make
make install
popd
