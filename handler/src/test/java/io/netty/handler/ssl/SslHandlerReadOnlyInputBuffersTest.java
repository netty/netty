/*
 * Copyright 2013 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SslHandlerReadOnlyInputBuffersTest {
    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    @ParameterizedTest(name = "{index}: {0}, wrapDataSize={3}")
    @MethodSource("testScenarios")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void jdkSslProviderSupportsReadOnlyInputBuffers(String scenario,
                                                           Consumer<ChannelHandlerContext> contentWriter,
                                                           byte[] expectedContent, int wrapDataSize) throws Exception {
        SslProvider serverProvider = SslProvider.JDK;
        SslProvider clientProvider = SslProvider.JDK;
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Class<? extends ServerChannel> serverClass = NioServerSocketChannel.class;
            Class<? extends Channel> clientClass = NioSocketChannel.class;
            SocketAddress bindAddress = new InetSocketAddress(0);

            writeAndReadContentAndAssertResult(clientProvider, serverProvider, group, bindAddress, serverClass,
                                               clientClass, testInfo.getDisplayName(), contentWriter,
                                               expectedContent, wrapDataSize);
        } finally {
            group.shutdownGracefully();
        }
    }

    static Collection<Object[]> testScenarios() {
        final byte[] content = "HelloWorld".getBytes(StandardCharsets.US_ASCII);
        Map<String, Consumer<ChannelHandlerContext>> contentWriters = new HashMap<>();
        contentWriters.put("readonly", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(content).asReadOnly());
            }
        });
        contentWriters.put("composite_with_readonly", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                composite.addComponent(true, Unpooled.wrappedBuffer(content).asReadOnly());
                ctx.writeAndFlush(composite);
            }
        });
        contentWriters.put("composite_with_wrapped_readonly_bytebuffer", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                composite.addComponent(true, Unpooled.wrappedBuffer(
                        ByteBuffer.allocateDirect(content.length).put(content).flip().asReadOnlyBuffer()));
                ctx.writeAndFlush(composite);
            }
        });
        contentWriters.put("composite_with_writable_and_wrapped_readonly_buffers",
                           new Consumer<ChannelHandlerContext>() {
                               @Override
                               public void accept(ChannelHandlerContext ctx) {
                                   CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                                   composite.addComponent(true, ctx.alloc().directBuffer(content.length / 2)
                                                                   .writeBytes(content, 0, content.length / 2));
                                   composite.addComponent(true, Unpooled.wrappedBuffer(
                                           ByteBuffer.allocateDirect(content.length / 2)
                                                     .put(content, content.length / 2, content.length / 2).flip()
                                                     .asReadOnlyBuffer()));
                                   ctx.writeAndFlush(composite);
                               }
                           });
        contentWriters.put("composite_first_then_readonly_buffer", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                composite.addComponent(true, ctx.alloc().directBuffer(content.length / 2)
                                                .writeBytes(content, 0, content.length / 2));
                ctx.write(composite);
                ctx.writeAndFlush(ctx.alloc().directBuffer(content.length / 2)
                                     .writeBytes(content, content.length / 2, content.length / 2).asReadOnly());
            }
        });
        contentWriters.put("writable_and_wrapped_readonly_buffers", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                ctx.write(ctx.alloc().directBuffer(content.length / 2).writeBytes(content, 0, content.length / 2));
                ctx.writeAndFlush(Unpooled.wrappedBuffer(ByteBuffer.allocateDirect(content.length / 2)
                                                                   .put(content, content.length / 2, content.length / 2)
                                                                   .flip().asReadOnlyBuffer()));
            }
        });
        contentWriters.put("readonly_and_writable_buffers", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                ctx.write(ctx.alloc().directBuffer(content.length / 2).writeBytes(content, 0, content.length / 2)
                             .asReadOnly());
                ctx.writeAndFlush(ctx.alloc().directBuffer(content.length / 2)
                                     .writeBytes(content, content.length / 2, content.length / 2).asReadOnly());
            }
        });

        contentWriters.put("with_empty_buffers_empty_first", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                ctx.write(Unpooled.EMPTY_BUFFER);
                ctx.writeAndFlush(Unpooled.wrappedBuffer(content));
            }
        });
        contentWriters.put("with_empty_buffers_empty_last", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext ctx) {
                ctx.write(Unpooled.wrappedBuffer(content));
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
            }
        });
        int[] wrapDataSizes = {
                0, content.length / 3, content.length / 2, content.length * 2 / 3 + 1, content.length,
                content.length * 2
        };

        List<Object[]> scenarios = new ArrayList<>();
        for (int wrapDataSize : wrapDataSizes) {
            for (Map.Entry<String, Consumer<ChannelHandlerContext>> entry : contentWriters.entrySet()) {
                String scenario = entry.getKey();
                scenarios.add(new Object[] { scenario, entry.getValue(), content, wrapDataSize });
            }
        }

        final byte[] largeContent = new byte[16 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i & 0xFF);
        }
        scenarios.add(new Object[] {
                "large_readonly_buffer", new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext channelHandlerContext) {
                channelHandlerContext.writeAndFlush(
                        Unpooled.wrappedBuffer(ByteBuffer.allocateDirect(largeContent.length)
                                                         .put(largeContent).flip().asReadOnlyBuffer()));
            }
        }, largeContent, largeContent.length / 2
        });
        return scenarios;
    }

    private static void writeAndReadContentAndAssertResult(SslProvider clientProvider, SslProvider serverProvider,
                                                           EventLoopGroup group, SocketAddress bindAddress,
                                                           Class<? extends ServerChannel> serverClass,
                                                           Class<? extends Channel> clientClass, final String scenario,
                                                           final Consumer<ChannelHandlerContext> contentWriter,
                                                           final byte[] expectedContent, final int wrapDataSize)
            throws Exception {
        final String errorMessagePrefix = "Failed in scenario " + scenario;

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        final SslContext sslServerCtx =
                SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).sslProvider(serverProvider).build();

        final SslContext sslClientCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                         .sslProvider(clientProvider).build();

        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> serverDonePromise = group.next().newPromise();
            final Promise<Void> clientDonePromise = group.next().newPromise();
            final ByteArrayOutputStream serverQueue = new ByteArrayOutputStream(expectedContent.length);
            final ByteArrayOutputStream clientQueue = new ByteArrayOutputStream(expectedContent.length);

            sc = new ServerBootstrap().group(group).channel(serverClass)
                                      .childHandler(new ChannelInitializer<Channel>() {
                                          @Override
                                          protected void initChannel(Channel ch) {
                                              SslHandler sslHandler = sslServerCtx.newHandler(ch.alloc());
                                              sslHandler.setHandshakeTimeoutMillis(0);
                                              if (wrapDataSize >= 0) {
                                                  sslHandler.setWrapDataSize(wrapDataSize);
                                              }
                                              ch.pipeline().addLast(sslHandler);
                                              ch.pipeline().addLast(
                                                      new TestSslReadWriteHandler(contentWriter, expectedContent.length,
                                                                                  serverQueue, serverDonePromise));
                                          }
                                      }).bind(bindAddress).syncUninterruptibly().channel();

            cc = new Bootstrap().group(group).channel(clientClass).handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    SslHandler sslHandler = sslClientCtx.newHandler(ch.alloc());
                    sslHandler.setHandshakeTimeoutMillis(0);
                    if (wrapDataSize >= 0) {
                        sslHandler.setWrapDataSize(wrapDataSize);
                    }
                    ch.pipeline().addLast(sslHandler);
                    ch.pipeline().addLast(
                            new TestSslReadWriteHandler(contentWriter, expectedContent.length, clientQueue,
                                                        clientDonePromise));
                }
            }).connect(sc.localAddress()).syncUninterruptibly().channel();

            serverDonePromise.get();
            assertArrayEquals(expectedContent, serverQueue.toByteArray(), errorMessagePrefix);
            clientDonePromise.get();
            assertArrayEquals(expectedContent, clientQueue.toByteArray(), errorMessagePrefix);
        } catch (ExecutionException e) {
            throw new Exception(errorMessagePrefix, e);
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    private static final class TestSslReadWriteHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Consumer<ChannelHandlerContext> contentWriter;
        private final int expectedSize;
        private final ByteArrayOutputStream readQueue;
        private final Promise<Void> donePromise;

        TestSslReadWriteHandler(Consumer<ChannelHandlerContext> contentWriter, int expectedSize,
                                ByteArrayOutputStream readQueue, Promise<Void> donePromise) {
            this.contentWriter = contentWriter;
            this.expectedSize = expectedSize;
            this.readQueue = readQueue;
            this.donePromise = donePromise;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                if (sslEvt.isSuccess()) {
                    contentWriter.accept(ctx);
                } else {
                    donePromise.tryFailure(sslEvt.cause());
                }
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                msg.readBytes(readQueue, msg.readableBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (readQueue.size() >= expectedSize) {
                donePromise.trySuccess(null);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            donePromise.tryFailure(cause);
            ctx.fireExceptionCaught(cause);
        }
    }
}
