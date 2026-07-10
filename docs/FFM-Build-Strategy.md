# FFM transport build strategy

Design notes for adding `java.lang.foreign` (FFM) transports (starting with kqueue on macOS)
alongside the existing JNI transports: module layout, how the jextract-generated bindings are
produced and committed, and the build-integration work required. See [Jextract.md](Jextract.md) for
the generator plugin itself.

## Summary

- Each FFM transport mirrors the existing `transport-classes-*` / `transport-native-*` split, except
  the "native" half holds **committed jextract-generated Java** instead of a compiled library.
- Bindings are **per-OS** (jextract bakes ABI constants and struct layouts into the output) and
  **arch-invariant within an OS**, so one plain jar per OS serves all its architectures. There are no
  per-arch classifier jars.
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

| JNI module | FFM module | Contents |
|---|---|---|
| `transport-native-unix-common` | `transport-ffm-unix-common` | OS-agnostic hand-written support. Currently only the errno capture-state helper (see below). |
| `transport-native-kqueue` | `transport-ffm-native-kqueue` | Committed jextract output for macOS, the provenance manifest, and the per-OS errno-capture call sites. No C headers. |
| `transport-classes-kqueue` | `transport-ffm-classes-kqueue` | Hand-written kqueue transport: `IoHandler`, channels, configs. |

Linux follows the same shape (`transport-ffm-{native,classes}-epoll`, likewise `io_uring`).

**Dependency direction.** The JNI native jar is a runtime-only, per-arch classifier the user selects.
The FFM bindings are Java types the transport calls, so `transport-ffm-classes-kqueue` has a plain
compile dependency on `transport-ffm-native-kqueue` (no classifier). Consumers depend on the classes
module and get the bindings transitively; there is no platform-classifier selection step and jextract
is never required to build.

**One module per OS, not a cross-OS common module.** Generated sources bake in per-OS ABI values, and
committed source has no classifier idiom the way a compiled `transport-native-unix-common` does.
Per-OS modules avoid shipping other OSes' classes or profile-gating source roots. If a second same-OS
FFM consumer appears (e.g. a macOS DNS resolver), split out a per-OS common bindings module then.

**Package naming.** Generated code lives in an OS-named package, e.g.
`io.netty.channel.unix.ffm.generated.macos`, so the target OS is visible at the call site and in stack
traces and a cross-OS import stands out in review.

## Bindings are per-OS, arch-invariant within an OS

jextract bakes constant values (`AF_INET`), struct layouts and offsets (`sockaddr_in`), and type
sizes into the generated code, and the API itself differs (`kqueue` vs `epoll`). A binding set is
therefore valid for exactly one OS. Within an OS the supported architectures share an ABI (x86_64 and
aarch64 are both LP64), so the output is identical across them and one no-classifier jar per OS
suffices. A CI check (below) regenerates on both arches and asserts byte-identical output; a future
non-LP64 architecture would need its own binding set.

## Hand-written code: errno capture

jextract does not build its downcall handles with `Linker.Option.captureCallState("errno")`, so the
generated `socket`/`bind`/etc. methods cannot read errno atomically (a separate read is racy). Until
jextract can emit capture-state handles, errno-sensitive calls are hand-written:

- An OS-agnostic helper in `transport-ffm-unix-common` decorates a generated `FunctionDescriptor` with
  `captureCallState` and owns the cached errno `VarHandle`. It runs once per symbol at handle
  construction, not per call.
- Per-OS typed call sites in the bindings module invoke the resulting `static final MethodHandle`.

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
- **Java version.** FFM requires Java 25; gate the modules behind a JDK-25-activated profile (as the
  existing `java25` profile does) so older JDKs skip them. No multi-release jar needed.
- **`module-info`** via the `io.github.dmlloyd.module-info` plugin. No `provides`: transport selection
  stays explicit (`KQueue.isAvailable() ? ... : NioIoHandler.newFactory()`), as with the other
  transports.
- **Runtime flags** for consumers and the testsuite: `--enable-native-access=ALL-UNNAMED` plus the
  usual `--add-opens java.base/sun.nio.ch=ALL-UNNAMED`.
- **Aggregation.** Add the modules to the root `<modules>`, the BOM, and the `all` aggregator.

## Open questions

- Module naming: `transport-ffm-native-kqueue` vs `transport-ffm-bindings-kqueue`.
- JDK gating: profile-skip on JDK < 25 vs. hard-require 25 to build.
- Exact jextract option for pinning the SDK sysroot (may be `SDKROOT` or a clang-arg passthrough
  rather than a first-class flag).
- `critical()` / `captureCallState` composition; benchmark before relying on it.

## Prerequisites for the first FFM module (not yet done)

- The checkstyle / forbidden-apis exemption above.
- CI: the per-OS regeneration-diff check and the arch-invariance byte-identical check.
- Migrate the exploratory `ffm/` prototype into the real modules and delete it.
