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
package io.netty.microbench.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.microbench.util.AbstractSharedExecutorMicrobenchmark;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static java.util.concurrent.TimeUnit.*;

@State(Scope.Benchmark)
public class AutoFlushBenchmark extends AbstractSharedExecutorMicrobenchmark {

    @Param({ "true", "false" })
    public boolean flush;

    @Param({ "1", "10", "100" })
    public int writeCount;

    private ChannelPipeline pipeline;
    private ByteBuf payload;
    private Channel serverChannel;
    private Channel clientChannel;
    private NioEventLoopGroup serverEventloop;
    private NioEventLoopGroup clientEventLoop;

    @Setup(Level.Trial)
    public void setup() {
        serverEventloop = new NioEventLoopGroup(1, new DefaultThreadFactory("server", true));
        clientEventLoop = new NioEventLoopGroup(1, new DefaultThreadFactory("client", true)) {
            @Override
            protected EventLoop newChild(Executor executor, Object... args) throws Exception {
                return super.newChild(executor, args[0], new SelectStrategyFactory() {
                    @Override
                    public SelectStrategy newSelectStrategy() {
                        return new SelectStrategy() {

                            @Override
                            public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks)
                                    throws Exception {
                                if (hasTasks) {
                                    return selectSupplier.get();
                                }
                                return SELECT;
                            }
                        };
                    }
                });
            }
        };
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventloop)
          .channel(NioServerSocketChannel.class)
          .childHandler(new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
              }
          });

        payload = createData(1024);

        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap cb = new Bootstrap();
        if (!flush) {
            cb.option(ChannelOption.AUTO_FLUSH, true);
        }

        cb.group(clientEventLoop)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
                  pipeline = ch.pipeline();
                  latch.countDown();
              }
          });

        ChannelFuture bind = sb.bind(0);
        SocketAddress serverAddr;
        try {
            bind.sync().await(1, MINUTES);
            serverChannel = bind.channel();
            serverAddr = serverChannel.localAddress();
            ChannelFuture clientChannelFuture = cb.connect(serverAddr);
            clientChannelFuture.sync().await(1, MINUTES);
            clientChannel = clientChannelFuture.channel();
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }

        AbstractSharedExecutorMicrobenchmark.executor(clientEventLoop.next());
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (clientChannel != null) {
            clientChannel.close();
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        Future<?> serverGroup = null;
        Future<?> clientGroup = null;

        if (serverEventloop != null) {
            serverGroup = serverEventloop.shutdownGracefully(0, 0, MILLISECONDS);
        }
        if (clientEventLoop != null) {
            clientGroup = clientEventLoop.shutdownGracefully(0, 0, MILLISECONDS);
        }
        if (serverGroup != null) {
            serverGroup.sync();
        }
        if (clientGroup != null) {
            clientGroup.sync();
        }
    }

    @Benchmark
    public void compareWithFlushOnEach() throws InterruptedException {
        ChannelFuture lastWriteFuture = clientChannel.voidPromise();
        if (flush) {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.writeAndFlush(payload.retain());
            }
        } else {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
        }

        lastWriteFuture.sync().await(10, SECONDS);
    }

    @Benchmark
    public void compareWithFlushAtEnd() throws InterruptedException {
        ChannelFuture lastWriteFuture = clientChannel.voidPromise();
        if (flush) {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
            pipeline.flush();
        } else {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
        }

        lastWriteFuture.sync().await(10, SECONDS);
    }

    @Benchmark
    public void compareWithFlushEvery5() throws InterruptedException {
        ChannelFuture lastWriteFuture = clientChannel.voidPromise();
        if (flush) {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
                if (i % 5 == 0) {
                    pipeline.flush();
                }
            }
            pipeline.flush();
        } else {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
        }

        lastWriteFuture.sync().await(10, SECONDS);
    }

    @Benchmark
    public void compareWithFlushEverySecond() throws InterruptedException {
        ChannelFuture lastWriteFuture = clientChannel.voidPromise();
        if (flush) {
            pipeline.channel().eventLoop().scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    pipeline.channel().flush();
                }
            }, 100, 100, SECONDS);

            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
            pipeline.flush();
        } else {
            for (int i = 0; i < writeCount; i++) {
                lastWriteFuture = pipeline.write(payload.retain());
            }
        }

        lastWriteFuture.sync().await(10, SECONDS);
    }

    private static ByteBuf createData(int length) {
        byte[] result = new byte[length];
        ThreadLocalRandom.current().nextBytes(result);
        return Unpooled.wrappedBuffer(result);
    }
}
