/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/*
 * Fallbacks for glibc-internal symbols that musl (Alpine Linux) does not export, so that the
 * single glibc-built shared library also loads and runs under musl.
 *
 * netty-codec-native-quic links quiche statically, and quiche brings the Rust standard library
 * with it. Rust's std on *-unknown-linux-gnu calls the LFS64 entry points by name (open64,
 * mmap64, readdir64, ...) and, on the older glibc of the release images, reaches stat through
 * the __xstat64 family. musl 1.2.4 removed the LFS64 aliases altogether and never had the
 * __?xstat64 family, so those references have nothing to bind to there. BoringSSL contributes
 * fopen64 on top; that one is shared with netty-tcnative and with the ohttp-hpke artifact.
 *
 * Verified against the musl 1.2.5 export set shipped by Alpine 3.22 on both x86_64 and
 * aarch64: every name defined below is absent from it, and every function it forwards to is
 * present.
 *
 * Undefined symbols are not by themselves fatal on musl -- its loader defers a relocation it
 * cannot resolve instead of failing the dlopen, so the library still loads and aborts at the
 * first call. That is why this file matters even though the DT_NEEDED fixes alone make the
 * library loadable: without it, loading succeeds and the first QUIC handshake dies.
 *
 * These are deliberately compiled in on a GLIBC host -- the release images are CentOS 7 -- and
 * the guard below is `#ifdef __GLIBC__` for that reason. Inverting it would compile the
 * definitions out of exactly the artifacts that need them. `weak` is what keeps them from
 * colliding with glibc's own definitions at link time. At runtime on glibc these are never
 * reached: a JNI library is dlopened into a local scope, so the global scope -- which already
 * holds libc.so.6 -- is searched first and glibc's own definitions win. On musl the global scope
 * has no such symbol and the definition below is used.
 *
 * `visibility("default")` is required because the build passes -fvisibility=hidden
 * (src/main/native-package/m4/custom.m4.template). Hidden would still satisfy the references
 * from libquiche.a at static link time, but it would also pin them to these forwarders on
 * glibc rather than letting the real libc entry points interpose.
 *
 * See https://github.com/netty/netty-tcnative/pull/997 for the same treatment in tcnative, and
 * https://github.com/netty/netty-tcnative/issues/907 for the original report.
 */

#ifdef __linux__

/*
 * MUST come before any include. With _FILE_OFFSET_BITS=64 glibc's headers __REDIRECT open to
 * open64, stat to stat64 and so on -- inside this file that would turn every forwarder below
 * into infinite recursion. The quic build does not currently set it (see custom.m4.template),
 * so this is a guard against a future -D on the command line rather than a fix for today.
 */
#undef _FILE_OFFSET_BITS
#undef __USE_FILE_OFFSET64

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
/* Declares the *64 entry points and struct stat64 on glibc. Not defined by the build. */
#ifndef _LARGEFILE64_SOURCE
#define _LARGEFILE64_SOURCE
#endif

/* Defines __GLIBC__ when there is one, via <features.h>. Must precede the test below. */
#include <stdlib.h>

/*
 * Compile the fallbacks in only where they are both needed and expressible: a glibc build.
 *
 * Note the direction. Guarding them OUT on glibc (`#ifndef __GLIBC__`) would be the bug -- the
 * release images are glibc, so that would remove the definitions from exactly the artifacts that
 * need them. Guarding them IN on glibc is the opposite, and is required, because the types these
 * shims are declared with -- off64_t, struct stat64, struct dirent64 -- do not exist on musl.
 * Without this a musl-native build of the module fails to compile, and it would not want the
 * shims anyway: a musl-linked artifact has no glibc symbols to stand in for.
 */
#ifdef __GLIBC__

#include <dirent.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

/*
 * <sys/auxv.h> and getauxval() only exist from glibc 2.16. Probing keeps this file compilable
 * on a glibc-2.12 host (the netty 4.1 x86_64 release image is CentOS 6), where including it is
 * a fatal error.
 */
#if defined(__has_include)
#  if __has_include(<sys/auxv.h>)
#    define NETTY_HAVE_SYS_AUXV 1
#  endif
#endif
#ifdef NETTY_HAVE_SYS_AUXV
#  include <sys/auxv.h>
#endif

#define NETTY_MUSL_COMPAT __attribute__((weak, visibility("default")))

