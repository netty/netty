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
package io.netty.microbench.channel;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.microbench.util.AbstractMicrobenchmark;
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

    private abstract static class SharableInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
        @Override
        public final boolean isSharable() {
            return true;
        }
    }

    private abstract static class SharableOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {
        @Override
        public final boolean isSharable() {
            return true;
        }
    }

    private static final ChannelHandler INBOUND_CONSUMING_HANDLER = new SharableInboundHandlerAdapter() {
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
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            // NOOP
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // NOOP
        }
    };

    private static final ChannelHandler OUTBOUND_CONSUMING_HANDLER = new SharableOutboundHandlerAdapter() {
        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            // NOOP
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }
    };

    private static final ChannelHandler[] HANDLERS = {
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireUserEventTriggered(evt);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireUserEventTriggered(evt);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelActive();
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireUserEventTriggered(evt);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    ctx.fireChannelInactive();
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireUserEventTriggered(evt);
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.fireUserEventTriggered(evt);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) throws Exception {
                    ctx.read();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    ctx.write(msg, promise);
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void flush(ChannelHandlerContext ctx) throws Exception {
                    ctx.flush();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) throws Exception {
                    ctx.read();
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    ctx.write(msg, promise);
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) throws Exception {
                    ctx.read();
                }

                @Override
                public void flush(ChannelHandlerContext ctx) throws Exception {
                    ctx.flush();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    ctx.write(msg, promise);
                }

                @Override
                public void flush(ChannelHandlerContext ctx) throws Exception {
                    ctx.flush();
                }
            },
    };
    private static final int CALL_TYPE_ARRAY_SIZE = 1024;
    private static final int CALL_TYPE_ARRAY_MASK = CALL_TYPE_ARRAY_SIZE - 1;

    @Param({ "1024" })
    private int pipelineArrayLength;
    private int pipelineArrayMask;

    @Param({ "16" })
    public int extraHandlers;

    private ChannelPipeline[] pipelines;
    private ChannelPromise[] promises;
    private int pipelineCounter;

    private int[] callTypes;
    private int callTypeCounter;

    @Setup(Level.Iteration)
    public void setup() {
        SplittableRandom rng = new SplittableRandom();
        pipelineArrayMask = pipelineArrayLength - 1;
        pipelines = new ChannelPipeline[pipelineArrayLength];
        promises = new ChannelPromise[pipelineArrayLength];
        for (int i = 0; i < pipelineArrayLength; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.config().setAutoRead(false);
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(OUTBOUND_CONSUMING_HANDLER);
            for (int j = 0; j < extraHandlers; j++) {
                pipeline.addLast(HANDLERS[rng.nextInt(0, HANDLERS.length)]);
            }
            pipeline.addLast(INBOUND_CONSUMING_HANDLER);
            pipelines[i] = pipeline;
            promises[i] = pipeline.newPromise();
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
    @Benchmark()
    public void propagateVariety(Blackhole hole) {
        int index = pipelineCounter++ & pipelineArrayMask;
        ChannelPipeline pipeline = pipelines[index];
        hole.consume(pipeline.fireChannelActive());             // 1
        hole.consume(pipeline.fireChannelRead(MESSAGE));        // 2
        hole.consume(pipeline.fireChannelRead(MESSAGE));        // 3
        hole.consume(pipeline.write(MESSAGE, promises[index])); // 4
        hole.consume(pipeline.fireChannelRead(MESSAGE));        // 5
        hole.consume(pipeline.fireChannelRead(MESSAGE));        // 6
        hole.consume(pipeline.write(MESSAGE, promises[index])); // 7
        hole.consume(pipeline.fireChannelReadComplete());       // 8
        hole.consume(pipeline.fireUserEventTriggered(MESSAGE)); // 9
        hole.consume(pipeline.fireChannelWritabilityChanged()); // 10
        hole.consume(pipeline.flush());                         // 11
        hole.consume(pipeline.fireChannelInactive());           // 12
    }
}
