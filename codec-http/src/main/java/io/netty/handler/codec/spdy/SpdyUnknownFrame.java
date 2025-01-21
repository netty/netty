/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/** A SPDY Control frame. */

public interface SpdyUnknownFrame extends SpdyFrame, ByteBufHolder {
    int frameType();

    byte flags();

    @Override
    SpdyUnknownFrame copy();

    @Override
    SpdyUnknownFrame duplicate();

    @Override
    SpdyUnknownFrame retainedDuplicate();

    @Override
    SpdyUnknownFrame replace(ByteBuf content);

    @Override
    SpdyUnknownFrame retain();

    @Override
    SpdyUnknownFrame retain(int increment);

    @Override
    SpdyUnknownFrame touch();

    @Override
    SpdyUnknownFrame touch(Object hint);
}