/*
 * Some glibc versions define these internal names as function-like MACROS rather than only
 * declaring them, in which case a definition here expands into the macro body and fails to
 * compile. Undefine them first; on the glibc versions that only declare them this is invisible,
 * so it must not be removed just because the toolchain at hand is happy without it.
 */
#undef __getauxval
#undef fopen64
#undef open64
#undef openat64
#undef mmap64
#undef lseek64
#undef pread64
#undef pwrite64
#undef sendfile64
#undef ftruncate64
#undef readdir64
#undef stat64
#undef fstat64
#undef lstat64
#undef fstatat64
#undef __xstat64
#undef __fxstat64
#undef __lxstat64
#undef __fxstatat64

/* ------------------------------------------------------------------ LFS64 aliases
 *
 * musl 1.2.4 (Alpine 3.19+) removed these. off_t is unconditionally 64-bit there and on every
 * 64-bit glibc target, so forwarding to the unsuffixed entry point is exact, not a narrowing.
 */

NETTY_MUSL_COMPAT FILE* fopen64(const char* path, const char* mode) {
    return fopen(path, mode);
}

/* glibc's __OPEN_NEEDS_MODE: the mode argument is only present for O_CREAT and O_TMPFILE. */
static int netty_open_needs_mode(int flags) {
#ifdef O_TMPFILE
    return (flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE;
#else
    return (flags & O_CREAT) != 0;
#endif
}

NETTY_MUSL_COMPAT int open64(const char* path, int flags, ...) {
    mode_t mode = 0;
    if (netty_open_needs_mode(flags)) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, mode_t);
        va_end(ap);
    }
    return open(path, flags, mode);
}

NETTY_MUSL_COMPAT int openat64(int dirfd, const char* path, int flags, ...) {
    mode_t mode = 0;
    if (netty_open_needs_mode(flags)) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, mode_t);
        va_end(ap);
    }
    return openat(dirfd, path, flags, mode);
}

NETTY_MUSL_COMPAT void* mmap64(void* addr, size_t length, int prot, int flags, int fd, off64_t offset) {
    return mmap(addr, length, prot, flags, fd, (off_t) offset);
}

NETTY_MUSL_COMPAT off64_t lseek64(int fd, off64_t offset, int whence) {
    return (off64_t) lseek(fd, (off_t) offset, whence);
}

NETTY_MUSL_COMPAT ssize_t pread64(int fd, void* buf, size_t count, off64_t offset) {
    return pread(fd, buf, count, (off_t) offset);
}

NETTY_MUSL_COMPAT ssize_t pwrite64(int fd, const void* buf, size_t count, off64_t offset) {
    return pwrite(fd, buf, count, (off_t) offset);
}

NETTY_MUSL_COMPAT ssize_t sendfile64(int out_fd, int in_fd, off64_t* offset, size_t count) {
    /* off_t and off64_t are the same 64-bit type here, so the in/out parameter can be aliased. */
    return sendfile(out_fd, in_fd, (off_t*) offset, count);
}

NETTY_MUSL_COMPAT int ftruncate64(int fd, off64_t length) {
    return ftruncate(fd, (off_t) length);
}

/*
 * struct dirent64 and musl's struct dirent have the same layout on every 64-bit target: both
 * mirror the kernel's linux_dirent64 (ino, off, reclen, type, name[256]).
 */
NETTY_MUSL_COMPAT struct dirent64* readdir64(DIR* dirp) {
    return (struct dirent64*) readdir(dirp);
}

/* ------------------------------------------------------------------ stat, both spellings
 *
 * Two families, because which one a build emits depends on the glibc it was compiled against:
 * before 2.33 stat() is not an exported function at all but a libc_nonshared.a stub that calls
 * __xstat(_STAT_VER, ...), from 2.33 it is a normal symbol. The released x86_64 artifact
 * carries the __?xstat64 family and the aarch64 one carries the plain *64 names, so one file
 * has to cover both.
 *
 * These go straight to the kernel rather than forwarding to stat()/fstatat() like every other
 * shim here does, and that is not a stylistic choice. Compiled on glibc 2.17 -- the CentOS 7
 * release image -- a call to stat() links that nonshared stub into this very library, and the
 * stub calls __xstat, which musl does not export either. The forwarding version would therefore
 * resolve one dead symbol into another and abort in exactly the case it exists to fix.
 *
 * The buffer needs no conversion: what these syscalls fill is the kernel's struct stat, which
 * is what glibc calls struct stat64 and what musl calls struct stat. All three are the same
 * layout on x86_64 and aarch64. The `ver` argument of the __?xstat64 family selects that same
 * struct (_STAT_VER) and is therefore ignored.
 *
 * SYS_newfstatat and SYS_fstat are the two that exist on both architectures: x86_64 also has
 * SYS_stat and SYS_lstat, aarch64 has neither and expects AT_FDCWD through newfstatat.
 */

