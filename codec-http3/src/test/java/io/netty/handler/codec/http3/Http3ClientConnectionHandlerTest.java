/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Http3ClientConnectionHandlerTest extends AbtractHttp3ConnectionHandlerTest {

    public Http3ClientConnectionHandlerTest() {
        super(false);
    }

    @Override
    protected Http3ConnectionHandler newConnectionHandler() {
        return new Http3ClientConnectionHandler();
    }

    @Override
    protected void assertBidirectionalStreamHandled(EmbeddedQuicChannel channel, QuicStreamChannel streamChannel) {
        Http3TestUtils.verifyClose(Http3ErrorCode.H3_STREAM_CREATION_ERROR, channel);
    }

    @Test
    public void customSensitivityDetectorIsForwardedToQpackEncoder() {
        Http3ClientConnectionHandler handler = new Http3ClientConnectionHandler(
                null, null, null, null,
                true, null, QpackSensitivityDetector.ALWAYS_SENSITIVE,
                Http3CodecUtils.DEFAULT_MAX_UNKNOWN_FRAME_PAYLOAD_LENGTH);

        Http3Headers headers = new DefaultHttp3Headers(false);
        headers.add("x-custom", "value");
        ByteBuf out = Unpooled.buffer();
        try {
            handler.qpackEncoder.encodeHeaders(new QpackAttributes(null, true), out,
                    UnpooledByteBufAllocator.DEFAULT, 1L, headers);
            byte first = out.getByte(2);
            assertEquals(1, (first & 0b0001_0000) >>> 4,
                    "N bit must be set when ALWAYS_SENSITIVE is configured");
        } finally {
            out.release();
        }
    }
}
