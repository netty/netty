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

import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * <a href="http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">permessage-deflate</a>
 * handshake implementation.
 */
public final class PerMessageDeflateClientExtensionHandshaker implements WebSocketClientExtensionHandshaker {

    private final int compressionLevel;
    private final boolean allowClientWindowSize;
    private final int requestedServerWindowSize;
    private final boolean allowClientNoContext;
    private final boolean requestedServerNoContext;

    /**
     * Constructor with default configuration.
     */
    public PerMessageDeflateClientExtensionHandshaker() {
        this(6, ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), MAX_WINDOW_SIZE, false, false);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param allowClientWindowSize
     *            allows WebSocket server to customize the client inflater window size
     *            (default is false).
     * @param requestedServerWindowSize
     *            indicates the requested sever window size to use if server inflater is customizable.
     * @param allowClientNoContext
     *            allows WebSocket server to activate client_no_context_takeover
     *            (default is false).
     * @param requestedServerNoContext
     *            indicates if client needs to activate server_no_context_takeover
     *            if server is compatible with (default is false).
     */
    public PerMessageDeflateClientExtensionHandshaker(int compressionLevel,
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
        if (!PERMESSAGE_DEFLATE_EXTENSION.equals(extensionData.name())) {
            return null;
        }

        boolean succeed = true;
        int clientWindowSize = MAX_WINDOW_SIZE;
        int serverWindowSize = MAX_WINDOW_SIZE;
        boolean serverNoContext = false;
        boolean clientNoContext = false;

        Iterator<Entry<String, String>> parametersIterator =
                extensionData.parameters().entrySet().iterator();
        while (succeed && parametersIterator.hasNext()) {
            Entry<String, String> parameter = parametersIterator.next();

            if (CLIENT_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                // allowed client_window_size_bits
                if (allowClientWindowSize) {
                    clientWindowSize = Integer.parseInt(parameter.getValue());
                } else {
                    succeed = false;
                }
            } else if (SERVER_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                // acknowledged server_window_size_bits
                serverWindowSize = Integer.parseInt(parameter.getValue());
                if (clientWindowSize > MAX_WINDOW_SIZE || clientWindowSize < MIN_WINDOW_SIZE) {
                    succeed = false;
                }
            } else if (CLIENT_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                // allowed client_no_context_takeover
                if (allowClientNoContext) {
                    clientNoContext = true;
                } else {
                    succeed = false;
                }
            } else if (SERVER_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                // acknowledged server_no_context_takeover
                if (requestedServerNoContext) {
                    serverNoContext = true;
                } else {
                    succeed = false;
                }
            } else {
                // unknown parameter
                succeed = false;
            }
        }

        if ((requestedServerNoContext && !serverNoContext) ||
                requestedServerWindowSize != serverWindowSize) {
            succeed = false;
        }

        if (succeed) {
            return new PermessageDeflateExtension(serverNoContext, serverWindowSize,
                    clientNoContext, clientWindowSize);
        } else {
            return null;
        }
    }

    private final class PermessageDeflateExtension implements WebSocketClientExtension {

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
        public WebSocketExtensionEncoder newExtensionEncoder() {
            return new PerMessageDeflateEncoder(compressionLevel, serverWindowSize, serverNoContext);
        }

        @Override
        public WebSocketExtensionDecoder newExtensionDecoder() {
            return new PerMessageDeflateDecoder(clientNoContext);
        }
    }

}
