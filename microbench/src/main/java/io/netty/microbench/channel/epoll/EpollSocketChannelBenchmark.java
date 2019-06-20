/*
 * Copyright 2018 The Netty Project
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
package io.netty.microbench.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Warmup(iterations = 10, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
public class EpollSocketChannelBenchmark extends AbstractMicrobenchmark {

    @Override
    protected String[] jvmArgs() {
        // Ensure we minimize the GC overhead by sizing the heap big enough.
        return new String[] { "-Xmx8g", "-Xms8g", "-Xmn6g" };
    }

    private EpollEventLoopGroup group;
    private Channel serverChan;
    private Channel chan;
    private ScheduledFuture<?> future;

    @Setup
    public void setup() throws Exception {
        group = new EpollEventLoopGroup(1);

        // add an arbitrary timeout to make the timer reschedule
        future = group.schedule(new Runnable() {
            @Override
            public void run() {
                throw new AssertionError();
            }
        }, 5, TimeUnit.MINUTES);
        serverChan = new ServerBootstrap()
            .channel(EpollServerSocketChannel.class)
            .group(group)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelDuplexHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof ByteBuf) {
                                ctx.writeAndFlush(msg, ctx.voidPromise());
                            } else {
                                throw new AssertionError();
                            }
                        }
                    });
                }
            })
            .bind(0)
            .sync()
            .channel();
    chan = new Bootstrap()
        .channel(EpollSocketChannel.class)
        .handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelDuplexHandler() {

                private ChannelPromise lastWritePromise;

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof ByteBuf) {

                            ByteBuf buf = (ByteBuf) msg;
                            try {
                                if (buf.readableBytes() == 1) {
                                    lastWritePromise.trySuccess();
                                    lastWritePromise = null;
                                } else {
                                    throw new AssertionError();
                                }
                            } finally {
                                buf.release();
                            }
                        } else {
                            throw new AssertionError();
                        }
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                            throws Exception {
                        if (lastWritePromise != null) {
                            throw new IllegalStateException();
                        }
                        lastWritePromise = promise;
                        super.write(ctx, msg, ctx.voidPromise());
                    }
                });
            }
        })
        .group(group)
        .connect(serverChan.localAddress())
        .sync()
        .channel();
    }

    @TearDown
    public void tearDown() throws Exception {
        chan.close().sync();
        serverChan.close().sync();
        future.cancel(true);
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Benchmark
    public Object pingPong(PerThreadState state) throws Exception {
        return spinWait(chan.pipeline().writeAndFlush(state.unReleasable.readerIndex(0)));
    }

    @Benchmark
    public Object executeSingle(PerThreadState state) throws Exception {
        state.reset();
        chan.eventLoop().execute(state.runnable);
        return state.spinUntilDone();
    }

    @Benchmark
    @Threads(3)
    public Object executeMulti(PerThreadState state) throws Exception {
        return executeSingle(state);
    }

    static <T> T spinWait(Future<T> future) throws ExecutionException, InterruptedException {
        while (!future.isDone()) ;
        return future.get();
    }

    @State(Scope.Thread)
    public static class PerThreadState {
        ByteBuf abyte;
        ByteBuf unReleasable;

        final AtomicBoolean done = new AtomicBoolean();

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                done.set(true);
            }
        };

        void reset() {
            done.lazySet(false);
        }

        boolean spinUntilDone() {
            for (;;) {
                boolean isDone = done.get();
                if (isDone) {
                    return isDone;
                }
            }
        }

        @Setup
        public void setup(EpollSocketChannelBenchmark bench) {
            abyte = bench.chan.alloc().directBuffer(1).writeByte('a');
            unReleasable = Unpooled.unreleasableBuffer(abyte);
        }

        @TearDown
        public void tearDown() throws Exception {
            abyte.release();
        }
    }
}
