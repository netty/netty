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

import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;

import java.util.Collections;

import static io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * <a href="https://tools.ietf.org/id/draft-tyoshino-hybi-websocket-perframe-deflate-06.txt">perframe-deflate</a>
 * handshake implementation.
 */
public final class DeflateFrameClientExtensionHandshaker implements WebSocketClientExtensionHandshaker {

    private final int compressionLevel;
    private final boolean useWebkitExtensionName;
    private final WebSocketExtensionFilterProvider extensionFilterProvider;

    /**
     * Constructor with default configuration.
     */
    public DeflateFrameClientExtensionHandshaker(boolean useWebkitExtensionName) {
        this(6, useWebkitExtensionName);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     */
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName) {
        this(compressionLevel, useWebkitExtensionName, WebSocketExtensionFilterProvider.DEFAULT);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param extensionFilterProvider
     *            provides client extension filters for per frame deflate encoder and decoder.
     */
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName,
            WebSocketExtensionFilterProvider extensionFilterProvider) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
        this.useWebkitExtensionName = useWebkitExtensionName;
        this.extensionFilterProvider = checkNotNull(extensionFilterProvider, "extensionFilterProvider");
    }

    @Override
    public WebSocketExtensionData newRequestData() {
        return new WebSocketExtensionData(
                useWebkitExtensionName ? X_WEBKIT_DEFLATE_FRAME_EXTENSION : DEFLATE_FRAME_EXTENSION,
                Collections.<String, String>emptyMap());
    }

    @Override
    public WebSocketClientExtension handshakeExtension(WebSocketExtensionData extensionData) {
        if (!X_WEBKIT_DEFLATE_FRAME_EXTENSION.equals(extensionData.name()) &&
            !DEFLATE_FRAME_EXTENSION.equals(extensionData.name())) {
            return null;
        }

        if (extensionData.parameters().isEmpty()) {
            return new DeflateFrameClientExtension(compressionLevel, extensionFilterProvider);
        } else {
            return null;
        }
    }

    private static class DeflateFrameClientExtension implements WebSocketClientExtension {

        private final int compressionLevel;
        private final WebSocketExtensionFilterProvider extensionFilterProvider;

        DeflateFrameClientExtension(int compressionLevel, WebSocketExtensionFilterProvider extensionFilterProvider) {
            this.compressionLevel = compressionLevel;
            this.extensionFilterProvider = extensionFilterProvider;
        }

        @Override
        public int rsv() {
            return RSV1;
        }

        @Override
        public WebSocketExtensionEncoder newExtensionEncoder() {
            return new PerFrameDeflateEncoder(compressionLevel, 15, false,
                                              extensionFilterProvider.encoderFilter());
        }

        @Override
        public WebSocketExtensionDecoder newExtensionDecoder() {
            return new PerFrameDeflateDecoder(false, extensionFilterProvider.decoderFilter());
        }
    }

}
