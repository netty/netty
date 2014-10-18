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
import io.netty.channel.ChannelFlushPromiseNotifier;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.TypeParameterMatcher;


/**
 * {@link ChannelOutboundHandlerAdapter} which encodes message in a stream-like fashion from one message to an
 * {@link ByteBuf}.
 *
 *
 * Example implementation which encodes {@link Integer}s to a {@link ByteBuf}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToByteEncoder}&lt;{@link Integer}&gt; {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} msg, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 *
 * By default each encoded message is written directly after {@link #encode(ChannelHandlerContext, Object, ByteBuf)}
 * returns. You can change this by specify a {@code flushTreshold} via the {@link #MessageToByteEncoder(boolean, int)}
 * or {@link #MessageToByteEncoder(Class, boolean, int)} constructor to only write once the treshold was reached.
 * Be aware that if you use a {@code flushTreshold} &gt 0 it is <strong>not possible</strong> marking your sub-class as
 * {@link Sharable}.
 */
public abstract class MessageToByteEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;
    private final boolean preferDirect;
    private final int flushTreshold;

    // Will be used if flushTreshold > 0
    private final ChannelFlushPromiseNotifier notifier;
    private ByteBuf buffer;

    /**
     * @see {@link #MessageToByteEncoder(boolean)} with {@code true} as boolean parameter.
     */
    protected MessageToByteEncoder() {
        this(true);
    }

    /**
     * @see {@link #MessageToByteEncoder(Class, boolean)} with {@code true} as boolean value.
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected MessageToByteEncoder(boolean preferDirect) {
        this(preferDirect, 0);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     * @param flushTreshold         the maximum size of the buffer before it is written or {@code 0} if it should be
     *                              written after each {@link #encode(ChannelHandlerContext, Object, ByteBuf)} call
     */
    protected MessageToByteEncoder(boolean preferDirect, int flushTreshold) {
        notifier = verifyAndInit(flushTreshold);
        matcher = TypeParameterMatcher.find(this, MessageToByteEncoder.class, "I");
        this.preferDirect = preferDirect;
        this.flushTreshold = flushTreshold;
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   the type of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect) {
       this(outboundMessageType, preferDirect, 0);
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   the type of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     @param flushTreshold           the maximum size of the buffer before it is written or {@code 0} if it should be
                                    written after each {@link #encode(ChannelHandlerContext, Object, ByteBuf)} call
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect, int flushTreshold) {
        notifier = verifyAndInit(flushTreshold);
        matcher = TypeParameterMatcher.get(outboundMessageType);
        this.preferDirect = preferDirect;
        this.flushTreshold = flushTreshold;
    }

    private ChannelFlushPromiseNotifier verifyAndInit(int flushTreshold) {
        if (flushTreshold == 0) {
            return null;
        }
        if (flushTreshold > 0) {
            CodecUtil.ensureNotSharable(this);
            return new ChannelFlushPromiseNotifier(true);
        }
        if (flushTreshold < 0) {
            throw new IllegalArgumentException("flushTreshold: " + flushTreshold + " (expected: >= 0)");
        }
        return null;
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (acceptOutboundMessage(msg)) {
                ByteBuf out = buffer;
                int writerIndex = 0;
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    if (out == null) {
                        // Allocate new buffer
                        out = allocateBuffer(ctx, cast, preferDirect);
                    }
                    writerIndex = out.writerIndex();
                    encode(ctx, cast, out);

                    int readable = out.readableBytes();
                    if (flushTreshold > 0) {
                        int wIndex = out.writerIndex();
                        if (wIndex < writerIndex) {
                            throw new EncoderException(
                                    StringUtil.simpleClassName(getClass()) +
                                            ".encode() did decrease writerIndex of out while flushTreshold > 0");
                        }
                        if (flushTreshold < out.readableBytes()) {
                            if (buffer == null) {
                                // we have nothing buffered so we can just directly write it.
                                ctx.write(out, promise);
                            } else {
                                // null out buffer
                                buffer = null;

                                // flush all bytes now
                                writeBufferedBytes0(ctx, out, promise);
                            }
                        } else {
                            buffer = out;

                            long delta = wIndex - writerIndex;
                            ctx.channel().unsafe().outboundBuffer().incrementPendingOutboundBytes(delta);
                            // add to notifier so the promise will be notified later once we wrote everything
                            notifier.add(promise, delta);
                        }
                    } else {
                        assert buffer == null;
                        if (readable > 0) {
                            ctx.write(out, promise);
                        } else {
                            out.release();
                            out = null;
                            ctx.write(Unpooled.EMPTY_BUFFER, promise);
                        }
                    }
                    // Set to null to prevent release of it.
                    out = null;
                } catch (Throwable cause) {
                    resetWriterIndexAndRethrow(cause, writerIndex);
                } finally {
                    ReferenceCountUtil.release(cast);
                    if (out != null) {
                        out.release();
                    }
                }
            } else {
                if (flushTreshold > 0) {
                    writeBufferedBytes(ctx);
                }
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        }
    }

    private void resetWriterIndexAndRethrow(Throwable e, int writerIndex) {
        ByteBuf buffer = this.buffer;
        if (buffer != null) {
            buffer.writerIndex(writerIndex);
        }
        if (e instanceof EncoderException) {
            throw (EncoderException) e;
        }
        throw new EncoderException(e);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (flushTreshold > 0) {
            writeBufferedBytes(ctx);
        }
        super.close(ctx, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (flushTreshold > 0) {
            writeBufferedBytes(ctx);
        }
        super.disconnect(ctx, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (flushTreshold > 0) {
            writeBufferedBytes(ctx);
        }
        super.flush(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (flushTreshold > 0) {
            writeBufferedBytes(ctx);
        }
        super.handlerRemoved(ctx);
    }

    /**
     * Manually flush the buffered data.
     */
    protected final ChannelFuture writeBufferedBytes(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buffer = this.buffer;
        if (buffer == null) {
            // Nothing buffered yet so just return here.
            return ctx.newSucceededFuture();
        }
        this.buffer = null;
        return writeBufferedBytes0(ctx, buffer, ctx.newPromise());
    }

    private ChannelFuture writeBufferedBytes0(ChannelHandlerContext ctx, ByteBuf buffer, ChannelPromise promise)
            throws Exception {
        assert notifier != null;

        final int size = buffer.readableBytes();
        // Decrement now as we will now trigger the actual write
        ctx.channel().unsafe().outboundBuffer().decrementPendingOutboundBytes(size);

        ChannelFuture future = ctx.write(buffer, promise).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                notifier.increaseWriteCounter(size);
                if (future.isSuccess()) {
                    notifier.notifyPromises();
                } else {
                    notifier.notifyPromises(future.cause());
                }
            }
        });
        bufferedBytesWritten(ctx, size);
        return future;
    }

    /**
     * Callback which is executed once {@link #writeBufferedBytes(ChannelHandlerContext)} was executed and something
     * was written through the {@link ChannelPipeline}.
     *
     * Do nothing by default, sub-classes may override this method.
     *
     * @param ctx       the {@link ChannelHandlerContext} to use
     * @param amount    the number of bytes that were written
     */
    protected void bufferedBytesWritten(@SuppressWarnings("unused") ChannelHandlerContext ctx,
                                        @SuppressWarnings("unused") int amount) {
        // NOOP
    }

    /**
     * Allocate a {@link ByteBuf} which will be used as argument of {@link #encode(ChannelHandlerContext, I, ByteBuf)}.
     * Sub-classes may override this method to returna {@link ByteBuf} with a perfect matching {@code initialCapacity}.
     */
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, @SuppressWarnings("unused") I msg,
                               boolean preferDirect) throws Exception {
        if (preferDirect) {
            return ctx.alloc().ioBuffer();
        } else {
            return ctx.alloc().heapBuffer();
        }
    }

    /**
     * Encode a message into a {@link ByteBuf}. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToByteEncoder} belongs to
     * @param msg           the message to encode
     * @param out           the {@link ByteBuf} into which the encoded message will be written
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;
}
