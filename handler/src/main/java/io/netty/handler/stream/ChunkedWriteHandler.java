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
package io.netty.handler.stream;

import io.netty.buffer.MessageBuf;
import io.netty.buffer.MessageBufs;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ChannelHandler} that adds support for writing a large data stream
 * asynchronously neither spending a lot of memory nor getting
 * {@link java.lang.OutOfMemoryError}.  Large data streaming such as file
 * transfer requires complicated state management in a {@link ChannelHandler}
 * implementation.  {@link ChunkedWriteHandler} manages such complicated states
 * so that you can send a large data stream without difficulties.
 * <p>
 * To use {@link ChunkedWriteHandler} in your application, you have to insert
 * a new {@link ChunkedWriteHandler} instance:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("streamer", <b>new {@link ChunkedWriteHandler}()</b>);
 * p.addLast("handler", new MyHandler());
 * </pre>
 * Once inserted, you can write a {@link ChunkedInput} so that the
 * {@link ChunkedWriteHandler} can pick it up and fetch the content of the
 * stream chunk by chunk and write the fetched chunk downstream:
 * <pre>
 * {@link Channel} ch = ...;
 * ch.write(new {@link ChunkedFile}(new File("video.mkv"));
 * </pre>
 *
 * <h3>Sending a stream which generates a chunk intermittently</h3>
 *
 * Some {@link ChunkedInput} generates a chunk on a certain event or timing.
 * Such {@link ChunkedInput} implementation often returns {@code null} on
 * {@link ChunkedInput#nextChunk()}, resulting in the indefinitely suspended
 * transfer.  To resume the transfer when a new chunk is available, you have to
 * call {@link #resumeTransfer()}.
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.stream.ChunkedInput oneway - - reads from
 */
public class ChunkedWriteHandler
        extends ChannelHandlerAdapter implements ChannelOutboundMessageHandler<Object> {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ChunkedWriteHandler.class);

    private static final int MAX_PENDING_WRITES = 4;

    private final MessageBuf<Object> queue = MessageBufs.buffer();

    private volatile ChannelHandlerContext ctx;
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private Object currentEvent;

    @Override
    public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return queue;
    }

    private boolean isWritable() {
        return pendingWrites.get() < MAX_PENDING_WRITES;
    }

    /**
     * Continues to fetch the chunks from the input.
     */
    public void resumeTransfer() {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            return;
        }
        if (ctx.executor().inEventLoop()) {
            try {
                doFlush(ctx);
            } catch (Exception e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Unexpected exception while sending chunks.", e);
                }
            }
        } else {
            // let the transfer resume on the next event loop round
            ctx.executor().execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        doFlush(ctx);
                    } catch (Exception e) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Unexpected exception while sending chunks.", e);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        queue.add(future);
        if (isWritable() || !ctx.channel().isActive()) {
            doFlush(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
        super.channelInactive(ctx);
    }

    private void discard(final ChannelHandlerContext ctx, final Throwable cause) {

        boolean fireExceptionCaught = false;
        for (;;) {
            Object currentEvent = this.currentEvent;

            if (this.currentEvent == null) {
                currentEvent = queue.poll();
            } else {
                this.currentEvent = null;
            }

            if (currentEvent == null) {
                break;
            }

            if (currentEvent instanceof ChunkedInput) {
                closeInput((ChunkedInput<?>) currentEvent);
            } else if (currentEvent instanceof ChannelFuture) {
                fireExceptionCaught = true;
                ((ChannelFuture) currentEvent).setFailure(cause);
            }
        }

        if (fireExceptionCaught) {
            ctx.fireExceptionCaught(cause);
        }
    }

    private void doFlush(final ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (!channel.isActive()) {
            discard(ctx, new ClosedChannelException());
            return;
        }
        while (isWritable()) {
            if (currentEvent == null) {
                currentEvent = queue.poll();
            }

            if (currentEvent == null) {
                break;
            }

            final Object currentEvent = this.currentEvent;
            if (currentEvent instanceof ChannelFuture) {
                this.currentEvent = null;
                ctx.flush((ChannelFuture) currentEvent);
            } else if (currentEvent instanceof ChunkedInput) {
                final ChunkedInput<?> chunks = (ChunkedInput<?>) currentEvent;
                boolean read;
                boolean endOfInput;
                boolean suspend;
                try {
                    read = readChunk(ctx, chunks);
                    endOfInput = chunks.isEndOfInput();

                    if (!read) {
                        // No need to suspend when reached at the end.
                        suspend = !endOfInput;
                    } else {
                        suspend = false;
                    }
                } catch (final Throwable t) {
                    this.currentEvent = null;

                    if (ctx.executor().inEventLoop()) {
                        ctx.fireExceptionCaught(t);
                    } else {
                        ctx.executor().execute(new Runnable() {
                            @Override
                            public void run() {
                                ctx.fireExceptionCaught(t);
                            }
                        });
                    }

                    closeInput(chunks);
                    break;
                }

                if (suspend) {
                    // ChunkedInput.nextChunk() returned null and it has
                    // not reached at the end of input. Let's wait until
                    // more chunks arrive. Nothing to write or notify.
                    break;
                }

                pendingWrites.incrementAndGet();
                ChannelFuture f = ctx.flush();
                if (endOfInput) {
                    this.currentEvent = null;

                    // Register a listener which will close the input once the write is complete.
                    // This is needed because the Chunk may have some resource bound that can not
                    // be closed before its not written.
                    //
                    // See https://github.com/netty/netty/issues/303
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            pendingWrites.decrementAndGet();
                            closeInput(chunks);
                        }
                    });
                } else if (isWritable()) {
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            pendingWrites.decrementAndGet();
                            if (!future.isSuccess()) {
                                closeInput((ChunkedInput<?>) currentEvent);
                            }
                        }
                    });
                } else {
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            pendingWrites.decrementAndGet();
                            if (!future.isSuccess()) {
                                closeInput((ChunkedInput<?>) currentEvent);
                            } else if (isWritable()) {
                                resumeTransfer();
                            }
                        }
                    });
                }
            } else {
                ctx.nextOutboundMessageBuffer().add(currentEvent);
                this.currentEvent = null;
            }

            if (!channel.isActive()) {
                discard(ctx, new ClosedChannelException());
                return;
            }
        }


    }

    /**
     * Read the next {@link ChunkedInput} and transfer it the the outbound buffer. 
     * 
     * @param ctx           the {@link ChannelHandlerContext} this handler is bound to
     * @param chunks        the {@link ChunkedInput} to read from
     * @return read         <code>true</code> if something could be transfered to the outbound buffer
     * @throws Exception    if something goes wrong
     */
    protected boolean readChunk(ChannelHandlerContext ctx, ChunkedInput<?> chunks) throws Exception {
        if (chunks instanceof ChunkedByteInput) {
            return ((ChunkedByteInput) chunks).readChunk(ctx.nextOutboundByteBuffer());
        } else if (chunks instanceof ChunkedMessageInput) {
            return ((ChunkedMessageInput) chunks).readChunk(ctx.nextOutboundMessageBuffer());
        } else {
            throw new IllegalArgumentException("ChunkedInput instance " + chunks + " not supported");
        }
    }
    
    static void closeInput(ChunkedInput<?> chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a chunked input.", t);
            }
        }
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // try to flush again a last time.
        //
        // See #304
        doFlush(ctx);
    }

    // This method should not need any synchronization as the ChunkedWriteHandler will not receive any new events
    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Fail all MessageEvent's that are left. This is needed because otherwise we would never notify the
        // ChannelFuture and the registered FutureListener. See #304
        discard(ctx, new ChannelException(ChunkedWriteHandler.class.getSimpleName() + " removed from pipeline."));
    }
}
