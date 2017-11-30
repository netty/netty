/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.Mapping;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.net.ssl.SSLException;

public class SniClientTest {

    @Test(timeout = 30000)
    public void testSniClientJdkSslServerJdkSsl() throws Exception {
        testSniClient(SslProvider.JDK, SslProvider.JDK);
    }

    @Test(timeout = 30000)
    public void testSniClientOpenSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testSniClient(SslProvider.OPENSSL, SslProvider.OPENSSL);
    }

    @Test(timeout = 30000)
    public void testSniClientJdkSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testSniClient(SslProvider.JDK, SslProvider.OPENSSL);
    }

    @Test(timeout = 30000)
    public void testSniClientOpenSslServerJdkSsl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testSniClient(SslProvider.OPENSSL, SslProvider.JDK);
    }

    @Test(timeout = 30000)
    public void testSniSNIMatcherMatchesClientJdkSslServerJdkSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        SniClientJava8TestUtil.testSniClient(SslProvider.JDK, SslProvider.JDK, true);
    }

    @Test(timeout = 30000, expected = SSLException.class)
    public void testSniSNIMatcherDoesNotMatchClientJdkSslServerJdkSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        SniClientJava8TestUtil.testSniClient(SslProvider.JDK, SslProvider.JDK, false);
    }

    @Test(timeout = 30000)
    public void testSniSNIMatcherMatchesClientOpenSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.OPENSSL, SslProvider.OPENSSL, true);
    }

    @Test(timeout = 30000, expected = SSLException.class)
    public void testSniSNIMatcherDoesNotMatchClientOpenSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.OPENSSL, SslProvider.OPENSSL, false);
    }

    @Test(timeout = 30000)
    public void testSniSNIMatcherMatchesClientJdkSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.JDK, SslProvider.OPENSSL, true);
    }

    @Test(timeout = 30000, expected = SSLException.class)
    public void testSniSNIMatcherDoesNotMatchClientJdkSslServerOpenSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.JDK, SslProvider.OPENSSL, false);
    }

    @Test(timeout = 30000)
    public void testSniSNIMatcherMatchesClientOpenSslServerJdkSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.OPENSSL, SslProvider.JDK, true);
    }

    @Test(timeout = 30000, expected = SSLException.class)
    public void testSniSNIMatcherDoesNotMatchClientOpenSslServerJdkSsl() throws Exception {
        Assume.assumeTrue(PlatformDependent.javaVersion() >= 8);
        Assume.assumeTrue(OpenSsl.isAvailable());
        SniClientJava8TestUtil.testSniClient(SslProvider.OPENSSL, SslProvider.JDK, false);
    }

    private static void testSniClient(SslProvider sslClientProvider, SslProvider sslServerProvider) throws Exception {
        final String sniHost = "sni.netty.io";
        LocalAddress address = new LocalAddress("test");
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        Channel sc = null;
        Channel cc = null;
        try {
            SelfSignedCertificate cert = new SelfSignedCertificate();
            final SslContext sslServerContext = SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslProvider(sslServerProvider).build();

            final Promise<String> promise = group.next().newPromise();
            ServerBootstrap sb = new ServerBootstrap();
            sc = sb.group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addFirst(new SniHandler(new Mapping<String, SslContext>() {
                        @Override
                        public SslContext map(String input) {
                            promise.setSuccess(input);
                            return sslServerContext;
                        }
                    }));
                }
            }).bind(address).syncUninterruptibly().channel();

            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(sslClientProvider).build();
            Bootstrap cb = new Bootstrap();
            cc = cb.group(group).channel(LocalChannel.class).handler(new SslHandler(
                    sslContext.newEngine(ByteBufAllocator.DEFAULT, sniHost, -1)))
                    .connect(address).syncUninterruptibly().channel();
            Assert.assertEquals(sniHost, promise.syncUninterruptibly().getNow());
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
        }
    }
}
