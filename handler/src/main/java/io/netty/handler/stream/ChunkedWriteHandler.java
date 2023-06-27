/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.stream;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A {@link ChannelHandler} that adds support for writing a large data stream
 * asynchronously neither spending a lot of memory nor getting
 * {@link OutOfMemoryError}.  Large data streaming such as file
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
 * {@link ChunkedInput#readChunk(ChannelHandlerContext)}, resulting in the indefinitely suspended
 * transfer.  To resume the transfer when a new chunk is available, you have to
 * call {@link #resumeTransfer()}.
 */
public class ChunkedWriteHandler extends ChannelDuplexHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ChunkedWriteHandler.class);

    private final Queue<PendingWrite> queue = new ArrayDeque<PendingWrite>();
    private volatile ChannelHandlerContext ctx;

    public ChunkedWriteHandler() {
    }

    /**
     * @deprecated use {@link #ChunkedWriteHandler()}
     */
    @Deprecated
    public ChunkedWriteHandler(int maxPendingWrites) {
        checkPositive(maxPendingWrites, "maxPendingWrites");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
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
            resumeTransfer0(ctx);
        } else {
            // let the transfer resume on the next event loop round
            ctx.executor().execute(new Runnable() {

                @Override
                public void run() {
                    resumeTransfer0(ctx);
                }
            });
        }
    }

    private void resumeTransfer0(ChannelHandlerContext ctx) {
        try {
            doFlush(ctx);
        } catch (Exception e) {
            logger.warn("Unexpected exception while sending chunks.", e);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        queue.add(new PendingWrite(msg, promise));
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            // channel is writable again try to continue flushing
            doFlush(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    private void discard(Throwable cause) {
        for (;;) {
            PendingWrite currentWrite = queue.poll();

            if (currentWrite == null) {
                break;
            }
            Object message = currentWrite.msg;
            if (message instanceof ChunkedInput) {
                ChunkedInput<?> in = (ChunkedInput<?>) message;
                boolean endOfInput;
                long inputLength;
                try {
                    endOfInput = in.isEndOfInput();
                    inputLength = in.length();
                    closeInput(in);
                } catch (Exception e) {
                    closeInput(in);
                    currentWrite.fail(e);
                    if (logger.isWarnEnabled()) {
                        logger.warn(ChunkedInput.class.getSimpleName() + " failed", e);
                    }
                    continue;
                }

                if (!endOfInput) {
                    if (cause == null) {
                        cause = new ClosedChannelException();
                    }
                    currentWrite.fail(cause);
                } else {
                    currentWrite.success(inputLength);
                }
            } else {
                if (cause == null) {
                    cause = new ClosedChannelException();
                }
                currentWrite.fail(cause);
            }
        }
    }

    private void doFlush(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) {
            discard(null);
            return;
        }

        boolean requiresFlush = true;
        ByteBufAllocator allocator = ctx.alloc();
        while (channel.isWritable()) {
            final PendingWrite currentWrite = queue.peek();

            if (currentWrite == null) {
                break;
            }

            if (currentWrite.promise.isDone()) {
                // This might happen e.g. in the case when a write operation
                // failed, but there're still unconsumed chunks left.
                // Most chunked input sources would stop generating chunks
                // and report end of input, but this doesn't work with any
                // source wrapped in HttpChunkedInput.
                // Note, that we're not trying to release the message/chunks
                // as this had to be done already by someone who resolved the
                // promise (using ChunkedInput.close method).
                // See https://github.com/netty/netty/issues/8700.
                queue.remove();
                continue;
            }

            final Object pendingMessage = currentWrite.msg;

            if (pendingMessage instanceof ChunkedInput) {
                final ChunkedInput<?> chunks = (ChunkedInput<?>) pendingMessage;
                boolean endOfInput;
                boolean suspend;
                Object message = null;
                try {
                    message = chunks.readChunk(allocator);
                    endOfInput = chunks.isEndOfInput();

                    if (message == null) {
                        // No need to suspend when reached at the end.
                        suspend = !endOfInput;
                    } else {
                        suspend = false;
                    }
                } catch (final Throwable t) {
                    queue.remove();

                    if (message != null) {
                        ReferenceCountUtil.release(message);
                    }

                    closeInput(chunks);
                    currentWrite.fail(t);
                    break;
                }

                if (suspend) {
                    // ChunkedInput.nextChunk() returned null and it has
                    // not reached at the end of input. Let's wait until
                    // more chunks arrive. Nothing to write or notify.
                    break;
                }

                if (message == null) {
                    // If message is null write an empty ByteBuf.
                    // See https://github.com/netty/netty/issues/1671
                    message = Unpooled.EMPTY_BUFFER;
                }

                if (endOfInput) {
                    // We need to remove the element from the queue before we call writeAndFlush() as this operation
                    // may cause an action that also touches the queue.
                    queue.remove();
                }
                // Flush each chunk to conserve memory
                ChannelFuture f = ctx.writeAndFlush(message);
                if (endOfInput) {
                    if (f.isDone()) {
                        handleEndOfInputFuture(f, currentWrite);
                    } else {
                        // Register a listener which will close the input once the write is complete.
                        // This is needed because the Chunk may have some resource bound that can not
                        // be closed before its not written.
                        //
                        // See https://github.com/netty/netty/issues/303
                        f.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                handleEndOfInputFuture(future, currentWrite);
                            }
                        });
                    }
                } else {
                    final boolean resume = !channel.isWritable();
                    if (f.isDone()) {
                        handleFuture(f, currentWrite, resume);
                    } else {
                        f.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                handleFuture(future, currentWrite, resume);
                            }
                        });
                    }
                }
                requiresFlush = false;
            } else {
                queue.remove();
                ctx.write(pendingMessage, currentWrite.promise);
                requiresFlush = true;
            }

            if (!channel.isActive()) {
                discard(new ClosedChannelException());
                break;
            }
        }

        if (requiresFlush) {
            ctx.flush();
        }
    }

    private static void handleEndOfInputFuture(ChannelFuture future, PendingWrite currentWrite) {
        ChunkedInput<?> input = (ChunkedInput<?>) currentWrite.msg;
        if (!future.isSuccess()) {
            closeInput(input);
            currentWrite.fail(future.cause());
        } else {
            // read state of the input in local variables before closing it
            long inputProgress = input.progress();
            long inputLength = input.length();
            closeInput(input);
            currentWrite.progress(inputProgress, inputLength);
            currentWrite.success(inputLength);
        }
    }

    private void handleFuture(ChannelFuture future, PendingWrite currentWrite, boolean resume) {
        ChunkedInput<?> input = (ChunkedInput<?>) currentWrite.msg;
        if (!future.isSuccess()) {
            closeInput(input);
            currentWrite.fail(future.cause());
        } else {
            currentWrite.progress(input.progress(), input.length());
            if (resume && future.channel().isWritable()) {
                resumeTransfer();
            }
        }
    }

    private static void closeInput(ChunkedInput<?> chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a chunked input.", t);
            }
        }
    }

    private static final class PendingWrite {
        final Object msg;
        final ChannelPromise promise;

        PendingWrite(Object msg, ChannelPromise promise) {
            this.msg = msg;
            this.promise = promise;
        }

        void fail(Throwable cause) {
            ReferenceCountUtil.release(msg);
            promise.tryFailure(cause);
        }

        void success(long total) {
            if (promise.isDone()) {
                // No need to notify the progress or fulfill the promise because it's done already.
                return;
            }
            progress(total, total);
            promise.trySuccess();
        }

        void progress(long progress, long total) {
            if (promise instanceof ChannelProgressivePromise) {
                ((ChannelProgressivePromise) promise).tryProgress(progress, total);
            }
        }
    }
}
