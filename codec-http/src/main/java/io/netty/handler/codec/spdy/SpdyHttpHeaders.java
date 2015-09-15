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
package io.netty.handler.codec.spdy;

import io.netty.util.AsciiString;

/**
 * Provides the constants for the header names and the utility methods
 * used by the {@link SpdyHttpDecoder} and {@link SpdyHttpEncoder}.
 */
public final class SpdyHttpHeaders {

    /**
     * SPDY HTTP header names
     */
    public static final class Names {
        /**
         * {@code "x-spdy-stream-id"}
         */
        public static final AsciiString STREAM_ID = new AsciiString("x-spdy-stream-id");
        /**
         * {@code "x-spdy-associated-to-stream-id"}
         */
        public static final AsciiString ASSOCIATED_TO_STREAM_ID = new AsciiString("x-spdy-associated-to-stream-id");
        /**
         * {@code "x-spdy-priority"}
         */
        public static final AsciiString PRIORITY = new AsciiString("x-spdy-priority");
        /**
         * {@code "x-spdy-scheme"}
         */
        public static final AsciiString SCHEME = new AsciiString("x-spdy-scheme");

        private Names() { }
    }

    private SpdyHttpHeaders() { }
}
