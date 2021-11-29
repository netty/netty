/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketExtensionFilterTest {

    @Test
    public void testNeverSkip() {
        WebSocketExtensionFilter neverSkip = WebSocketExtensionFilter.NEVER_SKIP;

        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(binaryFrame));
        assertTrue(binaryFrame.isAccessible());
        binaryFrame.close();

        TextWebSocketFrame textFrame = new TextWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(textFrame));
        assertTrue(textFrame.isAccessible());
        textFrame.close();

        PingWebSocketFrame pingFrame = new PingWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(pingFrame));
        assertTrue(pingFrame.isAccessible());
        pingFrame.close();

        PongWebSocketFrame pongFrame = new PongWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(pongFrame));
        assertTrue(pongFrame.isAccessible());
        pongFrame.close();

        CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(closeFrame));
        assertTrue(closeFrame.isAccessible());
        closeFrame.close();

        ContinuationWebSocketFrame continuationFrame = new ContinuationWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertFalse(neverSkip.mustSkip(continuationFrame));
        assertTrue(continuationFrame.isAccessible());
        continuationFrame.close();
    }

    @Test
    public void testAlwaysSkip() {
        WebSocketExtensionFilter neverSkip = WebSocketExtensionFilter.ALWAYS_SKIP;

        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(binaryFrame));
        assertTrue(binaryFrame.isAccessible());
        binaryFrame.close();

        TextWebSocketFrame textFrame = new TextWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(textFrame));
        assertTrue(textFrame.isAccessible());
        textFrame.close();

        PingWebSocketFrame pingFrame = new PingWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(pingFrame));
        assertTrue(pingFrame.isAccessible());
        pingFrame.close();

        PongWebSocketFrame pongFrame = new PongWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(pongFrame));
        assertTrue(pongFrame.isAccessible());
        pongFrame.close();

        CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(closeFrame));
        assertTrue(closeFrame.isAccessible());
        closeFrame.close();

        ContinuationWebSocketFrame continuationFrame = new ContinuationWebSocketFrame(DEFAULT_GLOBAL_BUFFER_ALLOCATOR);
        assertTrue(neverSkip.mustSkip(continuationFrame));
        assertTrue(continuationFrame.isAccessible());
        continuationFrame.close();
    }
}
