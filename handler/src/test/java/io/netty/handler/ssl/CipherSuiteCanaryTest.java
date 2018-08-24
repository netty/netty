/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertTrue;

/**
 * The purpose of this unit test is to act as a canary and catch changes in supported cipher suites.
 */
@RunWith(Parameterized.class)
public class CipherSuiteCanaryTest {

    private static EventLoopGroup GROUP;

    private static SelfSignedCertificate CERT;

    @Parameters(name = "{index}: serverSslProvider = {0}, clientSslProvider = {1}, rfcCipherName = {2}")
    public static Collection<Object[]> parameters() {
       List<Object[]> dst = new ArrayList<Object[]>();
       dst.addAll(expand("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")); // DHE-RSA-AES128-GCM-SHA256
       return dst;
    }

    @BeforeClass
    public static void init() throws Exception {
        GROUP = new DefaultEventLoopGroup();
        CERT = new SelfSignedCertificate();
    }

    @AfterClass
    public static void destory() {
        GROUP.shutdownGracefully();
        CERT.delete();
    }

    private final SslProvider serverSslProvider;

    private final SslProvider clientSslProvider;

    private final String rfcCipherName;

    public CipherSuiteCanaryTest(SslProvider serverSslProvider, SslProvider clientSslProvider, String rfcCipherName) {
        this.serverSslProvider = serverSslProvider;
        this.clientSslProvider = clientSslProvider;
        this.rfcCipherName = rfcCipherName;
    }

    @Test
    public void testHandshake() throws Exception {
        Assume.assumeTrue("Unsupported cipher: " + rfcCipherName, OpenSsl.isCipherSuiteAvailable(rfcCipherName));

        List<String> ciphers = Arrays.asList(rfcCipherName);

        final SslContext sslServerContext = SslContextBuilder.forServer(CERT.certificate(), CERT.privateKey())
                .sslProvider(serverSslProvider)
                .ciphers(ciphers)
                .build();

        try {
            final SslContext sslClientContext = SslContextBuilder.forClient()
                    .sslProvider(clientSslProvider)
                    .ciphers(ciphers)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            try {
                final ByteBuf request = Unpooled.wrappedBuffer(new byte[] {'P', 'I', 'N', 'G'});
                final ByteBuf response = Unpooled.wrappedBuffer(new byte[] {'P', 'O', 'N', 'G'});

                final Promise<Object> serverPromise = GROUP.next().newPromise();
                final Promise<Object> clientPromise = GROUP.next().newPromise();

                ChannelHandler serverHandler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(sslServerContext.newHandler(ch.alloc()));

                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                serverPromise.cancel(true);
                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (serverPromise.trySuccess(msg)) {
                                    ctx.writeAndFlush(response.slice());
                                }

                                ctx.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                if (!serverPromise.tryFailure(cause)) {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                };

                LocalAddress address = new LocalAddress("test-" + serverSslProvider
                        + "-" + clientSslProvider + "-" + rfcCipherName);

                Channel server = server(address, serverHandler);
                try {
                    ChannelHandler clientHandler = new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(sslClientContext.newHandler(ch.alloc()));

                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                    clientPromise.cancel(true);
                                    ctx.fireChannelInactive();
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    clientPromise.trySuccess(msg);
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception {
                                    if (!clientPromise.tryFailure(cause)) {
                                        ctx.fireExceptionCaught(cause);
                                    }
                                }
                            });
                        }
                    };

                    Channel client = client(server, clientHandler);
                    try {
                        client.writeAndFlush(request.slice());

                        assertTrue("client timeout", clientPromise.await(5L, TimeUnit.SECONDS));
                        assertTrue("server timeout", serverPromise.await(5L, TimeUnit.SECONDS));

                        clientPromise.sync();
                        serverPromise.sync();
                    } finally {
                        client.close().sync();
                    }
                } finally {
                    server.close().sync();
                }
            } finally {
                ReferenceCountUtil.release(sslClientContext);
            }
        } finally {
            ReferenceCountUtil.release(sslServerContext);
        }
    }

    private static Channel server(LocalAddress address, ChannelHandler handler) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(GROUP)
                .childHandler(handler);

        return bootstrap.bind(address).sync().channel();
    }

    private static Channel client(Channel server, ChannelHandler handler) throws Exception {
        SocketAddress remoteAddress = server.localAddress();

        Bootstrap bootstrap = new Bootstrap()
                .channel(LocalChannel.class)
                .group(GROUP)
                .handler(handler);

        return bootstrap.connect(remoteAddress).sync().channel();
    }

    private static List<Object[]> expand(String rfcCipherName) {
        List<Object[]> dst = new ArrayList<Object[]>();
        SslProvider[] sslProviders = SslProvider.values();

        for (int i = 0; i < sslProviders.length; i++) {
            SslProvider serverSslProvider = sslProviders[i];

            for (int j = 0; j < sslProviders.length; j++) {
                SslProvider clientSslProvider = sslProviders[j];

                if ((serverSslProvider != SslProvider.JDK || clientSslProvider != SslProvider.JDK)
                        && !OpenSsl.isAvailable()) {
                    continue;
                }

                dst.add(new Object[]{serverSslProvider, clientSslProvider, rfcCipherName});
            }
        }

        if (dst.isEmpty()) {
            throw new IllegalStateException();
        }

        return dst;
    }
}
