# jextract Maven Plugin

`netty5-jextract-maven-plugin` generates `java.lang.foreign` (FFM) bindings by driving a pre-installed
[`jextract`](https://jdk.java.net/jextract/), one invocation per `<binding>`. Output is **committed
to git**; ordinary builds and CI compile it and never run jextract. Regenerate only when the requested
symbols change.

## Install jextract

A developer prerequisite, **never downloaded by the build**. Install from
<https://jdk.java.net/jextract/>. The plugin resolves it from the `jextract` parameter
(`-Djextract=…`), then `$JEXTRACT`, then `PATH`, failing with an actionable message if none is
executable. (PATH lookup is POSIX-only; on Windows point at the binary explicitly.)

## Configure

```xml
<plugin>
  <groupId>io.netty</groupId>
  <artifactId>netty5-jextract-maven-plugin</artifactId>
  <version>${project.version}</version>
  <configuration>
    <targetPackage>io.netty.channel.unix.ffm.generated</targetPackage>
    <jextractVersion>22</jextractVersion>
    <bindings>
      <binding>
        <header>sys/socket.h</header>
        <className>BsdSocket</className>
        <functions>
          <function>socket</function>
          <function>bind</function>
        </functions>
        <constants>
          <constant>AF_INET</constant>
        </constants>
      </binding>
    </bindings>
  </configuration>
</plugin>
```

| Parameter | Req | Default | Notes |
|---|---|---|---|
| `targetPackage` | yes | none | Package for generated code (`--target-package`). |
| `jextractVersion` | yes | none | Pinned version (`jextract.version`); the goal runs `jextract --version` and fails on mismatch. |
| `bindings` | no | none | One `<binding>` per class; unique `className`. Empty = no-op. |
| `sdk` | no | none | Toolchain id (e.g. `MacOSX14.5`, `jextract.sdk`); recorded in the provenance manifest, not verified. |
| `outputDirectory` | no | `…/src/main/java/generated` | Where sources are written (committed). Must share a filesystem with the build directory (the default does), since promotion is an atomic rename. |
| `jextract` | no | none | Explicit path (`jextract`); overrides `$JEXTRACT`/`PATH`. |
| `timeoutSeconds` | no | `300` | Per-invocation timeout (`jextract.timeoutSeconds`); `0` = unbounded. |

Each `<binding>`: `header` (passed to jextract **verbatim**, libclang resolves it against the active
SDK, so a system path like `sys/socket.h` works with no vendored copy and no `-I`/`-D`), `className`
(`--header-class-name`), and the symbol lists mapping to `--include-*`: `functions`, `structs`,
`constants`, `typedefs`, `unions`, `vars`.

## Binding layout: one type per binding

jextract writes each **struct / union / aggregate typedef** to a standalone file named after the C
symbol (`sockaddr_in.java`), so two bindings requesting the same one clobber each other, the plugin
fails the build if they do. Functions, constants, and vars live *inside* their header class and never
clash. Therefore:

- Own each struct/union/aggregate typedef in **one** binding (a types-only binding per header).
- Other bindings list only functions/constants/vars and use those types **by pointer** (`C_POINTER`,
  no `--include` needed).
- Don't list **primitive** typedefs (e.g. `socklen_t`), jextract inlines them, so they emit no file.
- A function taking/returning a struct/union **by value** must sit in the same binding as that type
  (jextract errors otherwise); rare for the pointer-based POSIX/kqueue APIs.

## Regenerate

Not bound to a lifecycle phase, run explicitly on the **matching OS**, then commit the output:

```bash
mvn -pl <module> io.netty:netty5-jextract-maven-plugin:generate -Djextract.version=22
```

- **Deterministic per OS/arch.** macOS bindings are ABI-identical across x86_64/arm64, regenerating
  on both must be byte-identical.
- **Atomic + pruning.** Generates into a staging dir and, only on full success, prunes the
  `targetPackage` subtree and swaps the fresh output in, no orphans, and a failed run never touches
  the committed tree. Hand-written code must live outside `targetPackage`.
- **Provenance.** Writes a deterministic `GENERATED.properties` (`jextract.version`, `os`, `sdk`) with
  no timestamps or machine paths, so a toolchain change surfaces as a reviewable diff.
