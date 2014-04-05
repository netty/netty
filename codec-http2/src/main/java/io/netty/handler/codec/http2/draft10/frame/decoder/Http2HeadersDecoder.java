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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;

/**
 * Decodes HPACK-encoded headers blocks into {@link Http2Headers}.
 */
public interface Http2HeadersDecoder {

    /**
     * Decodes the given headers block and returns the headers.
     */
    Http2Headers decodeHeaders(ByteBuf headerBlock) throws Http2Exception;

    /**
     * Sets the new max header table size for this decoder.
     */
    void setHeaderTableSize(int size) throws Http2Exception;
}
