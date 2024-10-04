# Modular Netty

This contains two main sections

- User guide
- Developer guide

## User guide

Netty can be used in modular JDK9+ applications as a collection of automatic modules.

Starting 4.2 Netty provides explicit modules, instead of named automatic modules previously.

The module names follow the reverse-DNS style, and are derived from subproject names rather than root packages due to
historical reasons. They are listed below:

* `io.netty.buffer`
* `io.netty.codec`
* `io.netty.codec.dns`
* `io.netty.codec.haproxy`
* `io.netty.codec.http`
* `io.netty.codec.http2`
* `io.netty.codec.memcache`
* `io.netty.codec.mqtt`
* `io.netty.codec.redis`
* `io.netty.codec.smtp`
* `io.netty.codec.socks`
* `io.netty.codec.stomp`
* `io.netty.codec.xml`
* `io.netty.codec.compression`
* `io.netty.codec.marshalling`
* `io.netty.codec.protobuf`
* `io.netty.common`
* `io.netty.handler`
* `io.netty.handler.proxy`
* `io.netty.handler.ssl.ocsp`
* `io.netty.internal.tcnative.openssl.${os.name}.${os.arch}`
* `io.netty.resolver`
* `io.netty.resolver.dns`
* `io.netty.resolver.dns.classes.macos`
* `io.netty.tcnative.classes.openssl`
* `io.netty.transport`
* `io.netty.transport.classes.${transport}` (`native` omitted - reserved keyword in Java)
* `io.netty.transport.${transport}` (`native` omitted - reserved keyword in Java)
* `io.netty.transport.${transport}.${os.name}.${os.arch}` (`native` omitted - reserved keyword in Java)
* `io.netty.transport.unix.common` (`native` omitted - reserved keyword in Java)

Where `transport` can be `epoll`,`kqueue` or `io_uring`.

As it stands, Netty does not use strong encapsulation, therefore all Java packages are exported.

A few Maven module do not anymore support JPMS (`netty-transport-rxtx`, `netty-transport-sctp`, `netty-transport-udt`).

A few module require dependencies on third-party libraries with variable degree of JPMS support, some of them
are optional, here is a recap:

| Module                       | Dependency                   | Status    | Optional |
|------------------------------|------------------------------|-----------|----------|
| `io.netty.common`            | `org.apache.commons.logging` | explicit  | yes      |
| `io.netty.common`            | `org.apache.log4j`           | explicit  | yes      |
| `io.netty.common`            | `org.apache.logging.log4j`   | explicit  | yes      |
| `io.netty.common`            | `org.slf4j`                  | explicit  | yes      |
| `io.netty.codec.protobuf`    | `com.google.protobuf`        | automatic | no       |
| `io.netty.codec.protobuf`    | `protobuf.nano`              | automatic | no       |
| `io.netty.codec.marshalling` | `jboss.marshalling`          | automatic | no       |
| `io.netty.codec.compression` | `com.aayushatharva.brotli4j` | explicit  | yes      |
| `io.netty.codec.compression` | `com.github.luben.zstd_jni`  | explicit  | yes      |
| `io.netty.codec.compression` | `compress.lzf`               | explicit  | yes      |
| `io.netty.codec.compression` | `jzlib`                      | automatic | yes      |
| `io.netty.codec.compression` | `lz4`                        | automatic | yes      |
| `io.netty.codec.compression` | `lzma.java`                  | automatic | yes      |
| `io.netty.codec.xml`         | `com.fasterxml.aalto`        | explicit  | no       |
| `io.netty.handler`           | `org.bouncycastle.pkix`      | explicit  | yes      |
| `io.netty.handler`           | `org.bouncycastle.provider`  | explicit  | yes      |
| `io.netty.handler`           | `org.conscrypt`              | automatic | yes      |

### The case of io.netty.codec

The `io-netty-codec` Maven module is split into 4 modules in Netty 4.2:

- `io-netty-codec-base`
  - declares the `io.netty.codec` Java module
  - contains the `io.netty.handler.codec` Java package
  - and a few built-in codecs that do not require dependencies `io.netty.handler.codec.(base64|bytes|json|serialization|string)` 
- `io-netty-codec-compression`
  - declares the `io.netty.codec.compression` Java module
  - contains the `io.netty.handler.codec.compression` Java package
  - depends on optional compression libraries, e.g. Brotli4j.
- `io-netty-codec-protobuf`
  - declares the `io.netty.codec.protobuf` Java module
  - contains the `io.netty.handler.codec.protobuf` Java package
  - depends on the Google Protobuf library
- `io-netty-codec-marshalling`
  - declare the `io.netty.codec.marshalling` Java module
  - contains the `io.netty.handler.codec.marshalling` Java package
  - depends on the JBoss Marshalling  library

In order to preserve backward compatibility the `io-netty-codec` Maven module depends on 

- `io-netty-codec-base`
- `io-netty-codec-compression`
- `io-netty-codec-protobuf`
- `io-netty-codec-marshalling`

This module also declares the `io.netty.codec.unused` JPMS module that is empty in order to comply to tools expecting
modular jars such as jlink.

Depending on `io-netty-codec` therefore brings `io-netty-compression`/`io-netty-protobuf`/`io-netty-marshalling`, you
can still exclude one of these dependencies if you don't need it. Alternatively you can depend on `io-netty-codec-base`.

### Native transports

Native transports are supported.

