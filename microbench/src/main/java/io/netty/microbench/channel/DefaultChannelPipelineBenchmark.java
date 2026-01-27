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
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.CompletionHandler;
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

import java.util.SplittableRandom;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(5)
@State(Scope.Thread)
public class DefaultChannelPipelineBenchmark extends AbstractMicrobenchmark {
    private static final Object MESSAGE = new Object();

    private abstract static class SharableInboundHandlerAdapter implements ChannelInboundHandler {
        @Override
        public final boolean isSharable() {
            return true;
        }
    }

    private abstract static class SharableOutboundHandlerAdapter implements ChannelOutboundHandler {
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
        public void read(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
            // NOOP
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
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
                public void read(ChannelHandlerContext ctx) {
                    ctx.read();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                    ctx.write(msg, handler);
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void flush(ChannelHandlerContext ctx) {
                    ctx.flush();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) {
                    ctx.read();
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                    ctx.write(msg, handler);
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext ctx) {
                    ctx.read();
                }

                @Override
                public void flush(ChannelHandlerContext ctx) {
                    ctx.flush();
                }
            },
            new SharableOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                    ctx.write(msg, handler);
                }

                @Override
                public void flush(ChannelHandlerContext ctx) {
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
    private CompletionHandler[] handlers;
    private int pipelineCounter;

    private int[] callTypes;
    private int callTypeCounter;

    @Setup(Level.Iteration)
    public void setup() {
        SplittableRandom rng = new SplittableRandom();
        pipelineArrayMask = pipelineArrayLength - 1;
        pipelines = new ChannelPipeline[pipelineArrayLength];
        handlers = new CompletionHandler[pipelineArrayLength];
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
            handlers[i] = CompletionHandler.ignore();
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
    public void propagateEvent() {
        ChannelPipeline pipeline = pipelines[pipelineCounter++ & pipelineArrayMask];
        pipeline.fireChannelReadComplete();
    }

    @OperationsPerInvocation(12)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark()
    public void propagateVariety() {
        int index = pipelineCounter++ & pipelineArrayMask;
        ChannelPipeline pipeline = pipelines[index];
        pipeline.fireChannelActive();             // 1
        pipeline.fireChannelRead(MESSAGE);        // 2
        pipeline.fireChannelRead(MESSAGE);        // 3
        pipeline.write(MESSAGE, handlers[index]); // 4
        pipeline.fireChannelRead(MESSAGE);        // 5
        pipeline.fireChannelRead(MESSAGE);        // 6
        pipeline.write(MESSAGE, handlers[index]); // 7
        pipeline.fireChannelReadComplete();       // 8
        pipeline.fireUserEventTriggered(MESSAGE); // 9
        pipeline.fireChannelWritabilityChanged(); // 10
        pipeline.flush();                         // 11
        pipeline.fireChannelInactive();           // 12
    }
}
