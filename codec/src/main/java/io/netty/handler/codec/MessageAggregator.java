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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * An abstract {@link ChannelHandler} that aggregates a series of message objects into a single aggregated message.
 * <p>
 * 'A series of messages' is composed of the following:
 * <ul>
 * <li>a single start message which optionally contains the first part of the content, and</li>
 * <li>1 or more content messages.</li>
 * </ul>
 * The content of the aggregated message will be the merged content of the start message and its following content
 * messages. If this aggregator encounters a content message where {@link #isLastContentMessage(ByteBufHolder)}
 * return {@code true} for, the aggregator will finish the aggregation and produce the aggregated message and expect
 * another start message.
 * </p>
 *
 * @param <I> the type that covers both start message and content message
 * @param <S> the type of the start message
 * @param <C> the type of the content message (must be a subtype of {@link ByteBufHolder})
 * @param <O> the type of the aggregated message (must be a subtype of {@code S} and {@link ByteBufHolder})
 */
public abstract class MessageAggregator<I, S, C extends ByteBufHolder, O extends ByteBufHolder>
        extends MessageToMessageDecoder<I> {

    private static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private final int maxContentLength;
    private O currentMessage;
    private boolean handlingOversizedMessage;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;
    private ChannelHandlerContext ctx;
    private ChannelFutureListener continueResponseWriteListener;

    private boolean aggregating;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        {@link #handleOversizedMessage(ChannelHandlerContext, Object)} will be called.
     */
    protected MessageAggregator(int maxContentLength) {
        validateMaxContentLength(maxContentLength);
        this.maxContentLength = maxContentLength;
    }

    protected MessageAggregator(int maxContentLength, Class<? extends I> inboundMessageType) {
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

        @SuppressWarnings("unchecked")
        I in = (I) msg;

        if (isAggregated(in)) {
            return false;
        }

        // NOTE: It's tempting to make this check only if aggregating is false. There are however
        // side conditions in decode(...) in respect to large messages.
        if (isStartMessage(in)) {
            aggregating = true;
            return true;
        } else if (aggregating && isContentMessage(in)) {
            return true;
        }

        return false;
    }

    /**
     * Returns {@code true} if and only if the specified message is a start message. Typically, this method is
     * implemented as a single {@code return} statement with {@code instanceof}:
     * <pre>
     * return msg instanceof MyStartMessage;
     * </pre>
     */
    protected abstract boolean isStartMessage(I msg) throws Exception;

    /**
     * Returns {@code true} if and only if the specified message is a content message. Typically, this method is
     * implemented as a single {@code return} statement with {@code instanceof}:
     * <pre>
     * return msg instanceof MyContentMessage;
     * </pre>
     */
    protected abstract boolean isContentMessage(I msg) throws Exception;

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
    protected abstract boolean isAggregated(I msg) throws Exception;

    /**
     * Returns the maximum allowed length of the aggregated message in bytes.
     */
    public final int maxContentLength() {
        return maxContentLength;
    }

    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@value #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int maxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@value #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    /**
     * @deprecated This method will be removed in future releases.
     */
    @Deprecated
    public final boolean isHandlingOversizedMessage() {
        return handlingOversizedMessage;
    }

    protected final ChannelHandlerContext ctx() {
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline yet");
        }
        return ctx;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception {
        assert aggregating;

        if (isStartMessage(msg)) {
            handlingOversizedMessage = false;
            if (currentMessage != null) {
                currentMessage.release();
                currentMessage = null;
                throw new MessageAggregationException();
            }

            @SuppressWarnings("unchecked")
            S m = (S) msg;

            // Send the continue response if necessary (e.g. 'Expect: 100-continue' header)
            // Check before content length. Failing an expectation may result in a different response being sent.
            Object continueResponse = newContinueResponse(m, maxContentLength, ctx.pipeline());
            if (continueResponse != null) {
                // Cache the write listener for reuse.
                ChannelFutureListener listener = continueResponseWriteListener;
                if (listener == null) {
                    continueResponseWriteListener = listener = new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                ctx.fireExceptionCaught(future.cause());
                            }
                        }
                    };
                }

                // Make sure to call this before writing, otherwise reference counts may be invalid.
                boolean closeAfterWrite = closeAfterContinueResponse(continueResponse);
                handlingOversizedMessage = ignoreContentAfterContinueResponse(continueResponse);

                final ChannelFuture future = ctx.writeAndFlush(continueResponse).addListener(listener);

                if (closeAfterWrite) {
                    future.addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                if (handlingOversizedMessage) {
                    return;
                }
            } else if (isContentLengthInvalid(m, maxContentLength)) {
                // if content length is set, preemptively close if it's too large
                invokeHandleOversizedMessage(ctx, m);
                return;
            }

            if (m instanceof DecoderResultProvider && !((DecoderResultProvider) m).decoderResult().isSuccess()) {
                O aggregated;
                if (m instanceof ByteBufHolder) {
                    aggregated = beginAggregation(m, ((ByteBufHolder) m).content().retain());
                } else {
                    aggregated = beginAggregation(m, EMPTY_BUFFER);
                }
                finishAggregation0(aggregated);
                out.add(aggregated);
                return;
            }

            // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
            CompositeByteBuf content = ctx.alloc().compositeBuffer(maxCumulationBufferComponents);
            if (m instanceof ByteBufHolder) {
                appendPartialContent(content, ((ByteBufHolder) m).content());
            }
            currentMessage = beginAggregation(m, content);
        } else if (isContentMessage(msg)) {
            if (currentMessage == null) {
                // it is possible that a TooLongFrameException was already thrown but we can still discard data
                // until the begging of the next request/response.
                return;
            }

            // Merge the received chunk into the content of the current message.
            CompositeByteBuf content = (CompositeByteBuf) currentMessage.content();

            @SuppressWarnings("unchecked")
            final C m = (C) msg;
            // Handle oversized message.
            if (content.readableBytes() > maxContentLength - m.content().readableBytes()) {
                // By convention, full message type extends first message type.
                @SuppressWarnings("unchecked")
                S s = (S) currentMessage;
                invokeHandleOversizedMessage(ctx, s);
                return;
            }

            // Append the content of the chunk.
            appendPartialContent(content, m.content());

            // Give the subtypes a chance to merge additional information such as trailing headers.
            aggregate(currentMessage, m);

            final boolean last;
            if (m instanceof DecoderResultProvider) {
                DecoderResult decoderResult = ((DecoderResultProvider) m).decoderResult();
                if (!decoderResult.isSuccess()) {
                    if (currentMessage instanceof DecoderResultProvider) {
                        ((DecoderResultProvider) currentMessage).setDecoderResult(
                                DecoderResult.failure(decoderResult.cause()));
                    }
                    last = true;
                } else {
                    last = isLastContentMessage(m);
                }
            } else {
                last = isLastContentMessage(m);
            }

            if (last) {
                finishAggregation0(currentMessage);

                // All done
                out.add(currentMessage);
                currentMessage = null;
            }
        } else {
            throw new MessageAggregationException();
        }
    }

    private static void appendPartialContent(CompositeByteBuf content, ByteBuf partialContent) {
        if (partialContent.isReadable()) {
            content.addComponent(true, partialContent.retain());
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
     * Messages will stop being ignored the next time {@link #isContentMessage(Object)} returns {@code true}.
     *
     * @param msg The return value from {@link #newContinueResponse(Object, int, ChannelPipeline)}.
     * @return {@code true} if all objects for the current request/response should be ignored or not.
     * {@code false} otherwise.
     */
    protected abstract boolean ignoreContentAfterContinueResponse(Object msg) throws Exception;

    /**
     * Creates a new aggregated message from the specified start message and the specified content.  If the start
     * message implements {@link ByteBufHolder}, its content is appended to the specified {@code content}.
     * This aggregator will continue to append the received content to the specified {@code content}.
     */
    protected abstract O beginAggregation(S start, ByteBuf content) throws Exception;

    /**
     * Transfers the information provided by the specified content message to the specified aggregated message.
     * Note that the content of the specified content message has been appended to the content of the specified
     * aggregated message already, so that you don't need to.  Use this method to transfer the additional information
     * that the content message provides to {@code aggregated}.
     */
    protected void aggregate(O aggregated, C content) throws Exception { }

    private void finishAggregation0(O aggregated) throws Exception {
        aggregating = false;
        finishAggregation(aggregated);
    }

    /**
     * Invoked when the specified {@code aggregated} message is about to be passed to the next handler in the pipeline.
     */
    protected void finishAggregation(O aggregated) throws Exception { }

    private void invokeHandleOversizedMessage(ChannelHandlerContext ctx, S oversized) throws Exception {
        handlingOversizedMessage = true;
        currentMessage = null;
        try {
            handleOversizedMessage(ctx, oversized);
        } finally {
            // Release the message in case it is a full one.
            ReferenceCountUtil.release(oversized);
        }
    }

    /**
     * Invoked when an incoming request exceeds the maximum content length.  The default behvaior is to trigger an
     * {@code exceptionCaught()} event with a {@link TooLongFrameException}.
     *
     * @param ctx the {@link ChannelHandlerContext}
     * @param oversized the accumulated message up to this point, whose type is {@code S} or {@code O}
     */
    protected void handleOversizedMessage(ChannelHandlerContext ctx, S oversized) throws Exception {
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

    private void releaseCurrentMessage() {
        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
            handlingOversizedMessage = false;
            aggregating = false;
        }
    }
}
