/*
 * Copyright 2020 The Netty Project
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
package io.netty.microbench.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.stomp.DefaultStompFrame;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.handler.codec.stomp.StompHeadersSubframe;
import io.netty.handler.codec.stomp.StompSubframeEncoder;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

@State(Scope.Benchmark)
@Fork(value = 2)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class StompEncoderBenchmark extends AbstractMicrobenchmark {

    private StompSubframeEncoder stompEncoder;
    private ByteBuf content;
    private StompFrame stompFrame;
    private ChannelHandlerContext context;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Param({ "true", "false" })
    public boolean voidPromise;

    @Param
    public ExampleStompHeadersSubframe.HeadersType headersType;

    @Param({ "0", "100", "1000" })
    public int contentLength;

    @Setup(Level.Trial)
    public void setup() {
        byte[] bytes = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(bytes);
        content = Unpooled.wrappedBuffer(bytes);
        ByteBuf testContent = Unpooled.unreleasableBuffer(content.asReadOnly());

        StompHeadersSubframe headersSubframe = ExampleStompHeadersSubframe.EXAMPLES.get(headersType);
        stompFrame = new DefaultStompFrame(headersSubframe.command(), testContent);
        stompFrame.headers().setAll(headersSubframe.headers());

        stompEncoder = new StompSubframeEncoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(
                pooledAllocator? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT, stompEncoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        content.release();
        content = null;
    }

    @Benchmark
    public void writeStompFrame() throws Exception {
        stompEncoder.write(context, stompFrame.retain(), newPromise());
    }

    private ChannelPromise newPromise() {
        return voidPromise? context.voidPromise() : context.newPromise();
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }
}
