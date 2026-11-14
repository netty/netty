/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for {@link ByteBuf} encoders that may keep (parts of) the data of a written message in an internal buffer
 * until enough data arrived to produce a full block, or until the encoder is flushed / closed.
 * <p>
 * {@link MessageToByteEncoder#write(ChannelHandlerContext, Object, ChannelPromise)} assumes a 1:1 relation between a
 * message and the produced {@link ByteBuf} and so completes the {@link ChannelPromise} of a message once the (possibly
 * empty or only partially filled) {@link ByteBuf} was written, even if the remaining data is still buffered. This class
 * instead holds back the {@link ChannelPromise} of such a message until the {@link ByteBuf} which contains the end of
 * its data was written.
 * <p>
 * All methods must be called from the {@link io.netty.channel.EventLoop} of the {@link ChannelHandlerContext}.
 */
abstract class BufferingEncoder extends MessageToByteEncoder<ByteBuf> {

    /**
     * The {@link ChannelPromise}s of the messages whose data is (partly) still held in the internal buffer, in write
     * order. Lazily created as most of the time there is nothing pending.
     */
    private List<ChannelPromise> pending;

    /**
     * Makes the bytes that are held back by the encoder count towards the writability of the
     * {@link io.netty.channel.Channel}, as they will be written eventually. It holds one entry (a view as returned by
     * {@link #bufferedView(int)}) per message that has data buffered. Only used for accounting, the promises are
     * tracked in {@link #pending}. Lazily created as most of the time there is nothing buffered.
     */
    private PendingWriteQueue bufferedBytesMeter;

    BufferingEncoder() {
        super(ByteBuf.class);
    }

    /**
     * Returns the number of bytes (or an estimate of it) that are left in the internal buffer, which means they were
     * not part of the {@link ByteBuf} passed to {@link #encode(ChannelHandlerContext, ByteBuf, ByteBuf)} as
     * {@code out}.
     * <p>
     * This is called after {@code encode(...)} so it tells if the tail of the just encoded message is still buffered.
     * It is guaranteed that the data of all messages written before is buffered in front of that tail, so if
     * {@code encode(...)} produced output it contained all of the data of the earlier messages.
     */
    abstract long bufferedBytes();

    /**
     * Returns a {@link ByteBuf} with the given number of readable bytes, which must not be more than
     * {@link #bufferedBytes()}. It is only used to account for the buffered bytes in the writability of the
     * {@link io.netty.channel.Channel}, so it never has to be read or written and should not allocate any memory
     * but just wrap an array which is already in use.
     */
    abstract ByteBuf bufferedView(int length);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        try {
            if (acceptOutboundMessage(msg)) {
                ByteBuf in = (ByteBuf) msg;
                int length = in.readableBytes();
                buf = allocateBuffer(ctx, in, isPreferDirect());
                try {
                    encode(ctx, in, buf);
                } finally {
                    ReferenceCountUtil.release(in);
                }

                ByteBuf out = buf;
                buf = null;
                writeEncoded(ctx, out, length, promise);
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    private void writeEncoded(ChannelHandlerContext ctx, ByteBuf out, int length, ChannelPromise promise) {
        long buffered = bufferedBytes();
        if (buffered <= 0) {
            // The whole message (and everything that was pending before) is part of out.
            if (!out.isReadable()) {
                out.release();
                out = Unpooled.EMPTY_BUFFER;
            }
            writeBuffered(ctx, out, promise, false);
            return;
        }

        // The tail of the message is still buffered, so its promise can only be completed once that tail was written.
        int previousEntries = bufferedBytesEntries();
        boolean written = out.isReadable();
        if (written) {
            // out contains the data of all pending messages and the head of this message. A failure to write it
            // fails the message, but its success does not complete it yet.
            ctx.write(out, newWritePromise(ctx, takePending(), promise));
        } else {
            out.release();
        }
        addPending(promise);

        // Only account for the tail of this message, which is all that is left if something was written. Add it before
        // releasing the entries it replaces so the writability is not toggled needlessly.
        addBufferedBytes(ctx, (int) Math.min(buffered, length));
        releaseBufferedBytes(written ? previousEntries : 0);
    }

    /**
     * Writes {@code buf}, which has to contain the end of the data of everything that is buffered. It completes
     * {@code promise} and all the pending promises once the write completes.
     */
    final void writeBuffered(ChannelHandlerContext ctx, ByteBuf buf, ChannelPromise promise, boolean flush) {
        ChannelPromise writePromise = withPending(ctx, promise);
        if (flush) {
            ctx.writeAndFlush(buf, writePromise);
        } else {
            ctx.write(buf, writePromise);
        }
        releaseBufferedBytes(bufferedBytesEntries());
    }

    /**
     * Returns a {@link ChannelPromise} to use for writing a {@link ByteBuf} which contains the end of the data of
     * everything that is pending. It completes {@code promise} and all the pending promises once the write completes.
     */
    private ChannelPromise withPending(ChannelHandlerContext ctx, ChannelPromise promise) {
        List<ChannelPromise> pending = takePending();
        if (pending == null) {
            return promise;
        }
        pending.add(promise);
        return newWritePromise(ctx, pending, null);
    }

    private ChannelPromise newWritePromise(ChannelHandlerContext ctx, List<ChannelPromise> completed,
                                           ChannelPromise failedOnly) {
        ChannelPromise writePromise = ctx.newPromise();
        writePromise.addListener(new WriteListener(completed, failedOnly));
        return writePromise;
    }

    private List<ChannelPromise> takePending() {
        List<ChannelPromise> pending = this.pending;
        this.pending = null;
        return pending;
    }

    private void addPending(ChannelPromise promise) {
        if (pending == null) {
            pending = new ArrayList<ChannelPromise>(4);
        }
        pending.add(promise);
    }

    private int bufferedBytesEntries() {
        return bufferedBytesMeter == null ? 0 : bufferedBytesMeter.size();
    }

    private void addBufferedBytes(ChannelHandlerContext ctx, int bytes) {
        if (bufferedBytesMeter == null) {
            bufferedBytesMeter = new PendingWriteQueue(ctx);
        }
        bufferedBytesMeter.add(bufferedView(bytes), ctx.voidPromise());
    }

    /**
     * Stops accounting for the oldest {@code entries} entries as the bytes they stand for were written.
     */
    private void releaseBufferedBytes(int entries) {
        for (int i = 0; i < entries; i++) {
            bufferedBytesMeter.remove();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        List<ChannelPromise> pending = takePending();
        if (pending != null) {
            // The buffered data is discarded by the sub-class so we will never be able to write it.
            Throwable cause = new EncoderException("encoder removed while data was still buffered");
            for (ChannelPromise promise : pending) {
                promise.tryFailure(cause);
            }
        }
        if (bufferedBytesMeter != null) {
            bufferedBytesMeter.removeAndFailAll(new EncoderException("encoder removed while data was still buffered"));
            bufferedBytesMeter = null;
        }
        super.handlerRemoved(ctx);
    }

    private static final class WriteListener implements ChannelFutureListener {
        private final List<ChannelPromise> completed;
        private final ChannelPromise failedOnly;

        WriteListener(List<ChannelPromise> completed, ChannelPromise failedOnly) {
            this.completed = completed;
            this.failedOnly = failedOnly;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                if (completed != null) {
                    for (ChannelPromise promise : completed) {
                        promise.trySuccess();
                    }
                }
                return;
            }
            Throwable cause = future.cause();
            if (completed != null) {
                for (ChannelPromise promise : completed) {
                    promise.tryFailure(cause);
                }
            }
            if (failedOnly != null) {
                failedOnly.tryFailure(cause);
            }
        }
    }
}
