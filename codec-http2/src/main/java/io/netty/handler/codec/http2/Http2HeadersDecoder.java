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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.UnstableApi;

/**
 * Decodes HPACK-encoded headers blocks into {@link Http2Headers}.
 */
@UnstableApi
public interface Http2HeadersDecoder {
    /**
     * Configuration related elements for the {@link Http2HeadersDecoder} interface
     */
    interface Configuration {
        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
         * This method should only be called by Netty (not users) as a result of a receiving a {@code SETTINGS} frame.
         */
        void maxHeaderTableSize(long max) throws Http2Exception;

        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>. The initial value
         * returned by this method must be {@link Http2CodecUtil#DEFAULT_HEADER_TABLE_SIZE}.
         */
        long maxHeaderTableSize();

        /**
         * Configure the maximum allowed size in bytes of each set of headers.
         * <p>
         * This method should only be called by Netty (not users) as a result of a receiving a {@code SETTINGS} frame.
         * @param max <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         *      If this limit is exceeded the implementation should attempt to keep the HPACK header tables up to date
         *      by processing data from the peer, but a {@code RST_STREAM} frame will be sent for the offending stream.
         * @param goAwayMax Must be {@code >= max}. A {@code GO_AWAY} frame will be generated if this limit is exceeded
         *                  for any particular stream.
         * @throws Http2Exception if limits exceed the RFC's boundaries or {@code max > goAwayMax}.
         */
        void maxHeaderListSize(long max, long goAwayMax) throws Http2Exception;

        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         */
        long maxHeaderListSize();

        /**
         * Represents the upper bound in bytes for a set of headers before a {@code GO_AWAY} should be sent.
         * This will be {@code <=}
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         */
        long maxHeaderListSizeGoAway();
    }

    /**
     * Decodes the given headers block and returns the headers.
     */
    Http2Headers decodeHeaders(int streamId, ByteBuf headerBlock) throws Http2Exception;

    /**
     * Get the {@link Configuration} for this {@link Http2HeadersDecoder}
     */
    Configuration configuration();
}
