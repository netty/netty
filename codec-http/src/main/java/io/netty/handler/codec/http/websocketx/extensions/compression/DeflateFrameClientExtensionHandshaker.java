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
    private final int maxAllocation;

    /**
     * Constructor with default configuration.
     *
     * @deprecated
     *            Use {@link DeflateFrameClientExtensionHandshaker#DeflateFrameClientExtensionHandshaker(boolean, int)}.
     */
    @Deprecated
    public DeflateFrameClientExtensionHandshaker(boolean useWebkitExtensionName) {
        this(6, useWebkitExtensionName, 0);
    }

    /**
     * Constructor with default configuration.
     *
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public DeflateFrameClientExtensionHandshaker(boolean useWebkitExtensionName, int maxAllocation) {
        this(6, useWebkitExtensionName, maxAllocation);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @deprecated
     *            Use {@link
     *            DeflateFrameClientExtensionHandshaker#DeflateFrameClientExtensionHandshaker(int, boolean, int)}.
     */
    @Deprecated
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName) {
        this(compressionLevel, useWebkitExtensionName, 0);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName,
                                                 int maxAllocation) {
        this(compressionLevel, useWebkitExtensionName, WebSocketExtensionFilterProvider.DEFAULT, maxAllocation);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param extensionFilterProvider
     *            provides client extension filters for per frame deflate encoder and decoder.
     * @deprecated
     *            Use {@link DeflateFrameClientExtensionHandshaker#DeflateFrameClientExtensionHandshaker(int, boolean,
     *            WebSocketExtensionFilterProvider, int)}.
     */
    @Deprecated
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName,
                                                 WebSocketExtensionFilterProvider extensionFilterProvider) {
        this(compressionLevel, useWebkitExtensionName, extensionFilterProvider, 0);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param extensionFilterProvider
     *            provides client extension filters for per frame deflate encoder and decoder.
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public DeflateFrameClientExtensionHandshaker(int compressionLevel, boolean useWebkitExtensionName,
            WebSocketExtensionFilterProvider extensionFilterProvider, int maxAllocation) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
        this.useWebkitExtensionName = useWebkitExtensionName;
        this.extensionFilterProvider = checkNotNull(extensionFilterProvider, "extensionFilterProvider");
        this.maxAllocation = checkPositiveOrZero(maxAllocation, "maxAllocation");
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
            return new DeflateFrameClientExtension(compressionLevel, extensionFilterProvider, maxAllocation);
        } else {
            return null;
        }
    }

    private static class DeflateFrameClientExtension implements WebSocketClientExtension {

        private final int compressionLevel;
        private final WebSocketExtensionFilterProvider extensionFilterProvider;
        private final int maxAllocation;

        DeflateFrameClientExtension(int compressionLevel, WebSocketExtensionFilterProvider extensionFilterProvider,
                                    int maxAllocation) {
            this.compressionLevel = compressionLevel;
            this.extensionFilterProvider = extensionFilterProvider;
            this.maxAllocation = maxAllocation;
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
            return new PerFrameDeflateDecoder(false, extensionFilterProvider.decoderFilter(), maxAllocation);
        }
    }

}
