/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.quic.QuicException;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ChannelInboundHandlerAdapter} which makes it easy to handle
 * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7">HTTP3 request streams</a>.
 */
public abstract class Http3RequestStreamInboundHandler extends ChannelInboundHandlerAdapter {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(Http3RequestStreamInboundHandler.class);

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http3UnknownFrame) {
            channelRead(ctx, (Http3UnknownFrame) msg);
        } else if (msg instanceof Http3HeadersFrame) {
            channelRead(ctx, (Http3HeadersFrame) msg);
        } else if (msg instanceof Http3DataFrame) {
            channelRead(ctx, (Http3DataFrame) msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ChannelInputShutdownEvent.INSTANCE) {
            channelInputClosed(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof QuicException) {
            handleQuicException(ctx, (QuicException) cause);
        } else if (cause instanceof Http3Exception) {
            handleHttp3Exception(ctx, (Http3Exception) cause);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

    /**
     * Called once a {@link Http3HeadersFrame} is ready for this stream to process.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param frame         the {@link Http3HeadersFrame} that was read
     * @throws Exception    thrown if an error happens during processing.
     */
    protected abstract void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception;

    /**
     * Called once a {@link Http3DataFrame} is ready for this stream to process.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param frame         the {@link Http3DataFrame} that was read
     * @throws Exception    thrown if an error happens during processing.
     */
    protected abstract void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception;

    /**
     * Called once the input is closed and so no more inbound data is received on it.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @throws Exception    thrown if an error happens during processing.
     */
    protected abstract void channelInputClosed(ChannelHandlerContext ctx) throws Exception;

    /**
     * Called once a {@link Http3UnknownFrame} is ready for this stream to process. By default these frames are just
     * released and so dropped on the floor as stated in the RFC. That said you may want to override this method if
     * you use some custom frames which are not part of the main spec.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param frame         the {@link Http3UnknownFrame} that was read
     */
    protected void channelRead(@SuppressWarnings("unused") ChannelHandlerContext ctx, Http3UnknownFrame frame) {
        frame.release();
    }

    /**
     * Called once a {@link QuicException} should be handled.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param exception     the {@link QuicException} that caused the error.
     */
    protected void handleQuicException(@SuppressWarnings("unused") ChannelHandlerContext ctx, QuicException exception) {
        logger.debug("Caught QuicException on channel {}", ctx.channel(), exception);
    }

    /**
     * Called once a {@link Http3Exception} should be handled.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param exception     the {@link Http3Exception} that caused the error.
     */
    protected void handleHttp3Exception(@SuppressWarnings("unused") ChannelHandlerContext ctx,
                                        Http3Exception exception) {
        logger.error("Caught Http3Exception on channel {}", ctx.channel(), exception);
    }

    /**
     * Return the local control stream for this HTTP/3 connection. This can be used to send
     * {@link Http3ControlStreamFrame}s to the remote peer.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @return              the control stream.
     */
    @Nullable
    protected final QuicStreamChannel controlStream(ChannelHandlerContext ctx) {
        return Http3.getLocalControlStream(ctx.channel().parent());
    }
}
