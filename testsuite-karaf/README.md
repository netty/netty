# Netty/Karaf integration test suite

This directory contains a simple test to ensure Netty bundles can be resolved
in an Apache Karaf environment.

## Design

This test is structured as a Karaf Feature, hosted in `src/main/feature/feature.xml`, which lists tested budles
and contains instructions for `karaf-maven-plugin` to build an Karaf Archive. This includes a quick test ensuring
the bundles can be resolved in a plain Karaf environment when run with `maven.compiler.target`.

Please note that both `pom.xml` and `feature.xml` need to be list the bundles to be verified. The former ensures
the bundle is built before test and the latter ensures it is tested.

## Diagnostics

A typical failure without `mvn -e` looks like this:

    [ERROR] Failed to execute goal org.apache.karaf.tooling:karaf-maven-plugin:4.4.8:verify (default-verify) on project testsuite-karaf: Verification failures: Verification failures:
    [ERROR]         Feature resolution failed for [test/4.2.5.Final-SNAPSHOT]
    [ERROR] Message: Unable to resolve root: missing requirement [root] osgi.identity; osgi.identity=test; type=karaf.feature; version=4.2.5.Final-SNAPSHOT; filter:="(&(osgi.identity=test)(type=karaf.feature)(version>=4.2.5.Final-SNAPSHOT))" [caused by: Unable to resolve test/4.2.5.Final-SNAPSHOT: missing requirement [test/4.2.5.Final-SNAPSHOT] osgi.identity; osgi.identity=io.netty.buffer; type=osgi.bundle; version="[4.2.5.Final-SNAPSHOT,4.2.5.Final-SNAPSHOT]"; resolution:=mandatory [caused by: Unable to resolve io.netty.buffer/4.2.5.Final-SNAPSHOT: missing requirement [io.netty.buffer/4.2.5.Final-SNAPSHOT] osgi.wiring.package; filter:="(osgi.wiring.package=jdk.jfr)"]]
    [ERROR] Repositories: {
    [ERROR]         file:/home/nite/prj/netty/testsuite-karaf/target/feature/feature.xml
    [ERROR]         mvn:org.apache.karaf.features/framework/4.4.8/xml/features
    [ERROR] }
    [ERROR] Resources: {
    [ERROR]         mvn:io.netty/netty-buffer/4.2.5.Final-SNAPSHOT
    [ERROR]         mvn:io.netty/netty-common/4.2.5.Final-SNAPSHOT
    [ERROR]         mvn:io.netty/netty-resolver/4.2.5.Final-SNAPSHOT
    [ERROR] }

Here the `Message` shows the underlying problem, but it is a bit hard to read, because it is encoding an causality
stack. The last part indicates what went wrong:

    Unable to resolve io.netty.buffer/4.2.5.Final-SNAPSHOT: missing requirement [io.netty.buffer/4.2.5.Final-SNAPSHOT] osgi.wiring.package; filter:="(osgi.wiring.package=jdk.jfr)"

What this is telling us is that `netty-buffer` cannot be installed because it requires the `jdk.jfr` Java package,
which is not provided by Karaf.

Note that the `filter` part looks simple above, but can look quite arcane, perhaps like

    filter:="(&(osgi.ee=JavaSE)(version=1.8))"

This is not that difficult, as it really is just an RFC1960 LDAP search filter. As such, it is using Polish Notation
to say that the [execution environment](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module-execution.environment)
needs to be compatible with `JavaSE-1.8`.
