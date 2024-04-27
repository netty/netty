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
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DefaultHttp2HeadersEncoder}.
 */
public class DefaultHttp2HeadersEncoderTest {

    private DefaultHttp2HeadersEncoder encoder;

    @BeforeEach
    public void setup() {
        encoder = new DefaultHttp2HeadersEncoder(Http2HeadersEncoder.NEVER_SENSITIVE, newTestEncoder());
    }

    @AfterEach
    public void tearDown() {
        encoder.close();
    }

    @Test
    public void encodeShouldSucceed() throws Http2Exception {
        Http2Headers headers = headers();
        ByteBuf buf = Unpooled.buffer();
        try {
            encoder.encodeHeaders(3 /* randomly chosen */, headers, buf);
            assertTrue(buf.writerIndex() > 0);
        } finally {
            buf.release();
        }
    }

    @Test
    public void headersExceedMaxSetSizeShouldFail() throws Http2Exception {
        final Http2Headers headers = headers();
        encoder.maxHeaderListSize(2);
        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ByteBuf buf = Unpooled.buffer();
                try {
                    encoder.encodeHeaders(3 /* randomly chosen */, headers, buf);
                } finally {
                    buf.release();
                }
            }
        });
    }

    private static Http2Headers headers() {
        return new DefaultHttp2Headers().method(new AsciiString("GET")).add(new AsciiString("a"), new AsciiString("1"))
                .add(new AsciiString("a"), new AsciiString("2"));
    }
}
