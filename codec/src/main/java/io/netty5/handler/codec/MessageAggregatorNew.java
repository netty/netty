/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureContextListener;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * An abstract {@link ChannelHandler} that aggregates a series of message objects into a single aggregated message.
 * <p>
 * 'A series of messages' is composed of the following:
 * <ul>
 * <li>a single start message which optionally contains the first part of the content, and</li>
 * <li>1 or more content messages.</li>
 * </ul>
 * The content of the aggregated message will be the merged content of the start message and its following content
 * messages. If this aggregator encounters a content message where {@link #isLastContentMessage(AutoCloseable)}
 * return {@code true} for, the aggregator will finish the aggregation and produce the aggregated message and expect
 * another start message.
 * </p>
 *
 * @param <I> the type that covers both start message and content message
 * @param <S> the type of the start message
 * @param <C> the type of the content message
 * @param <A> the type of the aggregated message
 */
public abstract class MessageAggregatorNew<I, S, C extends AutoCloseable, A extends AutoCloseable>
        extends MessageToMessageDecoder<I> {
    private final int maxContentLength;
    private A currentMessage;
    private boolean handlingOversizedMessage;

    private ChannelHandlerContext ctx;
    private FutureContextListener<ChannelHandlerContext, Void> continueResponseWriteListener;

    private boolean aggregating;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        {@link #handleOversizedMessage(ChannelHandlerContext, Object)} will be called.
     */
    protected MessageAggregatorNew(int maxContentLength) {
        validateMaxContentLength(maxContentLength);
        this.maxContentLength = maxContentLength;
    }

    protected MessageAggregatorNew(int maxContentLength, Class<? extends I> inboundMessageType) {
        super(inboundMessageType);
        validateMaxContentLength(maxContentLength);
        this.maxContentLength = maxContentLength;
    }

    private static void validateMaxContentLength(int maxContentLength) {
        checkPositiveOrZero(maxContentLength, "maxContentLength");
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        // No need to match last and full types because they are subset of first and middle types.
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }

        if (isAggregated(msg)) {
            return false;
        }

        // NOTE: It's tempting to make this check only if aggregating is false. There are however
        // side conditions in decode(...) in respect to large messages.
        if (tryStartMessage(msg) != null) {
            aggregating = true;
            return true;
        }
        return aggregating && tryContentMessage(msg) != null;
    }

    /**
     * If the passed {@code msg} is a {@linkplain S start message} then cast and return the same, else return
     * {@code null}.
     */
    protected abstract S tryStartMessage(Object msg);

    /**
     * If the passed {@code msg} is a {@linkplain C content message} then cast and return the same, else return
     * {@code null}.
     */
    protected abstract C tryContentMessage(Object msg);

    /**
     * Returns {@code true} if and only if the specified message is the last content message. Typically, this method is
     * implemented as a single {@code return} statement with {@code instanceof}:
     * <pre>
     * return msg instanceof MyLastContentMessage;
     * </pre>
     * or with {@code instanceof} and boolean field check:
     * <pre>
     * return msg instanceof MyContentMessage && msg.isLastFragment();
     * </pre>
     */
    protected abstract boolean isLastContentMessage(C msg) throws Exception;

    /**
     * Returns {@code true} if and only if the specified message is already aggregated.  If this method returns
     * {@code true}, this handler will simply forward the message to the next handler as-is.
     */
    protected abstract boolean isAggregated(Object msg) throws Exception;

    /**
     * Returns the length in bytes of the passed message.
     *
     * @param msg to calculate length.
     * @return Length in bytes of the passed message.
     */
    protected abstract int lengthForContent(C msg);

    /**
     * Returns the length in bytes of the passed message.
     *
     * @param msg to calculate length.
     * @return Length in bytes of the passed message.
     */
    protected abstract int lengthForAggregation(A msg);

    /**
     * Returns the maximum allowed length of the aggregated message in bytes.
     */
    public final int maxContentLength() {
        return maxContentLength;
    }

    protected final ChannelHandlerContext ctx() {
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline yet");
        }
        return ctx;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, I msg) throws Exception {
        assert aggregating;
        final S startMsg = tryStartMessage(msg);
        if (startMsg != null) {
            handlingOversizedMessage = false;
            if (currentMessage != null) {
                currentMessage.close();
                currentMessage = null;
                throw new MessageAggregationException();
            }

            // Send the continue response if necessary (e.g. 'Expect: 100-continue' header)
            // Check before content length. Failing an expectation may result in a different response being sent.
            Object continueResponse = newContinueResponse(startMsg, maxContentLength, ctx.pipeline());
            if (continueResponse != null) {
                // Cache the write listener for reuse.
                FutureContextListener<ChannelHandlerContext, Void> listener = continueResponseWriteListener;
                if (listener == null) {
                    continueResponseWriteListener = listener = (context, future) -> {
                        if (future.isFailed()) {
                            context.fireExceptionCaught(future.cause());
                        }
                    };
                }

                // Make sure to call this before writing, otherwise reference counts may be invalid.
                boolean closeAfterWrite = closeAfterContinueResponse(continueResponse);
                handlingOversizedMessage = ignoreContentAfterContinueResponse(continueResponse);

                Future<Void> future = ctx.writeAndFlush(continueResponse).addListener(ctx, listener);

                if (closeAfterWrite) {
                    future.addListener(ctx, ChannelFutureListeners.CLOSE);
                    return;
                }
                if (handlingOversizedMessage) {
                    return;
                }
            } else if (isContentLengthInvalid(startMsg, maxContentLength)) {
                // if content length is set, preemptively close if it's too large
                invokeHandleOversizedMessage(ctx, startMsg);
                return;
            }

            if (startMsg instanceof DecoderResultProvider &&
                    !((DecoderResultProvider) startMsg).decoderResult().isSuccess()) {
                final A aggregated = beginAggregation(ctx.bufferAllocator(), startMsg);
                finishAggregation(ctx.bufferAllocator(), aggregated);
                ctx.fireChannelRead(aggregated);
                return;
            }

            currentMessage = beginAggregation(ctx.bufferAllocator(), startMsg);
            return;
        }

        final C contentMsg = tryContentMessage(msg);
        if (contentMsg != null) {
            if (currentMessage == null) {
                // it is possible that a TooLongFrameException was already thrown but we can still discard data
                // until the beginning of the next request/response.
                return;
            }

            // Handle oversized message.
            if (lengthForAggregation(currentMessage) > maxContentLength - lengthForContent(contentMsg)) {
                invokeHandleOversizedMessage(ctx, currentMessage);
                return;
            }

            aggregate(ctx.bufferAllocator(), currentMessage, contentMsg);

            final boolean last;
            if (contentMsg instanceof DecoderResultProvider) {
                DecoderResult decoderResult = ((DecoderResultProvider) contentMsg).decoderResult();
                if (!decoderResult.isSuccess()) {
                    if (currentMessage instanceof DecoderResultProvider) {
                        ((DecoderResultProvider) currentMessage).setDecoderResult(
                                DecoderResult.failure(decoderResult.cause()));
                    }
                    last = true;
                } else {
                    last = isLastContentMessage(contentMsg);
                }
            } else {
                last = isLastContentMessage(contentMsg);
            }

            if (last) {
                finishAggregation0(ctx.bufferAllocator(), currentMessage);

                // All done
                A message = currentMessage;
                currentMessage = null;
                ctx.fireChannelRead(message);
            }
        } else {
            throw new MessageAggregationException();
        }
    }

    /**
     * Determine if the message {@code start}'s content length is known, and if it greater than
     * {@code maxContentLength}.
     * @param start The message which may indicate the content length.
     * @param maxContentLength The maximum allowed content length.
     * @return {@code true} if the message {@code start}'s content length is known, and if it greater than
     * {@code maxContentLength}. {@code false} otherwise.
     */
    protected abstract boolean isContentLengthInvalid(S start, int maxContentLength) throws Exception;

    /**
     * Returns the 'continue response' for the specified start message if necessary. For example, this method is
     * useful to handle an HTTP 100-continue header.
     *
     * @return the 'continue response', or {@code null} if there's no message to send
     */
    protected abstract Object newContinueResponse(S start, int maxContentLength, ChannelPipeline pipeline)
            throws Exception;

    /**
     * Determine if the channel should be closed after the result of
     * {@link #newContinueResponse(Object, int, ChannelPipeline)} is written.
     * @param msg The return value from {@link #newContinueResponse(Object, int, ChannelPipeline)}.
     * @return {@code true} if the channel should be closed after the result of
     * {@link #newContinueResponse(Object, int, ChannelPipeline)} is written. {@code false} otherwise.
     */
    protected abstract boolean closeAfterContinueResponse(Object msg) throws Exception;

    /**
     * Determine if all objects for the current request/response should be ignored or not.
     * Messages will stop being ignored the next time {@link #tryContentMessage(Object)} returns a {@code non null}
     * value.
     *
     * @param msg The return value from {@link #newContinueResponse(Object, int, ChannelPipeline)}.
     * @return {@code true} if all objects for the current request/response should be ignored or not.
     * {@code false} otherwise.
     */
    protected abstract boolean ignoreContentAfterContinueResponse(Object msg) throws Exception;

    /**
     * Creates a new aggregated message from the specified start message.
     */
    protected abstract A beginAggregation(BufferAllocator allocator, S start) throws Exception;

    /**
     * Aggregated the passed {@code content} in the passed {@code aggregate}.
     */
    protected abstract void aggregate(BufferAllocator allocator, A aggregated, C content) throws Exception;

    private void finishAggregation0(BufferAllocator allocator, A aggregated) throws Exception {
        aggregating = false;
        finishAggregation(allocator, aggregated);
    }

    /**
     * Invoked when the specified {@code aggregated} message is about to be passed to the next handler in the pipeline.
     */
    protected void finishAggregation(BufferAllocator allocator, A aggregated) throws Exception { }

    private void invokeHandleOversizedMessage(ChannelHandlerContext ctx, Object oversized) throws Exception {
        handlingOversizedMessage = true;
        currentMessage = null;
        try {
            handleOversizedMessage(ctx, oversized);
        } finally {
            if (oversized instanceof AutoCloseable) {
                ((AutoCloseable) oversized).close();
            }
        }
    }

    /**
     * Invoked when an incoming request exceeds the maximum content length.  The default behavior is to trigger an
     * {@code exceptionCaught()} event with a {@link TooLongFrameException}.
     *
     * @param ctx the {@link ChannelHandlerContext}
     * @param oversized the accumulated message up to this point.
     */
    protected void handleOversizedMessage(ChannelHandlerContext ctx, @SuppressWarnings("unused") Object oversized)
            throws Exception {
        ctx.fireExceptionCaught(
                new TooLongFrameException("content length exceeded " + maxContentLength() + " bytes."));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // We might need keep reading the channel until the full message is aggregated.
        //
        // See https://github.com/netty/netty/issues/6583
        if (currentMessage != null && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            // release current message if it is not null as it may be a left-over
            super.channelInactive(ctx);
        } finally {
            releaseCurrentMessage();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            // release current message if it is not null as it may be a left-over as there is not much more we can do in
            // this case
            releaseCurrentMessage();
        }
    }

    private void releaseCurrentMessage() throws Exception {
        if (currentMessage != null) {
            currentMessage.close();
            currentMessage = null;
            handlingOversizedMessage = false;
            aggregating = false;
        }
    }
}
