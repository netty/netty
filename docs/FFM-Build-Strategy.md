# FFM transport build strategy

Design notes for adding `java.lang.foreign` (FFM) transports (starting with kqueue on macOS)
alongside the existing JNI transports: module layout, how the jextract-generated bindings are
produced and committed, and the build-integration work required. See [Jextract.md](Jextract.md) for
the generator plugin itself.

## Summary

- FFM bindings are **committed jextract-generated Java**, not a compiled library, so there is no
  per-arch classifier and no native/classes split to isolate one. Generated bindings and the
  hand-written transport that consumes them live in the **same module**.
- Modules are split only by **sharing scope**, never by native/classes: one combined module per
  self-contained backend (`transport-ffm-kqueue`), plus a shared bindings+helpers module where sibling
  backends reuse one ABI (`transport-ffm-linux`, shared by `transport-ffm-epoll` and
  `transport-ffm-iouring`).
- Bindings are **per-OS** (jextract bakes ABI constants and struct layouts into the output) and
  **arch-invariant within an OS** for the socket ABI, so one plain jar per OS serves all its
  architectures — with one known exception (`epoll_event`, packed only on x86_64; see below). There
  are no per-arch classifier jars.
- Generated sources are committed and treated as a build artifact: pinned jextract, pinned SDK, a
  provenance manifest, and a regeneration-diff CI check. jextract is never on a consumer's build path.
- The only hand-written binding code is a small errno-capture shim, needed until jextract can emit
  `captureCallState` handles.

## Relationship to the JNI transports

The JNI transports split platform-neutral Java from a native module that ships a compiled library per
architecture:

```
transport-classes-kqueue   platform-neutral Java (channels, IoHandler, config)
transport-native-kqueue    per-arch classifier jars (osx-x86_64, osx-aarch_64); .jnilib in META-INF/native
```

FFM resolves libc symbols at runtime via `Linker.nativeLinker().defaultLookup()`, so there is no
compiled library. The platform-varying artifact becomes the jextract-generated Java, which we commit.

## Module layout

FFM has no per-arch compiled artifact, so the JNI `transport-classes-*` / `transport-native-*` split —
whose only purpose was isolating a per-arch classifier jar — does not carry over. Generated bindings
and the hand-written transport that consumes them sit in the **same module**. The only module
boundaries that remain are (1) one combined module per self-contained backend, (2) a shared module
where sibling backends on one OS reuse the same ABI, and (3) `netty-native`, the single cross-cutting
native-access funnel.

| Module | Contents |
|---|---|
| `netty-native` | OS-agnostic hand-written plumbing and the single funnel for restricted native-access calls: the `Linker` downcall construction and the errno capture-state helper. No per-OS or per-arch code. `transport` is omitted from the name deliberately — native SSL funnels through it too and is not a transport. |
| `transport-ffm-kqueue` | macOS. Committed jextract output (socket + kqueue bindings), the provenance manifest, the per-OS errno-capture call sites, and the hand-written kqueue transport (`IoHandler`, channels, config). One combined module: macOS has a single backend, so there is no sibling to share bindings with. |

**`netty-native` is the native-access funnel, not where platform code lives.** It builds the FFM
downcall handles itself (to add `captureCallState` for errno, see below), so the restricted `Linker`
calls live there and not in the per-backend modules, which only invoke the resulting handles
(invocation is not restricted). That is what lets a consumer grant native access to one module,
`--enable-native-access=netty-native`, instead of enumerating every FFM transport and the SSL module.

**Linux: one shared bindings module, many event loops.** Unlike macOS (a single `kqueue` backend),
Linux grows more than one FFM event-loop backend — `epoll`, `io_uring`, and likely others — and they
all target the *same* Linux socket/libc ABI: `socket`/`bind`/`connect`/`accept`, `sockaddr*`,
`read`/`write`/`readv`/`writev`, `fcntl`, `iovec`, errno. That common core — bindings **and** the
hand-written socket helpers over it (`LinuxSocket`, `SocketIO`, …) — is factored **once** into a shared
`transport-ffm-linux` module. Each backend adds only its own event-mechanism bindings and wrappers and
`requires` the shared module:

```
transport-ffm-linux      shared socket/libc bindings + shared socket helpers    [combined]
transport-ffm-epoll      sys/epoll.h bindings + epoll IoHandler/channels         [combined]  requires -linux
transport-ffm-iouring    linux/io_uring.h bindings + io_uring transport          [combined]  requires -linux
```

