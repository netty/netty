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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFlushPromiseNotifier;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;


/**
 * {@link MessageToByteEncoder} which encodes message in a stream-like fashion from one message to an
 * {@link ByteBuf}. The difference between {@link MessageToBufferedByteEncoder} and {@link MessageToByteEncoder} is
 * that {@link MessageToBufferedByteEncoder}  will try to write multiple writes into one {@link ByteBuf} and only
 * write it to the next {@link ChannelOutboundHandler} in the {@link ChannelPipeline} if either:
 * <ul>
 *   <li>{@link #writeBufferedData(io.netty.channel.ChannelHandlerContext)} is called</li>
 *   <li>{@link Channel#flush()} is called</li>
 *   <li>{@link Channel#close()} is called</li>
 *   <li>{@link Channel#disconnect()} is called</li>
 * </ul>
 *
 * You should use this {@link MessageToBufferedByteEncoder} if you expect to either write messages in multiple parts
 * or if the used protocol supports PIPELINING.
 *
 * Example implementation which encodes {@link Integer}s to a {@link ByteBuf}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToBufferedByteEncoder}&lt;{@link Integer}&gt; {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} msg, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 */
public abstract class MessageToBufferedByteEncoder<I> extends MessageToByteEncoder<I> {
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private final ChannelFlushPromiseNotifier notifier  = new ChannelFlushPromiseNotifier();
    private final int bufferSize;
    private ByteBuf buffer;

    /**
     * @see {@link #MessageToBufferedByteEncoder(int)} with {@code 1024} as parameter.
     */
    protected MessageToBufferedByteEncoder() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Create a new instance
     *
     * @param bufferSize            The size of the buffer when it is allocated.
     */
    protected MessageToBufferedByteEncoder(int bufferSize) {
        checkBufferSize(bufferSize);
        checkSharable();
        this.bufferSize = bufferSize;
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The tpye of messages to match
     * @param bufferSize            The size of the buffer when it is allocated.
     */
    protected MessageToBufferedByteEncoder(Class<? extends I> outboundMessageType, int bufferSize) {
        super(outboundMessageType);
        checkBufferSize(bufferSize);
        checkSharable();
        this.bufferSize = bufferSize;
    }

    /**
     * Create a new instance
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     * @param bufferSize            The size of the buffer when it is allocated.
     */
    protected MessageToBufferedByteEncoder(boolean preferDirect, int bufferSize) {
        super(preferDirect);
        checkBufferSize(bufferSize);
        checkSharable();
        this.bufferSize = bufferSize;
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The tpye of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     * @param bufferSize            The size of the buffer when it is allocated.
     */
    protected MessageToBufferedByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect, int bufferSize) {
        super(outboundMessageType, preferDirect);
        checkBufferSize(bufferSize);
        checkSharable();
        this.bufferSize = bufferSize;
    }

    private static void checkBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(
                    "bufferSize must be a positive integer: " +
                            bufferSize);
        }
    }

    private void checkSharable() {
        if (getClass().isAnnotationPresent(Sharable.class)) {
            throw new IllegalStateException("@Sharable annotation is not allowed");
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                if (buffer == null) {
                    buffer = newBuffer(ctx, msg, preferDirect, bufferSize);
                }
                int writerIndex = buffer.writerIndex();
                try {
                    encode(ctx, cast, buffer);
                    // check for null as it may be flushed in the encode method
                    if (buffer != null) {
                        // add to notifier so the promise will be notified later once we wrote everything
                        notifier.add(promise, buffer.writerIndex() - writerIndex);
                    }
                } catch (Throwable e) {
                    // check for null as it may be flushed in the encode method
                    if (buffer != null) {
                        // something went wrong when encoding so reset the writerIndex to the old position
                        buffer.writerIndex(writerIndex);
                    }
                    throw e;
                } finally {
                    ReferenceCountUtil.release(cast);
                }
            } else {
                // write buffer data now so we not write stuff out of order
                writeBufferedData(ctx);
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        }
    }

    /**
     * Write all buffered data now
     */
    protected final void writeBufferedData(ChannelHandlerContext ctx) {
        if (buffer == null) {
            return;
        }
        ByteBuf buf = buffer;
        buffer = null;
        final int length = buf.readableBytes();
        ctx.write(buf).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                notifier.increaseWriteCounter(length);
                if (future.isSuccess()) {
                    notifier.notifyFlushFutures();
                } else {
                    notifier.notifyFlushFutures(future.cause());
                }
            }
        });
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        writeBufferedData(ctx);
        super.close(ctx, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        writeBufferedData(ctx);
        super.disconnect(ctx, promise);
    }

    /**
     * Called when a new {@link io.netty.buffer.ByteBuf} is allocated to buffer the data.
     *
     * @param ctx           the {@link ChannelHandlerContext} bound
     * @param msg           the message which should be encoded
     * @param preferDirect  {@code true} if direct {@link ByteBuf}'s should be prefered
     * @param preferSize    the size which was configured as prefered buffer size
     * @return buffer       the created {@link ByteBuf}
     */
    protected ByteBuf newBuffer(ChannelHandlerContext ctx, @SuppressWarnings("unused") Object msg,
                                boolean preferDirect, int preferSize) {
        if (preferDirect) {
            return ctx.alloc().ioBuffer(preferSize);
        } else {
            return ctx.alloc().heapBuffer(preferSize);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        // The user requested a flush so write buffered data in the pipeline and then flush
        writeBufferedData(ctx);
        super.flush(ctx);
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        writeBufferedData(ctx);
        handlerRemoved0(ctx);
        super.handlerRemoved(ctx);
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    protected void handlerRemoved0(@SuppressWarnings("unused")ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}
