/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;

/**
 * Encodes {@link Http2Headers} into HPACK-encoded headers blocks.
 */
public interface Http2HeadersEncoder {
    /**
     * Configuration related elements for the {@link Http2HeadersEncoder} interface
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
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
         * The initial value returned by this method must be {@link Http2CodecUtil#DEFAULT_HEADER_TABLE_SIZE}.
         */
        long maxHeaderTableSize();

        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         * This method should only be called by Netty (not users) as a result of a receiving a {@code SETTINGS} frame.
         */
        void maxHeaderListSize(long max) throws Http2Exception;

        /**
         * Represents the value for
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
         */
        long maxHeaderListSize();
    }

    /**
     * Determine if a header name/value pair is treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
     * If the object can be dynamically modified and shared across multiple connections it may need to be thread safe.
     * <p>
     * Implement {@link IndexingSensitivityDetector} to also control whether non-sensitive headers are added to the
     * HPACK dynamic table.
     */
    interface SensitivityDetector {
        /**
         * Determine if a header {@code name}/{@code value} pair should be treated as
         * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
         *
         * @param name The name for the header.
         * @param value The value of the header.
         * @return {@code true} if a header {@code name}/{@code value} pair should be treated as
         * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
         * {@code false} otherwise.
         */
        boolean isSensitive(CharSequence name, CharSequence value);
    }

    /**
     * A {@link SensitivityDetector} that can also determine if a header that is not
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a> should be added to the HPACK dynamic
     * table.
     * <p>
     * Headers for which {@link #shouldIndex(CharSequence, CharSequence)} returns {@code false} are encoded as a
     * <a href="https://tools.ietf.org/html/rfc7541#section-6.2.2">Literal Header Field without Indexing</a>, which
     * may still reference the header name from the static or dynamic table. Unlike sensitive headers, which are encoded
     * as <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">Literal Header Field Never Indexed</a>,
     * intermediaries are allowed to re-encode these headers with indexing. If the exact header name/value pair is
     * already present in the static or dynamic table it is still encoded as an
     * <a href="https://tools.ietf.org/html/rfc7541#section-6.1">Indexed Header Field</a>.
     * <p>
     * This is useful for headers with values that are unlikely to repeat, such as request identifiers or timestamps,
     * as adding them to the dynamic table would only evict more useful entries.
     * <p>
     * An instance can be passed anywhere a {@link SensitivityDetector} is accepted.
     * If the object can be dynamically modified and shared across multiple connections it may need to be thread safe.
     */
    interface IndexingSensitivityDetector extends SensitivityDetector {
        /**
         * Determine if a header {@code name}/{@code value} pair should be added to the HPACK dynamic table.
         * This method is only called for headers for which
         * {@link #isSensitive(CharSequence, CharSequence)} returned {@code false}, and only when the header would
         * otherwise be added to the dynamic table.
         *
         * @param name The name for the header.
         * @param value The value of the header.
         * @return {@code true} if the header may be added to the dynamic table, {@code false} if it should be encoded
         * as a <a href="https://tools.ietf.org/html/rfc7541#section-6.2.2">Literal Header Field without Indexing</a>.
         */
        boolean shouldIndex(CharSequence name, CharSequence value);
    }

    /**
     * Encodes the given headers and writes the output headers block to the given output buffer.
     *
     * @param streamId  the identifier of the stream for which the headers are encoded.
     * @param headers the headers to be encoded.
     * @param buffer the buffer to receive the encoded headers.
     */
    void encodeHeaders(int streamId, Http2Headers headers, ByteBuf buffer) throws Http2Exception;

    /**
     * Get the {@link Configuration} for this {@link Http2HeadersEncoder}
     */
    Configuration configuration();

    /**
     * Always return {@code false} for {@link SensitivityDetector#isSensitive(CharSequence, CharSequence)}.
     */
    SensitivityDetector NEVER_SENSITIVE = new SensitivityDetector() {
        @Override
        public boolean isSensitive(CharSequence name, CharSequence value) {
            return false;
        }
    };

    /**
     * Always return {@code true} for {@link SensitivityDetector#isSensitive(CharSequence, CharSequence)}.
     */
    SensitivityDetector ALWAYS_SENSITIVE = new SensitivityDetector() {
        @Override
        public boolean isSensitive(CharSequence name, CharSequence value) {
            return true;
        }
    };
}
