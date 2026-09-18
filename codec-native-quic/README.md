# netty-codec-native-quic

Native QUIC support, built on [quiche](https://github.com/cloudflare/quiche) and BoringSSL. The
artifact bundles both, compiled from pinned commits, so no system QUIC or TLS library is required at
runtime.

## Running on Alpine or another musl distribution

**Use jemalloc.** On musl, install it and preload it:

```dockerfile
RUN apk add --no-cache jemalloc
ENV LD_PRELOAD=/usr/lib/libjemalloc.so.2
```

This is not a nicety. Without it, QUIC throughput on musl is measurably below the same workload on
glibc, and with it the difference disappears.

### Why

QUIC allocates heavily. In a steady echo workload this module issued roughly **16 to 21 `malloc`
calls per request**, about 870k/s on a four-core host, with allocations and frees balanced: the
working set is stable and essentially all of it is transient churn. Ninety percent of those calls
come from the native QUIC library rather than from the JVM.

musl's default allocator, `mallocng`, serialises every allocation on a **single process-wide
exclusive lock** (`__malloc_lock` in `src/malloc/mallocng/glue.h`, with `RDLOCK_IS_EXCLUSIVE`). It
spins ten times and then parks on a futex. At this allocation rate that produces an order of
magnitude more context switches and a double-digit throughput loss. glibc is unaffected because it
uses per-thread caches and multiple arenas, so most allocations never touch a shared lock.

Measured on one host, five interleaved rounds, 500 QUIC connections, 1 KB payload, pinned CPU clock
with no thermal throttling, median requests/s with the range across rounds:

| configuration | median req/s | range | vs glibc default |
|---|---|---|---|
| musl, default `mallocng` | 54,208 | 46,613 - 54,324 | **-14.8%** |
| musl + jemalloc | 63,358 | 61,157 - 63,726 | -0.4%, indistinguishable |
| musl + mimalloc | 58,282 | 55,442 - 59,997 | -8.4% |
| glibc, default malloc | 63,631 | 62,265 - 63,978 | baseline |

With jemalloc the musl range overlaps the glibc range, so the remaining difference is not
distinguishable from noise. **Prefer jemalloc over mimalloc**: mimalloc recovers only part of the
gap on musl, though it performs fine on glibc. That asymmetry is unexplained.

### Scope of the measurement

One machine, one workload, one payload size. The mechanism generalises (any heavy native allocator
on musl meets the same lock), but the exact percentages should not be quoted as universal. Re-measure
for your own workload if the number matters to you.

### Why netty does not simply bundle an allocator

Two reasons, both deliberate.

Replacing `malloc` is a **process-wide** decision. Exporting a bundled allocator from a JNI library
would capture allocation for the entire JVM, including the heap, the JIT and every other native
library in the process. That is the application's choice to make, not a networking codec's.

And keeping it internal instead creates a cross-allocator hazard: memory allocated by one allocator
and released by another is heap corruption. This module is careful to release every native object
through the API that allocated it, but a bundled allocator would make that a permanent constraint
on every future call site.

`LD_PRELOAD` puts the decision where it belongs, at deployment, and applies it consistently to the
whole process.

## Other Alpine notes

The Linux artifacts are built so that they load under musl, which requires more than compiling
against it: the interpreter entry is removed from `DT_NEEDED` after linking, and glibc-only symbols
have weak fallbacks. `.github/workflows/ci-verify-musl.yml` loads the built artifacts inside
`eclipse-temurin:21-jdk-alpine` and `amazoncorretto:21-alpine` on x86_64 and aarch64 to keep that
working.

If the native library fails to load on Alpine with a message about `ld-linux-x86-64.so.2`, the
artifact predates that work; upgrade rather than trying to install a loader.
