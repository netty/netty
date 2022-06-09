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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.Resource;
import io.netty5.util.internal.UnstableApi;

/**
 * HTTP/2 GOAWAY frame.
 *
 * <p>The last stream identifier <em>must not</em> be set by the application, but instead the
 * relative {@link #extraStreamIds()} should be used. The {@link #lastStreamId()} will only be
 * set for incoming GOAWAY frames by the HTTP/2 codec.
 *
 * <p>Graceful shutdown as described in the HTTP/2 spec can be accomplished by calling
 * {@code #setExtraStreamIds(Integer.MAX_VALUE)}.
 */
@UnstableApi
public interface Http2GoAwayFrame extends Http2Frame, Resource<Http2GoAwayFrame> {
    /**
     * The reason for beginning closure of the connection. Represented as an HTTP/2 error code.
     */
    long errorCode();

    /**
     * The number of IDs to reserve for the receiver to use while GOAWAY is in transit. This allows
     * for new streams currently en route to still be created, up to a point, which allows for very
     * graceful shutdown of both sides.
     */
    int extraStreamIds();

    /**
     * Sets the number of IDs to reserve for the receiver to use while GOAWAY is in transit.
     *
     * @see #extraStreamIds
     * @return {@code this}
     */
    Http2GoAwayFrame setExtraStreamIds(int extraStreamIds);

    /**
     * Returns the last stream identifier if set, or {@code -1} else.
     */
    int lastStreamId();

    /**
     * Optional debugging information describing cause the GOAWAY. Will not be {@code null}, but may
     * be empty.
     */
    Buffer content();

    Http2GoAwayFrame copy();
}
