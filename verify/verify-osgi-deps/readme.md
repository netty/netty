### info

this module build will fail if any dependencies are not osgi bundles

http://en.wikipedia.org/wiki/OSGi#Bundles

on failure, build log will show error message similar to the following:
(org.rxtx/rxtx/2.1.7 is not osgi bundle)
```
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running io.netty.verify.osgi.IT
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<features xmlns="http://karaf.apache.org/xmlns/features/v1.2.0" name="netty-verify-osgi-deps">
    <feature name="netty-verify-osgi-deps" version="4.0.0.Beta1-SNAPSHOT" description="Netty/Verify/OSGi/Deps">
        <details>verify osgi compliance: all transitive dependencies are osgi bundles</details>
        <bundle>mvn:io.netty/netty-buffer/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-common/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-codec/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-transport/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-codec-http/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-handler/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-codec-socks/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-transport-rxtx/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>wrap:mvn:org.rxtx/rxtx/2.1.7</bundle>
        <bundle>mvn:io.netty/netty-transport-sctp/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:io.netty/netty-transport-udt/4.0.0.Beta1-SNAPSHOT</bundle>
        <bundle>mvn:com.barchart.udt/barchart-udt-bundle/2.2.0</bundle>
    </feature>
</features>

Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.038 sec <<< FAILURE!
verifyNoWrapProtocol(io.netty.verify.osgi.IT)  Time elapsed: 0.014 sec  <<< FAILURE!
java.lang.AssertionError: karaf feature.xml contains 'wrap:' protocol: some transitive dependencies are not osgi bundles
	at org.junit.Assert.fail(Assert.java:93)
	at io.netty.verify.osgi.IT.verifyNoWrapProtocol(IT.java:48)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:601)
	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:45)
	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:15)
	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:42)
	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:20)
	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:263)
	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:68)
	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:47)
	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:231)
	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:60)
	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:229)
	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:50)
	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:222)
	at org.junit.runners.ParentRunner.run(ParentRunner.java:300)
	at org.apache.maven.surefire.junit4.JUnit4Provider.execute(JUnit4Provider.java:252)
	at org.apache.maven.surefire.junit4.JUnit4Provider.executeTestSet(JUnit4Provider.java:141)
	at org.apache.maven.surefire.junit4.JUnit4Provider.invoke(JUnit4Provider.java:112)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:601)
	at org.apache.maven.surefire.util.ReflectionUtils.invokeMethodWithArray(ReflectionUtils.java:189)
	at org.apache.maven.surefire.booter.ProviderFactory$ProviderProxy.invoke(ProviderFactory.java:165)
	at org.apache.maven.surefire.booter.ProviderFactory.invokeProvider(ProviderFactory.java:85)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:115)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:75)
```
