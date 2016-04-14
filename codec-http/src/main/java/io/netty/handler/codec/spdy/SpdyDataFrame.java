/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;

/**
 * A SPDY Protocol DATA Frame
 */
public interface SpdyDataFrame extends ByteBufHolder, SpdyStreamFrame {

    @Override
    SpdyDataFrame setStreamId(int streamID);

    @Override
    SpdyDataFrame setLast(boolean last);

    /**
     * Returns the data payload of this frame.  If there is no data payload
     * {@link Unpooled#EMPTY_BUFFER} is returned.
     *
     * The data payload cannot exceed 16777215 bytes.
     */
    @Override
    ByteBuf content();

    @Override
    SpdyDataFrame copy();

    @Override
    SpdyDataFrame duplicate();

    @Override
    SpdyDataFrame retainedDuplicate();

    @Override
    SpdyDataFrame replace(ByteBuf content);

    @Override
    SpdyDataFrame retain();

    @Override
    SpdyDataFrame retain(int increment);

    @Override
    SpdyDataFrame touch();

    @Override
    SpdyDataFrame touch(Object hint);
}
