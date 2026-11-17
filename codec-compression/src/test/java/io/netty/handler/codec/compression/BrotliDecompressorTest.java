/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.compression;

import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrotliDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new BrotliEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return BrotliDecompressor.builder();
    }

    @Test
    public void reportsOutputBeforeMoreInput() throws Exception {
        ByteBuf[] data = largeData();

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(data[0]);
            data[0] = null;

            assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
            decompressor.takeOutput().release();
        } finally {
            for (ByteBuf buffer : data) {
                if (buffer != null) {
                    buffer.release();
                }
            }
        }
    }

    @Test
    public void endOfInputRejectsTruncatedStream() throws Exception {
        ByteBuf[] data = smallData();
        ByteBuf truncated = data[0].readRetainedSlice(data[0].readableBytes() - 1);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(truncated);
            truncated = null;

            while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                decompressor.takeOutput().release();
            }

            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        } finally {
            if (truncated != null) {
                truncated.release();
            }
            for (ByteBuf buffer : data) {
                buffer.release();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "524288, 18, 1000000",
            "524288, 18, 1",
            "100000, 10, 1000000",
            "100000, 10, 1",
            "1048576, 10, 1000000",
            "1048576, 10, 3",
    })
    public void highlyCompressibleData(int length, int window, int chunkSize) throws Exception {
        // a long run of zeros compresses to a few bytes, and the decoder has to produce many windows of output
        // after all input was consumed
        byte[] compressed = Encoder.compress(new byte[length], new Encoder.Parameters().setWindow(window));

        long decompressedLength = 0;
        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            for (int i = 0; i < compressed.length; i += chunkSize) {
                assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
                int n = Math.min(chunkSize, compressed.length - i);
                ByteBuf input = ByteBufAllocator.DEFAULT.buffer(n).writeBytes(compressed, i, n);
                decompressor.addInput(input);
                decompressedLength += drainZeros(decompressor);
            }
            if (decompressor.status() == Decompressor.Status.NEED_INPUT) {
                decompressor.endOfInput();
                decompressedLength += drainZeros(decompressor);
            }
            assertEquals(Decompressor.Status.COMPLETE, decompressor.status());
        }
        assertEquals(length, decompressedLength);
    }

    @Test
    public void truncatedHighlyCompressibleData() throws Exception {
        byte[] compressed = Encoder.compress(new byte[1048576], new Encoder.Parameters().setWindow(10));

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(compressed, 0, compressed.length - 1));
            drainZeros(decompressor);
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        }
    }

    private static long drainZeros(Decompressor decompressor) throws DecompressionException {
        long n = 0;
        while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
            ByteBuf output = decompressor.takeOutput();
            try {
                for (int i = output.readerIndex(); i < output.writerIndex(); i++) {
                    assertEquals(0, output.getByte(i));
                }
                n += output.readableBytes();
            } finally {
                output.release();
            }
        }
        return n;
    }
}
