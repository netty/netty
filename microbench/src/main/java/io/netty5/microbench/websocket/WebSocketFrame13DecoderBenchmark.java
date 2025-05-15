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
package io.netty5.microbench.websocket;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty5.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty5.microbench.channel.EmbeddedChannelHandlerContext;
import io.netty5.microbench.util.AbstractMicrobenchmark;

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

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;

@State(Scope.Benchmark)
@Fork(value = 1)
@Threads(1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class WebSocketFrame13DecoderBenchmark extends AbstractMicrobenchmark {

    private WebSocket13FrameDecoder websocketDecoder;

    private ChannelHandlerContext context;

    private Supplier<Buffer> websocketFrameSupplier;
    @Param({ "0", "2", "4", "8", "32", "100", "1000", "3000" })
    public int contentLength;

    @Param({ "true", "false" })
    public boolean offHeapAllocator;

    @Param({ "true", "false" })
    public boolean masking;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        byte[] bytes = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(bytes);
        BufferAllocator allocator = offHeapAllocator? offHeapAllocator() : onHeapAllocator();
        Buffer testContent = allocator.allocate(contentLength).writeBytes(bytes);

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocket13FrameEncoder(masking));
        channel.writeOutbound(new BinaryWebSocketFrame(testContent));
        try (Buffer encodedBuffer = channel.readOutbound()) {
            byte[] encodedBytes = new byte[encodedBuffer.readableBytes()];
            encodedBuffer.copyInto(encodedBuffer.readerOffset(), encodedBytes, 0, encodedBuffer.readableBytes());
            websocketFrameSupplier = allocator.constBufferSupplier(encodedBytes);
        }

        channel.pipeline().remove(WebSocket13FrameEncoder.class);

        websocketDecoder = new WebSocket13FrameDecoder(masking, false, 65536);
        context = new EmbeddedChannelHandlerContext(allocator, websocketDecoder, channel) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
        websocketDecoder.handlerAdded(context);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        websocketFrameSupplier = null;
        context.close();
    }

    @Benchmark
    public void readWebSocketFrame() throws Exception {
        websocketDecoder.channelRead(context, websocketFrameSupplier.get());
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }
}
