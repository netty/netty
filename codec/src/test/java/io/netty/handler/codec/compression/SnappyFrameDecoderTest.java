/*
 * Copyright 2012 The Netty Project
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnappyFrameDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    public void initChannel() {
        channel = new EmbeddedChannel(new SnappyFrameDecoder());
    }

    @AfterEach
    public void tearDown() {
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testReservedUnskippableChunkTypeCausesError() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x03, 0x01, 0x00, 0x00, 0x00
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testInvalidStreamIdentifierLength() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testInvalidStreamIdentifierValue() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            (byte) 0xff, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testReservedSkippableBeforeStreamIdentifier() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x7f, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testUncompressedDataBeforeStreamIdentifier() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testCompressedDataBeforeStreamIdentifier() {
        final ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x00, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(in);
            }
        });
    }

    @Test
    public void testReservedSkippableSkipsInput() {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           -0x7f, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertFalse(channel.writeInbound(in));
        assertNull(channel.readInbound());

        assertFalse(in.isReadable());
    }

    @Test
    public void testUncompressedDataAppendsToOut() {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
            0x01, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertTrue(channel.writeInbound(in));

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] { 'n', 'e', 't', 't', 'y' });
        ByteBuf actual = channel.readInbound();
        assertEquals(expected, actual);

        expected.release();
        actual.release();
    }

    @Test
    public void testCompressedDataDecodesAndAppendsToOut() {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
            0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                  0x05, // preamble length
                  0x04 << 2, // literal tag + length
                  0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        });

        assertTrue(channel.writeInbound(in));

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] { 'n', 'e', 't', 't', 'y' });
        ByteBuf actual = channel.readInbound();

        assertEquals(expected, actual);

        expected.release();
        actual.release();
    }

    // The following two tests differ in only the checksum provided for the literal
    // uncompressed string "netty"

    @Test
    public void testInvalidChecksumThrowsException() {
        final EmbeddedChannel channel = new EmbeddedChannel(new SnappyFrameDecoder(true));
        try {
            // checksum here is presented as 0
            final ByteBuf in = Unpooled.wrappedBuffer(new byte[]{
                    (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                    0x01, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
            });

            assertThrows(DecompressionException.class, new Executable() {
                @Override
                public void execute() {
                    channel.writeInbound(in);
                }
            });
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testInvalidChecksumDoesNotThrowException() {
        EmbeddedChannel channel = new EmbeddedChannel(new SnappyFrameDecoder(true));
        try {
            // checksum here is presented as a282986f (little endian)
            ByteBuf in = Unpooled.wrappedBuffer(new byte[]{
                    (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                    0x01, 0x09, 0x00, 0x00, 0x6f, -0x68, 0x2e, -0x47, 'n', 'e', 't', 't', 'y'
            });

            assertTrue(channel.writeInbound(in));
            ByteBuf expected = Unpooled.wrappedBuffer(new byte[] { 'n', 'e', 't', 't', 'y' });
            ByteBuf actual = channel.readInbound();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
