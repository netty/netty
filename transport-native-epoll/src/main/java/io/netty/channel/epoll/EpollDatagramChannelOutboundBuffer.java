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
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Recycler;

final class EpollDatagramChannelOutboundBuffer  extends ChannelOutboundBuffer {
    private static final Recycler<EpollDatagramChannelOutboundBuffer> RECYCLER =
            new Recycler<EpollDatagramChannelOutboundBuffer>() {
        @Override
        protected EpollDatagramChannelOutboundBuffer newObject(Handle<EpollDatagramChannelOutboundBuffer> handle) {
            return new EpollDatagramChannelOutboundBuffer(handle);
        }
    };

    static EpollDatagramChannelOutboundBuffer newInstance(EpollDatagramChannel channel) {
        EpollDatagramChannelOutboundBuffer buffer = RECYCLER.get();
        buffer.channel = channel;
        return buffer;
    }

    private EpollDatagramChannelOutboundBuffer(Recycler.Handle<EpollDatagramChannelOutboundBuffer> handle) {
        super(handle);
    }

    @Override
    protected Object beforeAdd(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf content = packet.content();
            if (isCopyNeeded(content)) {
                ByteBuf direct = copyToDirectByteBuf(content);
                return new DatagramPacket(direct, packet.recipient(), packet.sender());
            }
        } else if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (isCopyNeeded(buf)) {
                msg = copyToDirectByteBuf((ByteBuf) msg);
            }
        }
        return msg;
    }

    private static boolean isCopyNeeded(ByteBuf content) {
        return !content.hasMemoryAddress() || content.nioBufferCount() != 1;
    }
}
