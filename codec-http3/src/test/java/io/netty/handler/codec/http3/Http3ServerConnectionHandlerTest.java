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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Http3ServerConnectionHandlerTest extends AbtractHttp3ConnectionHandlerTest {
    private static final ChannelHandler REQUEST_HANDLER = new ChannelInboundHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    public Http3ServerConnectionHandlerTest() {
        super(true);
    }

    @Override
    protected Http3ConnectionHandler newConnectionHandler() {
        return new Http3ServerConnectionHandler(REQUEST_HANDLER);
    }

    @Override
    protected void assertBidirectionalStreamHandled(EmbeddedQuicChannel channel, QuicStreamChannel streamChannel) {
        assertNotNull(streamChannel.pipeline().context(REQUEST_HANDLER));
    }

    @Test
    public void customSensitivityDetectorIsForwardedToQpackEncoder() {
        Http3ServerConnectionHandler handler = new Http3ServerConnectionHandler(
                REQUEST_HANDLER, null, null, null,
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