Every module still combines generated bindings with the hand-written code over them; the *only* reason
`transport-ffm-linux` is a separate module is that epoll and io_uring are two siblings reusing one ABI.
**Do not duplicate the socket bindings into each backend** — that is drift waiting to happen and defeats
the byte-identical regeneration gate.

**Dependency direction.** The FFM bindings are ordinary Java types the transport calls, so a backend
module compile-depends (no classifier) on what it needs: `transport-ffm-epoll` on `transport-ffm-linux`,
and every FFM module on `netty-native` for the downcall/errno helpers. Consumers depend on the backend
module and get everything transitively; there is no platform-classifier selection step and jextract is
never required to build. Transport **selection stays explicit** — the consumer picks
`EpollIoHandler` / `IoUringIoHandler` / `KQueueIoHandler` (guarded by `Epoll.isAvailable()` etc.), which
is what pulls that backend and its shared core onto the path. No `provides`, no ServiceLoader.

**Split only by sharing scope, never by native/classes.** Generated sources bake in per-OS ABI values,
so a binding set is valid for exactly one OS (and libc — see below); that per-OS boundary is real. But
within an OS, do not split generated-from-hand-written: pure committed Java has no classifier to
isolate. Introduce a *shared* module only when sibling backends genuinely reuse one ABI (Linux today)
or a second same-OS consumer appears (e.g. a macOS DNS resolver reusing the socket bindings) — at which
point `transport-ffm-kqueue`'s socket half would be promoted to a `transport-ffm-macos` shared module,
exactly mirroring `transport-ffm-linux`.

**Package naming.** Generated code lives in an OS-named package, e.g.
`io.netty.channel.kqueue.ffm.generated`, so the target OS is visible at the call site and in stack
traces and a cross-OS import stands out in review. Keep the shared socket bindings and the
event-mechanism bindings in distinct packages so a future promotion to a shared module is a package
move, not a re-partition.

## Bindings are per-OS, arch-invariant within an OS

jextract bakes constant values (`AF_INET`), struct layouts and offsets (`sockaddr_in`), and type
sizes into the generated code, and the API itself differs (`kqueue` vs `epoll`). A binding set is
therefore valid for exactly one OS. Within an OS the supported architectures share an ABI (x86_64 and
aarch64 are both LP64), so the output is identical across them and one no-classifier jar per OS
suffices. A CI check (below) regenerates on both arches and asserts byte-identical output; a future
non-LP64 architecture would need its own binding set.

**LP64 sameness is necessary but not sufficient — the `epoll_event` exception.** A struct can still be
arch-variant under a shared LP64 ABI when a header applies `__attribute__((packed))` on one arch only.
The concrete case is Linux's `struct epoll_event`: glibc marks it `EPOLL_PACKED` on x86_64 (12 bytes,
`data` at offset 4) but leaves it naturally aligned elsewhere (16 bytes, `data` at offset 8), so the
epoll event binding is **not** byte-identical across x86_64 and aarch64. This is confined to the small
`transport-ffm-epoll` module: the shared `transport-ffm-linux` socket core has no packed structs and
stays arch-invariant (one no-classifier jar), while the epoll event binding is either regenerated
per-arch or has its per-arch outputs asserted separately by CI rather than held byte-identical. Verify
the packing against the pinned glibc/arch toolchains before finalizing the epoll module.

**libc implementations (Linux).** macOS ships a single system libc, but Linux does not: glibc and musl
can differ in struct layouts, type sizes, and symbol availability, and jextract reads whichever libc's
headers are installed. A binding set is therefore valid for one libc as well as one OS, so on Linux
"per-OS" is really "per-OS-and-libc." Two options: generate a separate set per libc (a musl variant
alongside glibc), or pin glibc as the supported target and treat musl as out of scope until there is
demand. Either way the pinned toolchain recorded in the provenance manifest must capture the libc on
Linux, so a glibc-vs-musl difference surfaces as a reviewable diff rather than silent drift.

## Hand-written code: errno capture

jextract does not build its downcall handles with `Linker.Option.captureCallState("errno")`, so the
generated `socket`/`bind`/etc. methods cannot read errno atomically (a separate read is racy). Until
jextract can emit capture-state handles, errno-sensitive calls are hand-written:

- An OS-agnostic helper in `netty-native` decorates a generated `FunctionDescriptor` with
  `captureCallState` and owns the cached errno `VarHandle`. It runs once per symbol at handle
  construction, not per call.
- Per-OS typed call sites in the backend module invoke the resulting `static final MethodHandle`.

Everything above the bindings (channels, IoHandler, config) is ordinary hand-written transport code.

