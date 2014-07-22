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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.Recycler;

import java.nio.ByteBuffer;

/**
 * Special {@link ChannelOutboundBuffer} implementation which allows to obtain a {@link IovArray}
 * and so doing gathering writes without the need to create a {@link ByteBuffer} internally. This reduce
 * GC pressure a lot.
 */
final class EpollChannelOutboundBuffer extends ChannelOutboundBuffer {
    private static final Recycler<EpollChannelOutboundBuffer> RECYCLER = new Recycler<EpollChannelOutboundBuffer>() {
        @Override
        protected EpollChannelOutboundBuffer newObject(Handle<EpollChannelOutboundBuffer> handle) {
            return new EpollChannelOutboundBuffer(handle);
        }
    };

    /**
     * Get a new instance of this {@link EpollChannelOutboundBuffer} and attach it the given {@link EpollSocketChannel}
     */
    static EpollChannelOutboundBuffer newInstance(EpollSocketChannel channel) {
        EpollChannelOutboundBuffer buffer = RECYCLER.get();
        buffer.channel = channel;
        return buffer;
    }

    private EpollChannelOutboundBuffer(Recycler.Handle<? extends ChannelOutboundBuffer> handle) {
        super(handle);
    }

    /**
     * Check if the message is a {@link ByteBuf} and if so if it has a memoryAddress. If not it will convert this
     * {@link ByteBuf} to be able to operate on the memoryAddress directly for maximal performance.
     */
    @Override
    protected Object beforeAdd(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.hasMemoryAddress()) {
                return copyToDirectByteBuf(buf);
            }
        }
        return msg;
    }

    /**
     * Returns a {@link IovArray} if the currently pending messages.
     * <p>
     * Note that the returned {@link IovArray} is reused and thus should not escape
     * {@link io.netty.channel.AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     */
    IovArray iovArray() {
        IovArray array = IovArray.get();
        final Entry[] buffer = entries();
        final int mask = entryMask();
        int unflushed = unflushed();
        int flushed = flushed();
        Object m;

        while (flushed != unflushed && (m = buffer[flushed].msg()) != null) {
            if (!(m instanceof ByteBuf)) {
                // Just break out of the loop as we can still use gathering writes for the buffers that we
                // found by now.
                break;
            }

            Entry entry = buffer[flushed];

            // Check if the entry was cancelled. if so we just skip it.
            if (!entry.isCancelled()) {
                ByteBuf buf = (ByteBuf) m;
                if (!array.add(buf)) {
                    // Can not hold more data so break here.
                    // We will handle this on the next write loop.
                    break;
                }
            }

            flushed = flushed + 1 & mask;
        }
        return array;
    }
}
