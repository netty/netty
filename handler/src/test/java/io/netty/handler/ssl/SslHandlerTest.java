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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.X509ExtendedTrustManager;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class SslHandlerTest {

    @Test(timeout = 5000)
    public void testNonApplicationDataFailureFailsQueuedWrites() throws NoSuchAlgorithmException, InterruptedException {
        final CountDownLatch writeLatch = new CountDownLatch(1);
        final Queue<ChannelPromise> writesToFail = new ConcurrentLinkedQueue<ChannelPromise>();
        SSLEngine engine = newClientModeSSLEngine();
        SslHandler handler = new SslHandler(engine) {
            @Override
            public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise);
                writeLatch.countDown();
            }
        };
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (msg instanceof ByteBuf) {
                    if (((ByteBuf) msg).isReadable()) {
                        writesToFail.add(promise);
                    } else {
                        promise.setSuccess();
                    }
                }
                ReferenceCountUtil.release(msg);
            }
        }, handler);

        try {
            final CountDownLatch writeCauseLatch = new CountDownLatch(1);
            final AtomicReference<Throwable> failureRef = new AtomicReference<Throwable>();
            ch.write(Unpooled.wrappedBuffer(new byte[]{1})).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    failureRef.compareAndSet(null, future.cause());
                    writeCauseLatch.countDown();
                }
            });
            writeLatch.await();

            // Simulate failing the SslHandler non-application writes after there are applications writes queued.
            ChannelPromise promiseToFail;
            while ((promiseToFail = writesToFail.poll()) != null) {
                promiseToFail.setFailure(new RuntimeException("fake exception"));
            }

            writeCauseLatch.await();
            Throwable writeCause = failureRef.get();
            assertNotNull(writeCause);
            assertThat(writeCause, is(CoreMatchers.<Throwable>instanceOf(SSLException.class)));
            Throwable cause = handler.handshakeFuture().cause();
            assertNotNull(cause);
            assertThat(cause, is(CoreMatchers.<Throwable>instanceOf(SSLException.class)));
        } finally {
            assertFalse(ch.finishAndReleaseAll());
        }
    }

    @Test
    public void testNoSslHandshakeEventWhenNoHandshake() throws Exception {
        final AtomicBoolean inActive = new AtomicBoolean(false);

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        EmbeddedChannel ch = new EmbeddedChannel(
                DefaultChannelId.newInstance(), false, false, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Not forward the event to the SslHandler but just close the Channel.
                ctx.close();
            }
        }, new SslHandler(engine) {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                // We want to override what Channel.isActive() will return as otherwise it will
                // return true and so trigger an handshake.
                inActive.set(true);
                super.handlerAdded(ctx);
                inActive.set(false);
            }
        }, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    throw (Exception) ((SslHandshakeCompletionEvent) evt).cause();
                }
            }
        }) {
            @Override
            public boolean isActive() {
                return !inActive.get() && super.isActive();
            }
        };

        ch.register();
        assertFalse(ch.finishAndReleaseAll());
    }

    @Test(expected = SslHandshakeTimeoutException.class, timeout = 3000)
    public void testClientHandshakeTimeout() throws Exception {
        testHandshakeTimeout(true);
    }

    @Test(expected = SslHandshakeTimeoutException.class, timeout = 3000)
    public void testServerHandshakeTimeout() throws Exception {
        testHandshakeTimeout(false);
    }

    private static SSLEngine newServerModeSSLEngine() throws NoSuchAlgorithmException {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // Set the mode before we try to do the handshake as otherwise it may throw an IllegalStateException.
        // See:
        //  - https://docs.oracle.com/javase/10/docs/api/javax/net/ssl/SSLEngine.html#beginHandshake()
        //  - http://mail.openjdk.java.net/pipermail/security-dev/2018-July/017715.html
        engine.setUseClientMode(false);
        return engine;
    }

    private static SSLEngine newClientModeSSLEngine() throws NoSuchAlgorithmException {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // Set the mode before we try to do the handshake as otherwise it may throw an IllegalStateException.
        // See:
        //  - https://docs.oracle.com/javase/10/docs/api/javax/net/ssl/SSLEngine.html#beginHandshake()
        //  - http://mail.openjdk.java.net/pipermail/security-dev/2018-July/017715.html
        engine.setUseClientMode(true);
        return engine;
    }

    private static void testHandshakeTimeout(boolean client) throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(client);
        SslHandler handler = new SslHandler(engine);
        handler.setHandshakeTimeoutMillis(1);

        EmbeddedChannel ch = new EmbeddedChannel(handler);
        try {
            while (!handler.handshakeFuture().isDone()) {
                Thread.sleep(10);
                // We need to run all pending tasks as the handshake timeout is scheduled on the EventLoop.
                ch.runPendingTasks();
            }

            handler.handshakeFuture().syncUninterruptibly();
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test(timeout = 5000L)
    public void testHandshakeAndClosePromiseFailedOnRemoval() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        SslHandler handler = new SslHandler(engine);
        final AtomicReference<Throwable> handshakeRef = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> closeRef = new AtomicReference<Throwable>();
        EmbeddedChannel ch = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    handshakeRef.set(((SslHandshakeCompletionEvent) evt).cause());
                } else if (evt instanceof SslCloseCompletionEvent) {
                    closeRef.set(((SslCloseCompletionEvent) evt).cause());
                }
            }
        });
        assertFalse(handler.handshakeFuture().isDone());
        assertFalse(handler.sslCloseFuture().isDone());

        ch.pipeline().remove(handler);

        try {
            while (!handler.handshakeFuture().isDone() || handshakeRef.get() == null
                    || !handler.sslCloseFuture().isDone() || closeRef.get() == null) {
                Thread.sleep(10);
                // Continue running all pending tasks until we notified for everything.
                ch.runPendingTasks();
            }

            assertSame(handler.handshakeFuture().cause(), handshakeRef.get());
            assertSame(handler.sslCloseFuture().cause(), closeRef.get());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testTruncatedPacket() throws Exception {
        SSLEngine engine = newServerModeSSLEngine();
        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        // Push the first part of a 5-byte handshake message.
        ch.writeInbound(wrappedBuffer(new byte[]{22, 3, 1, 0, 5}));

        // Should decode nothing yet.
        assertThat(ch.readInbound(), is(nullValue()));

        try {
            // Push the second part of the 5-byte handshake message.
            ch.writeInbound(wrappedBuffer(new byte[]{2, 0, 0, 1, 0}));
            fail();
        } catch (DecoderException e) {
            // Be sure we cleanup the channel and release any pending messages that may have been generated because
            // of an alert.
            // See https://github.com/netty/netty/issues/6057.
            ch.finishAndReleaseAll();

            // The pushed message is invalid, so it should raise an exception if it decoded the message correctly.
            assertThat(e.getCause(), is(instanceOf(SSLProtocolException.class)));
        }
    }

    @Test
    public void testNonByteBufWriteIsReleased() throws Exception {
        SSLEngine engine = newServerModeSSLEngine();
        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        AbstractReferenceCounted referenceCounted = new AbstractReferenceCounted() {
            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }

            @Override
            protected void deallocate() {
            }
        };
        try {
            ch.write(referenceCounted).get();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(UnsupportedMessageTypeException.class)));
        }
        assertEquals(0, referenceCounted.refCnt());
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test(expected = UnsupportedMessageTypeException.class)
    public void testNonByteBufNotPassThrough() throws Exception {
        SSLEngine engine = newServerModeSSLEngine();
        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        try {
            ch.writeOutbound(new Object());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testIncompleteWriteDoesNotCompletePromisePrematurely() throws NoSuchAlgorithmException {
        SSLEngine engine = newServerModeSSLEngine();
        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        ChannelPromise promise = ch.newPromise();
        ByteBuf buf = Unpooled.buffer(10).writeZero(10);
        ch.writeAndFlush(buf, promise);
        assertFalse(promise.isDone());
        assertTrue(ch.finishAndReleaseAll());
        assertTrue(promise.isDone());
        assertThat(promise.cause(), is(instanceOf(SSLException.class)));
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
                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
                EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(sslEngine));

                assertEquals(2, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(1, ((ReferenceCounted) sslEngine).refCnt());

                assertTrue(ch.finishAndReleaseAll());
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

            EmbeddedChannel ch = new EmbeddedChannel(false, false,
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

            ch.register();

            assertTrue(readIssued);
            readIssued = false;

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

    @Test(timeout = 30000)
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

                        // Schedule the close so removal has time to propagate exception if any.
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

    @Test
    public void testCloseFutureNotified() throws Exception {
        SSLEngine engine = newServerModeSSLEngine();
        SslHandler handler = new SslHandler(engine);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        ch.close();

        // When the channel is closed the SslHandler will write an empty buffer to the channel.
        ByteBuf buf = ch.readOutbound();
        assertFalse(buf.isReadable());
        buf.release();

        assertFalse(ch.finishAndReleaseAll());

        assertTrue(handler.handshakeFuture().cause() instanceof ClosedChannelException);
        assertTrue(handler.sslCloseFuture().cause() instanceof ClosedChannelException);
    }

    @Test(timeout = 5000)
    public void testEventsFired() throws Exception {
        SSLEngine engine = newServerModeSSLEngine();
        final BlockingQueue<SslCompletionEvent> events = new LinkedBlockingQueue<SslCompletionEvent>();
        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslCompletionEvent) {
                    events.add((SslCompletionEvent) evt);
                }
            }
        });
        assertTrue(events.isEmpty());
        assertTrue(channel.finishAndReleaseAll());

        SslCompletionEvent evt = events.take();
        assertTrue(evt instanceof SslHandshakeCompletionEvent);
        assertTrue(evt.cause() instanceof ClosedChannelException);

        evt = events.take();
        assertTrue(evt instanceof SslCloseCompletionEvent);
        assertTrue(evt.cause() instanceof ClosedChannelException);
        assertTrue(events.isEmpty());
    }

    @Test(timeout = 5000)
    public void testHandshakeFailBeforeWritePromise() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch latch2 = new CountDownLatch(2);
        final BlockingQueue<Object> events = new LinkedBlockingQueue<Object>();
        Channel serverChannel = null;
        Channel clientChannel = null;
        EventLoopGroup group = new DefaultEventLoopGroup();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                  @Override
                  protected void initChannel(Channel ch) {
                      ch.pipeline().addLast(sslServerCtx.newHandler(ch.alloc()));
                      ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                          @Override
                          public void channelActive(ChannelHandlerContext ctx) {
                              ByteBuf buf = ctx.alloc().buffer(10);
                              buf.writeZero(buf.capacity());
                              ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                                  @Override
                                  public void operationComplete(ChannelFuture future) {
                                      events.add(future);
                                      latch.countDown();
                                  }
                              });
                          }

                          @Override
                          public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                              if (evt instanceof SslCompletionEvent) {
                                  events.add(evt);
                                  latch.countDown();
                                  latch2.countDown();
                              }
                          }
                      });
                  }
                });

            Bootstrap cb = new Bootstrap();
            cb.group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ByteBuf buf = ctx.alloc().buffer(1000);
                                buf.writeZero(buf.capacity());
                                ctx.writeAndFlush(buf);
                            }
                        });
                    }
                });

            serverChannel = sb.bind(new LocalAddress("SslHandlerTest")).sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            latch.await();

            SslCompletionEvent evt = (SslCompletionEvent) events.take();
            assertTrue(evt instanceof SslHandshakeCompletionEvent);
            assertThat(evt.cause(), is(instanceOf(SSLException.class)));

            ChannelFuture future = (ChannelFuture) events.take();
            assertThat(future.cause(), is(instanceOf(SSLException.class)));

            serverChannel.close().sync();
            serverChannel = null;
            clientChannel.close().sync();
            clientChannel = null;

            latch2.await();
            evt = (SslCompletionEvent) events.take();
            assertTrue(evt instanceof SslCloseCompletionEvent);
            assertThat(evt.cause(), is(instanceOf(ClosedChannelException.class)));
            assertTrue(events.isEmpty());
        } finally {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (clientChannel != null) {
                clientChannel.close();
            }
            group.shutdownGracefully();
        }
    }

    @Test
    public void writingReadOnlyBufferDoesNotBreakAggregation() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final CountDownLatch serverReceiveLatch = new CountDownLatch(1);
        try {
            final int expectedBytes = 11;
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslServerCtx.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                private int readBytes;
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                    readBytes += msg.readableBytes();
                                    if (readBytes >= expectedBytes) {
                                        serverReceiveLatch.countDown();
                                    }
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslClientCtx.newHandler(ch.alloc()));
                        }
                    }).connect(sc.localAddress()).syncUninterruptibly().channel();

            // We first write a ReadOnlyBuffer because SslHandler will attempt to take the first buffer and append to it
            // until there is no room, or the aggregation size threshold is exceeded. We want to verify that we don't
            // throw when a ReadOnlyBuffer is used and just verify that we don't aggregate in this case.
            ByteBuf firstBuffer = Unpooled.buffer(10);
            firstBuffer.writeByte(0);
            firstBuffer = firstBuffer.asReadOnly();
            ByteBuf secondBuffer = Unpooled.buffer(10);
            secondBuffer.writeZero(secondBuffer.capacity());
            cc.write(firstBuffer);
            cc.writeAndFlush(secondBuffer).syncUninterruptibly();
            serverReceiveLatch.countDown();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 10000)
    public void testCloseOnHandshakeFailure() throws Exception {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.key(), ssc.cert()).build();
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(new SelfSignedCertificate().cert())
                .build();

        EventLoopGroup group = new NioEventLoopGroup(1);
        Channel sc = null;
        Channel cc = null;
        try {
            LocalAddress address = new LocalAddress(getClass().getSimpleName() + ".testCloseOnHandshakeFailure");
            ServerBootstrap sb = new ServerBootstrap()
                    .group(group)
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(sslServerCtx.newHandler(ch.alloc()));
                        }
                    });
            sc = sb.bind(address).syncUninterruptibly().channel();

            final AtomicReference<SslHandler> sslHandlerRef = new AtomicReference<SslHandler>();
            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler handler = sslClientCtx.newHandler(ch.alloc());

                            // We propagate the SslHandler via an AtomicReference to the outer-scope as using
                            // pipeline.get(...) may return null if the pipeline was teared down by the time we call it.
                            // This will happen if the channel was closed in the meantime.
                            sslHandlerRef.set(handler);
                            ch.pipeline().addLast(handler);
                        }
                    });
            cc = b.connect(sc.localAddress()).syncUninterruptibly().channel();
            SslHandler handler = sslHandlerRef.get();
            handler.handshakeFuture().awaitUninterruptibly();
            assertFalse(handler.handshakeFuture().isSuccess());

            cc.closeFuture().syncUninterruptibly();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test
    public void testOutboundClosedAfterChannelInactive() throws Exception {
        SslContext context = SslContextBuilder.forClient().build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);

        EmbeddedChannel channel = new EmbeddedChannel();
        assertFalse(channel.finish());
        channel.pipeline().addLast(new SslHandler(engine));
        assertFalse(engine.isOutboundDone());
        channel.close().syncUninterruptibly();

        assertTrue(engine.isOutboundDone());
    }

    @Test(timeout = 10000)
    public void testHandshakeFailedByWriteBeforeChannelActive() throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                                                         .protocols(SslUtils.PROTOCOL_SSL_V3)
                                                         .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                         .sslProvider(SslProvider.JDK).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final CountDownLatch activeLatch = new CountDownLatch(1);
        final AtomicReference<AssertionError> errorRef = new AtomicReference<AssertionError>();
        final SslHandler sslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslHandler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception {
                                    if (cause instanceof AssertionError) {
                                        errorRef.set((AssertionError) cause);
                                    }
                                }

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    activeLatch.countDown();
                                }
                            });
                        }
                    }).connect(sc.localAddress()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // Write something to trigger the handshake before fireChannelActive is called.
                            future.channel().writeAndFlush(wrappedBuffer(new byte [] { 1, 2, 3, 4 }));
                        }
                    }).syncUninterruptibly().channel();

            // Ensure there is no AssertionError thrown by having the handshake failed by the writeAndFlush(...) before
            // channelActive(...) was called. Let's first wait for the activeLatch countdown to happen and after this
            // check if we saw and AssertionError (even if we timed out waiting).
            activeLatch.await(5, TimeUnit.SECONDS);
            AssertionError error = errorRef.get();
            if (error != null) {
                throw error;
            }
            assertThat(sslHandler.handshakeFuture().await().cause(),
                       CoreMatchers.<Throwable>instanceOf(SSLException.class));
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 10000)
    public void testHandshakeTimeoutFlushStartsHandshake() throws Exception {
        testHandshakeTimeout0(false);
    }

    @Test(timeout = 10000)
    public void testHandshakeTimeoutStartTLS() throws Exception {
        testHandshakeTimeout0(true);
    }

    private static void testHandshakeTimeout0(final boolean startTls) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                                                         .startTls(true)
                                                         .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                         .sslProvider(SslProvider.JDK).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final SslHandler sslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        sslHandler.setHandshakeTimeout(500, TimeUnit.MILLISECONDS);

        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslHandler);
                            if (startTls) {
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        ctx.writeAndFlush(wrappedBuffer(new byte[] { 1, 2, 3, 4 }));
                                    }
                                });
                            }
                        }
                    }).connect(sc.localAddress());
            if (!startTls) {
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        // Write something to trigger the handshake before fireChannelActive is called.
                        future.channel().writeAndFlush(wrappedBuffer(new byte [] { 1, 2, 3, 4 }));
                    }
                });
            }
            cc = future.syncUninterruptibly().channel();

            Throwable cause = sslHandler.handshakeFuture().await().cause();
            assertThat(cause, CoreMatchers.<Throwable>instanceOf(SSLException.class));
            assertThat(cause.getMessage(), containsString("timed out"));
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test
    public void testHandshakeWithExecutorThatExecuteDirecty() throws Exception {
        testHandshakeWithExecutor(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
    }

    @Test
    public void testHandshakeWithImmediateExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateExecutor.INSTANCE);
    }

    @Test
    public void testHandshakeWithImmediateEventExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateEventExecutor.INSTANCE);
    }

    @Test
    public void testHandshakeWithExecutor() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            testHandshakeWithExecutor(executorService);
        } finally {
            executorService.shutdown();
        }
    }

    private static void testHandshakeWithExecutor(Executor executor) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.JDK).build();

        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.JDK).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, executor);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, executor);

        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(serverSslHandler)
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(clientSslHandler);
                        }
                    }).connect(sc.localAddress());
            cc = future.syncUninterruptibly().channel();

            assertTrue(clientSslHandler.handshakeFuture().await().isSuccess());
            assertTrue(serverSslHandler.handshakeFuture().await().isSuccess());
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test
    public void testClientHandshakeTimeoutBecauseExecutorNotExecute() throws Exception {
        testHandshakeTimeoutBecauseExecutorNotExecute(true);
    }

    @Test
    public void testServerHandshakeTimeoutBecauseExecutorNotExecute() throws Exception {
        testHandshakeTimeoutBecauseExecutorNotExecute(false);
    }

    private static void testHandshakeTimeoutBecauseExecutorNotExecute(final boolean client) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.JDK).build();

        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.JDK).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, new Executor() {
            @Override
            public void execute(Runnable command) {
                if (!client) {
                    command.run();
                }
                // Do nothing to simulate slow execution.
            }
        });
        if (client) {
            clientSslHandler.setHandshakeTimeout(100, TimeUnit.MILLISECONDS);
        }
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, new Executor() {
            @Override
            public void execute(Runnable command) {
                if (client) {
                    command.run();
                }
                // Do nothing to simulate slow execution.
            }
        });
        if (!client) {
            serverSslHandler.setHandshakeTimeout(100, TimeUnit.MILLISECONDS);
        }
        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(serverSslHandler)
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(clientSslHandler);
                        }
                    }).connect(sc.localAddress());
            cc = future.syncUninterruptibly().channel();

            if (client) {
                Throwable cause = clientSslHandler.handshakeFuture().await().cause();
                assertThat(cause, CoreMatchers.<Throwable>instanceOf(SSLException.class));
                assertThat(cause.getMessage(), containsString("timed out"));
                assertFalse(serverSslHandler.handshakeFuture().await().isSuccess());
            } else {
                Throwable cause = serverSslHandler.handshakeFuture().await().cause();
                assertThat(cause, CoreMatchers.<Throwable>instanceOf(SSLException.class));
                assertThat(cause.getMessage(), containsString("timed out"));
                assertFalse(clientSslHandler.handshakeFuture().await().isSuccess());
            }
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 5000L)
    public void testSessionTicketsWithTLSv12() throws Throwable {
        testSessionTickets(SslUtils.PROTOCOL_TLS_V1_2);
    }

    @Test(timeout = 5000L)
    public void testSessionTicketsWithTLSv13() throws Throwable {
        assumeTrue(OpenSsl.isTlsv13Supported());
        testSessionTickets(SslUtils.PROTOCOL_TLS_V1_3);
    }

    private static void testSessionTickets(String protocol) throws Throwable {
        assumeTrue(OpenSsl.isAvailable());
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.OPENSSL)
                .protocols(protocol)
                .build();

        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.OPENSSL)
                .protocols(protocol)
                .build();

        OpenSslSessionTicketKey key = new OpenSslSessionTicketKey(new byte[OpenSslSessionTicketKey.NAME_SIZE],
                new byte[OpenSslSessionTicketKey.HMAC_KEY_SIZE], new byte[OpenSslSessionTicketKey.AES_KEY_SIZE]);
        ((OpenSslSessionContext) sslClientCtx.sessionContext()).setTicketKeys(key);
        ((OpenSslSessionContext) sslServerCtx.sessionContext()).setTicketKeys(key);

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);

        final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
        final byte[] bytes = new byte[96];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(serverSslHandler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt)  {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
                                    }
                                }
                            });
                        }
                    })
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(clientSslHandler);
                            ch.pipeline().addLast(new ByteToMessageDecoder() {

                                @Override
                                protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                                    if (in.readableBytes() == bytes.length) {
                                        queue.add(in.readBytes(bytes.length));
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    queue.add(cause);
                                }
                            });
                        }
                    }).connect(sc.localAddress());
            cc = future.syncUninterruptibly().channel();

            assertTrue(clientSslHandler.handshakeFuture().await().isSuccess());
            assertTrue(serverSslHandler.handshakeFuture().await().isSuccess());
            Object obj = queue.take();
            if (obj instanceof ByteBuf) {
                ByteBuf buffer = (ByteBuf) obj;
                ByteBuf expected = Unpooled.wrappedBuffer(bytes);
                try {
                    assertEquals(expected, buffer);
                } finally {
                    expected.release();
                }
            } else {
                throw (Throwable) obj;
            }
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 10000L)
    public void testHandshakeFailureOnlyFireExceptionOnce() throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        failVerification();
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return EmptyArrays.EMPTY_X509_CERTIFICATES;
                    }

                    private void failVerification() throws CertificateException {
                        throw new CertificateException();
                    }
                })
                .sslProvider(SslProvider.JDK).build();

        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.JDK).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);

        try {
            final Object terminalEvent = new Object();
            final BlockingQueue<Object> errorQueue = new LinkedBlockingQueue<Object>();
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(serverSslHandler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void exceptionCaught(final ChannelHandlerContext ctx, Throwable cause) {
                                    errorQueue.add(cause);
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    errorQueue.add(terminalEvent);
                                }
                            });
                        }
                    })
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            final ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(clientSslHandler);
                        }
                    }).connect(sc.localAddress());
            future.syncUninterruptibly();
            clientSslHandler.handshakeFuture().addListener(new FutureListener<Channel>() {
                @Override
                public void operationComplete(Future<Channel> f) {
                    future.channel().close();
                }
            });
            assertFalse(clientSslHandler.handshakeFuture().await().isSuccess());
            assertFalse(serverSslHandler.handshakeFuture().await().isSuccess());

            Object error = errorQueue.take();
            assertThat(error, Matchers.instanceOf(DecoderException.class));
            assertThat(((Throwable) error).getCause(), Matchers.<Throwable>instanceOf(SSLException.class));
            Object terminal = errorQueue.take();
            assertSame(terminalEvent, terminal);

            assertNull(errorQueue.poll(1, TimeUnit.MILLISECONDS));
        } finally {
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
        }
    }
}
