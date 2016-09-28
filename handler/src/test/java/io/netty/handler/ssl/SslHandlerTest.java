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

package io.netty.handler.ssl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLProtocolException;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

import java.net.InetSocketAddress;

public class SslHandlerTest {

    @Test
    public void testTruncatedPacket() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        // Push the first part of a 5-byte handshake message.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{22, 3, 1, 0, 5}));

        // Should decode nothing yet.
        assertThat(ch.readInbound(), is(nullValue()));

        try {
            // Push the second part of the 5-byte handshake message.
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{2, 0, 0, 1, 0}));
            fail();
        } catch (DecoderException e) {
            // The pushed message is invalid, so it should raise an exception if it decoded the message correctly.
            assertThat(e.getCause(), is(instanceOf(SSLProtocolException.class)));
        }
    }

    @Test(expected = UnsupportedMessageTypeException.class)
    public void testNonByteBufNotPassThrough() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        ch.writeOutbound(new Object());
    }

    @Test
    public void testReleaseSslEngine() throws Exception {
        assumeTrue(OpenSsl.isAvailable());

        SelfSignedCertificate cert = new SelfSignedCertificate();
        try {
            SslContext sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.OPENSSL)
                .build();
            try {
                SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
                EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(sslEngine));

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(1, ((ReferenceCounted) sslEngine).refCnt());

                ch.close().syncUninterruptibly();

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(0, ((ReferenceCounted) sslEngine).refCnt());
            } finally {
                ReferenceCountUtil.release(sslContext);
            }
        } finally {
            cert.delete();
        }
    }

    private static final class TlsReadTest extends ChannelOutboundHandlerAdapter {
        private volatile boolean readIssued;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            readIssued = true;
            super.read(ctx);
        }

        public void test(final boolean dropChannelActive) throws Exception {
          SSLEngine engine = SSLContext.getDefault().createSSLEngine();
          engine.setUseClientMode(true);

          EmbeddedChannel ch = new EmbeddedChannel(
              this,
              new SslHandler(engine),
              new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                  if (!dropChannelActive) {
                    ctx.fireChannelActive();
                  }
                }
              }
          );
          ch.config().setAutoRead(false);
          assertFalse(ch.config().isAutoRead());

          assertTrue(ch.writeOutbound(Unpooled.EMPTY_BUFFER));
          assertTrue(readIssued);
          assertTrue(ch.finishAndReleaseAll());
       }
    }

    @Test
    public void testIssueReadAfterActiveWriteFlush() throws Exception {
        // the handshake is initiated by channelActive
        new TlsReadTest().test(false);
    }

    @Test
    public void testIssueReadAfterWriteFlushActive() throws Exception {
        // the handshake is initiated by flush
        new TlsReadTest().test(true);
    }

    @Test(timeout = 5000)
    public void testRemoval() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> clientPromise = group.next().newPromise();
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(newHandler(SslContextBuilder.forClient().trustManager(
                            InsecureTrustManagerFactory.INSTANCE).build(), clientPromise));

            SelfSignedCertificate ssc = new SelfSignedCertificate();
            final Promise<Void> serverPromise = group.next().newPromise();
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(group, group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(newHandler(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build(),
                            serverPromise));
            sc = serverBootstrap.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            cc = bootstrap.connect(sc.localAddress()).syncUninterruptibly().channel();

            serverPromise.syncUninterruptibly();
            clientPromise.syncUninterruptibly();
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

    private static ChannelHandler newHandler(final SslContext sslCtx, final Promise<Void> promise) {
        return new ChannelInitializer() {
            @Override
            protected void initChannel(final Channel ch) {
                final SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                sslHandler.setHandshakeTimeoutMillis(1000);
                ch.pipeline().addFirst(sslHandler);
                sslHandler.handshakeFuture().addListener(new FutureListener<Channel>() {
                    @Override
                    public void operationComplete(final Future<Channel> future) {
                        ch.pipeline().remove(sslHandler);

                        // Schedule the close so removal has time to propergate exception if any.
                        ch.eventLoop().execute(new Runnable() {
                            @Override
                            public void run() {
                                ch.close();
                            }
                        });
                    }
                });

                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (cause instanceof CodecException) {
                            cause = cause.getCause();
                        }
                        if (cause instanceof IllegalReferenceCountException) {
                            promise.setFailure(cause);
                        }
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        promise.trySuccess(null);
                    }
                });
            }
        };
    }
}
