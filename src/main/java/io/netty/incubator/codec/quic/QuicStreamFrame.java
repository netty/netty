/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * A QUIC STREAM_FRAME.
 */
public interface QuicStreamFrame extends ByteBufHolder {

    /**
     * Returns {@code true} if the frame has the FIN set, which means it notifies the remote peer that
     * there will be no more writing happen. {@code false} otherwise.
     *
     * @return {@code true} if the FIN flag should be set, {@code false} otherwise.
     */
    boolean hasFin();

    @Override
    QuicStreamFrame copy();

    @Override
    QuicStreamFrame duplicate();

    @Override
    QuicStreamFrame retainedDuplicate();

    @Override
    QuicStreamFrame replace(ByteBuf content);

    @Override
    QuicStreamFrame retain();

    @Override
    QuicStreamFrame retain(int increment);

    @Override
    QuicStreamFrame touch();

    @Override
    QuicStreamFrame touch(Object hint);
}
