/*
 * Copyright 2019 The Netty Project
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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class DefaultChannelPipelineBenchmark extends AbstractMicrobenchmark {

    private static final ChannelHandler NOOP_HANDLER = new ChannelInboundHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    private static final ChannelHandler CONSUMING_HANDLER = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    @Param({ "4" })
    public int extraHandlers;

    private ChannelPipeline pipeline;

    @Setup(Level.Iteration)
    public void setup() {
        pipeline = new EmbeddedChannel().pipeline();
        for (int i = 0; i < extraHandlers; i++) {
            pipeline.addLast(NOOP_HANDLER);
        }
        pipeline.addLast(CONSUMING_HANDLER);
    }

    @TearDown
    public void tearDown() {
        pipeline.channel().close();
    }

    @Benchmark
    public void propagateEvent(Blackhole hole) {
        for (int i = 0; i < 100; i++) {
            hole.consume(pipeline.fireChannelReadComplete());
        }
    }
}
