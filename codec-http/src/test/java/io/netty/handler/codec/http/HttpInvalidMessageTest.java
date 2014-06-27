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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class HttpInvalidMessageTest {

    private final Random rnd = new Random();

    @Test
    public void testRequestWithBadInitialLine() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("GET / HTTP/1.0 with extra\r\n", CharsetUtil.UTF_8));
        HttpRequest req = (HttpRequest) ch.readInbound();
        DecoderResult dr = req.getDecoderResult();
        assertFalse(dr.isSuccess());
        assertTrue(dr.isFailure());
        ensureInboundTrafficDiscarded(ch);
    }

    @Test
    public void testRequestWithBadHeader() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("GET /maybe-something HTTP/1.0\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("Good_Name: Good Value\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("Bad=Name: Bad Value\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.UTF_8));
        HttpRequest req = (HttpRequest) ch.readInbound();
        DecoderResult dr = req.getDecoderResult();
        assertFalse(dr.isSuccess());
        assertTrue(dr.isFailure());
        assertEquals("Good Value", req.headers().get("Good_Name"));
        assertEquals("/maybe-something", req.getUri());
        ensureInboundTrafficDiscarded(ch);
    }

    @Test
    public void testResponseWithBadInitialLine() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.0 BAD_CODE Bad Server\r\n", CharsetUtil.UTF_8));
        HttpResponse res = (HttpResponse) ch.readInbound();
        DecoderResult dr = res.getDecoderResult();
        assertFalse(dr.isSuccess());
        assertTrue(dr.isFailure());
        ensureInboundTrafficDiscarded(ch);
    }

    @Test
    public void testResponseWithBadHeader() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.0 200 Maybe OK\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("Good_Name: Good Value\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("Bad=Name: Bad Value\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.UTF_8));
        HttpResponse res = (HttpResponse) ch.readInbound();
        DecoderResult dr = res.getDecoderResult();
        assertFalse(dr.isSuccess());
        assertTrue(dr.isFailure());
        assertEquals("Maybe OK", res.getStatus().reasonPhrase());
        assertEquals("Good Value", res.headers().get("Good_Name"));
        ensureInboundTrafficDiscarded(ch);
    }

    @Test
    public void testBadChunk() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("GET / HTTP/1.0\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("Transfer-Encoding: chunked\r\n\r\n", CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer("BAD_LENGTH\r\n", CharsetUtil.UTF_8));

        HttpRequest req = (HttpRequest) ch.readInbound();
        assertTrue(req.getDecoderResult().isSuccess());

        LastHttpContent chunk = (LastHttpContent) ch.readInbound();
        DecoderResult dr = chunk.getDecoderResult();
        assertFalse(dr.isSuccess());
        assertTrue(dr.isFailure());
        ensureInboundTrafficDiscarded(ch);
    }

    private void ensureInboundTrafficDiscarded(EmbeddedChannel ch) {
        // Generate a lot of random traffic to ensure that it's discarded silently.
        byte[] data = new byte[1048576];
        rnd.nextBytes(data);

        ByteBuf buf = Unpooled.wrappedBuffer(data);
        for (int i = 0; i < 4096; i ++) {
            buf.setIndex(0, data.length);
            ch.writeInbound(buf.retain());
            ch.checkException();
            assertNull(ch.readInbound());
        }
        buf.release();
    }
}
