/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2.internal.hpack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.Before;
import org.junit.Test;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class EncoderTest {
    private Decoder decoder;
    private Encoder encoder;
    private Http2Headers mockHeaders;

    @Before
    public void setUp() throws Http2Exception {
        encoder = new Encoder();
        decoder = new Decoder(DEFAULT_HEADER_LIST_SIZE, 32);
        mockHeaders = mock(Http2Headers.class);
    }

    @Test
    public void testSetMaxHeaderTableSizeToMaxValue() throws Http2Exception {
        ByteBuf buf = Unpooled.buffer();
        encoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE);
        decoder.setMaxHeaderTableSize(MAX_HEADER_TABLE_SIZE);
        decoder.decode(0, buf, mockHeaders);
        assertEquals(MAX_HEADER_TABLE_SIZE, decoder.getMaxHeaderTableSize());
        buf.release();
    }

    @Test(expected = Http2Exception.class)
    public void testSetMaxHeaderTableSizeOverflow() throws Http2Exception {
        ByteBuf buf = Unpooled.buffer();
        try {
            encoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE + 1);
        } finally {
            buf.release();
        }
    }
}
