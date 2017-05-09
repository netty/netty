/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import static io.netty.handler.codec.http.websocketx.extensions.compression.
        PerMessageDeflateServerExtensionHandshaker.*;
import static org.junit.Assert.*;

import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

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
        assertEquals(WebSocketClientExtension.RSV1, extension.rsv());
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
        parameters.put(SERVER_MAX_WINDOW, "10");
        parameters.put(CLIENT_NO_CONTEXT, null);
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketClientExtension.RSV1, extension.rsv());
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
        assertEquals(WebSocketClientExtension.RSV1, extension.rsv());
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
}
