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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static io.netty.handler.codec.compression.Lz4Constants.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Lz4FrameDecoderTest extends AbstractDecoderTest {

    private static final byte[] DATA = { 0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x05, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00,  // compr. and decompr. length
                                         (byte) 0x86, (byte) 0xE4, 0x79, 0x0F,            // checksum
                                         0x4E, 0x65, 0x74, 0x74, 0x79,                    // data
                                         0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // last empty block
                                         0x00, 0x00, 0x00, 0x00 };

    public Lz4FrameDecoderTest() throws Exception {
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new Lz4FrameDecoder(true));
    }

    @Test
    public void testUnexpectedBlockIdentifier() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[1] = 0x00;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "unexpected block identifier");
    }

    @Test
    public void testInvalidCompressedLength() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[12] = (byte) 0xFF;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "invalid compressedLength");
    }

    @Test
    public void testInvalidDecompressedLength() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[16] = (byte) 0xFF;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "invalid decompressedLength");
    }

    @Test
    public void testDecompressedAndCompressedLengthMismatch() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[13] = 0x01;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "mismatch");
    }

    @Test
    public void testUnexpectedBlockType() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[8] = 0x36;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "unexpected blockType");
    }

    @Test
    public void testMismatchingChecksum() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[17] = 0x01;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "mismatching checksum");
    }

    @Test
    public void testChecksumErrorOfLastBlock() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[44] = 0x01;

        assertThrows(DecompressionException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        tryDecodeAndCatchBufLeaks(channel, Unpooled.wrappedBuffer(data));
                    }
                }, "checksum error");
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int size = MAX_BLOCK_SIZE + 1;
        LZ4BlockOutputStream lz4Os = new LZ4BlockOutputStream(os,
                rand.nextInt(size - MIN_BLOCK_SIZE) + MIN_BLOCK_SIZE);
        lz4Os.write(data);
        lz4Os.close();

        return os.toByteArray();
    }
}
