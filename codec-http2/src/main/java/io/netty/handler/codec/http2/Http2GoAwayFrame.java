/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;

/**
 * HTTP/2 GOAWAY frame. Last-Stream-Id is not exposed directly, but instead via the relative {@link
 * #extraStreamIds()}.
 */
public interface Http2GoAwayFrame extends Http2Frame, ByteBufHolder {
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
     * Optional debugging information describing cause the GOAWAY. Will not be {@code null}, but may
     * be empty.
     */
    @Override
    ByteBuf content();

    @Override
    Http2GoAwayFrame copy();

    @Override
    Http2GoAwayFrame duplicate();

    @Override
    Http2GoAwayFrame retain();

    @Override
    Http2GoAwayFrame retain(int increment);

    @Override
    Http2GoAwayFrame touch();

    @Override
    Http2GoAwayFrame touch(Object hint);
}