The module `io.netty.transport.classes.${transport}` is required as it contains the  transport classes
like `EpollServerSocketChannel`.

The module `io.netty.transport.${transport}.${os.name}.${os.arch}` contains the native library and its presence
is only required at runtime. It can be added as module required declaration or at runtime on the module path.
It is convenient to only depend on the transport classes and add the required transport native at runtime since the
`os` and `arch` will vary.

### OpenSSL

OpenSSL is supported.

The module `io.netty.tcnative.classes.openssl` is required as it contains the OpenSSL Netty classes.

The module `io.netty.internal.tcnative.openssl.${os.name}.${os.arch}` contains the native library and its presence
is only required at runtime. It can be added as module required declaration or at runtime on the module path.
It is convenient to only depend on the transport classes and add the required transport native at runtime since the
`os` and `arch` will vary.

### Compression

Compression modules are only required when the algorithm is actually used.

### Application images

The `jlink` tool can create application images, Netty does support it with restriction. Restrictions stem from
the dependencies which must be explicit modules (that is providing a `module-info.class` descriptor). You can
read the table above to learn more about them.

### Examples

There is so far not an extensive set of examples, however this module contains a simple HTTP server integrating
with native transport and OpenSSL. You can find the code [here](src/main/java/io/netty/testsuite_jpms/Main.java)

This project jar uses the [Apache Maven JLink Plugin](https://maven.apache.org/plugins/maven-jlink-plugin/) to 
produce an application image.

The server does provide info about the loaded modules as well as more server details:

```
Hello World
Transport: io.netty.channel.socket.nio.NioSocketChannel
Boot layer:
- io.netty.testsuite_jpms.main 
- java.base 
- io.netty.buffer 
- io.netty.codec.http 
- java.logging 
- io.netty.codec
- io.netty.transport 
- io.netty.handler 
- io.netty.common 
- io.netty.transport.classes.io_uring 
- jdk.unsupported 
- io.netty.resolver 
- io.netty.transport.unix.common 
- io.netty.transport.classes.epoll 
- io.netty.transport.classes.kqueue 
- io.netty.tcnative.classes.openssl 
```

You can run this image with `./target/maven-jlink/default/bin/java -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer`

```
julien@juliens-MBP-2 testsuite-jpms % ./target/maven-jlink/default/bin/http --help
usage: [options]
--ssl
--ssl-provider [ JDK | OPENSSL ]
--port <port>
--transport [ nio | kqueue | epoll | io_uring ]
```

- changing port `-m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --port 80`
- using SSL `-m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --ssl`
- using Open SSL `--add-modules io.netty.internal.tcnative.openssl.osx.aarch_64 -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --ssl --ssl-provider OPENSSL`
- using native transport `--add-modules io.netty.transport.kqueue.osx.aarch_64 -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --transport kqueue`

## Developer guide

This section aims to guide Netty developer who are not familiar with the Java Platform Module System (JPMS) and help
them contribute to Netty while paying attention to the requirements imposed by modules. For the record here
is the GitHub [issue](https://github.com/netty/netty/issues/14176) that contains the history of the required changes
so Netty 4.2 complies to JPMS.

Netty 4.2 provides explicit module declarations replacing the named automatic module found in Netty 4.1. Since
4.2 is based on Java 8, the [module-info](https://github.com/dmlloyd/module-info) Maven plugin creates `module-info.class`
files from a `module-info.yml` source file. The YML file is the equivalent of the `module-info.java` file.

You should be familiar with the basics of JPMS.

### Creating a new Java package

Creating a new Java package in a jar should not create a split package, it means this new package should not be used
anywhere in another Netty module. Unfortunately there is (so far) no tool for achieving that so we rely on the developer to avoid
creating such split packages. The implementation of Netty modules for 4.2 had eliminated a few remaining split packages
that where still standing in the project.

### Adding an external dependency

Adding a new module dependency should do best effort to use dependencies (transitively) that also support _explicit_ module,
an explicit module is a jar that contains a `module-info.class` descriptor at the root of the jar or in `META-INF/versions/*/`
for multi release jars (likewise Netty does).

Whenever that is not possible, named automatic module (that is a jar with an `Automatic-Module-Name` entry in `META-INF/MANIFEST.MF`)
can be tolerated at the cost of decoupling this dependency:

- using an optional dependency (`requires static`), for instance the `io.netty.codec.compression` provides the optional `jzlib` compression algorithm
- introducing a new maven module

### META-INF services

Netty does not interact much with them, the `ChannelInitializerExtension` is so far the only usage in the project,
therefore this extension is declared in the `module-info.yml` descriptor.

### Testing for JPMS

There is no strict requirement to test new features with JPMS, but adding integration test ensures that the feature works
with modules. Due to the dynamic nature of Java, bugs are often discovered at runtime when it is too late.

The `testsuite-jpms` contains a suite of tests that execute in a modular runtime.

The testsuite tries to run test with no class path and only the module path, the `pom.xml` Surefire section contains a
`classpathDependencyExcludes` to exclude all jars from the class path.

Testing with JPMS depends on the nature of what you want to test, usually such test tries to use the feature in a simple
manner.

### JPMS testsuite IDE support

Contributing to the JPMS testsuite with the default Netty project does not work best, since the IDE will be confused
due to the lack of module-info.java descriptors for Netty modules.

You can however load the [pom.xml](pom.xml) file as a standalone project, the IDE will usually find the modules
since they will be loaded from the local snapshot repository. 



