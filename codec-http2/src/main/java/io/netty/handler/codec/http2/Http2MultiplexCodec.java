/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import static io.netty.handler.logging.LogLevel.INFO;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelHandlerInvokerUtil;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.OneTimeTask;

import java.util.ArrayList;
import java.util.List;

/**
 * An HTTP/2 handler that creates child channels for each stream. Creating outgoing streams is not
 * yet supported. Server-side HTTP to HTTP/2 upgrade is supported in conjunction with {@link
 * Http2ServerUpgradeCodec}; the necessary HTTP-to-HTTP/2 conversion is performed automatically.
 *
 * <p><em>This API is very immature.</em> The Http2Connection-based API is currently preferred over
 * this API. This API is targeted to eventually replace or reduce the need for the
 * Http2Connection-based API.
 *
 * <p>This handler notifies the pipeline of channel events, such as {@link Http2GoAwayFrame}. It
 * is also capable of writing such messages. Directly writing {@link Http2StreamFrame}s for this
 * handler is unsupported.
 *
 * <h3>Child Channels</h3>
 *
 * <p>When a new stream is created, a new {@link Channel} is created for it. Applications send and
 * receive {@link Http2StreamFrame}s on the created channel. The {@link Http2StreamFrame#stream} is
 * expected to be {@code null}, but the channel can use the field for its own bookkeeping. {@link
 * ByteBuf}s cannot be processed by the channel; all writes that reach the head of the pipeline must
 * be an instance of {@link Http2StreamFrame}.  Writes that reach the head of the pipeline are
 * processed directly by this handler and cannot be intercepted.
 *
 * <p>The child channel will be notified of user events that impact the stream, such as {@link
 * Http2GoAwayFrame} and {@link Http2ResetFrame}, as soon as they occur. Although {@code
 * Http2GoAwayFrame} and {@code Http2ResetFrame} signify that the remote is ignoring further
 * communication, closing of the channel is delayed until any inbound queue is drained with {@link
 * Channel#read()}, which follows the default behavior of channels in Netty. Applications are
 * free to close the channel in response to such events if they don't have use for any queued
 * messages.
 *
 * <p>{@link ChannelConfig#setMaxMessagesPerRead(int)} and {@link
 * ChannelConfig#setAutoRead(boolean)} are supported.
 */
public final class Http2MultiplexCodec extends ChannelDuplexHandler {
    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new Http2FrameLogger(INFO, Http2MultiplexCodec.class);

    private final ChannelHandler streamHandler;
    private final EventLoopGroup streamGroup;
    private final Http2ConnectionHandler http2Handler;
    private final Http2Connection.PropertyKey streamInfoKey;
    private final List<StreamInfo> streamsToFireChildReadComplete = new ArrayList<StreamInfo>();
    private ChannelHandlerContext ctx;
    private ChannelHandlerContext http2HandlerCtx;

    /**
     * Construct a new handler whose child channels run in the same event loop as this handler.
     *
     * @param server {@code true} this is a server
     * @param streamHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}.
     */
    public Http2MultiplexCodec(boolean server, ChannelHandler streamHandler) {
        this(server, streamHandler, null);
    }

    /**
     * Construct a new handler whose child channels run in a different event loop.
     *
     * @param server {@code true} this is a server
     * @param streamHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}.
     * @param streamGroup event loop for registering child channels
     */
    public Http2MultiplexCodec(boolean server, ChannelHandler streamHandler,
            EventLoopGroup streamGroup) {
        this(server, streamHandler, streamGroup, new DefaultHttp2FrameWriter());
    }

