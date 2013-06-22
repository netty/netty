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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import io.netty.util.internal.StringUtil;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, {@link MessageList} out)
 *                 throws {@link Exception} {
 *             out.add(in.readBytes(in.readableBytes()));
 *         }
 *     }
 * </pre>
 */
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    protected ByteBuf cumulation;
    private boolean singleDecode;
    private boolean decodeWasNull;
    private MessageList<Object> out;

    /**
     * If set then only one message is decoded on each {@link #messageReceived(ChannelHandlerContext, MessageList)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #messageReceived(ChannelHandlerContext, MessageList)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ByteBuf internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buf = internalBuffer();
        if (buf.isReadable()) {
            if (out == null) {
                ctx.fireMessageReceived(buf);
            } else {
                out.add(buf.copy());
            }
            buf.clear();
        }
        handlerRemoved0(ctx);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        out = MessageList.newInstance();
        try {
            int size = msgs.size();
            for (int i = 0; i < size; i ++) {
                Object m = msgs.get(i);
                // handler was removed in the loop so now copy over all remaining messages
                if (ctx.isRemoved()) {
                    out.add(msgs, i, size - i);
                    return;
                }
                if (m instanceof ByteBuf) {
                    ByteBuf data = (ByteBuf) m;
                    if (cumulation == null) {
                        cumulation = data;
                        try {
                            callDecode(ctx, data, out);
                        } finally {
                            if (!data.isReadable()) {
                                cumulation = null;
                                data.release();
                            }
                        }
                    } else {
                        try {
                            if (cumulation.writerIndex() > cumulation.maxCapacity() - data.readableBytes()) {
                                ByteBuf oldCumulation = cumulation;
                                cumulation = ctx.alloc().buffer(oldCumulation.readableBytes() + data.readableBytes());
                                cumulation.writeBytes(oldCumulation);
                                oldCumulation.release();
                            }
                            cumulation.writeBytes(data);
                            callDecode(ctx, cumulation, out);
                        } finally {
                            if (!cumulation.isReadable()) {
                                cumulation.release();
                                cumulation = null;
                            } else {
                                cumulation.discardSomeReadBytes();
                            }
                            data.release();
                        }
                    }
                } else {
                    out.add(m);
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new DecoderException(t);
        } finally {
            // release the cumulation if the handler was removed while messages are processed
            if (ctx.isRemoved()) {
                if (cumulation != null) {
                    cumulation.release();
                    cumulation = null;
                }
            }
            MessageList<Object> out = this.out;
            this.out = null;
            if (out.isEmpty()) {
                decodeWasNull = true;
            }
            msgs.recycle();
            ctx.fireMessageReceived(out);
        }
    }

    @Override
    public void channelReadSuspended(ChannelHandlerContext ctx) throws Exception {
        if (decodeWasNull) {
            decodeWasNull = false;
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        super.channelReadSuspended(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        MessageList<Object> out = MessageList.newInstance();
        try {
            if (cumulation != null) {
                callDecode(ctx, cumulation, out);
                decodeLast(ctx, cumulation, out);
            } else {
                decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            if (cumulation != null) {
                cumulation.release();
                cumulation = null;
            }

            ctx.fireMessageReceived(out);
            ctx.fireChannelInactive();
        }
    }

    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, MessageList<Object> out) {
        try {
            while (in.isReadable()) {
                int outSize = out.size();
                int oldInputLength = in.readableBytes();
                decode(ctx, in, out);
                if (outSize == out.size()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                            ".decode() did not read anything but decoded a message.");
                }

                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (CodecException e) {
            throw e;
        } catch (Throwable cause) {
            throw new DecoderException(cause);
        }
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read anymore, till nothing was read from the input {@link ByteBuf} or till
     * this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link MessageList} to which decoded messages should be added

     * @throws Exception    is thrown if an error accour
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, MessageList<Object> out) throws Exception;

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, MessageList)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, MessageList<Object> out) throws Exception {
        decode(ctx, in, out);
    }
}