static int netty_fstatat64(int dirfd, const char* path, struct stat64* buf, int flags) {
    return (int) syscall(SYS_newfstatat, dirfd, path, buf, flags);
}

NETTY_MUSL_COMPAT int stat64(const char* path, struct stat64* buf) {
    return netty_fstatat64(AT_FDCWD, path, buf, 0);
}

NETTY_MUSL_COMPAT int fstat64(int fd, struct stat64* buf) {
    return (int) syscall(SYS_fstat, fd, buf);
}

NETTY_MUSL_COMPAT int lstat64(const char* path, struct stat64* buf) {
    return netty_fstatat64(AT_FDCWD, path, buf, AT_SYMLINK_NOFOLLOW);
}

NETTY_MUSL_COMPAT int fstatat64(int dirfd, const char* path, struct stat64* buf, int flags) {
    return netty_fstatat64(dirfd, path, buf, flags);
}

NETTY_MUSL_COMPAT int __xstat64(int ver, const char* path, struct stat64* buf) {
    (void) ver;
    return netty_fstatat64(AT_FDCWD, path, buf, 0);
}

NETTY_MUSL_COMPAT int __fxstat64(int ver, int fd, struct stat64* buf) {
    (void) ver;
    return (int) syscall(SYS_fstat, fd, buf);
}

NETTY_MUSL_COMPAT int __lxstat64(int ver, const char* path, struct stat64* buf) {
    (void) ver;
    return netty_fstatat64(AT_FDCWD, path, buf, AT_SYMLINK_NOFOLLOW);
}

NETTY_MUSL_COMPAT int __fxstatat64(int ver, int dirfd, const char* path, struct stat64* buf, int flags) {
    (void) ver;
    return netty_fstatat64(dirfd, path, buf, flags);
}

/* ------------------------------------------------------------------ the rest */

/*
 * glibc exports getauxval and the __-prefixed alias; musl exports only getauxval. This one is
 * load-fatal rather than latent on aarch64: linking libgcc statically (see the linux profiles
 * in pom.xml) brings in init_have_lse_atomics, an .init_array constructor that calls
 * __getauxval during dlopen, so an unresolved reference kills the JVM with SIGSEGV inside
 * JVM_LoadLibrary instead of raising UnsatisfiedLinkError. It must not be dropped later on the
 * grounds that no netty code calls it.
 */
NETTY_MUSL_COMPAT unsigned long __getauxval(unsigned long type) {
#ifdef NETTY_HAVE_SYS_AUXV
    return getauxval(type);
#else
    unsigned long entry[2];
    unsigned long value = 0;
    int fd = open("/proc/self/auxv", O_RDONLY);
    if (fd < 0) {
        return 0;
    }
    while (read(fd, entry, sizeof(entry)) == (ssize_t) sizeof(entry)) {
        if (entry[0] == type) {
            value = entry[1];
            break;
        }
        if (entry[0] == 0) { /* AT_NULL terminates the vector */
            break;
        }
    }
    close(fd);
    return value;
#endif
}

/*
 * Rust's standard library calls gnu_get_libc_version() to decide between a modern code path and
 * a workaround for an old glibc. There is no glibc here, so report a version new enough that
 * every such workaround is skipped -- the workarounds are the dangerous answer, because they
 * reach for further glibc internals (__pthread_get_minstack for the pre-2.27 stack-size
 * handling, for one) that musl does not have either.
 */
NETTY_MUSL_COMPAT const char* gnu_get_libc_version(void) {
    return "2.38";
}

/*
 * musl's own res_init() is a stub that returns 0, so returning 0 here is exactly what a musl
 * build of the same caller would do. Forwarding to res_init() instead would be worse than
 * useless: glibc's <resolv.h> macro-defines res_init to __res_init, and on glibc 2.17 the
 * un-prefixed symbol lives in libresolv.so.2, so the forward would either recurse or add a
 * DT_NEEDED on libresolv -- another name musl does not reserve.
 */
NETTY_MUSL_COMPAT int __res_init(void) {
    return 0;
}

#endif /* __GLIBC__ */

#endif /* __linux__ */
