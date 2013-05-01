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
package io.netty.handler.codec;

import io.netty.buffer.BufType;
import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultMessageBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;

final class OutputMessageBuf extends DefaultMessageBuf<Object> {

    private static final ThreadLocal<OutputMessageBuf> output =
            new ThreadLocal<OutputMessageBuf>() {
                @Override
                protected OutputMessageBuf initialValue() {
                    return new OutputMessageBuf();
                }

                @Override
                public OutputMessageBuf get() {
                    OutputMessageBuf buf = super.get();
                    assert buf.isEmpty();
                    return buf;
                }
            };

    static OutputMessageBuf get() {
        return output.get();
    }

    private int byteBufCnt;

    private OutputMessageBuf() {
        super(2);
    }

    @Override
    public boolean offer(Object e) {
        boolean added =  super.offer(e);
        if (added) {
            if (e instanceof ByteBuf) {
                byteBufCnt ++;
            }
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);
        if (removed) {
            if (o instanceof ByteBuf) {
                byteBufCnt --;
            }
        }
        return removed;
    }

    @Override
    public Object poll() {
        Object o = super.poll();
        if (o == null) {
            return o;
        }
        if (o instanceof ByteBuf) {
            byteBufCnt --;
        }
        return o;
    }

    @Override
    public void clear() {
        super.clear();
        byteBufCnt = 0;
    }

    public boolean drainToNextInbound(ChannelHandlerContext ctx) {
        final int size = size();
        if (size == 0) {
            return false;
        }

        final int byteBufCnt = this.byteBufCnt;
        if (byteBufCnt == 0 || ctx.nextInboundBufferType() != BufType.BYTE) {
            return drainTo(ctx.nextInboundMessageBuffer()) > 0;
        }

        final ByteBuf nextByteBuf = ctx.nextInboundByteBuffer();
        if (byteBufCnt == size) {
            // Contains only ByteBufs
            for (Object o = poll();;) {
                writeAndRelease(nextByteBuf, (ByteBuf) o);
                if ((o = poll()) == null) {
                    break;
                }
            }
        } else {
            // Contains both ByteBufs and non-ByteBufs (0 < byteBufCnt < size())
            final MessageBuf<Object> nextMsgBuf = ctx.nextInboundMessageBuffer();
            for (Object o = poll();;) {
                if (o instanceof ByteBuf) {
                    writeAndRelease(nextByteBuf, (ByteBuf) o);
                } else {
                    nextMsgBuf.add(o);
                }

                if ((o = poll()) == null) {
                    break;
                }
            }
        }

        return true;
    }

    public boolean drainToNextOutbound(ChannelHandlerContext ctx) {
        final int size = size();
        if (size == 0) {
            return false;
        }

        final int byteBufCnt = this.byteBufCnt;
        if (byteBufCnt == 0 || ctx.nextOutboundBufferType() != BufType.BYTE) {
            return drainTo(ctx.nextOutboundMessageBuffer()) > 0;
        }

        final ByteBuf nextByteBuf = ctx.nextOutboundByteBuffer();
        if (byteBufCnt == size) {
            // Contains only ByteBufs
            for (Object o = poll();;) {
                writeAndRelease(nextByteBuf, (ByteBuf) o);
                if ((o = poll()) == null) {
                    break;
                }
            }
        } else {
            // Contains both ByteBufs and non-ByteBufs (0 < byteBufCnt < size())
            final MessageBuf<Object> nextMsgBuf = ctx.nextOutboundMessageBuffer();
            for (Object o = poll();;) {
                if (o instanceof ByteBuf) {
                    writeAndRelease(nextByteBuf, (ByteBuf) o);
                } else {
                    nextMsgBuf.add(o);
                }

                if ((o = poll()) == null) {
                    break;
                }
            }
        }

        return true;
    }

    private static void writeAndRelease(ByteBuf dst, ByteBuf src) {
        try {
            dst.writeBytes(src, src.readerIndex(), src.readableBytes());
        } finally {
            BufUtil.release(src);
        }
    }
}
