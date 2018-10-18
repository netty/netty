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

import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * <a href="http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">permessage-deflate</a>
 * handshake implementation.
 */
public final class PerMessageDeflateServerExtensionHandshaker implements WebSocketServerExtensionHandshaker {

    public static final int MIN_WINDOW_SIZE = 8;
    public static final int MAX_WINDOW_SIZE = 15;

    static final String PERMESSAGE_DEFLATE_EXTENSION = "permessage-deflate";
    static final String CLIENT_MAX_WINDOW = "client_max_window_bits";
    static final String SERVER_MAX_WINDOW = "server_max_window_bits";
    static final String CLIENT_NO_CONTEXT = "client_no_context_takeover";
    static final String SERVER_NO_CONTEXT = "server_no_context_takeover";

    private final int compressionLevel;
    private final boolean allowServerWindowSize;
    private final int preferredClientWindowSize;
    private final boolean allowServerNoContext;
    private final boolean preferredClientNoContext;

    /**
     * Constructor with default configuration.
     */
    public PerMessageDeflateServerExtensionHandshaker() {
        this(6, ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), MAX_WINDOW_SIZE, false, false);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param allowServerWindowSize
     *            allows WebSocket client to customize the server inflater window size
     *            (default is false).
     * @param preferredClientWindowSize
     *            indicates the preferred client window size to use if client inflater is customizable.
     * @param allowServerNoContext
     *            allows WebSocket client to activate server_no_context_takeover
     *            (default is false).
     * @param preferredClientNoContext
     *            indicates if server prefers to activate client_no_context_takeover
     *            if client is compatible with (default is false).
     */
    public PerMessageDeflateServerExtensionHandshaker(int compressionLevel,
            boolean allowServerWindowSize, int preferredClientWindowSize,
            boolean allowServerNoContext, boolean preferredClientNoContext) {
        if (preferredClientWindowSize > MAX_WINDOW_SIZE || preferredClientWindowSize < MIN_WINDOW_SIZE) {
            throw new IllegalArgumentException(
                    "preferredServerWindowSize: " + preferredClientWindowSize + " (expected: 8-15)");
        }
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
        this.allowServerWindowSize = allowServerWindowSize;
        this.preferredClientWindowSize = preferredClientWindowSize;
        this.allowServerNoContext = allowServerNoContext;
        this.preferredClientNoContext = preferredClientNoContext;
    }

    @Override
    public WebSocketServerExtension handshakeExtension(WebSocketExtensionData extensionData) {
        if (!PERMESSAGE_DEFLATE_EXTENSION.equals(extensionData.name())) {
            return null;
        }

        boolean deflateEnabled = true;
        int clientWindowSize = MAX_WINDOW_SIZE;
        int serverWindowSize = MAX_WINDOW_SIZE;
        boolean serverNoContext = false;
        boolean clientNoContext = false;

        Iterator<Entry<String, String>> parametersIterator =
                extensionData.parameters().entrySet().iterator();
        while (deflateEnabled && parametersIterator.hasNext()) {
            Entry<String, String> parameter = parametersIterator.next();

            if (CLIENT_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
             // use preferred clientWindowSize because client is compatible with customization
                clientWindowSize = preferredClientWindowSize;
            } else if (SERVER_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                // use provided windowSize if it is allowed
                if (allowServerWindowSize) {
                    serverWindowSize = Integer.parseInt(parameter.getValue());
                    if (serverWindowSize > MAX_WINDOW_SIZE || serverWindowSize < MIN_WINDOW_SIZE) {
                        deflateEnabled = false;
                    }
                } else {
                    deflateEnabled = false;
                }
            } else if (CLIENT_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                // use preferred clientNoContext because client is compatible with customization
                clientNoContext = preferredClientNoContext;
            } else if (SERVER_NO_CONTEXT.equalsIgnoreCase(parameter.getKey())) {
                // use server no context if allowed
                if (allowServerNoContext) {
                    serverNoContext = true;
                } else {
                    deflateEnabled = false;
                }
            } else {
                // unknown parameter
                deflateEnabled = false;
            }
        }

        if (deflateEnabled) {
            return new PermessageDeflateExtension(compressionLevel, serverNoContext,
                    serverWindowSize, clientNoContext, clientWindowSize);
        } else {
            return null;
        }
    }

    private static class PermessageDeflateExtension implements WebSocketServerExtension {

        private final int compressionLevel;
        private final boolean serverNoContext;
        private final int serverWindowSize;
        private final boolean clientNoContext;
        private final int clientWindowSize;

        public PermessageDeflateExtension(int compressionLevel, boolean serverNoContext,
                int serverWindowSize, boolean clientNoContext, int clientWindowSize) {
            this.compressionLevel = compressionLevel;
            this.serverNoContext = serverNoContext;
            this.serverWindowSize = serverWindowSize;
            this.clientNoContext = clientNoContext;
            this.clientWindowSize = clientWindowSize;
        }

        @Override
        public int rsv() {
            return RSV1;
        }

        @Override
        public WebSocketExtensionEncoder newExtensionEncoder() {
            return new PerMessageDeflateEncoder(compressionLevel, serverWindowSize, serverNoContext);
        }

        @Override
        public WebSocketExtensionDecoder newExtensionDecoder() {
            return new PerMessageDeflateDecoder(clientNoContext);
        }

        @Override
        public WebSocketExtensionData newReponseData() {
            HashMap<String, String> parameters = new HashMap<String, String>(4);
            if (serverNoContext) {
                parameters.put(SERVER_NO_CONTEXT, null);
            }
            if (clientNoContext) {
                parameters.put(CLIENT_NO_CONTEXT, null);
            }
            if (serverWindowSize != MAX_WINDOW_SIZE) {
                parameters.put(SERVER_MAX_WINDOW, Integer.toString(serverWindowSize));
            }
            if (clientWindowSize != MAX_WINDOW_SIZE) {
                parameters.put(CLIENT_MAX_WINDOW, Integer.toString(clientWindowSize));
            }
            return new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters);
        }
    }

}
