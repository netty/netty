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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import org.junit.Before;
import org.junit.Test;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static io.netty.handler.codec.http2.Http2TestUtil.randomBytes;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for {@link DefaultHttp2HeadersDecoder}.
 */
public class DefaultHttp2HeadersDecoderTest {

    private DefaultHttp2HeadersDecoder decoder;

    @Before
    public void setup() {
        decoder = new DefaultHttp2HeadersDecoder(false);
    }

    @Test
    public void decodeShouldSucceed() throws Exception {
        ByteBuf buf = encode(b(":method"), b("GET"), b("akey"), b("avalue"), randomBytes(), randomBytes());
        try {
            Http2Headers headers = decoder.decodeHeaders(0, buf);
            assertEquals(3, headers.size());
            assertEquals("GET", headers.method().toString());
            assertEquals("avalue", headers.get(new AsciiString("akey")).toString());
        } finally {
            buf.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testExceedHeaderSize() throws Exception {
        final int maxListSize = 100;
        decoder.configuration().maxHeaderListSize(maxListSize, maxListSize);
        ByteBuf buf = encode(randomBytes(maxListSize), randomBytes(1));
        try {
            decoder.decodeHeaders(0, buf);
            fail();
        } finally {
            buf.release();
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MAX_HEADER_LIST_SIZE);
        ByteBuf buf = encode(b(":method"), b("GET"));
        final int streamId = 1;
        try {
            decoder.decodeHeaders(streamId, buf);
            fail();
        } catch (Http2Exception.HeaderListSizeException e) {
            assertEquals(streamId, e.streamId());
        } finally {
            buf.release();
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAwayWithInitialDecoderSettings() throws Exception {
        ByteBuf buf = encode(b(":method"), b("GET"), b("test_header"),
            b(String.format("%09000d", 0).replace('0', 'A')));
        final int streamId = 1;
        try {
            decoder.decodeHeaders(streamId, buf);
            fail();
        } catch (Http2Exception.HeaderListSizeException e) {
            assertEquals(streamId, e.streamId());
        } finally {
            buf.release();
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MIN_HEADER_LIST_SIZE);
        ByteBuf buf = encode(b(":method"), b("GET"));
        final int streamId = 1;
        try {
            decoder.decodeHeaders(streamId, buf);
            fail();
        } catch (Http2Exception e) {
            assertEquals(Http2Error.PROTOCOL_ERROR, e.error());
        } finally {
            buf.release();
        }
    }

    private static byte[] b(String string) {
        return string.getBytes(UTF_8);
    }

    private static ByteBuf encode(byte[]... entries) throws Exception {
        HpackEncoder hpackEncoder = newTestEncoder();
        ByteBuf out = Unpooled.buffer();
        Http2Headers http2Headers = new DefaultHttp2Headers(false);
        for (int ix = 0; ix < entries.length;) {
            http2Headers.add(new AsciiString(entries[ix++], false), new AsciiString(entries[ix++], false));
        }
        hpackEncoder.encodeHeaders(3 /* randomly chosen */, out, http2Headers, NEVER_SENSITIVE);
        return out;
    }
}
