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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.*;

@State(Scope.Benchmark)
public class AutoFlushBenchmark extends AbstractChannelBenchmark {

    @Param({ "true", "false" })
    public boolean flush;

    @Param({ "1", "10", "100" })
    public int writeCount;

    @Setup(Level.Trial)
    public void setup() {
        setup0(EMPTY_INITIALIZER, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setOption(ChannelOption.AUTO_FLUSH, !flush);
            }
        });
    }

    @Benchmark
    public void compareWithFlushOnEach() throws InterruptedException, TimeoutException {
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

        awaitCompletion(lastWriteFuture);
    }

    @Benchmark
    public void compareWithFlushAtEnd() throws InterruptedException, TimeoutException {
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

        awaitCompletion(lastWriteFuture);
    }

    @Benchmark
    public void compareWithFlushEvery5() throws InterruptedException, TimeoutException {
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

        awaitCompletion(lastWriteFuture);
    }

    @Benchmark
    public void compareWithFlushEverySecond() throws InterruptedException, TimeoutException {
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

        awaitCompletion(lastWriteFuture);
    }
}
