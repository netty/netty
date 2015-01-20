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

/**
 * Extracts a common interface for encoding and processing HPACK header constraints
 */
public interface Http2HeaderTable {
    /**
     * Sets the maximum size of the HPACK header table used for decoding HTTP/2 headers.
     */
    void maxHeaderTableSize(int max) throws Http2Exception;

    /**
     * Gets the maximum size of the HPACK header table used for decoding HTTP/2 headers.
     */
    int maxHeaderTableSize();

    /**
     * Sets the maximum allowed header elements.
     */
    void maxHeaderListSize(int max) throws Http2Exception;

    /**
     * Gets the maximum allowed header elements.
     */
    int maxHeaderListSize();
}
