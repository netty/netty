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
         * Access the Http2HeaderTable for this {@link Http2HeadersDecoder}
         */
        Http2HeaderTable headerTable();
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