Guidelines for the wrappers: invoke a `static final MethodHandle` via `invokeExact` (not
`invoke`/`Object[]`, which box and adapt at runtime); reuse a loop-confined capture segment rather
than allocating per call; return `(result, errno)` bit-packed in a `long`. `Linker.Option.critical()`
is worth evaluating for short non-blocking calls, but blocking calls (`read`/`write`/`kevent`) must
not use it, and its composition with `captureCallState` needs checking on the target JDK.

## Code generation and determinism

We author no C headers. jextract reads the OS's system headers directly from the platform SDK (e.g.
`<sys/socket.h>` from `$(xcrun --show-sdk-path)/usr/include`), and `--include-*` flags select the
symbols. It runs on-demand, never in the normal build, and the output is committed.

Generation is inherently per-OS: jextract parses with the host clang's predefined macros (`__APPLE__`,
`__linux__`, arch macros), so macOS bindings must be generated on macOS and Linux bindings in a
matching Linux toolchain (a pinned container works, including via Docker on a Mac). No `-I` flag
changes that.

Determinism is treated as a property of the artifact:

- Pin jextract; the plugin verifies `jextract --version` and fails on mismatch.
- Pin the SDK/toolchain and record its identity in the provenance manifest.
- A `GENERATED.properties` manifest sits beside the sources (jextract version, host OS, SDK id) and is
  deterministic (no timestamps or paths), so it reproduces byte-for-byte.
- A CI regeneration-diff job regenerates on the canonical pinned toolchain and fails on any diff. That
  job, not a developer's laptop, is the source of truth; it can be a manually-triggered workflow that
  opens the PR.

The POSIX socket / kqueue / epoll ABIs are frozen for binary compatibility, so cross-SDK-version drift
is unlikely in practice; the diff gate catches any exception.

See [Jextract.md](Jextract.md) for the plugin configuration and the recommended `<bindings>` layout.

## Build integration

- **Checkstyle / forbidden-apis.** The committed generated tree under `src/main/java` is scanned at
  `validate` and fails it (no copyright header, no `package-info.java`, line length, and so on). Exempt
  it, preferably module-locally with `<checkstyle.excludes>**/generated/**/*</checkstyle.excludes>`
  (and the forbidden-apis equivalent), or by adding a `generated/` entry to the shared
  `SuppressionFilter` in `netty-build-common` (a cross-artifact change). Needed before the first FFM
  module builds.
- **Java version.** The FFM API is stable since Java 22, but these modules baseline on Java 25 to
  match the target JDK; on the 5.0 branch they hard-require 25 directly rather than sitting behind a
  JDK-gated profile.
- **`module-info`** via the `io.github.dmlloyd.module-info` plugin. No `provides`: transport selection
  stays explicit (`KQueue.isAvailable() ? ... : NioIoHandler.newFactory()`), as with the other
  transports.
- **Runtime flags** for consumers and the testsuite: `--enable-native-access` for the module that
  performs the restricted calls (`netty-native`, since it builds the downcall handles) plus the usual
  `--add-opens java.base/sun.nio.ch=ALL-UNNAMED`. On the module path that target is the named module,
  `--enable-native-access=netty-native`; on the classpath the modules are unnamed, so it stays
  `--enable-native-access=ALL-UNNAMED`.
- **Aggregation.** Add the modules to the root `<modules>`, the BOM, and the `all` aggregator.

## Open questions

- Module naming for the macOS backend: `transport-ffm-kqueue` (OS-visible via the backend) vs a
  `transport-ffm-macos` shared module if/when a second macOS consumer (e.g. a DNS resolver) reuses the
  socket bindings. Deferred until that second consumer exists.
- Pinning the SDK sysroot so jextract always reads a fixed SDK's headers, which reproducible output
  depends on. The mechanism is not yet confirmed: likely the `SDKROOT` environment variable that
  `xcrun`/clang honor, or passing `-isysroot`/`--sysroot` through jextract's clang-argument passthrough,
  as jextract has no first-class flag for it. To be resolved before the CI diff gate is relied on; not
  addressed in this PR.
- `critical()` / `captureCallState` composition; benchmark before relying on it.

## Prerequisites for the first FFM module (not yet done)

- The checkstyle / forbidden-apis exemption above.
- CI: the per-OS regeneration-diff check and the arch-invariance byte-identical check (byte-identical
  for the socket core; per-arch outputs asserted separately for arch-variant event structs like
  `epoll_event`).
- Migrate the exploratory `ffm/` prototype into the real modules and delete it.
