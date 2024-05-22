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
package io.netty5.handler.stream;

import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

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
 * {@link ChunkedInput#readChunk(BufferAllocator)}, resulting in the indefinitely suspended
 * transfer.  To resume the transfer when a new chunk is available, you have to
 * call {@link #resumeTransfer()}.
 */
public class ChunkedWriteHandler implements ChannelHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChunkedWriteHandler.class);

    private Queue<PendingWrite> queue;
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

    private void allocateQueue() {
        if (queue == null) {
            queue = new ArrayDeque<>();
        }
    }

    private boolean queueIsEmpty() {
        return queue == null || queue.isEmpty();
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
            ctx.executor().execute(() -> resumeTransfer0(ctx));
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
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!queueIsEmpty() || msg instanceof ChunkedInput) {
            allocateQueue();
            Promise<Void> promise = ctx.newPromise();
            queue.add(new PendingWrite(msg, promise));
            return promise.asFuture();
        } else {
            return ctx.write(msg);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
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
        if (queueIsEmpty()) {
            return;
        }
        for (;;) {
            PendingWrite currentWrite = queue.poll();

            if (currentWrite == null) {
                break;
            }
            Object message = currentWrite.msg;
            if (message instanceof ChunkedInput) {
                ChunkedInput<?> in = (ChunkedInput<?>) message;
                boolean endOfInput;
                try {
                    endOfInput = in.isEndOfInput();
                    closeInput(in);
                } catch (Exception e) {
                    closeInput(in);
                    currentWrite.fail(e);
                    logger.warn("ChunkedInput failed", e);
                    continue;
                }

                if (!endOfInput) {
                    if (cause == null) {
                        cause = new ClosedChannelException();
                    }
                    currentWrite.fail(cause);
                } else {
                    currentWrite.success();
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

        if (queueIsEmpty()) {
            ctx.flush();
            return;
        }

        boolean requiresFlush = true;
        BufferAllocator allocator = ctx.bufferAllocator();
        while (channel.isWritable()) {
            final PendingWrite currentWrite = queue.peek();

            if (currentWrite == null) {
                break;
            }

            if (currentWrite.promise.isDone()) {
                // This might happen e.g. in the case when a write operation
                // failed, but there are still unconsumed chunks left.
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
                    // No need to suspend when reached at the end.
                    suspend = message == null && !endOfInput;

                } catch (final Throwable t) {
                    queue.remove();

                    if (message != null) {
                        Resource.dispose(message);
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
                    // If message is null write an empty Buffer.
                    // See https://github.com/netty/netty/issues/1671
                    message = allocator.allocate(0);
                }

                if (endOfInput) {
                    // We need to remove the element from the queue before we call writeAndFlush() as this operation
                    // may cause an action that also touches the queue.
                    queue.remove();
                }
                // Flush each chunk to conserve memory
                Future<Void> f = ctx.writeAndFlush(message);
                if (endOfInput) {
                    if (f.isDone()) {
                        handleEndOfInputFuture(f, chunks, currentWrite);
                    } else {
                        // Register a listener which will close the input once the write is complete.
                        // This is needed because the Chunk may have some resource bound that can not
                        // be closed before it's not written.
                        //
                        // See https://github.com/netty/netty/issues/303
                        f.addListener(future -> handleEndOfInputFuture(future, chunks, currentWrite));
                    }
                } else {
                    final boolean resume = !channel.isWritable();
                    if (f.isDone()) {
                        handleFuture(channel, f, chunks, currentWrite, resume);
                    } else {
                        f.addListener(future -> handleFuture(channel, future, chunks, currentWrite, resume));
                    }
                }
                requiresFlush = false;
            } else {
                queue.remove();
                ctx.write(pendingMessage).cascadeTo(currentWrite.promise);
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

    private static void handleEndOfInputFuture(Future<?> future, ChunkedInput<?> input, PendingWrite currentWrite) {
        closeInput(input);
        if (future.isFailed()) {
            currentWrite.fail(future.cause());
        } else {
            currentWrite.success();
        }
    }

    private void handleFuture(Channel channel, Future<?> future, ChunkedInput<?> input,
                              PendingWrite currentWrite, boolean resume) {
        if (future.isFailed()) {
            closeInput(input);
            currentWrite.fail(future.cause());
        } else {
            if (resume && channel.isWritable()) {
                resumeTransfer();
            }
        }
    }

    private static void closeInput(ChunkedInput<?> chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            logger.warn("Failed to close a ChunkedInput.", t);
        }
    }

    private static final class PendingWrite {
        final Object msg;
        final Promise<Void> promise;

        PendingWrite(Object msg, Promise<Void> promise) {
            this.msg = msg;
            this.promise = promise;
        }

        void fail(Throwable cause) {
            promise.tryFailure(cause);
            if (Resource.isAccessible(msg, false)) {
                SilentDispose.dispose(msg, logger);
            }
        }

        void success() {
            if (promise.isDone()) {
                // No need to notify the progress or fulfill the promise because it's done already.
                return;
            }
            promise.trySuccess(null);
        }
    }
}
