/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.concurrent.FastThreadLocal;

/**
 * Allow to obtain {@link IovArray} instances.
 */
final class IovArrayThreadLocal {

    private static final FastThreadLocal<IovArray> ARRAY = new FastThreadLocal<IovArray>() {
        @Override
        protected IovArray initialValue() throws Exception {
            return new IovArray();
        }

        @Override
        protected void onRemoval(IovArray value) throws Exception {
            // free the direct memory now
            value.release();
        }
    };

    /**
     * Returns a {@link IovArray} which is filled with the flushed messages of {@link ChannelOutboundBuffer}.
     */
    static IovArray get(ChannelOutboundBuffer buffer) throws Exception {
        IovArray array = ARRAY.get();
        array.clear();
        buffer.forEachFlushedMessage(array);
        return array;
    }

    /**
     * Returns a {@link IovArray} which is filled with the {@link CompositeByteBuf}.
     */
    static IovArray get(CompositeByteBuf buf) throws Exception {
        IovArray array = ARRAY.get();
        array.clear();
        array.add(buf);
        return array;
    }

    private IovArrayThreadLocal() { }
}
