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
package io.netty.channel.socket.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ThreadLocalPooledDirectByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Recycler;

/**
 * Special {@link ChannelOutboundBuffer} for {@link NioDatagramChannel} implementations.
 */
final class NioDatagramChannelOutboundBuffer extends ChannelOutboundBuffer {
    private static final Recycler<NioDatagramChannelOutboundBuffer> RECYCLER =
            new Recycler<NioDatagramChannelOutboundBuffer>() {
                @Override
                protected NioDatagramChannelOutboundBuffer newObject(Handle<NioDatagramChannelOutboundBuffer> handle) {
                    return new NioDatagramChannelOutboundBuffer(handle);
                }
            };

    /**
     * Get a new instance of this {@link NioSocketChannelOutboundBuffer} and attach it the given
     * {@link .NioDatagramChannel}.
     */
    static NioDatagramChannelOutboundBuffer newInstance(NioDatagramChannel channel) {
        NioDatagramChannelOutboundBuffer buffer = RECYCLER.get();
        buffer.channel = channel;
        return buffer;
    }

    private NioDatagramChannelOutboundBuffer(Recycler.Handle<NioDatagramChannelOutboundBuffer> handle) {
        super(handle);
    }

    /**
     * Convert all non direct {@link ByteBuf} to direct {@link ByteBuf}'s. This is done as the JDK implementation
     * will do the conversation itself and we can do a better job here.
     */
    @Override
    protected Object beforeAdd(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf content = packet.content();
            if (!content.isDirect() || content.nioBufferCount() != 1) {
                ByteBufAllocator alloc = channel.alloc();
                int readable = content.readableBytes();
                if (alloc.isDirectBufferPooled()) {
                    ByteBuf direct = alloc.directBuffer(readable);
                    direct.writeBytes(content, content.readerIndex(), readable);
                    DatagramPacket newPacket = new DatagramPacket(direct, packet.recipient(), packet.sender());
                    safeRelease(msg);
                    return newPacket;
                } else if (ThreadLocalPooledDirectByteBuf.threadLocalDirectBufferSize > 0) {
                    ByteBuf direct = ThreadLocalPooledDirectByteBuf.newInstance();
                    direct.writeBytes(content, content.readerIndex(), readable);
                    DatagramPacket newPacket = new DatagramPacket(direct, packet.recipient(), packet.sender());
                    safeRelease(msg);
                    return newPacket;
                }
            }
        }
        return msg;
    }
}
