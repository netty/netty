/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.AsciiString;

/**
 * Provides the constants for the header names used by
 * {@link InboundHttp2ToHttpAdapter} and {@link DelegatingHttp2HttpConnectionHandler}
 */
public final class Http2HttpHeaders {

    public static final class Names {
        /**
         * {@code "X-HTTP2-Stream-ID"}
         */
        public static final AsciiString STREAM_ID            = new AsciiString("X-HTTP2-Stream-ID");
        /**
         * {@code "X-HTTP2-Authority"}
         */
        public static final AsciiString AUTHORITY            = new AsciiString("X-HTTP2-Authority");
        /**
         * {@code "X-HTTP2-Scheme"}
         */
        public static final AsciiString SCHEME               = new AsciiString("X-HTTP2-Scheme");
        /**
         * {@code "X-HTTP2-Path"}
         */
        public static final AsciiString PATH                 = new AsciiString("X-HTTP2-Path");
        /**
         * {@code "X-HTTP2-Stream-Promise-ID"}
         */
        public static final AsciiString STREAM_PROMISE_ID    = new AsciiString("X-HTTP2-Stream-Promise-ID");
        /**
         * {@code "X-HTTP2-Stream-Dependency-ID"}
         */
        public static final AsciiString STREAM_DEPENDENCY_ID = new AsciiString("X-HTTP2-Stream-Dependency-ID");
        /**
         * {@code "X-HTTP2-Stream-Weight"}
         */
        public static final AsciiString STREAM_WEIGHT        = new AsciiString("X-HTTP2-Stream-Weight");
        /**
         * {@code "X-HTTP2-Stream-Exclusive"}
         */
        public static final AsciiString STREAM_EXCLUSIVE     = new AsciiString("X-HTTP2-Stream-Exclusive");

        private Names() {
        }
    }
}
