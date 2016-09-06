/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelProgressivePromise;
import io.netty.util.ReferenceCountUtil;

/**
 * {@link MessageToByteEncoder} which encodes message in a stream-like fashion from one message to an
 * {@link ByteBuf}. The difference to {@link MessageToByteEncoder} is that this implementation will buffer, and
 * so reuse the same {@link ByteBuf} until {@link #flush(ChannelHandlerContext)} is called.
 *
 * This can give quite some performance win as allocations may be less and less {@link ByteBuf} may need to be written
 * to the underlying transport.
 *
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
public abstract class MessageToBufferedByteEncoder<I> extends MessageToByteEncoder<I> implements ChannelInboundHandler {

    private ByteBuf currentBuffer;
    private AggregatedChannelProgressivePromise aggregatedPromise;

    /**
     * @see {@link MessageToByteEncoder#MessageToByteEncoder()}
     */
    protected MessageToBufferedByteEncoder() {
        CodecUtil.ensureNotSharable(this);
    }

    /**
     * @see {@link MessageToByteEncoder#MessageToByteEncoder(Class)}
     */
    protected MessageToBufferedByteEncoder(Class<? extends I> outboundMessageType) {
        super(outboundMessageType);
        CodecUtil.ensureNotSharable(this);
    }

    /**
     * @see {@link MessageToByteEncoder#MessageToByteEncoder(boolean)}
     */
    protected MessageToBufferedByteEncoder(boolean preferDirect) {
        super(preferDirect);
        CodecUtil.ensureNotSharable(this);
    }

    /**
     * @see {@link MessageToByteEncoder#MessageToByteEncoder(Class, boolean)}
     */
    protected MessageToBufferedByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect) {
        super(outboundMessageType, preferDirect);
        CodecUtil.ensureNotSharable(this);
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) {
        ByteBuf buffer = currentBuffer;
        final ChannelOutboundBuffer outboundBuffer = ctx.channel().unsafe().outboundBuffer();
        final int writerIndex;
        final int readerIndex;
        if (buffer == null) {
            writerIndex = 0;
            readerIndex = 0;
        } else {
            writerIndex = buffer.writerIndex();
            readerIndex = buffer.readerIndex();
        }
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                if (buffer == null) {
                    buffer = currentBuffer = allocateBuffer(ctx, cast, preferDirect);
                    assert aggregatedPromise == null;
                    aggregatedPromise = new AggregatedChannelProgressivePromise(ctx);
                }
                try {
                    encode(ctx, cast, buffer);
                } finally {
                    ReferenceCountUtil.release(cast);
                }
                int readableBytes = buffer.readableBytes();
                if (outboundBuffer != null) {
                    int readable = readableBytes - writerIndex - readerIndex;
                    if (readable < 0) {
                        throw new IllegalStateException("readableBytes are less then before calling encode(...)");
                    }
                    outboundBuffer.incrementPendingOutboundBytes(readable);
                    aggregatedPromise.add(readableBytes, promise);
                } else {
                    aggregatedPromise.add(readableBytes, promise);
                    writeBuffered(ctx, buffer, outboundBuffer);
                }
            } else {
                if (buffer != null) {
                    // First write the buffered data and then write the msg.
                    writeBuffered(ctx, buffer, outboundBuffer);
                    ctx.write(msg, promise);
                }
            }
        } catch (Throwable e) {
            if (buffer != null) {
                // Restore the old indices.
                buffer.setIndex(readerIndex, writerIndex);
            }
            final EncoderException exception = e instanceof EncoderException ?
                    (EncoderException) e : new EncoderException(e);
            throw exception;
        }
    }

    @Override
    public final void flush(ChannelHandlerContext ctx) {
        flush(ctx, true);
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        flush(ctx, false);
        handlerRemoved0(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // This will cause to have everything failed that is buffered in the handler.
        flush(ctx, true);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    /**
     * Gets called after the {@link MessageToBufferedByteEncoder} was removed from the actual context and it
     * doesn't handle events anymore.
     */
    protected void handlerRemoved0(@SuppressWarnings("unused") ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    private void flush(ChannelHandlerContext ctx, boolean alwaysFlush) {
        ByteBuf buffer = currentBuffer;
        if (buffer != null) {
            writeBuffered(ctx, buffer, ctx.channel().unsafe().outboundBuffer());
            ctx.flush();
        } else if (alwaysFlush) {
            ctx.flush();
        }
    }

    private void writeBuffered(ChannelHandlerContext ctx, ByteBuf buffer, ChannelOutboundBuffer outboundBuffer) {
        if (outboundBuffer != null) {
            outboundBuffer.decrementPendingOutboundBytes(buffer.readableBytes());
        }
        ctx.write(buffer, aggregatedPromise);
        aggregatedPromise = null;
        currentBuffer = null;
    }

    // Extend DefaultChannelProgressivePromise and implement ChannelProgressiveFutureListener to safe object allocation.
    private static final class AggregatedChannelProgressivePromise extends DefaultChannelProgressivePromise
            implements ChannelProgressiveFutureListener {
        private PendingChannelPromise head;
        private PendingChannelPromise tail;

        AggregatedChannelProgressivePromise(ChannelHandlerContext ctx) {
             super(ctx.channel(), ctx.executor());
             addListener(this);
        }

        void add(int bytes, ChannelPromise promise) {
            if (head == null) {
                assert tail == null;
                head = tail = new PendingChannelPromise(bytes, promise);
            } else {
                PendingChannelPromise pending = new PendingChannelPromise(bytes, promise);
                tail.next = pending;
                tail = pending;
            }
        }

        @Override
        public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception {
            if (future.cause() != null) {
                // In this case operationComplete(...) will just fail all promises.
                return;
            }
            notifySuccess((int) progress);
        }

        @Override
        public void operationComplete(ChannelProgressiveFuture future) throws Exception {
            Throwable cause = future.cause();
            if (cause == null) {
                notifySuccess(Integer.MAX_VALUE);
            } else {
                // Fail all pending promises.
                while (head != null) {
                    head.fail(cause);
                    head = head.next;
                }
                tail = null;
            }
        }

        private void notifySuccess(int amount) {
            while (head != null) {
                if (head.success(amount)) {
                    head = head.next;
                } else {
                    return;
                }
            }
            tail = null;
        }

        private static final class PendingChannelPromise {
            private final int bytes;
            private final ChannelPromise promise;
            private PendingChannelPromise next;

            PendingChannelPromise(int bytes, ChannelPromise promise) {
                this.bytes = bytes;
                this.promise = promise;
            }

            boolean success(int amount) {
                if (amount >= bytes) {
                    promise.trySuccess();
                    return true;
                }
                return false;
            }

            void fail(Throwable cause) {
                promise.tryFailure(cause);
            }
        }
    }
}
