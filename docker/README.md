# Using the docker images

```
cd /path/to/netty/
```

## centos 6 with java 8

```
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.18.yaml run test
```

## centos 6 with java 11

```
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.111.yaml run test
```

## Cross compiling the native libraries

All native libraries that are released (Linux x86_64, aarch64 and riscv64, macOS x86_64 and aarch64, Windows x86_64)
are built with one (CentOS 7 based) image, `Dockerfile.cross_compile`, on an x86_64 host:

```
NETTY_CROSS_TARGET=osx-aarch_64 docker compose -f docker/docker-compose.cross.yaml run cross-compile-build
```

`NETTY_CROSS_TARGET` is one of `linux-x86_64`, `linux-aarch_64`, `linux-riscv64`, `osx-x86_64`, `osx-aarch_64` and
`windows-x86_64`. `NETTY_CROSS_MODULES` optionally restricts the build to some of the native modules of the target
(comma separated), or extends it to the whole project with `all`.

[zig](https://ziglang.org) is used as the C/C++ compiler and linker for all targets. It brings its own glibc stubs
(of any version), macOS libSystem stubs and headers, and MinGW-w64, so what the libraries are compatible with does not
depend on the glibc of the image, and no macOS SDK is needed. `cross-compile/zig-toolchain.sh` makes it look like the
`<triple>-gcc` style cross toolchain our builds expect, and `cross-compile/build.sh` selects the maven profiles,
modules and environment of a target. The minimum versions are:

| Target | Minimum |
| ------ | ------- |
| Linux x86_64, aarch64 | glibc 2.17 (checked, see below) |
| Linux riscv64 | glibc 2.31 |
| macOS x86_64, aarch64 | macOS 11.0 |
| Windows x86_64 | Windows 10 (UCRT) |

Running `cross-compile/build.sh` outside of the image also works, given zig, Rust (with the standard libraries of the
targets), cmake, ninja, nasm, autotools, libclang (for bindgen), patchelf and python3 on the `PATH`.

etc, etc

## Pinned toolchain downloads

The Dockerfiles verify downloaded toolchains (SDKMAN! installer, `rustup-init`, zig) against a pinned SHA-256 before using them.
SDKMAN! does not version its installer script, so if a docker build fails with a checksum mismatch for `sdkman-install.sh`, review the new script and refresh the pinned hash in all Dockerfiles with:

```
docker/update-sdkman-sha.sh
```

Use `docker/update-sdkman-sha.sh --check` to only verify whether the pinned hashes are still current.

## Minimum glibc version of the native libraries

`Dockerfile.centos7` and `Dockerfile.cross_compile` set `NETTY_CHECK_NATIVE_GLIBC_VERSIONS=true`, which activates the `check-native-glibc-versions` profile in `pom.xml`.
It fails the build if a Linux native library requires a glibc symbol version newer than `GLIBC_2.17` (x86_64 and aarch64).
Version needs marked as `WEAK` are only reported, as they do not prevent loading the library.
Modules can opt out by setting the `skipNativeGlibcVersionsCheck` property to `true`, which `codec-native-quic` does.
The check is done by `.github/scripts/check_native_glibc_versions.sh`, which can also be run by hand:

```
bash .github/scripts/check_native_glibc_versions.sh transport-native-epoll/target/classes/META-INF/native
```

Builds outside of these images are not checked, as they are usually done against a newer glibc.
