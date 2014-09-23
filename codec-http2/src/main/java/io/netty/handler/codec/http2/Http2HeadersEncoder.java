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

/**
 * Encodes {@link Http2Headers} into HPACK-encoded headers blocks.
 */
public interface Http2HeadersEncoder {
    /**
     * Configuration related elements for the {@link Http2HeadersEncoder} interface
     */
    public interface Configuration {
        /**
         * Access the Http2HeaderTable for this {@link Http2HeadersEncoder}
         */
        Http2HeaderTable headerTable();
    }

    /**
     * Encodes the given headers and writes the output headers block to the given output buffer.
     *
     * @param headers the headers to be encoded.
     * @param buffer the buffer to receive the encoded headers.
     */
    void encodeHeaders(Http2Headers headers, ByteBuf buffer) throws Http2Exception;

    /**
     * Get the {@link Configuration} for this {@link Http2HeadersEncoder}
     */
    Configuration configuration();
}
