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

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;

/**
 * Extends <tt>io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientExtensionHandler</tt>
 * to handle the most common WebSocket Compression Extensions.
 *
 * See <tt>io.netty.example.http.websocketx.client.WebSocketClient</tt> for usage.
 */
@ChannelHandler.Sharable
public final class WebSocketClientCompressionHandler extends WebSocketClientExtensionHandler {

    private static final int MAX_ALLOCATION = 1024 * 1024 * 10;

    public static final WebSocketClientCompressionHandler INSTANCE = new WebSocketClientCompressionHandler();

    private WebSocketClientCompressionHandler() {
        super(new PerMessageDeflateClientExtensionHandshaker(MAX_ALLOCATION),
                new DeflateFrameClientExtensionHandshaker(false, MAX_ALLOCATION),
                new DeflateFrameClientExtensionHandshaker(true, MAX_ALLOCATION));
    }

}
