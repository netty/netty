/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;

import static org.junit.Assert.*;

public class WebSocketExtensionFilterTest {

    @Test
    public void testNeverSkip() {
        WebSocketExtensionFilter neverSkip = WebSocketExtensionFilter.NEVER_SKIP;

        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame();
        assertFalse(neverSkip.mustSkip(binaryFrame));
        assertTrue(binaryFrame.release());

        TextWebSocketFrame textFrame = new TextWebSocketFrame();
        assertFalse(neverSkip.mustSkip(textFrame));
        assertTrue(textFrame.release());

        PingWebSocketFrame pingFrame = new PingWebSocketFrame();
        assertFalse(neverSkip.mustSkip(pingFrame));
        assertTrue(pingFrame.release());

        PongWebSocketFrame pongFrame = new PongWebSocketFrame();
        assertFalse(neverSkip.mustSkip(pongFrame));
        assertTrue(pongFrame.release());

        CloseWebSocketFrame closeFrame = new CloseWebSocketFrame();
        assertFalse(neverSkip.mustSkip(closeFrame));
        assertTrue(closeFrame.release());

        ContinuationWebSocketFrame continuationFrame = new ContinuationWebSocketFrame();
        assertFalse(neverSkip.mustSkip(continuationFrame));
        assertTrue(continuationFrame.release());
    }

    @Test
    public void testAlwaysSkip() {
        WebSocketExtensionFilter neverSkip = WebSocketExtensionFilter.ALWAYS_SKIP;

        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame();
        assertTrue(neverSkip.mustSkip(binaryFrame));
        assertTrue(binaryFrame.release());

        TextWebSocketFrame textFrame = new TextWebSocketFrame();
        assertTrue(neverSkip.mustSkip(textFrame));
        assertTrue(textFrame.release());

        PingWebSocketFrame pingFrame = new PingWebSocketFrame();
        assertTrue(neverSkip.mustSkip(pingFrame));
        assertTrue(pingFrame.release());

        PongWebSocketFrame pongFrame = new PongWebSocketFrame();
        assertTrue(neverSkip.mustSkip(pongFrame));
        assertTrue(pongFrame.release());

        CloseWebSocketFrame closeFrame = new CloseWebSocketFrame();
        assertTrue(neverSkip.mustSkip(closeFrame));
        assertTrue(closeFrame.release());

        ContinuationWebSocketFrame continuationFrame = new ContinuationWebSocketFrame();
        assertTrue(neverSkip.mustSkip(continuationFrame));
        assertTrue(continuationFrame.release());
    }
}
