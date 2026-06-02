/*
 * Copyright 2024 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ZstdDecoderTest extends AbstractDecoderTest {

    public ZstdDecoderTest() throws Exception {
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new ZstdDecoder());
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        return Zstd.compress(data);
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

        // Decoder caps Window_Log at 15 (32 KiB) -> the frame must be rejected.
        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(4 * 1024 * 1024, 15));
        try {
            assertThrows(DecompressionException.class,
                    () -> ch.writeInbound(Unpooled.wrappedBuffer(compressed)));
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testFrameWithWindowLogWithinCapIsAccepted() {
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        byte[] compressed = compressWithWindowLog(payload, 18); // 256 KiB window

        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(4 * 1024 * 1024, 20));
        try {
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(compressed)));

            ByteBuf acc = Unpooled.buffer();
            try {
                ByteBuf b;
                while ((b = ch.readInbound()) != null) {
                    try {
                        acc.writeBytes(b);
                    } finally {
                        b.release();
                    }
                }
                byte[] actual = new byte[acc.readableBytes()];
                acc.readBytes(actual);
                assertArrayEquals(payload, actual);
            } finally {
                acc.release();
            }
        } finally {
            ch.finishAndReleaseAll();
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
