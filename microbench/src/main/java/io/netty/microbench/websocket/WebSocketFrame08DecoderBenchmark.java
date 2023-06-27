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
package io.netty.microbench.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.microbench.channel.EmbeddedChannelHandlerContext;
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
public class WebSocketFrame08DecoderBenchmark extends AbstractMicrobenchmark {

    private WebSocket08FrameDecoder websocketDecoder;

    private ChannelHandlerContext context;

    private ByteBuf websocketFrame;
    @Param({ "0", "2", "4", "8", "32", "100", "1000", "3000" })
    public int contentLength;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Param({ "true" })
    public boolean masking;

    @Setup(Level.Trial)
    public void setUp() {
        byte[] bytes = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(bytes);
        ByteBufAllocator allocator = pooledAllocator? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT;
        ByteBuf testContent = allocator.buffer(contentLength).writeBytes(bytes);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocket08FrameEncoder(masking));
        channel.writeOutbound(new BinaryWebSocketFrame(testContent));
        websocketFrame = Unpooled.unreleasableBuffer(((ByteBuf) channel.readOutbound()).asReadOnly());
        channel.pipeline().remove(WebSocket08FrameEncoder.class);

        websocketDecoder = new WebSocket08FrameDecoder(masking, false, 65536);
        context = new EmbeddedChannelHandlerContext(allocator, websocketDecoder, channel) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        websocketFrame.release();
        websocketFrame = null;
    }

    @Benchmark
    public void readWebSocketFrame() throws Exception {
        websocketDecoder.channelRead(context, websocketFrame.retainedDuplicate());
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }
}
