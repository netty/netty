/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class WebSocketClientHandshaker07Test extends WebSocketClientHandshakerTest {
    @Override
    protected WebSocketClientHandshaker newHandshaker(URI uri) {
        return new WebSocketClientHandshaker07(uri, WebSocketVersion.V07, null, false, null, 1024);
    }

    @Test
    public void testSecOriginWss() {
        URI uri = URI.create("wss://localhost/path%20with%20ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        FullHttpRequest request = handshaker.newHandshakeRequest();
        try {
            assertEquals("https://localhost", request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN));
        } finally {
            request.release();
        }
    }

    @Test
    public void testSecOriginWs() {
        URI uri = URI.create("ws://localhost/path%20with%20ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        FullHttpRequest request = handshaker.newHandshakeRequest();
        try {
            assertEquals("http://localhost", request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN));
        } finally {
            request.release();
        }
    }
}
