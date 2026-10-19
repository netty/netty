/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeadersFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpdyHttpDecoderTest {

    /**
     * A non-final SYN_STREAM buffers a request while its body/trailers arrive later. Prior to the
     * fix, an attacker could keep the stream open and send an unbounded number of non-final HEADERS
     * frames, each appended verbatim to the buffered message with no cumulative cap, exhausting the
     * heap. The decoder must bound the total accumulated header size, mirroring the cap HTTP/2 places
     * on its decoded header list (see HpackDecoder/Http2CodecUtil#headerListSizeExceeded).
     */
    @Test
    public void testUnboundedHeaderAccumulationIsRejected() {
        int maxHeadersSize = 8192;
        int maxContentLength = 8192;
        Map<Integer, FullHttpMessage> messageMap = new HashMap<Integer, FullHttpMessage>();
        EmbeddedChannel ch = new EmbeddedChannel(
            new SpdyHttpDecoder(SpdyVersion.SPDY_3_1, maxHeadersSize, maxContentLength, messageMap,
                DefaultHttpHeadersFactory.headersFactory(), DefaultHttpHeadersFactory.trailersFactory()));

        SpdySynStreamFrame synStream = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        synStream.setLast(false);
        synStream.headers().set(":method", "GET");
        synStream.headers().set(":path", "/");
        synStream.headers().set(":version", "HTTP/1.1");
        synStream.headers().set(":scheme", "https");
        synStream.headers().set(":host", "netty.io");
        assertFalse(ch.writeInbound(synStream));

        assertTrue(messageMap.containsKey(1));

        try {
            assertThrows(TooLongFrameException.class, () -> {
                for (int frame = 0; frame < 1000; frame++) {
                    SpdyHeadersFrame headers = new DefaultSpdyHeadersFrame(1);
                    headers.setLast(false);
                    for (int i = 0; i < 100; i++) {
                        headers.headers().add("x-pad-" + frame + '-' + i, "v");
                    }
                    ch.writeInbound(headers);
                }
            });

            // The buffered message must be removed and released once the cumulative header
            // size guard trips, instead of being retained for the lifetime of the connection.
            assertFalse(messageMap.containsKey(1));
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testHeadersWithinLimitAreAccepted() {
        int maxHeadersSize = 8192;
        int maxContentLength = 8192;
        EmbeddedChannel ch = new EmbeddedChannel(
                new SpdyHttpDecoder(SpdyVersion.SPDY_3_1, maxHeadersSize, maxContentLength, new HashMap<>(),
                    DefaultHttpHeadersFactory.headersFactory(), DefaultHttpHeadersFactory.trailersFactory()));

        SpdySynStreamFrame synStream = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        synStream.setLast(false);
        synStream.headers().set(":method", "GET");
        synStream.headers().set(":path", "/");
        synStream.headers().set(":version", "HTTP/1.1");
        synStream.headers().set(":scheme", "https");
        synStream.headers().set(":host", "netty.io");
        assertFalse(ch.writeInbound(synStream));

        SpdyHeadersFrame headers = new DefaultSpdyHeadersFrame(1);
        headers.setLast(true);
        headers.headers().add("x-small", "value");
        assertTrue(ch.writeInbound(headers));

        Object decoded = ch.readInbound();
        assertTrue(decoded instanceof FullHttpMessage);
        ((FullHttpMessage) decoded).release();

        ch.finishAndReleaseAll();
    }
}
