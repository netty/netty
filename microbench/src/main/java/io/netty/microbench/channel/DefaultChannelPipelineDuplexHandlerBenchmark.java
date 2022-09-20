/*
 * Copyright 2022 The Netty Project
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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.LinuxPerfAsmProfiler;
import org.openjdk.jmh.profile.LinuxPerfC2CProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class DefaultChannelPipelineDuplexHandlerBenchmark extends AbstractMicrobenchmark {

    private ChannelPipeline pipeline;
    private EmbeddedChannel channel;

    @Setup
    public void setup() {
        channel = new EmbeddedChannel() {
            @Override
            public void runPendingTasks() {
                // NO-OP to reduce noise on flush
            }
        };
        // disabling auto-read to reduce noise on flush
        channel.config().setAutoRead(false);
        pipeline = channel.pipeline();
        pipeline.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(final ChannelHandlerContext ctx) {
                ctx.fireChannelReadComplete();
            }
        });
        // Duplex handlers implements both out/in interfaces causing a scalability issue
        // see https://bugs.openjdk.org/browse/JDK-8180450
        pipeline.addLast(new ChannelDuplexHandler() {
            @Override
            public void channelReadComplete(final ChannelHandlerContext ctx) {
                ctx.fireChannelReadComplete();
            }

            @Override
            public void flush(final ChannelHandlerContext ctx) {
                ctx.flush();
            }
        });
        pipeline.addLast(new ChannelDuplexHandler() {
            @Override
            public void channelReadComplete(final ChannelHandlerContext ctx) {
                ctx.flush();
            }
        });
    }

    @TearDown
    public void tearDown() {
        pipeline.channel().close();
    }

    @Benchmark
    public void propagateEvent(Blackhole hole) {
        hole.consume(pipeline.fireChannelReadComplete());
    }

    @Benchmark
    @Threads(4)
    public void parallelPropagateEvent(Blackhole hole) {
        hole.consume(pipeline.fireChannelReadComplete());
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        if (PlatformDependent.isOsx() || PlatformDependent.isWindows()) {
            return super.newOptionsBuilder();
        }
        return super.newOptionsBuilder()
                .addProfiler(LinuxPerfAsmProfiler.class)
                .addProfiler(LinuxPerfC2CProfiler.class);
    }
}
