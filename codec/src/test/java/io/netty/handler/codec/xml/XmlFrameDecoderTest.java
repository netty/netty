/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.xml;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class XmlFrameDecoderTest {

    private final List<String> xmlSamples =
            Arrays.asList(SAMPLE_01, SAMPLE_02, SAMPLE_03, SAMPLE_04);

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithIllegalArgs01() {
        new XmlFrameDecoder(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithIllegalArgs02() {
        new XmlFrameDecoder(-23);
    }

    @Test(expected = TooLongFrameException.class)
    public void testDecodeWithFrameExceedingMaxLength() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(3);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        ch.writeInbound(Unpooled.copiedBuffer("<v/>", CharsetUtil.UTF_8));
    }

    @Test(expected = CorruptedFrameException.class)
    public void testDecodeWithInvalidInput() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        ch.writeInbound(Unpooled.copiedBuffer("invalid XML", CharsetUtil.UTF_8));
    }

    @Test(expected = CorruptedFrameException.class)
    public void testDecodeWithInvalidContentBeforeXml() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        ch.writeInbound(Unpooled.copiedBuffer("invalid XML<foo/>", CharsetUtil.UTF_8));
    }

    @Test
    public void testDecodeShortValidXml() {
        testDecodeWithXml("<xxx/>", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace01() {
        testDecodeWithXml("   <xxx/>", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace02() {
        testDecodeWithXml("  \n\r \t<xxx/>\t", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace02AndTrailingGarbage() {
        testDecodeWithXml("  \n\r \t<xxx/>\ttrash", "<xxx/>", CorruptedFrameException.class);
    }

    @Test
    public void testDecodeWithTwoMessages() {
        testDecodeWithXml(
                "<root xmlns=\"http://www.acme.com/acme\" status=\"loginok\" timestamp=\"1362410583776\"/>\n" +
                        '\n' +
                        "<root xmlns=\"http://www.acme.com/acme\" status=\"start\" time=\"0\" timestamp=\"1362410584794\">\n" +
                        "<child active=\"1\" status=\"started\" id=\"935449\" msgnr=\"2\"/>\n" +
                        "</root>",
                "<root xmlns=\"http://www.acme.com/acme\" status=\"loginok\" timestamp=\"1362410583776\"/>",
                "<root xmlns=\"http://www.acme.com/acme\" status=\"start\" time=\"0\" timestamp=\"1362410584794\">\n" +
                        "<child active=\"1\" status=\"started\" id=\"935449\" msgnr=\"2\"/>\n" +
                        "</root>"
                );
    }

    @Test
    public void testDecodeWithSampleXml() {
        for (final String xmlSample : xmlSamples) {
            testDecodeWithXml(xmlSample, xmlSample);
        }
    }

    private static void testDecodeWithXml(String xml, Object... expected) {
        EmbeddedChannel ch = new EmbeddedChannel(new XmlFrameDecoder(1048576));
        Exception cause = null;
        try {
            ch.writeInbound(Unpooled.copiedBuffer(xml, CharsetUtil.UTF_8));
        } catch (Exception e) {
            cause = e;
            e.printStackTrace();
        }
        List<Object> actual = new ArrayList<Object>();
        for (;;) {
            ByteBuf buf = (ByteBuf) ch.readInbound();
            if (buf == null) {
                break;
            }
            actual.add(buf.toString(CharsetUtil.UTF_8));
        }

        if (cause != null) {
            actual.add(cause.getClass());
        }

        List<Object> expectedList = new ArrayList<Object>();
        Collections.addAll(expectedList, expected);
        assertThat(actual, is(expectedList));
    }

    private static final String SAMPLE_01 = "<root xmlns=\"http://www.acme.com/acme\" status=\"loginok\" timestamp=\"1362392496667\"/>";

    private static final String SAMPLE_02 = "<root xmlns=\"http://www.acme.com/acme\" status=\"start\" time=\"0\" timestamp=\"1362392497684\">\n" +
            "<child active=\"1\" status=\"started\" id=\"935449\" msgnr=\"2\"/>\n" +
            "</root>";

    private static final String SAMPLE_03 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!--\n" +
            "  ~ Copyright 2012 The Netty Project\n" +
            "  ~\n" +
            "  ~ The Netty Project licenses this file to you under the Apache License,\n" +
            "  ~ version 2.0 (the \"License\"); you may not use this file except in compliance\n" +
            "  ~ with the License. You may obtain a copy of the License at:\n" +
            "  ~\n" +
            "  ~   http://www.apache.org/licenses/LICENSE-2.0\n" +
            "  ~\n" +
            "  ~ Unless required by applicable law or agreed to in writing, software\n" +
            "  ~ distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT\n" +
            "  ~ WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the\n" +
            "  ~ License for the specific language governing permissions and limitations\n" +
            "  ~ under the License.\n" +
            "  -->\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
            '\n' +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <parent>\n" +
            "    <groupId>org.sonatype.oss</groupId>\n" +
            "    <artifactId>oss-parent</artifactId>\n" +
            "    <version>7</version>\n" +
            "  </parent>\n" +
            '\n' +
            "  <groupId>io.netty</groupId>\n" +
            "  <artifactId>netty-parent</artifactId>\n" +
            "  <packaging>pom</packaging>\n" +
            "  <version>4.0.0.Beta3-SNAPSHOT</version>\n" +
            '\n' +
            "  <name>Netty</name>\n" +
            "  <url>http://netty.io/</url>\n" +
            "  <description>\n" +
            "    Netty is an asynchronous event-driven network application framework for \n" +
            "    rapid development of maintainable high performance protocol servers and\n" +
            "    clients.\n" +
            "  </description>\n" +
            '\n' +
            "  <organization>\n" +
            "    <name>The Netty Project</name>\n" +
            "    <url>http://netty.io/</url>\n" +
            "  </organization>\n" +
            '\n' +
            "  <licenses>\n" +
            "    <license>\n" +
            "      <name>Apache License, Version 2.0</name>\n" +
            "      <url>http://www.apache.org/licenses/LICENSE-2.0</url>\n" +
            "    </license>\n" +
            "  </licenses>\n" +
            "  <inceptionYear>2008</inceptionYear>\n" +
            '\n' +
            "  <scm>\n" +
            "    <url>https://github.com/netty/netty</url>\n" +
            "    <connection>scm:git:git://github.com/netty/netty.git</connection>\n" +
            "    <developerConnection>scm:git:ssh://git@github.com/netty/netty.git</developerConnection>\n" +
            "    <tag>HEAD</tag>\n" +
            "  </scm>\n" +
            '\n' +
            "  <developers>\n" +
            "    <developer>\n" +
            "      <id>netty.io</id>\n" +
            "      <name>The Netty Project Contributors</name>\n" +
            "      <email>netty@googlegroups.com</email>\n" +
            "      <url>http://netty.io/</url>\n" +
            "      <organization>The Netty Project</organization>\n" +
            "      <organizationUrl>http://netty.io/</organizationUrl>\n" +
            "    </developer>\n" +
            "  </developers>\n" +
            '\n' +
            "  <properties>\n" +
            "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
            "    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>\n" +
            "    <jboss.marshalling.version>1.3.14.GA</jboss.marshalling.version>\n" +
            "  </properties>\n" +
            " \n" +
            "  <modules>\n" +
            "    <module>common</module>\n" +
            "    <module>buffer</module>\n" +
            "    <module>codec</module>\n" +
            "    <module>codec-http</module>\n" +
            "    <module>codec-socks</module>\n" +
            "    <module>transport</module>\n" +
            "    <module>transport-rxtx</module>\n" +
            "    <module>transport-sctp</module>\n" +
            "    <module>transport-udt</module>\n" +
            "    <module>handler</module>\n" +
            "    <module>example</module>\n" +
            "    <module>testsuite</module>\n" +
            "    <module>testsuite-osgi</module>\n" +
            "    <module>microbench</module>\n" +
            "    <module>all</module>\n" +
            "    <module>tarball</module>\n" +
            "  </modules>\n" +
            '\n' +
            "  <dependencyManagement>\n" +
            "    <dependencies>\n" +
            "      <!-- JBoss Marshalling - completely optional -->\n" +
            "      <dependency>\n" +
            "        <groupId>org.jboss.marshalling</groupId>\n" +
            "        <artifactId>jboss-marshalling</artifactId>\n" +
            "        <version>${jboss.marshalling.version}</version>\n" +
            "        <scope>compile</scope>\n" +
            "        <optional>true</optional>\n" +
            "      </dependency>\n" +
            "    \n" +
            "      <dependency>\n" +
            "        <groupId>com.google.protobuf</groupId>\n" +
            "        <artifactId>protobuf-java</artifactId>\n" +
            "        <version>2.4.1</version>\n" +
            "      </dependency>\n" +
            "      <dependency>\n" +
            "        <groupId>com.jcraft</groupId>\n" +
            "        <artifactId>jzlib</artifactId>\n" +
            "          <version>1.1.2</version>\n" +
            "      </dependency>\n" +
            '\n' +
            "      <dependency>\n" +
            "        <groupId>org.rxtx</groupId>\n" +
            "        <artifactId>rxtx</artifactId>\n" +
            "        <version>2.1.7</version>\n" +
            "      </dependency>\n" +
            '\n' +
            "      <dependency>\n" +
            "        <groupId>com.barchart.udt</groupId>\n" +
            "        <artifactId>barchart-udt-bundle</artifactId>\n" +
            "        <version>2.2.2</version>\n" +
            "      </dependency>\n" +
            '\n' +
            "      <dependency>\n" +
            "        <groupId>javax.servlet</groupId>\n" +
            "        <artifactId>servlet-api</artifactId>\n" +
            "        <version>2.5</version>\n" +
            "      </dependency>\n" +
            '\n' +
            "      <dependency>\n" +
            "        <groupId>org.slf4j</groupId>\n" +
            "        <artifactId>slf4j-api</artifactId>\n" +
            "        <version>1.7.2</version>\n" +
            "      </dependency>\n" +
            "      <dependency>\n" +
            "        <groupId>commons-logging</groupId>\n" +
            "        <artifactId>commons-logging</artifactId>\n" +
            "        <version>1.1.1</version>\n" +
            "      </dependency>\n" +
            "      <dependency>\n" +
            "        <groupId>log4j</groupId>\n" +
            "        <artifactId>log4j</artifactId>\n" +
            "        <version>1.2.17</version>\n" +
            "        <exclusions>\n" +
            "          <exclusion>\n" +
            "            <artifactId>mail</artifactId>\n" +
            "            <groupId>javax.mail</groupId>\n" +
            "          </exclusion>\n" +
            "          <exclusion>\n" +
            "            <artifactId>jms</artifactId>\n" +
            "            <groupId>javax.jms</groupId>\n" +
            "          </exclusion>\n" +
            "          <exclusion>\n" +
            "            <artifactId>jmxtools</artifactId>\n" +
            "            <groupId>com.sun.jdmk</groupId>\n" +
            "          </exclusion>\n" +
            "          <exclusion>\n" +
            "            <artifactId>jmxri</artifactId>\n" +
            "            <groupId>com.sun.jmx</groupId>\n" +
            "          </exclusion>\n" +
            "        </exclusions>\n" +
            "        <optional>true</optional>\n" +
            "      </dependency>\n" +
            "      \n" +
            "      <!-- Metrics providers -->\n" +
            "      <dependency>\n" +
            "        <groupId>com.yammer.metrics</groupId>\n" +
            "        <artifactId>metrics-core</artifactId>\n" +
            "        <version>2.2.0</version>\n" +
            "      </dependency>\n" +
            "      \n" +
            "      <!-- Test dependencies for jboss marshalling encoder/decoder -->\n" +
            "      <dependency>\n" +
            "        <groupId>org.jboss.marshalling</groupId>\n" +
            "        <artifactId>jboss-marshalling-serial</artifactId>\n" +
            "        <version>${jboss.marshalling.version}</version>\n" +
            "        <scope>test</scope>\n" +
            "       </dependency>\n" +
            "      <dependency>\n" +
            "        <groupId>org.jboss.marshalling</groupId>\n" +
            "        <artifactId>jboss-marshalling-river</artifactId>\n" +
            "        <version>${jboss.marshalling.version}</version>\n" +
            "        <scope>test</scope>\n" +
            "      </dependency>\n" +
            '\n' +
            "      <!-- Test dependencies for microbench -->\n" +
            "      <dependency>\n" +
            "        <groupId>com.google.caliper</groupId>\n" +
            "        <artifactId>caliper</artifactId>\n" +
            "        <version>0.5-rc1</version>\n" +
            "        <scope>test</scope>\n" +
            "      </dependency>\n" +
            "    </dependencies>\n" +
            "  </dependencyManagement>\n" +
            '\n' +
            "  <dependencies>\n" +
            "    <!-- Byte code generator - completely optional -->\n" +
            "    <dependency>\n" +
            "      <groupId>org.javassist</groupId>\n" +
            "      <artifactId>javassist</artifactId>\n" +
            "      <version>3.17.1-GA</version>\n" +
            "      <scope>compile</scope>\n" +
            "      <optional>true</optional>\n" +
            "    </dependency>\n" +
            '\n' +
            "    <!-- Testing frameworks and related dependencies -->\n" +
            "    <dependency>\n" +
            "      <groupId>junit</groupId>\n" +
            "      <artifactId>junit</artifactId>\n" +
            "      <version>4.10</version>\n" +
            "      <scope>test</scope>\n" +
            "      <exclusions>\n" +
            "        <!-- JUnit ships an older version of hamcrest. -->\n" +
            "        <exclusion>\n" +
            "          <groupId>org.hamcrest</groupId>\n" +
            "          <artifactId>hamcrest-core</artifactId>\n" +
            "        </exclusion>\n" +
            "      </exclusions>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>org.hamcrest</groupId>\n" +
            "      <artifactId>hamcrest-library</artifactId>\n" +
            "      <version>1.3</version>\n" +
            "      <scope>test</scope>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>org.easymock</groupId>\n" +
            "      <artifactId>easymock</artifactId>\n" +
            "      <version>3.1</version>\n" +
            "      <scope>test</scope>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>org.easymock</groupId>\n" +
            "      <artifactId>easymockclassextension</artifactId>\n" +
            "      <version>3.1</version>\n" +
            "      <scope>test</scope>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>org.jmock</groupId>\n" +
            "      <artifactId>jmock-junit4</artifactId>\n" +
            "      <version>2.5.1</version>\n" +
            "      <scope>test</scope>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>ch.qos.logback</groupId>\n" +
            "      <artifactId>logback-classic</artifactId>\n" +
            "      <version>1.0.9</version>\n" +
            "      <scope>test</scope>\n" +
            "    </dependency>\n" +
            "  </dependencies>\n" +
            '\n' +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-enforcer-plugin</artifactId>\n" +
            "        <version>1.1</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>enforce-tools</id>\n" +
            "            <goals>\n" +
            "              <goal>enforce</goal>\n" +
            "            </goals>\n" +
            "            <configuration>\n" +
            "              <rules>\n" +
            "                <requireJavaVersion>\n" +
            "                  <!-- Enforce java 1.7 as minimum for compiling -->\n" +
            "                  <!-- This is needed because of java.util.zip.Deflater and NIO UDP multicast-->\n" +
            "                  <version>[1.7.0,)</version>\n" +
            "                </requireJavaVersion>\n" +
            "                <requireMavenVersion>\n" +
            "                  <version>[3.0.5,)</version>\n" +
            "                </requireMavenVersion>\n" +
            "              </rules>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-compiler-plugin</artifactId>\n" +
            "        <version>2.5.1</version>\n" +
            "        <configuration>\n" +
            "          <compilerVersion>1.7</compilerVersion>\n" +
            "          <fork>true</fork>\n" +
            "          <source>1.6</source>\n" +
            "          <target>1.6</target>\n" +
            "          <debug>true</debug>\n" +
            "          <optimize>true</optimize>\n" +
            "          <showDeprecation>true</showDeprecation>\n" +
            "          <showWarnings>true</showWarnings>\n" +
            "          <!-- XXX: maven-release-plugin complains - MRELEASE-715 -->\n" +
            "          <!--\n" +
            "          <compilerArguments>\n" +
            "            <Xlint:-options />\n" +
            "            <Xlint:unchecked />\n" +
            "            <Xlint:deprecation />\n" +
            "          </compilerArguments>\n" +
            "          -->\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <!-- ensure that only methods available in java 1.6 can\n" +
            "             be used even when compiling with java 1.7+ -->\n" +
            "        <groupId>org.codehaus.mojo</groupId>\n" +
            "        <artifactId>animal-sniffer-maven-plugin</artifactId>\n" +
            "        <version>1.8</version>\n" +
            "        <configuration>\n" +
            "          <signature>\n" +
            "            <groupId>org.codehaus.mojo.signature</groupId>\n" +
            "            <artifactId>java16</artifactId>\n" +
            "            <version>1.0</version>\n" +
            "          </signature>\n" +
            "          <ignores>\n" +
            "            <ignore>sun.misc.Unsafe</ignore>\n" +
            "            <ignore>sun.misc.Cleaner</ignore>\n" +
            '\n' +
            "            <ignore>java.util.zip.Deflater</ignore>\n" +
            '\n' +
            "            <!-- Used for NIO UDP multicast -->\n" +
            "            <ignore>java.nio.channels.DatagramChannel</ignore>\n" +
            "            <ignore>java.nio.channels.MembershipKey</ignore>\n" +
            "            <ignore>java.net.StandardProtocolFamily</ignore>\n" +
            '\n' +
            "            <!-- Used for NIO. 2 -->\n" +
            "            <ignore>java.nio.channels.AsynchronousChannel</ignore>\n" +
            "            <ignore>java.nio.channels.AsynchronousSocketChannel</ignore>\n" +
            "            <ignore>java.nio.channels.AsynchronousServerSocketChannel</ignore>\n" +
            "            <ignore>java.nio.channels.AsynchronousChannelGroup</ignore>\n" +
            "            <ignore>java.nio.channels.NetworkChannel</ignore>\n" +
            "            <ignore>java.nio.channels.InterruptedByTimeoutException</ignore>\n" +
            "            <ignore>java.net.StandardSocketOptions</ignore>\n" +
            "            <ignore>java.net.SocketOption</ignore>\n" +
            "          </ignores>\n" +
            "        </configuration>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <phase>process-classes</phase>\n" +
            "            <goals>\n" +
            "              <goal>check</goal>\n" +
            "            </goals>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-checkstyle-plugin</artifactId>\n" +
            "        <version>2.9.1</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>check-style</id>\n" +
            "            <goals>\n" +
            "              <goal>check</goal>\n" +
            "            </goals>\n" +
            "            <phase>validate</phase>\n" +
            "            <configuration>\n" +
            "              <consoleOutput>true</consoleOutput>\n" +
            "              <logViolationsToConsole>true</logViolationsToConsole>\n" +
            "              <failsOnError>true</failsOnError>\n" +
            "              <failOnViolation>true</failOnViolation>\n" +
            "              <configLocation>io/netty/checkstyle.xml</configLocation>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "        <dependencies>\n" +
            "          <dependency>\n" +
            "            <groupId>${project.groupId}</groupId>\n" +
            "            <artifactId>netty-build</artifactId>\n" +
            "            <version>17</version>\n" +
            "          </dependency>\n" +
            "        </dependencies>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-surefire-plugin</artifactId>\n" +
            "        <configuration>\n" +
            "          <includes>\n" +
            "             <include>**/*Test*.java</include>\n" +
            "             <include>**/*Benchmark*.java</include>\n" +
            "          </includes>\n" +
            "          <excludes>\n" +
            "            <exclude>**/Abstract*</exclude>\n" +
            "            <exclude>**/TestUtil*</exclude>\n" +
            "          </excludes>\n" +
            "          <runOrder>random</runOrder>\n" +
            "          <argLine>\n" +
            "            -server \n" +
            "            -Dio.netty.resourceLeakDetection\n" +
            "            -dsa -da -ea:io.netty...\n" +
            "            -XX:+AggressiveOpts\n" +
            "            -XX:+TieredCompilation\n" +
            "            -XX:+UseBiasedLocking\n" +
            "            -XX:+UseFastAccessorMethods\n" +
            "            -XX:+UseStringCache\n" +
            "            -XX:+OptimizeStringConcat\n" +
            "            -XX:+HeapDumpOnOutOfMemoryError\n" +
            "          </argLine>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "      <!-- always produce osgi bundles -->\n" +
            "      <plugin>\n" +
            "        <groupId>org.apache.felix</groupId>\n" +
            "        <artifactId>maven-bundle-plugin</artifactId>\n" +
            "        <version>2.3.7</version>\n" +
            "        <extensions>true</extensions>\n" +
            "      </plugin>             \n" +
            "       <plugin>\n" +
            "        <artifactId>maven-source-plugin</artifactId>\n" +
            "        <version>2.1.2</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>attach-sources</id>\n" +
            "            <goals>\n" +
            "              <goal>jar</goal>\n" +
            "            </goals>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-javadoc-plugin</artifactId>\n" +
            "        <version>2.8.1</version>\n" +
            "        <configuration>\n" +
            "          <detectOfflineLinks>false</detectOfflineLinks>\n" +
            "          <breakiterator>true</breakiterator>\n" +
            "          <version>false</version>\n" +
            "          <author>false</author>\n" +
            "          <keywords>true</keywords>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-deploy-plugin</artifactId>\n" +
            "        <version>2.7</version>\n" +
            "        <configuration>\n" +
            "          <retryFailedDeploymentCount>10</retryFailedDeploymentCount>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <artifactId>maven-release-plugin</artifactId>\n" +
            "        <version>2.3.2</version>\n" +
            "        <configuration>\n" +
            "          <useReleaseProfile>false</useReleaseProfile>\n" +
            "          <arguments>-P release,sonatype-oss-release,full</arguments>\n" +
            "          <autoVersionSubmodules>true</autoVersionSubmodules>\n" +
            "          <allowTimestampedSnapshots>true</allowTimestampedSnapshots>\n" +
            "          <tagNameFormat>netty-@{project.version}</tagNameFormat>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "      \n" +
            "    </plugins>\n" +
            '\n' +
            "    <pluginManagement>\n" +
            "      <plugins>\n" +
            "        <!-- keep surefire and failsafe in sync -->\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-surefire-plugin</artifactId>\n" +
            "          <version>2.12</version>\n" +
            "        </plugin>\n" +
            "        <!-- keep surefire and failsafe in sync -->\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-failsafe-plugin</artifactId>\n" +
            "          <version>2.12</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-clean-plugin</artifactId>\n" +
            "          <version>2.5</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-resources-plugin</artifactId>\n" +
            "          <version>2.5</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-jar-plugin</artifactId>\n" +
            "          <version>2.4</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-dependency-plugin</artifactId>\n" +
            "          <version>2.4</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-assembly-plugin</artifactId>\n" +
            "          <version>2.3</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-jxr-plugin</artifactId>\n" +
            "          <version>2.2</version>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <artifactId>maven-antrun-plugin</artifactId>\n" +
            "          <version>1.7</version>\n" +
            "          <dependencies>\n" +
            "            <dependency>\n" +
            "              <groupId>ant-contrib</groupId>\n" +
            "              <artifactId>ant-contrib</artifactId>\n" +
            "              <version>1.0b3</version>\n" +
            "              <exclusions>\n" +
            "                <exclusion>\n" +
            "                  <groupId>ant</groupId>\n" +
            "                  <artifactId>ant</artifactId>\n" +
            "                </exclusion>\n" +
            "              </exclusions>\n" +
            "            </dependency>\n" +
            "          </dependencies>\n" +
            "        </plugin>\n" +
            "        <plugin>\n" +
            "          <groupId>org.codehaus.mojo</groupId>\n" +
            "          <artifactId>build-helper-maven-plugin</artifactId>\n" +
            "          <version>1.7</version>\n" +
            "        </plugin>               \n" +
            '\n' +
            "        <!-- Workaround for the 'M2E plugin execution not covered' problem.\n" +
            "             See: http://wiki.eclipse.org/M2E_plugin_execution_not_covered -->\n" +
            "        <plugin>\n" +
            "          <groupId>org.eclipse.m2e</groupId>\n" +
            "          <artifactId>lifecycle-mapping</artifactId>\n" +
            "          <version>1.0.0</version>\n" +
            "          <configuration>\n" +
            "            <lifecycleMappingMetadata>\n" +
            "              <pluginExecutions>\n" +
            "                <pluginExecution>\n" +
            "                  <pluginExecutionFilter>\n" +
            "                    <groupId>org.apache.maven.plugins</groupId>\n" +
            "                    <artifactId>maven-checkstyle-plugin</artifactId>\n" +
            "                    <versionRange>[1.0,)</versionRange>\n" +
            "                    <goals>\n" +
            "                      <goal>check</goal>\n" +
            "                    </goals>\n" +
            "                  </pluginExecutionFilter>\n" +
            "                  <action>\n" +
            "                    <execute>\n" +
            "                      <runOnIncremental>false</runOnIncremental>\n" +
            "                    </execute>\n" +
            "                  </action>\n" +
            "                </pluginExecution>\n" +
            "                <pluginExecution>\n" +
            "                  <pluginExecutionFilter>\n" +
            "                    <groupId>org.apache.maven.plugins</groupId>\n" +
            "                    <artifactId>maven-enforcer-plugin</artifactId>\n" +
            "                    <versionRange>[1.0,)</versionRange>\n" +
            "                    <goals>\n" +
            "                      <goal>enforce</goal>\n" +
            "                    </goals>\n" +
            "                  </pluginExecutionFilter>\n" +
            "                  <action>\n" +
            "                    <execute>\n" +
            "                      <runOnIncremental>false</runOnIncremental>\n" +
            "                    </execute>\n" +
            "                  </action>\n" +
            "                </pluginExecution>\n" +
            "                <pluginExecution>\n" +
            "                  <pluginExecutionFilter>\n" +
            "                    <groupId>org.apache.maven.plugins</groupId>\n" +
            "                    <artifactId>maven-clean-plugin</artifactId>\n" +
            "                    <versionRange>[1.0,)</versionRange>\n" +
            "                    <goals>\n" +
            "                      <goal>clean</goal>\n" +
            "                    </goals>\n" +
            "                  </pluginExecutionFilter>\n" +
            "                  <action>\n" +
            "                    <execute>\n" +
            "                      <runOnIncremental>false</runOnIncremental>\n" +
            "                    </execute>\n" +
            "                  </action>\n" +
            "                </pluginExecution>\n" +
            "              </pluginExecutions>\n" +
            "            </lifecycleMappingMetadata>\n" +
            "          </configuration>\n" +
            "        </plugin>\n" +
            "      </plugins>\n" +
            "    </pluginManagement>\n" +
            "  </build>\n" +
            "</project>";

    private static final String SAMPLE_04 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!--\n" +
            "  ~ Copyright 2012 The Netty Project\n" +
            "  ~\n" +
            "  ~ The Netty Project licenses this file to you under the Apache License,\n" +
            "  ~ version 2.0 (the \"License\"); you may not use this file except in compliance\n" +
            "  ~ with the License. You may obtain a copy of the License at:\n" +
            "  ~\n" +
            "  ~   http://www.apache.org/licenses/LICENSE-2.0\n" +
            "  ~\n" +
            "  ~ Unless required by applicable law or agreed to in writing, software\n" +
            "  ~ distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT\n" +
            "  ~ WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the\n" +
            "  ~ License for the specific language governing permissions and limitations\n" +
            "  ~ under the License.\n" +
            "  -->\n" +
            "<FindBugsFilter>\n" +
            "  <!-- Tests -->\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*Test(\\$[^\\$]+)*\"/>\n" +
            "  </Match>\n" +
            "  <!-- Generated code -->\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*\\.LocalTimeProtocol(\\$[^\\$]+)*\"/>\n" +
            "  </Match>\n" +
            "  <!-- Noise -->\n" +
            "  <Match>\n" +
            "    <Bug code=\"Co,SF\"\n" +
            "         category=\"I18N\"\n" +
            "         pattern=\"REC_CATCH_EXCEPTION,UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR,DB_DUPLICATE_SWITCH_CLAUSES,VO_VOLATILE_REFERENCE_TO_ARRAY\" />\n" +
            "  </Match>\n" +
            "  <!-- Known false positives -->\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*Channel(Group)?Future\"/>\n" +
            "    <Method name=\"~await.*\"/>\n" +
            "    <Bug pattern=\"PS_PUBLIC_SEMAPHORES\"/>\n" +
            "  </Match>\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*SelectorLoop\"/>\n" +
            "    <Method name=\"run\"/>\n" +
            "    <Bug code=\"ESync\"/>\n" +
            "  </Match>\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*Channel\"/>\n" +
            "    <Or>\n" +
            "      <Method name=\"setClosed\"/>\n" +
            "      <Method name=\"setInterestOpsNow\"/>\n" +
            "    </Or>\n" +
            "    <Bug pattern=\"USM_USELESS_SUBCLASS_METHOD\"/>\n" +
            "  </Match>\n" +
            "  <Match>\n" +
            "    <Class name=\"~.*HttpTunnelingChannelHandler\"/>\n" +
            "    <Method name=\"~await.*\"/>\n" +
            "    <Bug pattern=\"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE,RV_RETURN_VALUE_IGNORED2\"/>\n" +
            "  </Match>\n" +
            "  <!-- Known issues that don't matter -->\n" +
            "  <Match>\n" +
            "    <Or>\n" +
            "      <Class name=\"~.*\\.util\\.internal\\.Concurrent[A-Za-z]*HashMap(\\$[^\\$]+)*\"/>\n" +
            "      <Class name=\"~.*\\.util\\.internal\\..*TransferQueue(\\$[^\\$]+)*\"/>\n" +
            "      <Class name=\"~.*\\.util\\.internal\\.MapBackedSet\"/>\n" +
            "    </Or>\n" +
            "    <Bug pattern=\"SE_TRANSIENT_FIELD_NOT_RESTORED,SE_BAD_FIELD\"/>\n" +
            "  </Match>\n" +
            "</FindBugsFilter>";

}
