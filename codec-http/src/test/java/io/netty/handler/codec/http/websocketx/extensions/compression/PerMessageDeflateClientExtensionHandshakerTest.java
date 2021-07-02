/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension.RSV1;
import static io.netty.handler.codec.http.websocketx.extensions.compression.
        PerMessageDeflateServerExtensionHandshaker.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PerMessageDeflateClientExtensionHandshakerTest {

    @Test
    public void testNormalData() {
        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker();

        WebSocketExtensionData data = handshaker.newRequestData();

        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertEquals(ZlibCodecFactory.isSupportingWindowSizeAndMemLevel() ? 1 : 0, data.parameters().size());
    }

    @Test
    public void testCustomData() {
        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker(6, true, 10, true, true);

        WebSocketExtensionData data = handshaker.newRequestData();

        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("10", data.parameters().get(SERVER_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
    }

    @Test
    public void testNormalHandshake() {
        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker();

        WebSocketClientExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, Collections.<String, String>emptyMap()));

        assertNotNull(extension);
        assertEquals(RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);
    }

    @Test
    public void testCustomHandshake() {
        WebSocketClientExtension extension;
        Map<String, String> parameters;

        // initialize
        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker(6, true, 10, true, true);

        parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, "12");
        parameters.put(SERVER_MAX_WINDOW, "8");
        parameters.put(CLIENT_NO_CONTEXT, null);
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // initialize
        parameters = new HashMap<String, String>();
        parameters.put(SERVER_MAX_WINDOW, "10");
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // initialize
        parameters = new HashMap<String, String>();

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNull(extension);
    }

    @Test
    public void testParameterValidation() {
        WebSocketClientExtension extension;
        Map<String, String> parameters;

        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker(6, true, 15, true, false);

        parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, "15");
        parameters.put(SERVER_MAX_WINDOW, "8");
        extension = handshaker.handshakeExtension(new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // Test that handshake succeeds when parameters are valid
        assertNotNull(extension);
        assertEquals(RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, "15");
        parameters.put(SERVER_MAX_WINDOW, "7");

        extension = handshaker.handshakeExtension(new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // Test that handshake fails when parameters are invalid
        assertNull(extension);
    }

    @Test
    public void testServerNoContextTakeover() {
        WebSocketClientExtension extension;
        Map<String, String> parameters;

        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker(6, true, 15, true, false);

        parameters = new HashMap<String, String>();
        parameters.put(SERVER_NO_CONTEXT, null);
        extension = handshaker.handshakeExtension(new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // Test that handshake succeeds when server responds with `server_no_context_takeover` that we didn't offer
        assertNotNull(extension);
        assertEquals(RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // initialize
        handshaker = new PerMessageDeflateClientExtensionHandshaker(6, true, 15, true, true);

        parameters = new HashMap<String, String>();
        extension = handshaker.handshakeExtension(new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // Test that handshake fails when client offers `server_no_context_takeover` but server doesn't support it
        assertNull(extension);
    }

    @Test
    public void testDecoderNoClientContext() {
        PerMessageDeflateClientExtensionHandshaker handshaker =
                new PerMessageDeflateClientExtensionHandshaker(6, true, MAX_WINDOW_SIZE, true, false);

        byte[] firstPayload = new byte[] {
                76, -50, -53, 10, -62, 48, 20, 4, -48, 95, 41, 89, -37, 36, 77, 90, 31, -39, 41, -72, 112, 33, -120, 20,
                20, 119, -79, 70, 123, -95, 121, -48, 92, -116, 80, -6, -17, -58, -99, -37, -31, 12, 51, 19, 1, -9, -12,
                68, -111, -117, 25, 58, 111, 77, -127, -66, -64, -34, 20, 59, -64, -29, -2, 90, -100, -115, 30, 16, 114,
                -68, 61, 29, 40, 89, -112, -73, 25, 35, 120, -105, -67, -32, -43, -70, -84, 120, -55, 69, 43, -124, 106,
                -92, 18, -110, 114, -50, 111, 25, -3, 10, 17, -75, 13, 127, -84, 106, 90, -66, 84, -75, 84, 53, -89,
                -75, 92, -3, -40, -61, 119, 49, -117, 30, 49, 68, -59, 88, 74, -119, -34, 1, -83, -7, -48, 124, -124,
                -23, 16, 88, -118, 121, 54, -53, 1, 44, 32, 81, 19, 25, -115, -43, -32, -64, -67, -120, -110, -101, 121,
                -2, 2
        };

        byte[] secondPayload = new byte[] {
                -86, 86, 42, 46, 77, 78, 78, 45, 6, 26, 83, 82, 84, -102, -86, 3, -28, 38, 21, 39, 23, 101, 38, -91, 2,
                -51, -51, 47, 74, 73, 45, 114, -54, -49, -49, -10, 49, -78, -118, 112, 10, 9, 13, 118, 1, -102, 84,
                -108, 90, 88, 10, 116, 27, -56, -84, 124, -112, -13, 16, 26, 116, -108, 18, -117, -46, -127, 6, 69, 99,
                -45, 24, 91, 91, 11, 0
        };

        Map<String, String> parameters =  Collections.singletonMap(CLIENT_NO_CONTEXT, null);

        WebSocketClientExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));
        assertNotNull(extension);

        EmbeddedChannel decoderChannel = new EmbeddedChannel(extension.newExtensionDecoder());
        assertTrue(
                decoderChannel.writeInbound(new TextWebSocketFrame(true, RSV1, Unpooled.copiedBuffer(firstPayload))));
        TextWebSocketFrame firstFrameDecompressed = decoderChannel.readInbound();
        assertTrue(
                decoderChannel.writeInbound(new TextWebSocketFrame(true, RSV1, Unpooled.copiedBuffer(secondPayload))));
        TextWebSocketFrame secondFrameDecompressed = decoderChannel.readInbound();

        assertNotNull(firstFrameDecompressed);
        assertNotNull(firstFrameDecompressed.content());
        assertTrue(firstFrameDecompressed instanceof TextWebSocketFrame);
        assertEquals(firstFrameDecompressed.text(),
                     "{\"info\":\"Welcome to the BitMEX Realtime API.\",\"version\"" +
                     ":\"2018-10-02T22:53:23.000Z\",\"timestamp\":\"2018-10-15T06:43:40.437Z\"," +
                     "\"docs\":\"https://www.bitmex.com/app/wsAPI\",\"limit\":{\"remaining\":39}}");
        assertTrue(firstFrameDecompressed.release());

        assertNotNull(secondFrameDecompressed);
        assertNotNull(secondFrameDecompressed.content());
        assertTrue(secondFrameDecompressed instanceof TextWebSocketFrame);
        assertEquals(secondFrameDecompressed.text(),
                     "{\"success\":true,\"subscribe\":\"orderBookL2:XBTUSD\"," +
                     "\"request\":{\"op\":\"subscribe\",\"args\":[\"orderBookL2:XBTUSD\"]}}");
        assertTrue(secondFrameDecompressed.release());

        assertFalse(decoderChannel.finish());
    }
}
