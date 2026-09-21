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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Arrays;

import static io.netty.handler.codec.compression.Bzip2Constants.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

public class Bzip2DecoderTest extends AbstractDecoderTest {

    private static final byte[] DATA = { 0x42, 0x5A, 0x68, 0x37, 0x31, 0x41, 0x59, 0x26, 0x53,
                                         0x59, 0x77, 0x7B, (byte) 0xCA, (byte) 0xC0, 0x00, 0x00,
                                         0x00, 0x05, (byte) 0x80, 0x00, 0x01, 0x02, 0x00, 0x04,
                                         0x20, 0x20, 0x00, 0x30, (byte) 0xCD, 0x34, 0x19, (byte) 0xA6,
                                         (byte) 0x89, (byte) 0x99, (byte) 0xC5, (byte) 0xDC, (byte) 0x91,
                                         0x4E, 0x14, 0x24, 0x1D, (byte) 0xDE, (byte) 0xF2, (byte) 0xB0, 0x00 };

    public Bzip2DecoderTest() throws Exception {
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new Bzip2Decoder());
    }

    private void writeInboundDestroyAndExpectDecompressionException(ByteBuf in) {
        try {
            channel.writeInbound(in);
        } finally {
            try {
                destroyChannel();
                fail();
            } catch (DecompressionException ignored) {
                // expected
            }
        }
    }

    @Test
    public void testUnexpectedStreamIdentifier() {
        final ByteBuf in = Unpooled.buffer();
        in.writeLong(1823080128301928729L); //random value
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                writeInboundDestroyAndExpectDecompressionException(in);
            }
        }, "Unexpected stream identifier contents");
    }

    @Test
    public void testInvalidBlockSize() {
        final ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('0');  //incorrect block size

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "block size is invalid");
    }

    @Test
    public void testBadBlockHeader() {
        final ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('1');  //block size
        in.writeMedium(11); //incorrect block header
        in.writeMedium(11); //incorrect block header
        in.writeInt(11111); //block CRC

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "bad block header");
    }

    @Test
    public void testStreamCrcErrorOfEmptyBlock() {
        final ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('1');  //block size
        in.writeMedium(END_OF_STREAM_MAGIC_1);
        in.writeMedium(END_OF_STREAM_MAGIC_2);
        in.writeInt(1);  //wrong storedCombinedCRC

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "stream CRC error");
    }

    @Test
    public void testStreamCrcError() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[41] = (byte) 0xDD;

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                tryDecodeAndCatchBufLeaks(channel, Unpooled.wrappedBuffer(data));
            }
        }, "stream CRC error");
    }

    @Test
    public void testIncorrectHuffmanGroupsNumber() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[25] = 0x70;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "incorrect huffman groups number");
    }

    @Test
    public void testIncorrectSelectorsNumber() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[25] = 0x2F;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        }, "incorrect selectors number");
    }

    @Test
    public void testBlockCrcError() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[11] = 0x77;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                writeInboundDestroyAndExpectDecompressionException(in);
            }
        }, "block CRC error");
    }

    @Test
    public void testStartPointerInvalid() {
        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[14] = (byte) 0xFF;

        final ByteBuf in = Unpooled.wrappedBuffer(data);
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                writeInboundDestroyAndExpectDecompressionException(in);
            }
        }, "start pointer invalid");
    }

    /**
     * Regression test for the infinite-loop in {@link Bzip2BlockDecompressor#read()}.
     *
     * <p>The bzip2 block below is hand-crafted so that its inverse-BWT output is exactly
     * four consecutive 'A' bytes with no trailing run-length count byte. This is a
     * malformed-stream
     *
     * <p>Stream construction (bit-level, MSB first after the byte-aligned header):
     * <pre>
     *   Bytes  0– 3   "BZh1"                  stream header (block size 1 = 100 000 bytes)
     *   Bytes  4– 9   0x314159265359           block-header magic (pi)
     *   Bytes 10–13   0x00000000               block CRC — intentionally wrong; a
     *                                           DecompressionException from checkCRC() is the
     *                                           expected outcome after the fix is applied
     *   --- bit-level section (read via Bzip2BitReader, MSB-first) ---
     *    1 bit          randomized = 0
     *   24 bits         bwtStartPointer = 0
     *   16 bits         huffmanInUse16 = 0x0800 (group 4, bytes 0x40–0x4F present)
     *   16 bits         group-4 symbol bitmap = 0x4000 (only 0x41 = 'A' present)
     *    3 bits         totalTables = 2 (minimum allowed)
     *   15 bits         totalSelectors = 1
     *    1 bit          selector[0] unary-coded as 0 → table 0
     *    8 bits         table 0: initial length 2 (5 bits = 00010), then three 0-delta
     *                   bits for RUNA/RUNB/EOB → all code lengths = 2
     *    8 bits         table 1: identical to table 0
     *    6 bits         Huffman data: RUNB(01) RUNA(00) EOB(10)
     *                   RUNB first: repeatCount=2, RUNA: repeatCount=4, EOB flushes
     *                   4 copies of huffmanSymbolMap[0]='A' into the BWT block.
     *                   bwtBlockLength = 4 with NO trailing count byte → triggers the bug.
     *   48 bits         end-of-stream magic 0x177245 0x385090
     *   32 bits         stream CRC = 0
     * </pre>
     */
    @Test
    public void testRleOffByOneDoesNotCauseInfiniteLoop() {
        final byte[] malformed = {
            0x42, 0x5A, 0x68, 0x31,                                    // "BZh1"
            0x31, 0x41, 0x59, 0x26, 0x53, 0x59,                        // block magic
            0x00, 0x00, 0x00, 0x00,                                     // block CRC
            // bit-level section (MSB-first packing, computed field-by-field):
            0x00, 0x00, 0x00, 0x04, 0x00, 0x20,                        // random+bwtPtr+inUse16 start
            0x00, 0x20, 0x00, 0x21, 0x01, 0x04,                        // inUse16 end+grp4+tbls+sel+tbl0
            (byte) 0x85, (byte) 0xDC, (byte) 0x91, 0x4E, 0x14, 0x24,  // tbl1+data+EOS magic
            0x00, 0x00, 0x00, 0x00, 0x00                               // stream CRC + padding
        };

        EmbeddedChannel ch = new EmbeddedChannel(new Bzip2Decoder());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThrows(DecompressionException.class, () -> {
                ch.writeInbound(Unpooled.wrappedBuffer(malformed));
                ch.finishAndReleaseAll();
            });
        }, "Bzip2Decoder hung: bwtBytesDecoded overshot bwtBlockLength and the " +
           "equality termination check was permanently false");
    }

    /**
     * Verifies that a well-formed bzip2 block whose inverse-BWT output ends with exactly
     * four consecutive identical bytes followed by a run-length count byte is decoded
     * correctly and terminates cleanly.
     *
     * <p>Compressing {@code "AAAA"}: bzip2's pre-BWT RLE encodes a run of exactly 4
     * identical bytes as {@code [A,A,A,A,0x00]} (the four bytes plus count-byte 0,
     * meaning zero additional copies beyond the initial four).  The post-BWT RLE
     * decoder in {@link Bzip2BlockDecompressor#read()} must reach
     * {@code rleAccumulator == 4}, read the count byte, and then terminate cleanly
     * when {@code bwtBytesDecoded} reaches {@code bwtBlockLength == 5}.
     */
    @Test
    public void testWellFormedFourByteRleRunAtBlockEnd() throws Exception {
        byte[] compressed = compress(new byte[]{'A', 'A', 'A', 'A'});
        channel.writeInbound(Unpooled.wrappedBuffer(compressed));
        ByteBuf decoded = channel.readInbound();
        assertNotNull(decoded, "expected decoded output for well-formed AAAA block");
        try {
            byte[] result = new byte[decoded.readableBytes()];
            decoded.readBytes(result);
            assertArrayEquals(new byte[]{'A', 'A', 'A', 'A'}, result);
        } finally {
            decoded.release();
        }
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BZip2CompressorOutputStream bZip2Os = new BZip2CompressorOutputStream(os, MIN_BLOCK_SIZE);
        bZip2Os.write(data);
        bZip2Os.close();

        return os.toByteArray();
    }
}
