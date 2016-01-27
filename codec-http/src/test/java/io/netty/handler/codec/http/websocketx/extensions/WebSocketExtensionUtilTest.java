/*
 * Copyright 2016 The Netty Project
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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Test;

import static org.junit.Assert.*;

public class WebSocketExtensionUtilTest {

    @Test
    public void testIsWebsocketUpgrade() {
        HttpHeaders headers = new DefaultHttpHeaders();
        assertFalse(WebSocketExtensionUtil.isWebsocketUpgrade(headers));

        headers.add(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        assertFalse(WebSocketExtensionUtil.isWebsocketUpgrade(headers));

        headers.add(HttpHeaderNames.CONNECTION, "Keep-Alive, Upgrade");
        assertTrue(WebSocketExtensionUtil.isWebsocketUpgrade(headers));
    }
}
