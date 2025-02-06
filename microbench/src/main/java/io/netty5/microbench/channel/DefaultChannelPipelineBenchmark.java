/*
 * Copyright 2019 The Netty Project
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
package io.netty5.microbench.channel;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(5)
@State(Scope.Thread)
public class DefaultChannelPipelineBenchmark extends AbstractMicrobenchmark {
    private static final Object MESSAGE = new Object();
    private static final Future<Void> FUTURE = GlobalEventExecutor.INSTANCE.newSucceededFuture();

    private abstract static class SharableHandlerAdapter implements ChannelHandler {
        @Override
        public final boolean isSharable() {
            return true;
        }
    }

    private static final ChannelHandler CONSUMING_HANDLER = new SharableHandlerAdapter() {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // NOOP
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
            // NOOP
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
            // NOOP
        }

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            // NOOP
            return FUTURE;
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // NOOP
        }
    };

    private static final ChannelHandler[] HANDLERS = {
            new SharableHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireChannelInboundEvent(evt);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireChannelInboundEvent(evt);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireChannelInboundEvent(evt);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireChannelInboundEvent(evt);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireChannelInboundEvent(evt);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
                    ctx.read();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    return ctx.write(msg);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void flush(ChannelHandlerContext ctx) {
                    ctx.flush();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
                    ctx.read();
                }

                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    return ctx.write(msg);
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
                    ctx.read();
                }

                @Override
                public void flush(ChannelHandlerContext ctx) {
                    ctx.flush();
                }
            },
            new SharableHandlerAdapter() {
                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    return ctx.write(msg);
                }

                @Override
                public void flush(ChannelHandlerContext ctx) {
                    ctx.flush();
                }
            },
    };

    @Param({ "1024" })
    private int pipelineArrayLength;
    private int pipelineArrayMask;

    @Param({ "16" })
    public int extraHandlers;

    private ChannelPipeline[] pipelines;
    private int pipelineCounter;

    @Setup(Level.Iteration)
    public void setup() {
        SplittableRandom rng = new SplittableRandom();
        pipelineArrayMask = pipelineArrayLength - 1;
        pipelines = new ChannelPipeline[pipelineArrayLength];
        for (int i = 0; i < pipelineArrayLength; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.setOption(ChannelOption.AUTO_READ, false);
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(CONSUMING_HANDLER);
            for (int j = 0; j < extraHandlers; j++) {
                pipeline.addLast(HANDLERS[rng.nextInt(0, HANDLERS.length)]);
            }
            pipeline.addLast(CONSUMING_HANDLER);
            pipelines[i] = pipeline;
        }
    }

    @TearDown
    public void tearDown() {
        for (ChannelPipeline pipeline : pipelines) {
            pipeline.channel().close();
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void propagateEvent(Blackhole hole) {
        ChannelPipeline pipeline = pipelines[pipelineCounter++ & pipelineArrayMask];
        hole.consume(pipeline.fireChannelReadComplete());
    }

    @OperationsPerInvocation(12)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void propagateVariety(Blackhole hole) {
        int index = pipelineCounter++ & pipelineArrayMask;
        ChannelPipeline pipeline = pipelines[index];
        hole.consume(pipeline.fireChannelActive());              // 1
        hole.consume(pipeline.fireChannelRead(MESSAGE));         // 2
        hole.consume(pipeline.fireChannelRead(MESSAGE));         // 3
        hole.consume(pipeline.write(MESSAGE));                   // 4
        hole.consume(pipeline.fireChannelRead(MESSAGE));         // 5
        hole.consume(pipeline.fireChannelRead(MESSAGE));         // 6
        hole.consume(pipeline.write(MESSAGE));                   // 7
        hole.consume(pipeline.fireChannelReadComplete());        // 8
        hole.consume(pipeline.fireChannelInboundEvent(MESSAGE)); // 9
        hole.consume(pipeline.fireChannelWritabilityChanged());  // 10
        hole.consume(pipeline.flush());                          // 11
        hole.consume(pipeline.fireChannelInactive());            // 12
    }
}
