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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ChannelWriteBenchmark extends AbstractChannelBenchmark {

    @Param({ "1", "5", "10" })
    public int handlerCount;

    @Param({ "1", "10" })
    public int writeCount;

    @Setup(Level.Trial)
    public void setup() {
        setup0(EMPTY_INITIALIZER, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                for (int i = 0; i < handlerCount; i++) {
                    ch.pipeline().addLast(new ChannelDuplexHandler());
                }
                ch.pipeline().addFirst(BufferReleaseHandler.INSTANCE);
            }
        });
    }

    @Benchmark
    public void measureWriteWithFlushAtEnd() throws Exception {
        ChannelFuture lastWriteFuture = null;
        for (int i = 0; i < writeCount; i++) {
            lastWriteFuture = pipeline.write(payload.retainedDuplicate());
        }
        pipeline.flush();
        awaitCompletion(lastWriteFuture);
    }

    @Benchmark
    public void measureWriteWithFlushOnEach() throws Exception {
        ChannelFuture lastWriteFuture = null;
        for (int i = 0; i < writeCount; i++) {
            lastWriteFuture = pipeline.writeAndFlush(payload.retainedDuplicate());
        }
        awaitCompletion(lastWriteFuture);
    }
}
