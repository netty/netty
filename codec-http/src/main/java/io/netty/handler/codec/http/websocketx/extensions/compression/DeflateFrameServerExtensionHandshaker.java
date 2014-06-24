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

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionUtil.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;

import java.util.Collections;

/**
 * This server handler negotiates the WebSocket compression and initialize the deflater and inflater
 * according the client capabilities.
 *
 * See <tt>io.netty.example.http.websocketx.html5.WebSocketServer</tt> for usage.
 */
public class DeflateFrameServerExtensionHandshaker implements WebSocketServerExtensionHandshaker {

    static final String X_WEBKIT_DEFLATE_FRAME_EXTENSION = "x-webkit-deflate-frame";
    static final String DEFLATE_FRAME_EXTENSION = "deflate-frame";

    private final int compressionLevel;

    /**
     * Constructor with default configuration.
     */
    public DeflateFrameServerExtensionHandshaker() {
        this(6);
    }

    /**
     * Constructor with custom configuration.
     *
     * @param compressionLevel
     *            Compression level between 0 and 9 (default is 6).
     */
    public DeflateFrameServerExtensionHandshaker(int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
    }

    @Override
    public WebSocketServerExtension handshakeExtension(WebSocketExtensionData extensionData) {
        if (!X_WEBKIT_DEFLATE_FRAME_EXTENSION.equals(extensionData.getName()) &&
            !DEFLATE_FRAME_EXTENSION.equals(extensionData.getName())) {
            return null;
        }

        if (extensionData.getParameters().isEmpty()) {
            return new XWebkitDeflateFrameServerExtension(compressionLevel, extensionData.getName());
        } else {
            return null;
        }
    }

    private static class XWebkitDeflateFrameServerExtension implements WebSocketServerExtension {

        private final String extensionName;
        private final int compressionLevel;

        public XWebkitDeflateFrameServerExtension(int compressionLevel, String extensionName) {
            this.extensionName = extensionName;
            this.compressionLevel = compressionLevel;
        }

        @Override
        public int rsv() {
            return RSV1;
        }

        @Override
        public WebSocketExtensionEncoder createExtensionEncoder() {
            return new PerframeDeflateEncoder(compressionLevel, 15, false);
        }

        @Override
        public WebSocketExtensionDecoder createExtensionDecoder() {
            return new PerframeDeflateDecoder(false);
        }

        @Override
        public WebSocketExtensionData newReponseData() {
            return new WebSocketExtensionData(extensionName, Collections.<String, String>emptyMap());
        }
    }

}
