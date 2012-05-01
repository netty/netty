/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.stream;

import static io.netty.channel.Channels.*;

import java.nio.channels.ClosedChannelException;
import java.util.Queue;

import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.QueueFactory;

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
public class ChunkedWriteHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ChunkedWriteHandler.class);

    private final Queue<MessageEvent> queue = QueueFactory.createQueue(MessageEvent.class);

    private ChannelHandlerContext ctx;
    private MessageEvent currentEvent;

    /**
     * Continues to fetch the chunks from the input.
     */
    public void resumeTransfer() {
        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            return;
        }

        try {
            flush(ctx, false);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception while sending chunks.", e);
            }
        }
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (!(e instanceof MessageEvent)) {
            ctx.sendDownstream(e);
            return;
        }

        boolean offered = queue.offer((MessageEvent) e);
        assert offered;

        final Channel channel = ctx.channel();
        if (channel.isWritable()) {
            this.ctx = ctx;
            flush(ctx, false);
        } else if (!channel.isConnected()) {
            this.ctx = ctx;
            discard(ctx, false);
        }
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            switch (cse.getState()) {
            case INTEREST_OPS:
                // Continue writing when the channel becomes writable.
                flush(ctx, true);
                break;
            case OPEN:
                if (!Boolean.TRUE.equals(cse.getValue())) {
                    // Fail all pending writes
                    discard(ctx, true);
                }
                break;
            }
        }
        ctx.sendUpstream(e);
    }

    private void discard(ChannelHandlerContext ctx, boolean fireNow) {
        ClosedChannelException cause = null;
        boolean fireExceptionCaught = false;
           
        for (;;) {
            MessageEvent currentEvent = this.currentEvent;
                
            if (this.currentEvent == null) { 
                currentEvent = queue.poll(); 
            } else {
                this.currentEvent = null; 
            }

            if (currentEvent == null) { 
                break; 
            }
            
              
            Object m = currentEvent.getMessage();
            if (m instanceof ChunkedInput) {
                closeInput((ChunkedInput) m);
            }

            // Trigger a ClosedChannelException
            if (cause == null) {
                cause = new ClosedChannelException();
            }
            currentEvent.getFuture().setFailure(cause);
            fireExceptionCaught = true;
        }
        

        if (fireExceptionCaught) {
            if (fireNow) {
                fireExceptionCaught(ctx, cause);
            } else {
                fireExceptionCaughtLater(ctx, cause);
            }
        }
    }

    private synchronized void flush(ChannelHandlerContext ctx, boolean fireNow) throws Exception {
        final Channel channel = ctx.channel();
        if (!channel.isConnected()) {
            discard(ctx, fireNow);
        }

        while (channel.isWritable()) {
            if (currentEvent == null) {
                currentEvent = queue.poll();
            }

            if (currentEvent == null) {
                break;
            }

            if (currentEvent.getFuture().isDone()) {
                // Skip the current request because the previous partial write
                // attempt for the current request has been failed.
                currentEvent = null;
            } else {
                final MessageEvent currentEvent = this.currentEvent;
                Object m = currentEvent.getMessage();
                if (m instanceof ChunkedInput) {
                    ChunkedInput chunks = (ChunkedInput) m;
                    Object chunk;
                    boolean endOfInput;
                    boolean suspend;
                    try {
                        chunk = chunks.nextChunk();
                        endOfInput = chunks.isEndOfInput();
                        if (chunk == null) {
                            chunk = ChannelBuffers.EMPTY_BUFFER;
                            // No need to suspend when reached at the end.
                            suspend = !endOfInput;
                        } else {
                            suspend = false;
                        }
                    } catch (Throwable t) {
                        this.currentEvent = null;

                        currentEvent.getFuture().setFailure(t);
                        if (fireNow) {  
                            fireExceptionCaught(ctx, t);
                        } else {
                            fireExceptionCaughtLater(ctx, t);
                        }

                        closeInput(chunks);
                        break;
                    }

                    if (suspend) {
                        // ChunkedInput.nextChunk() returned null and it has
                        // not reached at the end of input.  Let's wait until
                        // more chunks arrive.  Nothing to write or notify.
                        break;
                    } else {
                        ChannelFuture writeFuture;
                        if (endOfInput) {
                            this.currentEvent = null;
                            closeInput(chunks);
                            writeFuture = currentEvent.getFuture();
                        } else {
                            writeFuture = future(channel);
                            writeFuture.addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future)
                                        throws Exception {
                                    if (!future.isSuccess()) {
                                        currentEvent.getFuture().setFailure(future.cause());
                                        closeInput((ChunkedInput) currentEvent.getMessage());
                                    }
                                }
                            });
                        }

                        Channels.write(
                                ctx, writeFuture, chunk,
                                currentEvent.getRemoteAddress());
                    }
                } else {
                    this.currentEvent = null;
                    ctx.sendDownstream(currentEvent);
                }
            }

            if (!channel.isConnected()) {
                discard(ctx, fireNow);
                break;
            }
        }
    }

    static void closeInput(ChunkedInput chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a chunked input.", t);
            }
        }
    }
}
