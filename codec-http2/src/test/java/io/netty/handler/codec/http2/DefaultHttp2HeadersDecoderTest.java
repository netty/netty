/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertEquals;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.Test;

import com.twitter.hpack.Encoder;

/**
 * Tests for {@link DefaultHttp2HeadersDecoder}.
 */
public class DefaultHttp2HeadersDecoderTest {

    private DefaultHttp2HeadersDecoder decoder;

    @Before
    public void setup() {
        decoder = new DefaultHttp2HeadersDecoder();
    }

    @Test
    public void decodeShouldSucceed() throws Exception {
        final ByteBuf buf = encode(":method", "GET", "akey", "avalue");
        try {
            Http2Headers headers = decoder.decodeHeaders(buf).build();
            assertEquals(2, headers.size());
            assertEquals("GET", headers.method());
            assertEquals("avalue", headers.get("akey"));
        } finally {
            buf.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void decodeWithInvalidPseudoHeaderShouldFail() throws Exception {
        final ByteBuf buf = encode(":invalid", "GET", "akey", "avalue");
        try {
            decoder.decodeHeaders(buf);
        } finally {
            buf.release();
        }
    }

    private ByteBuf encode(String... entries) throws Exception {
        final Encoder encoder = new Encoder();
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (int ix = 0; ix < entries.length;) {
            String key = entries[ix++];
            String value = entries[ix++];
            encoder.encodeHeader(stream, key.getBytes(UTF_8), value.getBytes(UTF_8), false);
        }
        return Unpooled.wrappedBuffer(stream.toByteArray());
    }
}
