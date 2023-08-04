/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebSocketClientHandshaker07Test extends WebSocketClientHandshakerTest {

    @Test
    public void testHostHeaderPreserved() {
        URI uri = URI.create("ws://localhost:9999");
        WebSocketClientHandshaker handshaker = newHandshaker(uri, null,
                new DefaultHttpHeaders().set(HttpHeaderNames.HOST, "test.netty.io"), false, true);

        FullHttpRequest request = handshaker.newHandshakeRequest();
        try {
            assertEquals("/", request.uri());
            assertEquals("test.netty.io", request.headers().get(HttpHeaderNames.HOST));
        } finally {
            request.release();
        }
    }

    @Override
    protected WebSocketClientHandshaker newHandshaker(URI uri, String subprotocol, HttpHeaders headers,
                                                      boolean absoluteUpgradeUrl, boolean generateOriginHeader) {
        return new WebSocketClientHandshaker07(uri, WebSocketVersion.V07, subprotocol, false, headers,
          1024, true, false, 10000,
          absoluteUpgradeUrl, generateOriginHeader);
    }

    @Override
    protected CharSequence getOriginHeaderName() {
        return HttpHeaderNames.SEC_WEBSOCKET_ORIGIN;
    }

    @Override
    protected CharSequence getProtocolHeaderName() {
        return HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
    }

    @Override
    protected CharSequence[] getHandshakeRequiredHeaderNames() {
        return new CharSequence[] {
                HttpHeaderNames.UPGRADE,
                HttpHeaderNames.CONNECTION,
                HttpHeaderNames.SEC_WEBSOCKET_KEY,
                HttpHeaderNames.HOST,
                HttpHeaderNames.SEC_WEBSOCKET_VERSION,
        };
    }
}