    Http2MultiplexCodec(boolean server, ChannelHandler streamHandler,
            EventLoopGroup streamGroup, Http2FrameWriter frameWriter) {
        if (!streamHandler.getClass().isAnnotationPresent(Sharable.class)) {
            throw new IllegalArgumentException("streamHandler must be Sharable");
        }
        this.streamHandler = streamHandler;
        this.streamGroup = streamGroup;
        Http2Connection connection = new DefaultHttp2Connection(server);
        frameWriter = new Http2OutboundFrameLogger(frameWriter, HTTP2_FRAME_LOGGER);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2FrameReader reader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), HTTP2_FRAME_LOGGER);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        decoder.frameListener(new FrameListener());
        http2Handler = new InternalHttp2ConnectionHandler(decoder, encoder, new Http2Settings());
        http2Handler.connection().addListener(new ConnectionListener());
        streamInfoKey = http2Handler.connection().newKey();
    }

    Http2ConnectionHandler connectionHandler() {
        return http2Handler;
    }

    /**
     * Save context and load any dependencies.
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        ctx.pipeline().addBefore(ctx.executor(), ctx.name(), null, http2Handler);
        http2HandlerCtx = ctx.pipeline().context(http2Handler);
    }

    /**
     * Clean up any dependencies.
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      ctx.pipeline().remove(http2Handler);
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via
     * HTTP/2 on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!(evt instanceof UpgradeEvent)) {
            super.userEventTriggered(ctx, evt);
            return;
        }

        UpgradeEvent upgrade = (UpgradeEvent) evt;
        ctx.fireUserEventTriggered(upgrade.retain());
        try {
            Http2Stream stream = http2Handler.connection().stream(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID);
            // TODO: improve handler/stream lifecycle so that stream isn't active before handler added.
            // The stream was already made active, but ctx may have been null so it wasn't initialized.
            // https://github.com/netty/netty/issues/4942
            new ConnectionListener().onStreamActive(stream);
            upgrade.upgradeRequest().headers().setInt(
                HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), Http2CodecUtil.HTTP_UPGRADE_STREAM_ID);
            new InboundHttpToHttp2Adapter(http2Handler.connection(), http2Handler.decoder().frameListener())
                .channelRead(ctx, upgrade.upgradeRequest().retain());
        } finally {
            upgrade.release();
        }
    }

    /**
     * Processes all {@link Http2Frame}s. {@link Http2StreamFrame}s may only originate in child
     * streams.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (!(msg instanceof Http2Frame)) {
            super.write(ctx, msg, promise);
            return;
        }
        try {
            if (msg instanceof Http2StreamFrame) {
                Object streamObject = ((Http2StreamFrame) msg).stream();
                int streamId = ((Http2StreamChannel) streamObject).stream.id();
                if (msg instanceof Http2DataFrame) {
                    Http2DataFrame frame = (Http2DataFrame) msg;
                    http2Handler.encoder().writeData(http2HandlerCtx, streamId, frame.content().retain(),
                        frame.padding(), frame.isEndStream(), promise);
                } else if (msg instanceof Http2HeadersFrame) {
                    Http2HeadersFrame frame = (Http2HeadersFrame) msg;
                    http2Handler.encoder().writeHeaders(
                        http2HandlerCtx, streamId, frame.headers(), frame.padding(), frame.isEndStream(), promise);
                } else if (msg instanceof Http2ResetFrame) {
                    Http2ResetFrame frame = (Http2ResetFrame) msg;
                    http2Handler.resetStream(http2HandlerCtx, streamId, frame.errorCode(), promise);
                } else {
                    throw new UnsupportedMessageTypeException(msg);
                }
            } else if (msg instanceof Http2GoAwayFrame) {
                Http2GoAwayFrame frame = (Http2GoAwayFrame) msg;
                int lastStreamId = http2Handler.connection().remote().lastStreamCreated()
                    + frame.extraStreamIds() * 2;
                http2Handler.goAway(
                    http2HandlerCtx, lastStreamId, frame.errorCode(), frame.content().retain(), promise);
            } else {
                throw new UnsupportedMessageTypeException(msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    ChannelFuture createStreamChannel(ChannelHandlerContext ctx, Http2Stream stream,
            ChannelHandler handler) {
        EventLoopGroup group = streamGroup != null ? streamGroup : ctx.channel().eventLoop();
        Http2StreamChannel channel = new Http2StreamChannel(stream);
        channel.pipeline().addLast(handler);
        ChannelFuture future = group.register(channel);
        // Handle any errors that occurred on the local thread while registering. Even though
        // failures can happen after this point, they will be handled by the channel by closing the
        // channel.
        if (future.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }
        return future;
    }

    /**
     * Notifies any child streams of the read completion.
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        for (int i = 0; i < streamsToFireChildReadComplete.size(); i++) {
            final StreamInfo streamInfo = streamsToFireChildReadComplete.get(i);
            // Clear early in case fireChildReadComplete() causes it to need to be re-processed
            streamInfo.inStreamsToFireChildReadComplete = false;
            streamInfo.childChannel.fireChildReadComplete();
        }
        streamsToFireChildReadComplete.clear();
    }

    void fireChildReadAndRegister(StreamInfo streamInfo, Object msg) {
        // Can't use childChannel.fireChannelRead() as it would fire independent of whether
        // channel.read() had been called.
        streamInfo.childChannel.fireChildRead(msg);
        if (!streamInfo.inStreamsToFireChildReadComplete) {
            streamsToFireChildReadComplete.add(streamInfo);
            streamInfo.inStreamsToFireChildReadComplete = true;
        }
    }

    final class ConnectionListener extends Http2ConnectionAdapter {
        @Override
        public void onStreamActive(Http2Stream stream) {
            if (ctx == null) {
                // UPGRADE stream is active before handlerAdded().
                return;
            }
            // If it is an outgoing stream, then we already created the channel.
            // TODO: support outgoing streams. https://github.com/netty/netty/issues/4913
            if (stream.getProperty(streamInfoKey) != null) {
                return;
            }
            ChannelFuture future = createStreamChannel(ctx, stream, streamHandler);
            stream.setProperty(streamInfoKey, new StreamInfo((Http2StreamChannel) future.channel()));
        }

        @Override
        public void onStreamClosed(Http2Stream stream) {
            final StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
            if (streamInfo != null) {
                final EventLoop eventLoop = streamInfo.childChannel.eventLoop();
                if (eventLoop.inEventLoop()) {
                    onStreamClosed0(streamInfo);
                } else {
                    eventLoop.execute(new OneTimeTask() {
                        @Override
                        public void run() {
                            onStreamClosed0(streamInfo);
                        }
                    });
                }
            }
        }

        private void onStreamClosed0(StreamInfo streamInfo) {
            streamInfo.childChannel.onStreamClosedFired = true;
            streamInfo.childChannel.fireChildRead(AbstractHttp2StreamChannel.CLOSE_MESSAGE);
        }

        @Override
        public void onGoAwayReceived(final int lastStreamId, long errorCode, ByteBuf debugData) {
            final Http2GoAwayFrame goAway = new DefaultHttp2GoAwayFrame(errorCode, debugData);
            try {
                http2Handler.connection().forEachActiveStream(new Http2StreamVisitor() {
                    @Override
                    public boolean visit(Http2Stream stream) {
                        if (stream.id() > lastStreamId
                                && http2Handler.connection().local().isValidStreamId(stream.id())) {
                            StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
                            // TODO: Can we force a user interaction pattern that doesn't require us to duplicate()?
                            // https://github.com/netty/netty/issues/4943
                            streamInfo.childChannel.pipeline().fireUserEventTriggered(goAway.duplicate().retain());
                        }
                        return true;
                    }
                });
            } catch (Throwable t) {
                ctx.invoker().invokeExceptionCaught(ctx, t);
            }
            ctx.fireUserEventTriggered(goAway.duplicate().retain());
        }
    }

    class InternalHttp2ConnectionHandler extends Http2ConnectionHandler {
        public InternalHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        protected void onStreamError(ChannelHandlerContext ctx, Throwable cause,
                Http2Exception.StreamException http2Ex) {
            try {
                Http2Stream stream = http2Handler.connection().stream(http2Ex.streamId());
                if (stream == null) {
                    return;
                }
                StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
                if (streamInfo == null) {
                    return;
                }
                streamInfo.childChannel.pipeline().fireExceptionCaught(http2Ex);
            } finally {
                super.onStreamError(ctx, cause, http2Ex);
            }
        }
    }

    class FrameListener extends Http2FrameAdapter {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            Http2Stream stream = http2Handler.connection().stream(streamId);
            StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
            // Use a user event in order to circumvent read queue.
            streamInfo.childChannel.pipeline().fireUserEventTriggered(new DefaultHttp2ResetFrame(errorCode));
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                Http2Headers headers, int streamDependency, short weight, boolean
                exclusive, int padding, boolean endStream) throws Http2Exception {
            onHeadersRead(ctx, streamId, headers, padding, endStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                int padding, boolean endOfStream) throws Http2Exception {
            Http2Stream stream = http2Handler.connection().stream(streamId);
            StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
            fireChildReadAndRegister(streamInfo, new DefaultHttp2HeadersFrame(headers, endOfStream, padding));
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream) throws Http2Exception {
            Http2Stream stream = http2Handler.connection().stream(streamId);
            StreamInfo streamInfo = (StreamInfo) stream.getProperty(streamInfoKey);
            fireChildReadAndRegister(streamInfo, new DefaultHttp2DataFrame(data.retain(), endOfStream, padding));
            // We return the bytes in bytesConsumed() once the stream channel consumed the bytes.
            return 0;
        }
    }

    static final class StreamInfo {
        final Http2StreamChannel childChannel;
        /**
         * {@code true} if stream is in {@link Http2MultiplexCodec#steamsToFireChildReadComplete}.
         */
        boolean inStreamsToFireChildReadComplete;

        StreamInfo(Http2StreamChannel childChannel) {
            this.childChannel = childChannel;
        }
    }

    // This class uses ctx.invoker().invoke* instead of ctx.* to send to the ctx's handler instead
    // of the 'next' handler.
    final class Http2StreamChannel extends AbstractHttp2StreamChannel {
        private final Http2Stream stream;
        boolean onStreamClosedFired;

        Http2StreamChannel(Http2Stream stream) {
            super(ctx.channel());
            this.stream = stream;
        }

        @Override
        protected void doClose() throws Exception {
            if (!onStreamClosedFired) {
                Http2StreamFrame resetFrame = new DefaultHttp2ResetFrame(Http2Error.CANCEL).setStream(this);
                ChannelHandlerInvoker invoker = ctx.invoker();
                invoker.invokeWrite(ctx, resetFrame, ctx.newPromise());
                invoker.invokeFlush(ctx);
            }
            super.doClose();
        }

        @Override
        protected void doWrite(Object msg) {
            if (!(msg instanceof Http2StreamFrame)) {
                ReferenceCountUtil.release(msg);
                throw new IllegalArgumentException("Message must be an Http2StreamFrame: " + msg);
            }
            Http2StreamFrame frame = (Http2StreamFrame) msg;
            if (frame.stream() != null) {
                ReferenceCountUtil.release(frame);
                throw new IllegalArgumentException("Stream must be null on the frame");
            }
            frame.setStream(this);
            ctx.invoker().invokeWrite(ctx, frame, ctx.newPromise());
        }

        @Override
        protected void doWriteComplete() {
            ctx.invoker().invokeFlush(ctx);
        }

        @Override
        protected EventExecutor preferredEventExecutor() {
            return ctx.executor();
        }

        @Override
        protected void bytesConsumed(final int bytes) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                bytesConsumed0(bytes);
            } else {
                executor.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        bytesConsumed0(bytes);
                    }
                });
            }
        }

        private void bytesConsumed0(int bytes) {
            try {
                http2Handler.connection().local().flowController().consumeBytes(stream, bytes);
            } catch (Throwable t) {
                ChannelHandlerInvokerUtil.invokeExceptionCaughtNow(ctx, t);
            }
        }
    }
}
