/*
 * Copyright 2018 The Netty Project
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
package io.netty5.microbench.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.epoll.EpollHandler;
import io.netty5.channel.epoll.EpollServerSocketChannel;
import io.netty5.channel.epoll.EpollSocketChannel;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

public class EpollSocketChannelBenchmark extends AbstractMicrobenchmark {
    private static final Runnable runnable = () -> { };

    private EventLoopGroup group;
    private Channel serverChan;
    private Channel chan;
    private ByteBuf abyte;
    private Future<?> future;

    @Setup
    public void setup() throws Exception {
        group = new MultithreadEventLoopGroup(1, EpollHandler.newFactory());

        // add an arbitrary timeout to make the timer reschedule
        future = group.schedule((Runnable) () -> {
            throw new AssertionError();
        }, 5, TimeUnit.MINUTES);
        serverChan = new ServerBootstrap()
            .channel(EpollServerSocketChannel.class)
            .group(group)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof ByteBuf) {
                                ctx.writeAndFlush(msg);
                            } else {
                                throw new AssertionError();
                            }
                        }
                    });
                }
            })
            .bind(0)
            .get();
    chan = new Bootstrap()
        .channel(EpollSocketChannel.class)
        .handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelHandler() {

                private Promise<Void> lastWritePromise;

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof ByteBuf) {

                            ByteBuf buf = (ByteBuf) msg;
                            try {
                                if (buf.readableBytes() == 1) {
                                    lastWritePromise.trySuccess(null);
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
                    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                        if (lastWritePromise != null) {
                            throw new IllegalStateException();
                        }
                        lastWritePromise = ctx.newPromise();
                        return ctx.write(msg);
                    }
                });
            }
        })
        .group(group)
        .connect(serverChan.localAddress())
        .get();

        abyte = chan.alloc().directBuffer(1);
        abyte.writeByte('a');
    }

    @TearDown
    public void tearDown() throws Exception {
        chan.close().sync();
        serverChan.close().sync();
        future.cancel();
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        abyte.release();
    }

    @Benchmark
    public Object pingPong() throws Exception {
        return chan.pipeline().writeAndFlush(abyte.retainedSlice()).sync();
    }

    @Benchmark
    public Object executeSingle() throws Exception {
        return chan.executor().submit(runnable).get();
    }

    @Benchmark
    @GroupThreads(3)
    public Object executeMulti() throws Exception {
        return chan.executor().submit(runnable).get();
    }
}
