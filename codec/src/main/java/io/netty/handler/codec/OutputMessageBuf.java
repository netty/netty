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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultMessageBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;

final class OutputMessageBuf extends DefaultMessageBuf<Object> {
    private int byteBufs;
    private int nonByteBufs;

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

    private OutputMessageBuf() {
        super(2);
    }

    @Override
    public boolean offer(Object e) {
        boolean added =  super.offer(e);
        if (added) {
            if (e instanceof ByteBuf) {
                byteBufs++;
            } else {
                nonByteBufs++;
            }
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);

        if (removed) {
            if (o instanceof ByteBuf) {
                byteBufs--;
            } else {
                nonByteBufs--;
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
            byteBufs--;
        } else {
            nonByteBufs--;
        }
        return o;
    }

    @Override
    public void clear() {
        super.clear();
        byteBufs = 0;
        nonByteBufs = 0;
    }

    private boolean containsByteBuf() {
        return byteBufs > 0;
    }

    private boolean containsNonByteBuf() {
       return nonByteBufs > 0;
    }

    public boolean drainToNextInbound(ChannelHandlerContext ctx) {
        if (containsByteBuf() && ctx.nextInboundBufferType() == BufType.BYTE) {
            ByteBuf buf = ctx.nextInboundByteBuffer();
            boolean drained = false;
            if (!containsNonByteBuf()) {
                for (;;) {
                    Object o = poll();
                    if (o == null) {
                        break;
                    }
                    buf.writeBytes((ByteBuf) o);
                    drained = true;
                }
            } else {
                // mixed case
                MessageBuf<Object> msgBuf = ctx.nextInboundMessageBuffer();
                for (;;) {
                    Object o = poll();
                    if (o == null) {
                        break;
                    }
                    if (o instanceof ByteBuf) {
                        buf.writeBytes((ByteBuf) o);
                    } else {
                        msgBuf.add(o);
                    }
                    drained = true;
                }
            }
            return drained;
        } else {
            return drainTo(ctx.nextInboundMessageBuffer()) > 0;
        }
    }

    public boolean drainToNextOutbound(ChannelHandlerContext ctx) {
        if (containsByteBuf() && ctx.nextOutboundBufferType() == BufType.BYTE) {
            ByteBuf buf = ctx.nextOutboundByteBuffer();
            boolean drained = false;
            if (!containsNonByteBuf()) {
                for (;;) {
                    Object o = poll();
                    if (o == null) {
                        break;
                    }
                    buf.writeBytes((ByteBuf) o);
                    drained = true;
                }
            } else {
                // mixed case
                MessageBuf<Object> msgBuf = ctx.nextOutboundMessageBuffer();
                for (;;) {
                    Object o = poll();
                    if (o == null) {
                        break;
                    }
                    if (o instanceof ByteBuf) {
                        buf.writeBytes((ByteBuf) o);
                    } else {
                        msgBuf.add(o);
                    }
                    drained = true;
                }
            }
            return drained;
        } else {
            return drainTo(ctx.nextOutboundMessageBuffer()) > 0;
        }
    }
}
