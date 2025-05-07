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

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;

import java.util.Collections;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * <a href="https://tools.ietf.org/id/draft-tyoshino-hybi-websocket-perframe-deflate-06.txt">perframe-deflate</a>
 * handshake implementation.
 */
public final class DeflateFrameServerExtensionHandshaker implements WebSocketServerExtensionHandshaker {

    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    static final String X_WEBKIT_DEFLATE_FRAME_EXTENSION = "x-webkit-deflate-frame";
    static final String DEFLATE_FRAME_EXTENSION = "deflate-frame";

    private final int compressionLevel;
    private final WebSocketExtensionFilterProvider extensionFilterProvider;
    private final int maxAllocation;

    /**
     * Constructor with default configuration.
     * @deprecated
     *            Use {@link DeflateFrameServerExtensionHandshaker#DeflateFrameServerExtensionHandshaker(int, int)}
     *            with {@link DeflateFrameServerExtensionHandshaker#DEFAULT_COMPRESSION_LEVEL}.
     */
    @Deprecated
    public DeflateFrameServerExtensionHandshaker() {
        this(DEFAULT_COMPRESSION_LEVEL, 0);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @deprecated
     *            Use {@link DeflateFrameServerExtensionHandshaker#DeflateFrameServerExtensionHandshaker(int, int)}.
     */
    @Deprecated
    public DeflateFrameServerExtensionHandshaker(int compressionLevel) {
        this(compressionLevel, 0);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public DeflateFrameServerExtensionHandshaker(int compressionLevel, int maxAllocation) {
        this(compressionLevel, WebSocketExtensionFilterProvider.DEFAULT, maxAllocation);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param extensionFilterProvider
     *            provides server extension filters for per frame deflate encoder and decoder.
     * @deprecated
     *            Use {@link DeflateFrameServerExtensionHandshaker#DeflateFrameServerExtensionHandshaker(int,
     *            WebSocketExtensionFilterProvider, int)}.
     */
    @Deprecated
    public DeflateFrameServerExtensionHandshaker(int compressionLevel,
                                                 WebSocketExtensionFilterProvider extensionFilterProvider) {
        this(compressionLevel, extensionFilterProvider, 0);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     * @param extensionFilterProvider
     *            provides server extension filters for per frame deflate encoder and decoder.
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public DeflateFrameServerExtensionHandshaker(int compressionLevel,
            WebSocketExtensionFilterProvider extensionFilterProvider,
            int maxAllocation) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
        this.extensionFilterProvider = checkNotNull(extensionFilterProvider, "extensionFilterProvider");
        this.maxAllocation = checkPositiveOrZero(maxAllocation, "maxAllocation");
    }

    @Override
    public WebSocketServerExtension handshakeExtension(WebSocketExtensionData extensionData) {
        if (!X_WEBKIT_DEFLATE_FRAME_EXTENSION.equals(extensionData.name()) &&
            !DEFLATE_FRAME_EXTENSION.equals(extensionData.name())) {
            return null;
        }

        if (extensionData.parameters().isEmpty()) {
            return new DeflateFrameServerExtension(compressionLevel, extensionData.name(), extensionFilterProvider,
                                                   maxAllocation);
        } else {
            return null;
        }
    }

    private static class DeflateFrameServerExtension implements WebSocketServerExtension {

        private final String extensionName;
        private final int compressionLevel;
        private final WebSocketExtensionFilterProvider extensionFilterProvider;
        private final int maxAllocation;

        DeflateFrameServerExtension(int compressionLevel, String extensionName,
                WebSocketExtensionFilterProvider extensionFilterProvider,
                int maxAllocation) {
            this.extensionName = extensionName;
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

        @Override
        public WebSocketExtensionData newReponseData() {
            return new WebSocketExtensionData(extensionName, Collections.<String, String>emptyMap());
        }
    }

}
