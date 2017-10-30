/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteBuffer;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
public class Base64Benchmark extends AbstractMicrobenchmark {
    private ResourceLeakDetector.Level oldLevel;
    private java.util.Base64.Encoder encoder;

    private ByteBufAllocator allocator;
    private ByteBufAllocator pooledAllocator;

    @Param({ "true", "false" })
    public boolean directSrc;

    @Param({ "1024", "65536" })
    public int size;

    @Param({ "SIMPLE", "ADVANCED", "PARANOID" })
    public String level;

    private ByteBuf src;

    @Setup
    public void setup() {
        oldLevel = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(level));
        allocator = new UnpooledByteBufAllocator(true);
        pooledAllocator = new PooledByteBufAllocator(true);

        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        if (directSrc) {
            src = allocator.directBuffer(bytes.length);
            src.writeBytes(bytes);
        } else {
            src = Unpooled.wrappedBuffer(bytes);
        }
        encoder = java.util.Base64.getMimeEncoder(76, new byte[]{ '\n' });
    }

    @TearDown
    public void tearDown() {
        src.release();
        ResourceLeakDetector.setLevel(oldLevel);
    }

    @Benchmark
    public boolean javaBase64Encode() {
        src.setIndex(0, size);
        return encodeJava8(allocator, src).release();
    }

    @Benchmark
    public boolean javaBase64EncodePooled() {
        src.setIndex(0, size);
        return encodeJava8(pooledAllocator, src).release();
    }

    @Benchmark
    public boolean nettyBase64Encode() {
        src.setIndex(0, size);
        return encode(allocator, src).release();
    }

    @Benchmark
    public boolean nettyBase64EncodePooled() {
        src.setIndex(0, size);
        return encode(pooledAllocator, src).release();
    }

    private static ByteBuf encode(ByteBufAllocator allocator, ByteBuf src) {
        ByteBuf dst = Base64.encode(src, src.readerIndex(),
                src.readableBytes(), true, Base64Dialect.STANDARD, allocator);
        src.readerIndex(src.writerIndex());
        return dst;
    }

    private ByteBuf encodeJava8(ByteBufAllocator allocator, ByteBuf src) {
        ByteBuffer srcBuffer = src.internalNioBuffer(src.readerIndex(), src.readableBytes());
        int remaining = srcBuffer.remaining();
        ByteBuffer encodedBuffer = encoder.encode(srcBuffer);
        src.skipBytes(remaining - srcBuffer.remaining());
        // Copy to direct uffer
        ByteBuf dst = allocator.buffer(encodedBuffer.remaining());
        dst.writeBytes(dst);
        return dst;
    }
}
