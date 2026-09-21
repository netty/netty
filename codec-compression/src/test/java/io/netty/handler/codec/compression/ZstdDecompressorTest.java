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

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ZstdDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new ZstdEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return ZstdDecompressor.builder();
    }

    @Test
    public void addInputReleasesEmptyBuffer() throws DecompressionException {
        ByteBuf empty = Unpooled.buffer(0);
        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(empty);
            assertEquals(0, empty.refCnt());
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        }
    }

    @Test
    public void testFrameWithWindowLogAboveCapIsRejected() {
        // Incompressible random data so libzstd actually has to use the declared window
        // (highly compressible content lets libzstd shrink the effective window to the
        // content size, making setLongMax ineffective for the test).
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        // Compressed with windowLog = 21 (2 MiB window).
        byte[] compressed = compressWithWindowLog(payload, 21);

        // Decompressor caps Window_Log at 15 (32 KiB) -> the frame must be rejected.
        assertThrows(DecompressionException.class, () -> decompress(compressed, 15));
    }

    @Test
    public void testFrameWithWindowLogWithinCapIsAccepted() throws DecompressionException {
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        byte[] compressed = compressWithWindowLog(payload, 18); // 256 KiB window

        assertArrayEquals(payload, decompress(compressed, 20));
    }

    @Test
    public void testTruncatedFrameIsRejectedAfterEndOfInput() {
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        byte[] compressed = compressWithWindowLog(payload, 18);
        byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

        assertThrows(DecompressionException.class, () -> decompress(truncated, 20));
    }

    private static byte[] decompress(byte[] compressed, int maxWindowLog) throws DecompressionException {
        ByteBuf input = Unpooled.wrappedBuffer(compressed);
        ByteBuf output = Unpooled.buffer();
        try (Decompressor decompressor = ZstdDecompressor.builder()
                .maxWindowLog(maxWindowLog)
                .build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(input);
            input = null;

            boolean sentEof = false;
            while (true) {
                switch (decompressor.status()) {
                    case NEED_INPUT:
                        assertFalse(sentEof);
                        sentEof = true;
                        decompressor.endOfInput();
                        break;
                    case NEED_OUTPUT:
                        ByteBuf b = decompressor.takeOutput();
                        try {
                            output.writeBytes(b);
                        } finally {
                            b.release();
                        }
                        break;
                    case COMPLETE:
                        byte[] bytes = new byte[output.readableBytes()];
                        output.readBytes(bytes);
                        return bytes;
                    default:
                        throw new AssertionError("Unknown status: " + decompressor.status());
                }
            }
        } finally {
            if (input != null) {
                input.release();
            }
            output.release();
        }
    }

    private static byte[] compressWithWindowLog(byte[] data, int windowLog) {
        try (ZstdCompressCtx ctx = new ZstdCompressCtx()) {
            ctx.setLevel(Zstd.defaultCompressionLevel());
            ctx.setWindowLog(windowLog);
            byte[] dst = new byte[(int) Zstd.compressBound(data.length)];
            int written = ctx.compressByteArray(dst, 0, dst.length, data, 0, data.length);
            byte[] out = new byte[written];
            System.arraycopy(dst, 0, out, 0, written);
            return out;
        }
    }
}
