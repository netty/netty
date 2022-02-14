/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static io.netty.handler.codec.http2.Http2TestUtil.randomBytes;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link DefaultHttp2HeadersDecoder}.
 */
public class DefaultHttp2HeadersDecoderTest {

    private DefaultHttp2HeadersDecoder decoder;

    @BeforeEach
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

    @Test
    public void testExceedHeaderSize() throws Exception {
        final int maxListSize = 100;
        decoder.configuration().maxHeaderListSize(maxListSize, maxListSize);
        final ByteBuf buf = encode(randomBytes(maxListSize), randomBytes(1));

        try {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decoder.decodeHeaders(0, buf);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MAX_HEADER_LIST_SIZE);
        final ByteBuf buf = encode(b(":method"), b("GET"));
        final int streamId = 1;
        Http2Exception.HeaderListSizeException e =
                assertThrows(Http2Exception.HeaderListSizeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decoder.decodeHeaders(streamId, buf);
            }
        });
        assertEquals(streamId, e.streamId());
        buf.release();
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAwayWithInitialDecoderSettings() throws Exception {
        final ByteBuf buf = encode(b(":method"), b("GET"), b("test_header"),
            b(String.format("%09000d", 0).replace('0', 'A')));
        final int streamId = 1;
        try {
            Http2Exception.HeaderListSizeException e = assertThrows(Http2Exception.HeaderListSizeException.class,
                    new Executable() {
                        @Override
                        public void execute() throws Throwable {
                            decoder.decodeHeaders(streamId, buf);
                        }
                    });
            assertEquals(streamId, e.streamId());
        } finally {
            buf.release();
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MIN_HEADER_LIST_SIZE);
        final ByteBuf buf = encode(b(":method"), b("GET"));
        final int streamId = 1;
        try {
            Http2Exception e = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decoder.decodeHeaders(streamId, buf);
                }
            });
            assertEquals(Http2Error.PROTOCOL_ERROR, e.error());
        } finally {
            buf.release();
        }
    }

    @Test
    public void duplicatePseudoHeadersMustFailValidation() throws Exception {
        final ByteBuf buf = encode(b(":authority"), b("abc"), b(":authority"), b("def"));
        try {
            final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
            Http2Exception e = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decoder.decodeHeaders(1, buf);
                }
            });
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
