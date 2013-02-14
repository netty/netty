### info

this module build will fail if any dependencies introduce osgi split package

http://wiki.osgi.org/wiki/Split_Packages

on failure, build log will show error message similar to the following:

```
[INFO] --- maven-bundle-plugin:2.3.7:bundle (default-bundle) @ netty-verify-osgi ---
[ERROR] Bundle io.netty:netty-verify-osgi:bundle:4.0.0.Beta1-SNAPSHOT : Split package io/netty/buffer
Use directive -split-package:=(merge-first|merge-last|error|first) on Export/Private Package instruction to get rid of this warning
Package found in   [Jar:netty-buffer, Jar:netty-transport-udt]
Reference from     /home/user1/.m2/repository/io/netty/netty-transport-udt/4.0.0.Beta1-SNAPSHOT/netty-transport-udt-4.0.0.Beta1-SNAPSHOT.jar
Classpath          [Jar:., Jar:netty-buffer, Jar:netty-codec, Jar:netty-codec-http, Jar:netty-codec-socks, Jar:netty-common, Jar:netty-handler, Jar:netty-transport, Jar:netty-transport-rxtx, Jar:rxtx, Jar:netty-transport-sctp, Jar:netty-transport-udt, Jar:barchart-udt-bundle]
[ERROR] Error(s) found in bundle configuration
```
