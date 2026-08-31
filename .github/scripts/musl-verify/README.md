# musl verification for netty-codec-native-quic

The Linux native artifacts are built against glibc but are expected to load from the same jar on
musl (Alpine). Nothing in the ordinary build notices when that stops being true: the build hosts
are CentOS, the test hosts are Ubuntu, and musl does not fail a `dlopen` on a relocation it cannot
resolve -- it defers it and aborts at the first call instead. So a broken artifact still loads.

That is why `verify.sh` does not stop at loading. `codec-native-quic` links quiche statically, and
quiche brings a Rust standard library that calls glibc-only entry points by name, so only a real
QUIC handshake decides the outcome.

## Running it

```
# build the jars (x86_64)
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-7.111.yaml build
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-7.111.yaml run build-native-quic

# run them on bare Alpine
docker run --rm \
  -v "$PWD/.github/scripts/musl-verify:/verify:ro" \
  -v "$HOME/.m2/repository/io/netty:/jars:ro" \
  -e MUSL_VARIANT=bare -e MUSL_DROP_ELFTOOLS=1 \
  eclipse-temurin:21-jdk-alpine \
  sh -c 'apk add --no-cache --virtual .elftools binutils >/dev/null && sh /verify/verify.sh /jars'
```

`MUSL_DROP_ELFTOOLS=1` makes the script `apk del` binutils again before it executes anything.
binutils depends on libgcc, so leaving it installed silently satisfies a `libgcc_s.so.1`
`DT_NEEDED` and the bare run stops testing what it claims to.

Swap the image for `eclipse-temurin:21-jdk-noble` as a glibc control: anything that fails on Alpine
but also fails there is a broken harness, not a broken library.

## What each check is for

| check | catches |
|---|---|
| `ldd` | the musl loader's own verdict, with no JVM in the way |
| `DT_NEEDED` | a name musl does not reserve and cannot find on disk, e.g. `ld-linux-x86-64.so.2` or `libgcc_s.so.1` |
| undefined symbols | glibc-internal names musl never exported; reported, not fatal, because a deferred relocation nothing calls never aborts |
| `load` | the `dlopen` itself, before netty unpacks its own copy |
| `init` | `Quic.ensureAvailability()`, i.e. netty's loader and quiche's own startup |
| `handshake` | a real client and server over loopback, exchanging a stream |

A hard JVM crash prints no `RESULT` line, and the script turns that into a failure rather than
letting a missing line read as success. That matters: the equivalent netty-tcnative bug killed the
JVM with SIGSEGV inside `JVM_LoadLibrary` rather than raising `UnsatisfiedLinkError`.

## Known failure signatures

```
Error loading shared library ld-linux-x86-64.so.2: No such file or directory
```
`ld-linux-...` does not begin with `lib`, so it is not one of the names musl satisfies internally
and the loader goes to the filesystem. Fixed by the `patchelf --remove-needed` step in
`codec-native-quic/pom.xml`.

```
Error loading shared library libgcc_s.so.1: No such file or directory
```
`gcc_s.` is not a musl-reserved stem either, so this resolves only on images that happen to ship
Alpine's `libgcc` package. `eclipse-temurin:21-jdk-alpine` does; `amazoncorretto:21-alpine` does
not. Fixed by linking the unwinder statically.

```
Error relocating ...: gnu_get_libc_version: symbol not found
```
A glibc-internal name musl does not export. Fixed by
`codec-native-quic/src/main/c/musl_compat.c`.

See https://github.com/netty/netty-tcnative/issues/907 for the original report against
netty-tcnative and https://github.com/netty/netty-tcnative/pull/997 for the same treatment there.
