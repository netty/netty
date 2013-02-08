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
package io.netty.channel;

import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;

/**
 * Abstract base class which handles messages of a specific type.
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelOutboundMessageHandlerAdapter<I>
        extends ChannelOperationHandlerAdapter implements ChannelOutboundMessageHandler<I> {

    private final Class<?>[] acceptedMsgTypes;

    /**
     * The types which will be accepted by the message handler. If a received message is an other type it will be just
     * forwarded  to the next {@link ChannelInboundMessageHandler} in the {@link ChannelPipeline}.
     */
    protected ChannelOutboundMessageHandlerAdapter(Class<?>... acceptedMsgTypes) {
        this.acceptedMsgTypes = ChannelHandlerUtil.acceptedMessageTypes(acceptedMsgTypes);
    }

    @Override
    public MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundMessageBuffer().free();
    }

    @Override
    public final void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (!beginMessageFlushed(ctx)) {
            promise.setSuccess();
            return;
        }
        boolean unsupportedFound = false;
        boolean processed = false;
        MessageBuf<I> in = ctx.outboundMessageBuffer();

        try {
            MessageBuf<Object> out = null;
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                try {
                    if (!isSupported(msg)) {
                        if (out == null) {
                            out = ctx.nextOutboundMessageBuffer();
                        }
                        out.add(msg);
                        unsupportedFound = true;
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    I imsg = (I) msg;
                    try {
                        messageFlushed(ctx, imsg);
                        processed = true;
                    } finally {
                        freeOutboundMessage(imsg);
                    }
                } catch (Throwable cause) {
                    // reset these will be picked up on next flush
                    unsupportedFound = false;
                    if (processed) {
                        cause = new PartialFlushException("Unable to encoded all messages", cause);
                    }
                    promise.setFailure(cause);
                    return;
                }
            }
        } finally {
            if (unsupportedFound) {
                ctx.flush(promise);
            }
        }
        if (!unsupportedFound) {
            allMessageConsumed(ctx, promise);
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be handled by this handler.
     *
     * @param msg the message
     */
    public boolean isSupported(Object msg) throws Exception {
        return ChannelHandlerUtil.acceptMessage(acceptedMsgTypes, msg);
    }

    /**
     * Will get notified once {@link #flush(ChannelHandlerContext, ChannelPromise)} was called.
     *
     * If this method returns {@code false} no further processing of the {@link MessageBuf}
     * will be done until the next call of {@link #flush(ChannelHandlerContext, ChannelPromise)}.
     *
     * This will return {@code true} by default, and may get overriden by sub-classes for
     * special handling.
     */
    @SuppressWarnings("unused")
    protected boolean beginMessageFlushed(ChannelHandlerContext ctx) throws Exception {
        return true;
    }

    /**
     * Is called once a message was received.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @param msg           the message to handle
     * @throws Exception    thrown when an error accour
     */
    protected abstract void messageFlushed(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called after all messages of the {@link MessageBuf} was consumed without an error. This implementation will
     * success the {@link ChannelPromise}, sub-classes may-override this for special handling.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @throws Exception    thrown when an error accour
     */
    @SuppressWarnings("unused")
    protected void allMessageConsumed(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        promise.setSuccess();
    }

    /**
     * Is called after a message was processed via {@link #messageFlushed(ChannelHandlerContext, Object)} to free
     * up any resources that is held by the inbound message. You may want to override this if your implementation
     * just pass-through the input message or need it for later usage.
     */
    protected void freeOutboundMessage(I msg) throws Exception {
        ChannelHandlerUtil.freeMessage(msg);
    }
}
