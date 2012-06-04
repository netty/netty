/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public final class ChannelBufferHolder<E> {

    private final ChannelHandlerContext ctx;
    /** 0 - not a bypass, 1 - inbound bypass, 2 - outbound bypass */
    private final int bypassDirection;
    private final Queue<E> msgBuf;
    private final ChannelBuffer byteBuf;

    ChannelBufferHolder(ChannelHandlerContext ctx, boolean inbound) {
        if (ctx == null) {
            throw new NullPointerException("ctx");
        }
        this.ctx = ctx;
        bypassDirection = inbound? 1 : 2;
        msgBuf = null;
        byteBuf = null;
    }

    ChannelBufferHolder(Queue<E> msgBuf) {
        if (msgBuf == null) {
            throw new NullPointerException("msgBuf");
        }
        ctx = null;
        bypassDirection = 0;
        this.msgBuf = msgBuf;
        byteBuf = null;

    }

    ChannelBufferHolder(ChannelBuffer byteBuf) {
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf");
        }
        ctx = null;
        bypassDirection = 0;
        msgBuf = null;
        this.byteBuf = byteBuf;
    }

    public boolean isBypass() {
        return bypassDirection != 0;
    }

    public boolean hasMessageBuffer() {
        switch (bypassDirection) {
        case 0:
            return msgBuf != null;
        case 1:
            return ctx.hasNextInboundMessageBuffer();
        case 2:
            return ctx.hasNextOutboundMessageBuffer();
        default:
            throw new Error();
        }
    }

    public boolean hasByteBuffer() {
        switch (bypassDirection) {
        case 0:
            return byteBuf != null;
        case 1:
            return ctx.hasNextInboundByteBuffer();
        case 2:
            return ctx.hasNextOutboundByteBuffer();
        default:
            throw new Error();
        }
    }

    public Queue<E> messageBuffer() {
        switch (bypassDirection) {
        case 0:
            if (msgBuf == null) {
                throw new NoSuchBufferException();
            }
            return msgBuf;
        case 1:
            return (Queue<E>) ctx.nextInboundMessageBuffer();
        case 2:
            return (Queue<E>) ctx.nextOutboundMessageBuffer();
        default:
            throw new Error();
        }
    }

    public ChannelBuffer byteBuffer() {
        switch (bypassDirection) {
        case 0:
            if (byteBuf == null) {
                throw new NoSuchBufferException();
            }
            return byteBuf;
        case 1:
            return ctx.nextInboundByteBuffer();
        case 2:
            return ctx.nextOutboundByteBuffer();
        default:
            throw new Error();
        }
    }

    @Override
    public String toString() {
        switch (bypassDirection) {
        case 0:
            if (msgBuf != null) {
                return "MessageBuffer(" + msgBuf.size() + ')';
            } else {
                return byteBuf.toString();
            }
        case 1:
            return "InboundBypassBuffer";
        case 2:
            return "OutboundBypassBuffer";
        default:
            throw new Error();
        }
    }

    public int size() {
        switch (bypassDirection) {
        case 0:
            if (msgBuf != null) {
                return msgBuf.size();
            } else {
                return byteBuf.readableBytes();
            }
        case 1:
        case 2:
            throw new UnsupportedOperationException();
        default:
            throw new Error();
        }
    }

    public boolean isEmpty() {
        switch (bypassDirection) {
        case 0:
            if (msgBuf != null) {
                return msgBuf.isEmpty();
            } else {
                return !byteBuf.readable();
            }
        case 1:
        case 2:
            throw new UnsupportedOperationException();
        default:
            throw new Error();
        }
    }
}
