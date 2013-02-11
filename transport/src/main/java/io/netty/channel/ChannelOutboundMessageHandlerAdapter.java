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

import io.netty.buffer.BufUtil;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.Signal;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * Abstract base class which handles messages of a specific type.
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelOutboundMessageHandlerAdapter<I>
        extends ChannelOperationHandlerAdapter implements ChannelOutboundMessageHandler<I> {

    /**
     * Thrown by {@link #flush(ChannelHandlerContext, Object)} to abort message processing.
     */
    protected static final Signal ABORT = new Signal(ChannelOutboundMessageHandlerAdapter.class.getName() + ".ABORT");

    private final TypeParameterMatcher msgMatcher;
    private boolean closeOnFailedFlush = true;

    protected ChannelOutboundMessageHandlerAdapter() {
        this(ChannelOutboundMessageHandlerAdapter.class, 0);
    }

    protected ChannelOutboundMessageHandlerAdapter(
            @SuppressWarnings("rawtypes")
            Class<? extends ChannelOutboundMessageHandlerAdapter> parameterizedHandlerType,
            int messageTypeParamIndex) {
        msgMatcher = TypeParameterMatcher.find(this, parameterizedHandlerType, messageTypeParamIndex);
    }

    protected final boolean isCloseOnFailedFlush() {
        return closeOnFailedFlush;
    }

    protected final void setCloseOnFailedFlush(boolean closeOnFailedFlush) {
        this.closeOnFailedFlush = closeOnFailedFlush;
    }

    @Override
    public MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundMessageBuffer().release();
    }

    /**
     * Returns {@code true} if and only if the specified message can be handled by this handler.
     *
     * @param msg the message
     */
    protected boolean acceptOutboundMessage(Object msg) throws Exception {
        return msgMatcher.match(msg);
    }

    @Override
    public final void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        MessageBuf<Object> in = ctx.outboundMessageBuffer();
        MessageBuf<Object> out = null;

        final int inSize = in.size();
        if (inSize == 0) {
            ctx.flush(promise);
            return;
        }

        int processed = 0;
        try {
            beginFlush(ctx);
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                if (!acceptOutboundMessage(msg)) {
                    if (out == null) {
                        out = ctx.nextOutboundMessageBuffer();
                    }
                    out.add(msg);
                    processed ++;
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                try {
                    flush(ctx, imsg);
                    processed ++;
                } finally {
                    BufUtil.release(imsg);
                }
            }
        } catch (Throwable t) {
            PartialFlushException pfe;
            String msg = processed + " out of " + inSize + " message(s) flushed";
            if (t instanceof Signal) {
                Signal abort = (Signal) t;
                abort.expect(ABORT);
                pfe = new PartialFlushException("aborted by " + getClass().getSimpleName() + ": " + msg);
            } else {
                pfe = new PartialFlushException(msg, t);
            }
            fail(ctx, promise, pfe);
        }

        try {
            endFlush(ctx);
        } catch (Throwable t) {
            if (promise.isDone()) {
                InternalLoggerFactory.getInstance(getClass()).warn(
                        "endFlush() raised a masked exception due to failed flush().", t);
            } else {
                fail(ctx, promise, t);
            }
            return;
        }

        ctx.flush(promise);
    }

    private void fail(ChannelHandlerContext ctx, ChannelPromise promise, Throwable cause) {
        promise.setFailure(cause);
        if (isCloseOnFailedFlush()) {
            ctx.close();
        }
    }

    /**
     * Will get notified once {@link #flush(ChannelHandlerContext, ChannelPromise)} was called.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     */
    protected void beginFlush(@SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx) throws Exception { }

    /**
     * Is called once a message is being flushed.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @param msg           the message to handle
     */
    protected abstract void flush(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called when {@link #flush(ChannelHandlerContext, ChannelPromise)} returns.
     *
     * Super-classes may-override this for special handling.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     */
    protected void endFlush(@SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx) throws Exception { }
}
