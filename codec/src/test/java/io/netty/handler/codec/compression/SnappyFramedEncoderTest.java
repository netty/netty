/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.Assert.*;

public class SnappyFramedEncoderTest {
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new SnappyFramedEncoder());
    }

    @Test
    public void testSmallAmountOfDataIsUncompressed() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y'
        });

        channel.writeOutbound(in);
        assertTrue(channel.finish());

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x01, 0x09, 0x00, 0x00, 0x6f, -0x68, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y'
        });

        assertEquals(releaseLater(expected), releaseLater(channel.readOutbound()));
    }

    @Test
    public void testLargeAmountOfDataIsCompressed() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y', 'n', 'e', 't', 't', 'y',
            'n', 'e', 't', 't', 'y', 'n', 'e', 't', 't', 'y'
        });

        channel.writeOutbound(in);
        assertTrue(channel.finish());

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x00, 0x0E, 0x00, 0x00, 0x3b, 0x36, -0x7f, 0x37,
                   0x14, 0x10,
                   'n', 'e', 't', 't', 'y',
                   0x3a, 0x05, 0x00
        });

        assertEquals(releaseLater(expected), releaseLater(channel.readOutbound()));
    }

    @Test
    public void testStreamStartIsOnlyWrittenOnce() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y'
        });

        channel.writeOutbound(in.copy());
        in.readerIndex(0); // rewind the buffer to write the same data
        channel.writeOutbound(in.copy());
        assertTrue(channel.finish());

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x01, 0x09, 0x00, 0x00, 0x6f, -0x68, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y',
             0x01, 0x09, 0x00, 0x00, 0x6f, -0x68, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y',
        });

        CompositeByteBuf actual = Unpooled.compositeBuffer();
        for (;;) {
            ByteBuf m = (ByteBuf) channel.readOutbound();
            if (m == null) {
                break;
            }
            actual.addComponent(true, m);
        }
        assertEquals(releaseLater(expected), releaseLater(actual));
        in.release();
    }

    /**
     * This test asserts that if we have a remainder after emitting a copy that
     * is less than 4 bytes (ie. the minimum required for a copy), we should
     * emit a literal rather than trying to see if we can emit another copy.
     */
    @Test
    public void testInputBufferOverseek() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
             11,    0, // literal
              0,    0,    0,    0, // 1st copy
             16,   65,   96,  119, -22,   79,  -43,   76,  -75,  -93,
             11,  104,   96,  -99, 126,  -98,   27,  -36,   40,  117,
            -65,   -3,  -57,  -83, -58,    7,  114,  -14,   68, -122,
             124,  88,  118,   54,  45,  -26,  117,   13,  -45,   -9,
             60,  -73,  -53,  -44,  53,   68,  -77,  -71,  109,   43,
             -38,  59,  100,  -12, -87,   44, -106,  123, -107,   38,
             13, -117,  -23,  -49,  29,   21,   26,   66,    1,   -1,
             -1, // literal
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1, // 2nd copy
             66,    0, -104,  -49,  16, -120,   22,    8,  -52,  -54,
           -102,  -52, -119, -124, -92,  -71,  101, -120,  -52,  -48,
             45,  -26,  -24,   26,  41,  -13,   36,   64,  -47,   15,
           -124,   -7,  -16,   91,  96,    0,  -93,  -42,  101,   20,
            -74,   39, -124,   35,  43,  -49,  -21,  -92,  -20,  -41,
             79,   41,  110, -105,  42,  -96,   90,   -9, -100,  -22,
            -62,   91,    2,   35, 113,  117,  -71,   66,    1, // literal
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1,   -1,   -1,  -1,   -1,   -1,   -1,   -1,
             -1,   -1,   -1, // copy
             -1,   1 // remainder
        });

        channel.writeOutbound(in);
        assertTrue(channel.finish());
        ByteBuf out = (ByteBuf) channel.readOutbound();
        out.release();
    }
}
