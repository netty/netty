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

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LzmaFrameEncoderTest extends AbstractEncoderTest {

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new CompressionHandler(LzmaCompressor.newFactory()));
    }

    @ParameterizedTest
    @MethodSource("smallData")
    @Override
    public void testCompressionOfBatchedFlowOfData(Buffer data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    @Override
    protected void testCompressionOfBatchedFlow(final Buffer data) throws Exception {
        try (Buffer expected = data.copy()) {
            List<Integer> originalLengths = new ArrayList<>();
            final int dataLength = data.readableBytes();
            int written = 0, length = rand.nextInt(50);
            while (written + length < dataLength) {
                Buffer in = data.readSplit(length);
                assertTrue(channel.writeOutbound(in));
                written += length;
                originalLengths.add(length);
                length = rand.nextInt(50);
            }
            length = dataLength - written;
            Buffer in = data.readSplit(length);
            originalLengths.add(length);
            assertTrue(channel.writeOutbound(in));
            assertTrue(channel.finish());

            class TestSupplier implements Supplier<Buffer> {
                int proccessed;
                @Override
                public Buffer get() {
                    Buffer msg = channel.readOutbound();
                    if (msg == null) {
                        return null;
                    }
                    try {
                        return decompress(msg, originalLengths.get(proccessed++));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }

            TestSupplier supplier = new TestSupplier();
            try (CompositeBuffer decompressed = CompressionTestUtils.compose(channel.bufferAllocator(), supplier)) {
                assertEquals(originalLengths.size(), supplier.proccessed);
                assertEquals(expected, decompressed);
            }
        }
    }

    @Override
    protected Buffer decompress(Buffer compressed, int originalLength) throws Exception {
        InputStream is = new BufferInputStream(compressed.send());
        LzmaInputStream lzmaIs = null;
        byte[] decompressed = new byte[originalLength];
        try {
            lzmaIs = new LzmaInputStream(is, new Decoder());
            int remaining = originalLength;
            while (remaining > 0) {
                int read = lzmaIs.read(decompressed, originalLength - remaining, remaining);
                if (read > 0) {
                    remaining -= read;
                } else {
                    break;
                }
            }
            assertEquals(-1, lzmaIs.read());
        } finally {
            if (lzmaIs != null) {
                lzmaIs.close();
            }
            // LzmaInputStream does not close the stream it wraps, so we should always close.
            // The close operation should be safe to call multiple times anyways so lets just call it and be safe.
            // https://github.com/jponge/lzma-java/issues/14
            if (is != null) {
                is.close();
            }
        }

        return channel.bufferAllocator().copyOf(decompressed);
    }
}
