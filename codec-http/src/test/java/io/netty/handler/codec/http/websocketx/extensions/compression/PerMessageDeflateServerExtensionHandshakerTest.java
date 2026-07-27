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

import static io.netty.handler.codec.http.websocketx.extensions.compression.
        PerMessageDeflateServerExtensionHandshaker.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class PerMessageDeflateServerExtensionHandshakerTest {

    @Test
    public void testNormalHandshake() {
        WebSocketServerExtension extension;
        WebSocketExtensionData data;
        Map<String, String> parameters;

        // initialize
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(0);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, Collections.<String, String>emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newReponseData();

        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());

        // initialize
        parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, null);
        parameters.put(CLIENT_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, Collections.<String, String>emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newReponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());

        // initialize
        parameters = new HashMap<String, String>();
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNull(extension);
    }

    @Test
    public void testCustomHandshake() {
        WebSocketServerExtension extension;
        Map<String, String> parameters;
        WebSocketExtensionData data;

        // initialize
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, 10, true, true, 0);

        parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, null);
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(CLIENT_NO_CONTEXT, null);
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newReponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertEquals("10", data.parameters().get(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("12", data.parameters().get(SERVER_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));

        // initialize
        parameters = new HashMap<String, String>();
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newReponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertEquals(2, data.parameters().size());
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("12", data.parameters().get(SERVER_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_NO_CONTEXT));

        // initialize
        parameters = new HashMap<String, String>();

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));
        // test
        assertNotNull(extension);

        // execute
        data = extension.newReponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());
    }

    @Test
    public void testClientMaxWindowWithValue() {
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, 10, true, true, 0);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, "12");

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());

        WebSocketExtensionData data = extension.newReponseData();
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        // Server should use the client's requested value (12) not the preferred (10)
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertEquals("12", data.parameters().get(CLIENT_MAX_WINDOW));
    }

    @Test
    public void testClientMaxWindowWithInvalidValue() {
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, 10, true, true, 0);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(CLIENT_MAX_WINDOW, "7"); // Below MIN_WINDOW_SIZE (8)

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // Handshake should fail when client_max_window_bits is out of range
        assertNull(extension);
    }

    @Test
    public void testServerWindowSizeAdvertisedWhenLowerThanMax() {
        // Server prefers a 10-bit window; client didn't request server_max_window_bits.
        // RFC 7692 §7.1.2: server MAY include server_max_window_bits unilaterally.
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, false, MAX_WINDOW_SIZE,
                        false, false, 10, 8, 0);

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION,
                        Collections.<String, String>emptyMap()));

        assertNotNull(extension);
        WebSocketExtensionData data = extension.newReponseData();
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("10", data.parameters().get(SERVER_MAX_WINDOW));
    }

    @Test
    public void testServerWindowSizeCapsClientOffer() {
        // Server limit is 10. Client offers server_max_window_bits=12.
        // RFC 7692: server accepts with same or smaller value → 10.
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, 10, 8, 0);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(SERVER_MAX_WINDOW, "12");

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        assertNotNull(extension);
        WebSocketExtensionData data = extension.newReponseData();
        assertEquals("10", data.parameters().get(SERVER_MAX_WINDOW));
    }

    @Test
    public void testServerWindowSizeHonorsSmallerClientOffer() {
        // Server limit is 12. Client offers server_max_window_bits=9. Server uses 9.
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, 12, 8, 0);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(SERVER_MAX_WINDOW, "9");

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        assertNotNull(extension);
        WebSocketExtensionData data = extension.newReponseData();
        assertEquals("9", data.parameters().get(SERVER_MAX_WINDOW));
    }

    @Test
    public void testServerWindowSizeOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, 16, 8, 0);
            }
        });
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, 7, 8, 0);
            }
        });
    }

    @Test
    public void testMemLevelOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, MAX_WINDOW_SIZE, 10, 0);
            }
        });
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new PerMessageDeflateServerExtensionHandshaker(6, true, MAX_WINDOW_SIZE,
                        false, false, MAX_WINDOW_SIZE, 0, 0);
            }
        });
    }
}
