# Transport Security (TLS)

Although the [HTTP/2 RFC](https://tools.ietf.org/html/rfc7540#section-3.3) does not require using TLS the RFC does enforce requirements if TLS is in use [[1](https://tools.ietf.org/html/rfc7540#section-9.2)][[2](https://tools.ietf.org/html/rfc7540#section-3.3)][[3](https://tools.ietf.org/html/rfc7540#section-3.4)]. 
HTTP/2 over TLS mandates the use of [ALPN](https://tools.ietf.org/html/rfc7301) to negotiate the use of the `h2` protocol. ALPN is a fairly new standard and (where possible) Netty supports protocol negotiation via [NPN](https://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04) for systems that do not yet support ALPN.

## TLS with OpenSSL

This is currently the recommended approach for doing TLS with Netty.

### Benefits of using OpenSSL

1. **Speed**: In local testing, we've seen performance improvements of 3x over the JDK. GCM, which is used by the only cipher suite required by the [HTTP/2 RFC](https://tools.ietf.org/html/rfc7540#section-9.2.2), is 10-500x faster.
2. **Ciphers**: OpenSSL has its own ciphers and is not dependent on the limitations of the JDK. This allows supporting GCM on Java 7.
3. **ALPN to NPN Fallback**: OpenSSL can support ALPN and NPN simultaneously. The JDK implementation by Netty only supports either ALPN or NPN at any given time and [NPN is only supported in JDK 7](https://wiki.eclipse.org/Jetty/Feature/NPN).
4. **Java Version Independence**: does not require using a different library version depending on the JDK update. This is a limitation of the JDK ALPN and NPN implementation used by Netty.

### Requirements for using OpenSSL

1. [OpenSSL](https://www.openssl.org/) version >= 1.0.2 for ALPN support, or version >= 1.0.1 for NPN.
2. [netty-tcnative](https://github.com/netty/netty-tcnative) version >= 1.1.33.Fork7 must be on classpath.
3. Supported platforms (for netty-tcnative): `linux-x86_64`, `mac-x86_64`, `windows-x86_64`. Supporting other platforms will require manually building netty-tcnative.

If the above requirements are met, Netty will automatically select OpenSSL as the default TLS provider.

### Configuring netty-tcnative

[Netty-tcnative](https://github.com/netty/netty-tcnative) is a fork of [Apache Tomcat's tcnative](http://tomcat.apache.org/native-doc/). Netty primarily uses it as a JNI wrapper around OpenSSL.

Netty uses classifiers when deploying to [Maven Central](http://repo1.maven.org/maven2/io/netty/netty-tcnative/) to provide distributions for the various platforms. On Linux it should be noted that OpenSSL uses a different soname for Fedora derivatives than other Linux releases. To work around this limitation, netty-tcnative deploys two separate versions for Linux.

Classifier | Description
---------------- | -----------
windows-x86_64 | Windows distribution
osx-x86_64 | Mac distribution
linux-x86_64 | Used for non-Fedora derivatives of Linux
linux-x86_64-fedora | Used for Fedora derivatives

*NOTE: Make sure you use a version of netty-tcnative >= 1.1.33.Fork7.  Prior versions only supported NPN and only Fedora-derivatives were supported for Linux.*

#### Getting netty-tcnative from Maven

In Maven, you can use the [os-maven-plugin](https://github.com/trustin/os-maven-plugin) to help simplify the dependency.

```xml
<project>
  <dependencies>
    <dependency>
      <groupId>io.netty</groupId>
      <artifactId>netty-tcnative</artifactId>
      <version>1.1.33.Fork9</version>
      <classifier>${tcnative.classifier}</classifier>
    </dependency>
  </dependencies>

  <build>
    <extensions>
      <!-- Use os-maven-plugin to initialize the "os.detected" properties -->
      <extension>
        <groupId>kr.motd.maven</groupId>
        <artifactId>os-maven-plugin</artifactId>
        <version>1.4.0.Final</version>
      </extension>
    </extensions>
    <plugins>
      <!-- Use Ant to configure the appropriate "tcnative.classifier" property -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-antrun-plugin</artifactId>
        <executions>
          <execution>
            <phase>initialize</phase>
            <configuration>
              <exportAntProperties>true</exportAntProperties>
              <target>
                <condition property="tcnative.classifier"
                           value="${os.detected.classifier}-fedora"
                           else="${os.detected.classifier}">
                  <isset property="os.detected.release.fedora"/>
                </condition>
              </target>
            </configuration>
            <goals>
              <goal>run</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

#### Getting netty-tcnative from Gradle

There is a Gradle plugin [osdetector-gradle-plugin](https://github.com/google/osdetector-gradle-plugin), which is a wrapper around the os-maven-plugin.

```gradle
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath 'com.google.gradle:osdetector-gradle-plugin:1.4.0'
  }
}

// Use the osdetector-gradle-plugin
apply plugin: "com.google.osdetector"

def tcnative_classifier = osdetector.classifier;
// Fedora variants use a different soname for OpenSSL than other linux distributions
// (see http://netty.io/wiki/forked-tomcat-native.html).
if (osdetector.os == "linux" && osdetector.release.isLike("fedora")) {
  tcnative_classifier += "-fedora";
}

dependencies {
    compile 'io.netty:netty-tcnative:1.1.33.Fork9:' + tcnative_classifier
}
```

## TLS with JDK (Jetty ALPN/NPN)

If you are not able to use OpenSSL then the alternative is to use the JDK for TLS.

Java does not currently support ALPN or NPN ([there is a tracking issue](https://bugs.openjdk.java.net/browse/JDK-8051498) so go upvote it!). For lack of support in the JDK we need to use the [Jetty-ALPN](https://github.com/jetty-project/jetty-alpn") (or [Jetty-NPN](https://github.com/jetty-project/jetty-npn) if on Java < 8) bootclasspath extension for OpenJDK. To do this, add a `Xbootclasspath` JVM option referencing the path to the Jetty `alpn-boot` jar.

```sh
java -Xbootclasspath/p:/path/to/jetty/alpn/extension.jar ...
```

Note that you must use the [release of the Jetty-ALPN jar](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions) specific to the version of Java you are using.

### JDK Ciphers

Java 7 does not support [the cipher suites recommended](https://tools.ietf.org/html/rfc7540#section-9.2.2) by the HTTP2 RFC. To address this we suggest servers use Java 8 where possible or use an alternative JCE implementation such as [Bouncy Castle](https://www.bouncycastle.org/java.html). If this is not practical it is possible to use other ciphers but you need to ensure that the services you intend to call also support these ciphers forbidden by the HTTP/2 RFC and have evaluated the security risks of doing so.

Users should be aware that GCM is [_very_ slow (1 MB/s)](https://bugzilla.redhat.com/show_bug.cgi?id=1135504) before Java 8u60. With Java 8u60 GCM is 10x faster (10-20 MB/s), but that is still slow compared to OpenSSL (~200 MB/s), especially with AES-NI support (~1 GB/s). GCM cipher suites are the only suites available that comply with HTTP2's cipher requirements.

## Enabling ALPN or NPN

The [SslContextBuilder](https://github.com/netty/netty/blob/4.1/handler/src/main/java/io/netty/handler/ssl/SslContextBuilder.java#L279) has a setter for an [ApplicationProtocolConfig](https://github.com/netty/netty/blob/4.1/handler/src/main/java/io/netty/handler/ssl/ApplicationProtocolConfig.java) which is used to configure ALPN or NPN. See the [HTTP/2 examples](https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example/http2/helloworld) for ALPN and [SPDY examples](https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example/spdy) for NPN usage.