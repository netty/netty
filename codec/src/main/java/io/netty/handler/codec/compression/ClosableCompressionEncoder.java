/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.TimeUnit;

/**
 * A {@link CompressionEncoder} which may be closed.
 */
public interface ClosableCompressionEncoder extends CompressionEncoder {

    /**
     * Amount of time to be delayed before the ensuring that the channel is closed
     * after the call of {@link #close(ChannelHandlerContext, ChannelPromise)}.
     */
    long lastWriteTimeoutMillis();

    /**
     * Set amount of time to be delayed before the ensuring that the channel is closed
     * after the call of {@link #close(ChannelHandlerContext, ChannelPromise)}.
     *
     * @param timeout value of time.
     * @param unit    unit of time.
     */
    void setLastWriteTimeout(long timeout, TimeUnit unit);

    /**
     * Returns {@code true} if and only if the end of the compressed stream has been reached.
     */
    boolean isClosed();

    /**
     * Close this {@link AbstractClosableCompressionEncoder} and so finish the encoding.
     *
     * The returned {@link ChannelFuture} will be notified once the operation completes.
     *
     * Note: for some {@link CompressionFormat}'s which does not support close operation
     * this method could return failed {@link ChannelFuture}.
     */
    ChannelFuture close();

    /**
     * Close this {@link AbstractClosableCompressionEncoder} and so finish the encoding.
     * The given {@link ChannelFuture} will be notified once the operation
     * completes and will also be returned.
     *
     * Note: for some {@link CompressionFormat}'s which does not support close operation
     * this method could return failed {@link ChannelFuture}.
     */
    ChannelFuture close(ChannelPromise promise);
}
