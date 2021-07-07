/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import io.netty.util.concurrent.Future;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;
import io.netty.util.Mapping;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.ResourcesUtil;
import io.netty.util.internal.StringUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class SniHandlerTest {

    private static ApplicationProtocolConfig newApnConfig() {
        return new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                "myprotocol");
    }

    private static void assumeApnSupported(SslProvider provider) {
        switch (provider) {
            case OPENSSL:
            case OPENSSL_REFCNT:
                assumeTrue(OpenSsl.isAlpnSupported());
                break;
            case JDK:
                assumeTrue(JettyAlpnSslEngine.isAvailable());
                break;
            default:
                throw new Error();
        }
    }

    private static SslContext makeSslContext(SslProvider provider, boolean apn) throws Exception {
        if (apn) {
            assumeApnSupported(provider);
        }

        File keyFile = ResourcesUtil.getFile(SniHandlerTest.class, "test_encrypted.pem");
        File crtFile = ResourcesUtil.getFile(SniHandlerTest.class, "test.crt");

        SslContextBuilder sslCtxBuilder = SslContextBuilder.forServer(crtFile, keyFile, "12345")
                .sslProvider(provider);
        if (apn) {
            sslCtxBuilder.applicationProtocolConfig(newApnConfig());
        }
        return sslCtxBuilder.build();
    }

    private static SslContext makeSslClientContext(SslProvider provider, boolean apn) throws Exception {
        if (apn) {
            assumeApnSupported(provider);
        }

        File crtFile = ResourcesUtil.getFile(SniHandlerTest.class, "test.crt");

        SslContextBuilder sslCtxBuilder = SslContextBuilder.forClient().trustManager(crtFile).sslProvider(provider);
        if (apn) {
            sslCtxBuilder.applicationProtocolConfig(newApnConfig());
        }
        return sslCtxBuilder.build();
    }

    static Iterable<?> data() {
        List<SslProvider> params = new ArrayList<SslProvider>(3);
        if (OpenSsl.isAvailable()) {
            params.add(SslProvider.OPENSSL);
            params.add(SslProvider.OPENSSL_REFCNT);
        }
        params.add(SslProvider.JDK);
        return params;
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testNonSslRecord(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, false);
        try {
            final AtomicReference<SslHandshakeCompletionEvent> evtRef =
                    new AtomicReference<SslHandshakeCompletionEvent>();
            SniHandler handler = new SniHandler(new DomainNameMappingBuilder<SslContext>(nettyContext).build());
            final EmbeddedChannel ch = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof SslHandshakeCompletionEvent) {
                        assertTrue(evtRef.compareAndSet(null, (SslHandshakeCompletionEvent) evt));
                    }
                }
            });

            try {
                final byte[] bytes = new byte[1024];
                bytes[0] = SslUtils.SSL_CONTENT_TYPE_ALERT;

                DecoderException e = assertThrows(DecoderException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        ch.writeInbound(Unpooled.wrappedBuffer(bytes));
                    }
                });
                assertThat(e.getCause(), CoreMatchers.instanceOf(NotSslRecordException.class));
                assertFalse(ch.finish());
            } finally {
                ch.finishAndReleaseAll();
            }
            assertThat(evtRef.get().cause(), CoreMatchers.instanceOf(NotSslRecordException.class));
        } finally {
            releaseAll(nettyContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testServerNameParsing(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, false);
        SslContext leanContext = makeSslContext(provider, false);
        SslContext leanContext2 = makeSslContext(provider, false);

        try {
            DomainNameMapping<SslContext> mapping = new DomainNameMappingBuilder<SslContext>(nettyContext)
                    .add("*.netty.io", nettyContext)
                    // input with custom cases
                    .add("*.LEANCLOUD.CN", leanContext)
                    // a hostname conflict with previous one, since we are using order-sensitive config,
                    // the engine won't be used with the handler.
                    .add("chat4.leancloud.cn", leanContext2)
                    .build();

            final AtomicReference<SniCompletionEvent> evtRef = new AtomicReference<SniCompletionEvent>();
            SniHandler handler = new SniHandler(mapping);
            EmbeddedChannel ch = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof SniCompletionEvent) {
                        assertTrue(evtRef.compareAndSet(null, (SniCompletionEvent) evt));
                    } else {
                        ctx.fireUserEventTriggered(evt);
                    }
                }
            });

            try {
                // hex dump of a client hello packet, which contains hostname "CHAT4.LEANCLOUD.CN"
                String tlsHandshakeMessageHex1 = "16030100";
                // part 2
                String tlsHandshakeMessageHex = "c6010000c20303bb0855d66532c05a0ef784f7c384feeafa68b3" +
                        "b655ac7288650d5eed4aa3fb52000038c02cc030009fcca9cca8ccaac02b" +
                        "c02f009ec024c028006bc023c0270067c00ac0140039c009c0130033009d" +
                        "009c003d003c0035002f00ff010000610000001700150000124348415434" +
                        "2e4c45414e434c4f55442e434e000b000403000102000a000a0008001d00" +
                        "170019001800230000000d0020001e060106020603050105020503040104" +
                        "0204030301030203030201020202030016000000170000";

                ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(tlsHandshakeMessageHex1)));
                ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(tlsHandshakeMessageHex)));

                // This should produce an alert
                assertTrue(ch.finish());

                assertThat(handler.hostname(), is("chat4.leancloud.cn"));
                assertThat(handler.sslContext(), is(leanContext));

                SniCompletionEvent evt = evtRef.get();
                assertNotNull(evt);
                assertEquals("chat4.leancloud.cn", evt.hostname());
                assertTrue(evt.isSuccess());
                assertNull(evt.cause());
            } finally {
                ch.finishAndReleaseAll();
            }
        } finally {
            releaseAll(leanContext, leanContext2, nettyContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testNonAsciiServerNameParsing(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, false);
        SslContext leanContext = makeSslContext(provider, false);
        SslContext leanContext2 = makeSslContext(provider, false);

        try {
            DomainNameMapping<SslContext> mapping = new DomainNameMappingBuilder<SslContext>(nettyContext)
                    .add("*.netty.io", nettyContext)
                    // input with custom cases
                    .add("*.LEANCLOUD.CN", leanContext)
                    // a hostname conflict with previous one, since we are using order-sensitive config,
                    // the engine won't be used with the handler.
                    .add("chat4.leancloud.cn", leanContext2)
                    .build();

            SniHandler handler = new SniHandler(mapping);
            final EmbeddedChannel ch = new EmbeddedChannel(handler);

            try {
                // hex dump of a client hello packet, which contains an invalid hostname "CHAT4。LEANCLOUD。CN"
                String tlsHandshakeMessageHex1 = "16030100";
                // part 2
                final String tlsHandshakeMessageHex = "bd010000b90303a74225676d1814ba57faff3b366" +
                        "3656ed05ee9dbb2a4dbb1bb1c32d2ea5fc39e0000000100008c0000001700150000164348" +
                        "415434E380824C45414E434C4F5544E38082434E000b000403000102000a00340032000e0" +
                        "00d0019000b000c00180009000a0016001700080006000700140015000400050012001300" +
                        "0100020003000f0010001100230000000d0020001e0601060206030501050205030401040" +
                        "20403030103020303020102020203000f00010133740000";

                // Push the handshake message.
                // Decode should fail because of the badly encoded "HostName" string in the SNI extension
                // that isn't ASCII as per RFC 6066 - https://tools.ietf.org/html/rfc6066#page-6
                ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(tlsHandshakeMessageHex1)));

                assertThrows(DecoderException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(tlsHandshakeMessageHex)));
                    }
                });
            } finally {
                ch.finishAndReleaseAll();
            }
        } finally {
            releaseAll(leanContext, leanContext2, nettyContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testFallbackToDefaultContext(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, false);
        SslContext leanContext = makeSslContext(provider, false);
        SslContext leanContext2 = makeSslContext(provider, false);

        try {
            DomainNameMapping<SslContext> mapping = new DomainNameMappingBuilder<SslContext>(nettyContext)
                    .add("*.netty.io", nettyContext)
                    // input with custom cases
                    .add("*.LEANCLOUD.CN", leanContext)
                    // a hostname conflict with previous one, since we are using order-sensitive config,
                    // the engine won't be used with the handler.
                    .add("chat4.leancloud.cn", leanContext2)
                    .build();

            SniHandler handler = new SniHandler(mapping);
            EmbeddedChannel ch = new EmbeddedChannel(handler);

            // invalid
            byte[] message = {22, 3, 1, 0, 0};
            try {
                // Push the handshake message.
                ch.writeInbound(Unpooled.wrappedBuffer(message));
                // TODO(scott): This should fail because the engine should reject zero length records during handshake.
                // See https://github.com/netty/netty/issues/6348.
                // fail();
            } catch (Exception e) {
                // expected
            }

            ch.close();

            // When the channel is closed the SslHandler will write an empty buffer to the channel.
            ByteBuf buf = ch.readOutbound();
            // TODO(scott): if the engine is shutdown correctly then this buffer shouldn't be null!
            // See https://github.com/netty/netty/issues/6348.
            if (buf != null) {
                assertFalse(buf.isReadable());
                buf.release();
            }

            assertThat(ch.finish(), is(false));
            assertThat(handler.hostname(), nullValue());
            assertThat(handler.sslContext(), is(nettyContext));
        } finally {
            releaseAll(leanContext, leanContext2, nettyContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testMajorVersionNot3(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, false);

        try {
            DomainNameMapping<SslContext> mapping = new DomainNameMappingBuilder<SslContext>(nettyContext).build();

            SniHandler handler = new SniHandler(mapping);
            EmbeddedChannel ch = new EmbeddedChannel(handler);

            // invalid
            byte[] message = {22, 2, 0, 0, 0};
            try {
                // Push the handshake message.
                ch.writeInbound(Unpooled.wrappedBuffer(message));
                // TODO(scott): This should fail because the engine should reject zero length records during handshake.
                // See https://github.com/netty/netty/issues/6348.
                // fail();
            } catch (Exception e) {
                // expected
            }

            ch.close();

            // Consume all the outbound data that may be produced by the SSLEngine.
            for (;;) {
                ByteBuf buf = ch.readOutbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }

            assertThat(ch.finish(), is(false));
            assertThat(handler.hostname(), nullValue());
            assertThat(handler.sslContext(), is(nettyContext));
        } finally {
            releaseAll(nettyContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testSniWithApnHandler(SslProvider provider) throws Exception {
        SslContext nettyContext = makeSslContext(provider, true);
        SslContext sniContext = makeSslContext(provider, true);
        final SslContext clientContext = makeSslClientContext(provider, true);
        try {
            final AtomicBoolean serverApnCtx = new AtomicBoolean(false);
            final AtomicBoolean clientApnCtx = new AtomicBoolean(false);
            final CountDownLatch serverApnDoneLatch = new CountDownLatch(1);
            final CountDownLatch clientApnDoneLatch = new CountDownLatch(1);

            final DomainNameMapping<SslContext> mapping = new DomainNameMappingBuilder<SslContext>(nettyContext)
                    .add("*.netty.io", nettyContext)
                    .add("sni.fake.site", sniContext).build();
            final SniHandler handler = new SniHandler(mapping);
            EventLoopGroup group = new NioEventLoopGroup(2);
            Channel serverChannel = null;
            Channel clientChannel = null;
            try {
                ServerBootstrap sb = new ServerBootstrap();
                sb.group(group);
                sb.channel(NioServerSocketChannel.class);
                sb.childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        // Server side SNI.
                        p.addLast(handler);
                        // Catch the notification event that APN has completed successfully.
                        p.addLast(new ApplicationProtocolNegotiationHandler("foo") {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                // addresses issue #9131
                                serverApnCtx.set(ctx.pipeline().context(this) != null);
                                serverApnDoneLatch.countDown();
                            }
                        });
                    }
                });

                Bootstrap cb = new Bootstrap();
                cb.group(group);
                cb.channel(NioSocketChannel.class);
                cb.handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new SslHandler(clientContext.newEngine(
                                ch.alloc(), "sni.fake.site", -1)));
                        // Catch the notification event that APN has completed successfully.
                        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("foo") {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                // addresses issue #9131
                                clientApnCtx.set(ctx.pipeline().context(this) != null);
                                clientApnDoneLatch.countDown();
                            }
                        });
                    }
                });

                serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();

                ChannelFuture ccf = cb.connect(serverChannel.localAddress());
                assertTrue(ccf.awaitUninterruptibly().isSuccess());
                clientChannel = ccf.channel();

                assertTrue(serverApnDoneLatch.await(5, TimeUnit.SECONDS));
                assertTrue(clientApnDoneLatch.await(5, TimeUnit.SECONDS));
                assertTrue(serverApnCtx.get());
                assertTrue(clientApnCtx.get());
                assertThat(handler.hostname(), is("sni.fake.site"));
                assertThat(handler.sslContext(), is(sniContext));
            } finally {
                if (serverChannel != null) {
                    serverChannel.close().sync();
                }
                if (clientChannel != null) {
                    clientChannel.close().sync();
                }
                group.shutdownGracefully(0, 0, TimeUnit.MICROSECONDS);
            }
        } finally {
            releaseAll(clientContext, nettyContext, sniContext);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testReplaceHandler(SslProvider provider) throws Exception {
        switch (provider) {
            case OPENSSL:
            case OPENSSL_REFCNT:
                final String sniHost = "sni.netty.io";
                LocalAddress address = new LocalAddress("testReplaceHandler-" + Math.random());
                EventLoopGroup group = new DefaultEventLoopGroup(1);
                Channel sc = null;
                Channel cc = null;
                SslContext sslContext = null;

                SelfSignedCertificate cert = new SelfSignedCertificate();

                try {
                    final SslContext sslServerContext = SslContextBuilder
                            .forServer(cert.key(), cert.cert())
                            .sslProvider(provider)
                            .build();

                    final Mapping<String, SslContext> mapping = new Mapping<String, SslContext>() {
                        @Override
                        public SslContext map(String input) {
                            return sslServerContext;
                        }
                    };

                    final Promise<Void> releasePromise = group.next().newPromise();

                    final SniHandler handler = new SniHandler(mapping) {
                        @Override
                        protected void replaceHandler(ChannelHandlerContext ctx,
                                                      String hostname, final SslContext sslContext)
                                throws Exception {

                            boolean success = false;
                            try {
                                assertEquals(1, ((ReferenceCountedOpenSslContext) sslContext).refCnt());
                                // The SniHandler's replaceHandler() method allows us to implement custom behavior.
                                // As an example, we want to release() the SslContext upon channelInactive() or rather
                                // when the SslHandler closes it's SslEngine. If you take a close look at SslHandler
                                // you'll see that it's doing it in the #handlerRemoved0() method.

                                SSLEngine sslEngine = sslContext.newEngine(ctx.alloc());
                                try {
                                    assertEquals(2, ((ReferenceCountedOpenSslContext) sslContext).refCnt());
                                    SslHandler customSslHandler = new CustomSslHandler(sslContext, sslEngine) {
                                        @Override
                                        public void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
                                            try {
                                                super.handlerRemoved0(ctx);
                                            } finally {
                                                releasePromise.trySuccess(null);
                                            }
                                        }
                                    };
                                    ctx.pipeline().replace(this, CustomSslHandler.class.getName(), customSslHandler);
                                    success = true;
                                } finally {
                                    if (!success) {
                                        ReferenceCountUtil.safeRelease(sslEngine);
                                    }
                                }
                            } finally {
                                if (!success) {
                                    ReferenceCountUtil.safeRelease(sslContext);
                                    releasePromise.cancel(true);
                                }
                            }
                        }
                    };

                    ServerBootstrap sb = new ServerBootstrap();
                    sc = sb.group(group).channel(LocalServerChannel.class)
                            .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addFirst(handler);
                        }
                    }).bind(address).syncUninterruptibly().channel();

                    sslContext = SslContextBuilder.forClient().sslProvider(provider)
                            .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

                    Bootstrap cb = new Bootstrap();
                    cc = cb.group(group).channel(LocalChannel.class).handler(new SslHandler(
                            sslContext.newEngine(ByteBufAllocator.DEFAULT, sniHost, -1)))
                            .connect(address).syncUninterruptibly().channel();

                    cc.writeAndFlush(Unpooled.wrappedBuffer("Hello, World!".getBytes()))
                            .syncUninterruptibly();

                    // Notice how the server's SslContext refCnt is 2 as it is incremented when the SSLEngine is created
                    // and only decremented once it is destroyed.
                    assertEquals(2, ((ReferenceCounted) sslServerContext).refCnt());

                    // The client disconnects
                    cc.close().syncUninterruptibly();
                    if (!releasePromise.awaitUninterruptibly(10L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("It doesn't seem #replaceHandler() got called.");
                    }

                    // We should have successfully release() the SslContext
                    assertEquals(0, ((ReferenceCounted) sslServerContext).refCnt());
                } finally {
                    if (cc != null) {
                        cc.close().syncUninterruptibly();
                    }
                    if (sc != null) {
                        sc.close().syncUninterruptibly();
                    }
                    if (sslContext != null) {
                        ReferenceCountUtil.release(sslContext);
                    }
                    group.shutdownGracefully();

                    cert.delete();
                }
            case JDK:
                return;
            default:
                throw new Error();
        }
    }

    /**
     * This is a {@link SslHandler} that will call {@code release()} on the {@link SslContext} when
     * the client disconnects.
     *
     * @see SniHandlerTest#testReplaceHandler(SslProvider)
     */
    private static class CustomSslHandler extends SslHandler {
        private final SslContext sslContext;

        CustomSslHandler(SslContext sslContext, SSLEngine sslEngine) {
            super(sslEngine);
            this.sslContext = ObjectUtil.checkNotNull(sslContext, "sslContext");
        }

        @Override
        public void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
            super.handlerRemoved0(ctx);
            ReferenceCountUtil.release(sslContext);
        }
    }

    private static void releaseAll(SslContext... contexts) {
        for (SslContext ctx: contexts) {
            ReferenceCountUtil.release(ctx);
        }
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testNonFragmented(SslProvider provider) throws Exception {
        testWithFragmentSize(provider, Integer.MAX_VALUE);
    }

    @ParameterizedTest(name = "{index}: sslProvider={0}")
    @MethodSource("data")
    public void testFragmented(SslProvider provider) throws Exception {
        testWithFragmentSize(provider, 50);
    }

    private void testWithFragmentSize(SslProvider provider, final int maxFragmentSize) throws Exception {
        final String sni = "netty.io";
        SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext context = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(provider)
                .build();
        try {
            @SuppressWarnings("unchecked") final EmbeddedChannel server = new EmbeddedChannel(
                    new SniHandler(mock(DomainNameMapping.class)) {
                @Override
                protected Future<SslContext> lookup(final ChannelHandlerContext ctx, final String hostname) {
                    assertEquals(sni, hostname);
                    return ctx.executor().newSucceededFuture(context);
                }
            });

            final List<ByteBuf> buffers = clientHelloInMultipleFragments(provider, sni, maxFragmentSize);
            for (ByteBuf buffer : buffers) {
                server.writeInbound(buffer);
            }
            assertTrue(server.finishAndReleaseAll());
        } finally {
            releaseAll(context);
            cert.delete();
        }
    }

    private static List<ByteBuf> clientHelloInMultipleFragments(
            SslProvider provider, String hostname, int maxTlsPlaintextSize) throws SSLException {
        final EmbeddedChannel client = new EmbeddedChannel();
        final SslContext ctx = SslContextBuilder.forClient()
                .sslProvider(provider)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        try {
            final SslHandler sslHandler = ctx.newHandler(client.alloc(), hostname, -1);
            client.pipeline().addLast(sslHandler);
            final ByteBuf clientHello = client.readOutbound();
            List<ByteBuf> buffers = split(clientHello, maxTlsPlaintextSize);
            assertTrue(client.finishAndReleaseAll());
            return buffers;
        } finally {
            releaseAll(ctx);
        }
    }

    private static List<ByteBuf> split(ByteBuf clientHello, int maxSize) {
        final int type = clientHello.readUnsignedByte();
        final int version = clientHello.readUnsignedShort();
        final int length = clientHello.readUnsignedShort();
        assertEquals(length, clientHello.readableBytes());

        final List<ByteBuf> result = new ArrayList<ByteBuf>();
        while (clientHello.readableBytes() > 0) {
            final int toRead = Math.min(maxSize, clientHello.readableBytes());
            final ByteBuf bb = clientHello.alloc().buffer(SslUtils.SSL_RECORD_HEADER_LENGTH + toRead);
            bb.writeByte(type);
            bb.writeShort(version);
            bb.writeShort(toRead);
            bb.writeBytes(clientHello, toRead);
            result.add(bb);
        }
        clientHello.release();
        return result;
    }
}
