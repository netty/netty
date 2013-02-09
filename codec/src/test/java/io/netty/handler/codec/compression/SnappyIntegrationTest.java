/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.util.CharsetUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class SnappyIntegrationTest {
    @Test
    public void testText() throws Exception {
        testIdentity(copiedBuffer(
                "Netty has been designed carefully with the experiences earned from the implementation of a lot of " +
                "protocols such as FTP, SMTP, HTTP, and various binary and text-based legacy protocols",
                CharsetUtil.US_ASCII));
    }

    @Test
    @Ignore // FIXME: Make it pass.
    public void test1002() throws Exception {
        // Data from https://github.com/netty/netty/issues/1002
        testIdentity(wrappedBuffer(new byte[] {
                  11,    0,    0,    0,    0,    0,   16,   65,   96,  119,  -22,   79,  -43,   76,  -75,  -93,
                  11,  104,   96,  -99,  126,  -98,   27,  -36,   40,  117,  -65,   -3,  -57,  -83,  -58,    7,
                 114,  -14,   68, -122,  124,   88,  118,   54,   45,  -26,  117,   13,  -45,   -9,   60,  -73,
                 -53,  -44,   53,   68,  -77,  -71,  109,   43,  -38,   59,  100,  -12,  -87,   44, -106,  123,
                -107,   38,   13, -117,  -23,  -49,   29,   21,   26,   66,    1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   66,    0, -104,  -49,
                  16, -120,   22,    8,  -52,  -54, -102,  -52, -119, -124,  -92,  -71,  101, -120,  -52,  -48,
                  45,  -26,  -24,   26,   41,  -13,   36,   64,  -47,   15, -124,   -7,  -16,   91,   96,    0,
                 -93,  -42,  101,   20,  -74,   39, -124,   35,   43,  -49,  -21,  -92,  -20,  -41,   79,   41,
                 110, -105,   42,  -96,   90,   -9, -100,  -22,  -62,   91,    2,   35,  113,  117,  -71,   66,
                   1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,   -1,
                  -1,   -1
        }));
    }

    @Test
    @Ignore // FIXME: Make it pass.
    public void testRandom() throws Exception {
        byte[] data = new byte[16 * 1048576];
        new Random().nextBytes(data);
        testIdentity(wrappedBuffer(data));
    }

    private static void testIdentity(ByteBuf in) {
        EmbeddedByteChannel encoder = new EmbeddedByteChannel(new SnappyFramedEncoder());
        EmbeddedByteChannel decoder = new EmbeddedByteChannel(new SnappyFramedDecoder());

        encoder.writeOutbound(in.copy());
        ByteBuf compressed = encoder.readOutbound();
        assertThat(compressed, is(notNullValue()));
        assertThat(compressed, is(not(in)));
        decoder.writeInbound(compressed);
        assertFalse(compressed.isReadable());
        ByteBuf decompressed = (ByteBuf) decoder.readInbound();
        assertEquals(in, decompressed);
    }
}
