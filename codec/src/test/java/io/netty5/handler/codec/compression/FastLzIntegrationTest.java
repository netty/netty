/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FastLzIntegrationTest extends AbstractIntegrationTest {

    public static class TestWithChecksum extends AbstractIntegrationTest {

        @Override
        protected EmbeddedChannel createEncoder() {
            return new EmbeddedChannel(new CompressionHandler(FastLzCompressor.newFactory(true)));
        }

        @Override
        protected EmbeddedChannel createDecoder() {
            return new EmbeddedChannel(new DecompressionHandler(FastLzDecompressor.newFactory(true)));
        }
    }

    public static class TestRandomChecksum extends AbstractIntegrationTest {

        @Override
        protected EmbeddedChannel createEncoder() {
            return new EmbeddedChannel(new CompressionHandler(FastLzCompressor.newFactory(rand.nextBoolean())));
        }

        @Override
        protected EmbeddedChannel createDecoder() {
            return new EmbeddedChannel(new DecompressionHandler(FastLzDecompressor.newFactory(rand.nextBoolean())));
        }
    }

    @Override
    protected EmbeddedChannel createEncoder() {
        return new EmbeddedChannel(new CompressionHandler(FastLzCompressor.newFactory(rand.nextBoolean())));
    }

    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new DecompressionHandler(FastLzDecompressor.newFactory(rand.nextBoolean())));
    }

    @Override   // test batched flow of data
    protected void testIdentity(final byte[] data, boolean heapBuffer) {
        initChannels();
        final BufferAllocator allocator = heapBuffer? onHeapAllocator() : offHeapAllocator();

        try (Buffer original = allocator.copyOf(data)) {
            int written = 0, length = rand.nextInt(100);
            while (written + length < data.length) {
                Buffer in = allocator.allocate(length);
                in.writeBytes(data, written, length);
                encoder.writeOutbound(in);
                written += length;
                length = rand.nextInt(100);
            }
            int leftover = data.length - written;
            Buffer in = allocator.allocate(leftover);
            in.writeBytes(data, written, leftover);
            encoder.writeOutbound(in);
            encoder.finish();

            List<Buffer> outbounds = new ArrayList<>();
            Buffer msg;
            while ((msg = encoder.readOutbound()) != null) {
                outbounds.add(msg);
            }
            try (Buffer compressed = allocator.compose(outbounds)) {
                assertThat(compressed, is(notNullValue()));

                final byte[] compressedArray = new byte[compressed.readableBytes()];
                compressed.readBytes(compressedArray, 0, compressedArray.length);
                written = 0;
                length = rand.nextInt(100);
                while (written + length < compressedArray.length) {
                    in = allocator.allocate(length);
                    in.writeBytes(compressedArray, written, length);
                    decoder.writeInbound(in);
                    written += length;
                    length = rand.nextInt(100);
                }
                leftover = compressedArray.length - written;
                in = allocator.allocate(leftover);
                in.writeBytes(compressedArray, written, leftover);
                decoder.writeInbound(in);

                assertFalse(compressed.readableBytes() > 0);
            }

            List<Buffer> inbounds = new ArrayList<>();
            while ((msg = decoder.readInbound()) != null) {
                inbounds.add(msg);
            }
            try (Buffer decompressed = allocator.compose(inbounds)) {
                assertEquals(original, decompressed);
            }
        } finally {
            closeChannels();
        }
    }
}
