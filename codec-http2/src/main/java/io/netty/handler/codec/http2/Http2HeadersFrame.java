/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

/**
 * HTTP/2 HEADERS frame.
 */
public interface Http2HeadersFrame extends Http2StreamFrame {

    /**
     * A complete header list. CONTINUATION frames are automatically handled.
     */
    Http2Headers headers();

    /**
     * Frame padding to use. Must be non-negative and less than 256.
     */
    int padding();

    /**
     * Returns {@code true} if the END_STREAM flag is set.
     */
    boolean isEndStream();
}
