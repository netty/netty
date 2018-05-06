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
         * @throws Http2Exception if limits exceed the RFC's boundaries or {@code max > goAwayMax}.
         */
        void maxHeaderListSize(long max) throws Http2Exception;

        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         */
        long maxHeaderListSize();
    }

    /**
     * Starts decoding a new headers block. Any previous headers block must have been fully decoded
     * by passing {@code endHeaders = true} to this method or {@link #decodeHeadersContinue}.
     *
     * <p>When {@code endHeaders} is {@code false}, after returning, {@code headersBlock} may not be
     * fully consumed. The unconsumed portion should be included in future calls to
     * {@link #decodeHeadersContinue}.
     *
     * @return non-{@code null} when {@code endHeaders} is {@code true}
     */
    Http2Headers decodeHeadersStart(int streamId, ByteBuf headersBlock, boolean endHeaders) throws Http2Exception;

    /**
     * Continues decoding a headers block. A block must already have been started by a call to
     * {@link #decodeHeadersStart} and must not have been completed via {@code endHeaders = true}.
     *
     * <p>When {@code endHeaders} is {@code false}, after returning, {@code headersBlock} may not be
     * fully consumed. The unconsumed portion should be included in future calls to
     * {@link #decodeHeadersContinue}.
     *
     * @return non-{@code null} when {@code endHeaders} is {@code true}
     */
    Http2Headers decodeHeadersContinue(ByteBuf headerBlock, boolean endHeaders) throws Http2Exception;

    /**
     * Get the {@link Configuration} for this {@link Http2HeadersDecoder}
     */
    Configuration configuration();
}
