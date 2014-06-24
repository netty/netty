/*
 * Copyright 2012 The Netty Project
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
// (BSD License: http://www.opensource.org/licenses/bsd-license)
//
// Copyright (c) 2011, Joe Walnes and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the
// following conditions are met:
//
// * Redistributions of source code must retain the above
// copyright notice, this list of conditions and the
// following disclaimer.
//
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the
// following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// * Neither the name of the Webbit nor the names of
// its contributors may be used to endorse or promote products
// derived from this software without specific prior written
// permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package io.netty.handler.codec.http.websocketx.extensions.compression;

import static io.netty.handler.codec.http.websocketx.extensions.compression.
        PermessageDeflateServerExtensionHandshaker.*;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionUtil.WebSocketExtensionData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * This server handler negotiates the WebSocket compression and initialize the deflater and inflater
 * according the client capabilities.
 *
 * See <tt>io.netty.example.http.websocketx.html5.WebSocketServer</tt> for usage.
 */
public class PermessageDeflateClientExtensionHandshaker implements WebSocketClientExtensionHandshaker {

    private final int compressionLevel;
    private final boolean allowClientWindowSize;
    private final int requestedServerWindowSize;
    private final boolean allowClientNoContext;
    private final boolean requestedServerNoContext;

    /**
     * Constructor with default configuration.
     */
    public PermessageDeflateClientExtensionHandshaker() {
        this(6, false, MAX_WINDOW_SIZE, false, false);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param allowCustomServerWindowSize
     *            true to allow WebSocket client to customize the server inflater window size
     *            (default is false).
     * @param preferredClientWindowSize
     *            indicate the preferred client window size to use if client inflater is customizable.
     */
    public PermessageDeflateClientExtensionHandshaker(int compressionLevel,
            boolean allowClientWindowSize, int requestedServerWindowSize,
            boolean allowClientNoContext, boolean requestedServerNoContext) {
        if (requestedServerWindowSize > MAX_WINDOW_SIZE || requestedServerWindowSize < MIN_WINDOW_SIZE) {
            throw new IllegalArgumentException(
                    "requestedServerWindowSize: " + requestedServerWindowSize + " (expected: 8-15)");
        }
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
        this.allowClientWindowSize = allowClientWindowSize;
        this.requestedServerWindowSize = requestedServerWindowSize;
        this.allowClientNoContext = allowClientNoContext;
        this.requestedServerNoContext = requestedServerNoContext;
    }

    @Override
    public WebSocketExtensionData newRequestData() {
        HashMap<String, String> parameters = new HashMap<String, String>(4);
        if (requestedServerWindowSize != MAX_WINDOW_SIZE) {
            parameters.put(SERVER_NO_CONTEXT, null);
        }
        if (allowClientNoContext) {
            parameters.put(CLIENT_NO_CONTEXT, null);
        }
        if (requestedServerWindowSize != MAX_WINDOW_SIZE) {
            parameters.put(SERVER_MAX_WINDOW, Integer.toString(requestedServerWindowSize));
        }
        if (allowClientWindowSize) {
            parameters.put(CLIENT_MAX_WINDOW, null);
        }
        return new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters);
    }

    @Override
    public WebSocketClientExtension handshakeExtension(WebSocketExtensionData extensionData) {
        if (!PERMESSAGE_DEFLATE_EXTENSION.equals(extensionData.getName())) {
            return null;
        }

        boolean deflateEnabled = true;
        int clientWindowSize = MAX_WINDOW_SIZE;
        int serverWindowSize = MAX_WINDOW_SIZE;
        boolean serverNoContext = false;
        boolean clientNoContext = false;

        Iterator<Entry<String, String>> parametersIterator =
                extensionData.getParameters().entrySet().iterator();
        while (deflateEnabled && parametersIterator.hasNext()) {
            Entry<String, String> parameter = parametersIterator.next();

            if (CLIENT_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                if (allowClientWindowSize) {
                    clientWindowSize = Integer.valueOf(parameter.getValue());
                    if (clientWindowSize > MAX_WINDOW_SIZE || clientWindowSize < MIN_WINDOW_SIZE) {
                        throw new IllegalArgumentException(CLIENT_MAX_WINDOW);
                    }
                } else {
                    deflateEnabled = false;
                }
            } else if (SERVER_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                // check server_window_size_bits acknowledge
                if (requestedServerWindowSize != Integer.parseInt(parameter.getValue())) {
                    throw new IllegalArgumentException(SERVER_MAX_WINDOW);
                }
            } else if (CLIENT_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                if (allowClientNoContext) {
                    clientNoContext = true;
                } else {
                    throw new IllegalArgumentException(CLIENT_NO_CONTEXT);
                }
            } else if (SERVER_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                // check server_no_context_takeover acknowledge
                if (!requestedServerNoContext) {
                    throw new IllegalArgumentException(SERVER_NO_CONTEXT);
                }
                serverNoContext = true;
            } else {
                // unknown parameter
                throw new IllegalArgumentException(parameter.getKey());
            }
        }

        if (deflateEnabled) {
            return new PermessageDeflateExtension(serverNoContext, serverWindowSize,
                    clientNoContext, clientWindowSize);
        } else {
            return null;
        }
    }

    private class PermessageDeflateExtension implements WebSocketClientExtension {

        private final boolean serverNoContext;
        private final int serverWindowSize;
        private final boolean clientNoContext;
        private final int clientWindowSize;

        @Override
        public int rsv() {
            return RSV1;
        }

        public PermessageDeflateExtension(boolean serverNoContext, int serverWindowSize,
                boolean clientNoContext, int clientWindowSize) {
            this.serverNoContext = serverNoContext;
            this.serverWindowSize = serverWindowSize;
            this.clientNoContext = clientNoContext;
            this.clientWindowSize = clientWindowSize;
        }

        @Override
        public WebSocketExtensionEncoder createExtensionEncoder() {
            return new PermessageDeflateEncoder(compressionLevel, serverWindowSize, serverNoContext);
        }

        @Override
        public WebSocketExtensionDecoder createExtensionDecoder() {
            return new PermessageDeflateDecoder(clientNoContext);
        }
    }

}
