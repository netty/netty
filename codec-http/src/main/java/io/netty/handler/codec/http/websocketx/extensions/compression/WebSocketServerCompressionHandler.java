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

import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;

/**
 * Extends <tt>io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerExtensionHandler</tt>
 * to handle the most common WebSocket Compression Extensions.
 *
 * See <tt>io.netty.example.http.websocketx.html5.WebSocketServer</tt> for usage.
 */
public class WebSocketServerCompressionHandler extends WebSocketServerExtensionHandler {

    /**
     * Constructor with default configuration.
     */
    public WebSocketServerCompressionHandler() {
        super(new PerMessageDeflateServerExtensionHandshaker(),
                new DeflateFrameServerExtensionHandshaker());
    }

}
