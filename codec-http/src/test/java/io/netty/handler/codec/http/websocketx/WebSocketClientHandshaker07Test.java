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

import io.netty.handler.codec.http.HttpHeaderNames;

import java.net.URI;

public class WebSocketClientHandshaker07Test extends WebSocketClientHandshakerTest {
    @Override
    protected WebSocketClientHandshaker newHandshaker(URI uri) {
        return new WebSocketClientHandshaker07(uri, WebSocketVersion.V07, null, false, null, 1024);
    }

    @Override
    protected CharSequence getOriginHeaderName() {
        return HttpHeaderNames.SEC_WEBSOCKET_ORIGIN;
    }
}
