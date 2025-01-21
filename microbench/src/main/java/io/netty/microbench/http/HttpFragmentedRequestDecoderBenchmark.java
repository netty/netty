/*
 * Copyright 2023 The Netty Project
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
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static io.netty.microbench.http.HttpRequestDecoderUtils.CONTENT_LENGTH;
import static io.netty.microbench.http.HttpRequestDecoderUtils.CONTENT_MIXED_DELIMITERS;

/**
 * This benchmark is based on HttpRequestDecoderTest class.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class HttpFragmentedRequestDecoderBenchmark extends AbstractMicrobenchmark {
    @Param({ "64", "128" })
    public int headerFragmentBytes;

    @Param({ "false", "true" })
    public boolean direct;

    @Param({ "false", "true" })
    public boolean pooled;

    @Param({ "true", "false"})
    public boolean validateHeaders;

    private EmbeddedChannel channel;

    private ByteBuf[] fragmentedRequest;

    private static ByteBuf[] stepsBuffers(ByteBufAllocator alloc, byte[] content, int fragmentSize, boolean direct) {
        // allocate a single big buffer and just slice it
        final int headerLength = content.length - CONTENT_LENGTH;
        final ArrayList<ByteBuf> bufs = new ArrayList<ByteBuf>();
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength - a;
            }
            final ByteBuf buf = direct? alloc.directBuffer(amount, amount) : alloc.heapBuffer(amount, amount);
            buf.writeBytes(content, a, amount);
            bufs.add(buf);
            a += amount;
        }
        // don't split the content
        // Should produce HttpContent
        final ByteBuf buf = direct?
                alloc.directBuffer(CONTENT_LENGTH, CONTENT_LENGTH) :
                alloc.heapBuffer(CONTENT_LENGTH, CONTENT_LENGTH);
        buf.writeBytes(content, content.length - CONTENT_LENGTH, CONTENT_LENGTH);
        bufs.add(buf);
        return bufs.toArray(new ByteBuf[0]);
    }

    @Setup
    public void initPipeline() {
        final ByteBufAllocator allocator = pooled? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT;
        fragmentedRequest = stepsBuffers(allocator, CONTENT_MIXED_DELIMITERS, headerFragmentBytes, direct);
        channel = new EmbeddedChannel(
                new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE,
                                       validateHeaders, DEFAULT_INITIAL_BUFFER_SIZE));
    }

    @TearDown
    public void releaseStepBuffers() {
        for (ByteBuf buf : fragmentedRequest) {
            buf.release();
        }
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public void testDecodeWholeRequestInMultipleStepsMixedDelimiters() {
        final EmbeddedChannel channel = this.channel;
        for (ByteBuf buf : this.fragmentedRequest) {
            buf.resetReaderIndex();
            buf.retain();
            channel.writeInbound(buf);
            final Queue<Object> decoded = channel.inboundMessages();
            Object o;
            while ((o = decoded.poll()) != null) {
                ReferenceCountUtil.release(o);
            }
        }
    }
}
