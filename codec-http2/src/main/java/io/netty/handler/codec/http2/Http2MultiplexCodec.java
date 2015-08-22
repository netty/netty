/*
 * Copyright 2015 The Netty Project
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
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
public class Http2MultiplexCodec extends ChannelHandlerAdapter {
    private static final Http2FrameLogger HTTP2_FRAME_LOGGER
        = new Http2FrameLogger(INFO, Http2MultiplexCodec.class);

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
        http2Handler = new InternalHttp2ConnectionHandler(new DefaultHttp2Connection(server),
                new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), HTTP2_FRAME_LOGGER),
                new Http2OutboundFrameLogger(frameWriter, HTTP2_FRAME_LOGGER),
                new FrameListener());
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
        String name = ctx.name() + "-Http2ConnectionHandler";
        if (ctx.pipeline().context(name) != null) {
          throw new Exception("Name conflict: " + name);
        }
        ctx.pipeline().addBefore(ctx.invoker(), ctx.name(), name, http2Handler);
        this.http2HandlerCtx = ctx.pipeline().context(http2Handler);
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
            new ConnectionListener().onStreamActive(stream);
            upgrade.upgradeRequest().headers().setInt(
                HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), Http2CodecUtil.HTTP_UPGRADE_STREAM_ID);
            new InboundHttpToHttp2Adapter(http2Handler.connection(), http2Handler.decoder().listener())
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
            boolean unexpected = false;
            if (msg instanceof Http2StreamFrame) {
                Object streamObject = ((Http2StreamFrame) msg).stream();
                if (!(streamObject instanceof Http2StreamChannel)) {
                    throw new IllegalArgumentException(
                        "Http2StreamFrame.stream() has invalid type: " + streamObject.getClass().getName());
                }
                int streamId = ((Http2StreamChannel) streamObject).stream.id();
                if (msg instanceof Http2DataFrame) {
                    Http2DataFrame frame = (Http2DataFrame) msg;
                    http2Handler.encoder().writeData(http2HandlerCtx, streamId, frame.content().retain(),
                        frame.padding(), frame.endStream(), promise);
                } else if (msg instanceof Http2HeadersFrame) {
                    Http2HeadersFrame frame = (Http2HeadersFrame) msg;
                    http2Handler.encoder().writeHeaders(
                        http2HandlerCtx, streamId, frame.headers(), frame.padding(), frame.endStream(), promise);
                } else if (msg instanceof Http2ResetFrame) {
                    Http2ResetFrame frame = (Http2ResetFrame) msg;
                    http2Handler.resetStream(http2HandlerCtx, streamId, frame.errorCode(), promise);
                } else {
                    unexpected = true;
                }
            } else if (msg instanceof Http2GoAwayFrame) {
                Http2GoAwayFrame frame = (Http2GoAwayFrame) msg;
                int lastStreamId = http2Handler.connection().remote().lastStreamCreated()
                    + frame.extraStreamIds() * 2;
                http2Handler.goAway(
                    http2HandlerCtx, lastStreamId, frame.errorCode(), frame.content().retain(), promise);
            } else {
                unexpected = true;
            }
            if (unexpected) {
                throw new IllegalStateException("Unexpected Http2Frame type: " + msg.getClass().getName());
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    Http2StreamChannel createStreamChannel(ChannelHandlerContext ctx, Http2Stream stream,
            ChannelHandler handler) {
        EventLoopGroup group = streamGroup != null ? streamGroup : ctx.channel().eventLoop();
        Http2StreamChannel channel = new Http2StreamChannel(stream);
        channel.pipeline().addLast(handler);
        group.register(channel);
        return channel;
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
            // TODO: support outgoing streams
            if (stream.getProperty(streamInfoKey) != null) {
                return;
            }
            stream.setProperty(streamInfoKey,
                new StreamInfo(createStreamChannel(ctx, stream, streamHandler)));
        }

        @Override
        public void onStreamClosed(Http2Stream stream) {
            final StreamInfo streamInfo = (StreamInfo) stream.removeProperty(streamInfoKey);
            if (streamInfo != null) {
                if (streamInfo.childChannel.eventLoop().inEventLoop()) {
                    onStreamClosed0(streamInfo);
                } else {
                    streamInfo.childChannel.eventLoop().execute(new OneTimeTask() {
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
                            if (streamInfo != null) {
                                streamInfo.childChannel.pipeline()
                                    .fireUserEventTriggered(goAway.retain());
                            }
                        }
                        return true;
                    }
                });
            } catch (Throwable t) {
                ctx.invoker().invokeExceptionCaught(ctx, t);
            }
            ctx.fireUserEventTriggered(goAway.retain());
        }
    }

    class InternalHttp2ConnectionHandler extends Http2ConnectionHandler {
        public InternalHttp2ConnectionHandler(Http2Connection connection,
                Http2FrameReader frameReader, Http2FrameWriter frameWriter,
                Http2FrameListener listener) {
            super(connection, frameReader, frameWriter, listener);
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
            return 0;
        }
    }

    static final class StreamInfo {
        final Http2StreamChannel childChannel;
        /**
         * True if stream is in {@link Http2MultiplexCodec#steamsToFireChildReadComplete}.
         */
        boolean inStreamsToFireChildReadComplete;

        StreamInfo(Http2StreamChannel childChannel) {
            this.childChannel = childChannel;
        }
    }

    // This class uses ctx.invoker().invoke* instead of ctx.* to send to the ctx's handler instead
    // of the 'next' handler.
    class Http2StreamChannel extends AbstractHttp2StreamChannel {
        private final Http2Stream stream;
        boolean onStreamClosedFired;

        Http2StreamChannel(Http2Stream stream) {
            super(Http2MultiplexCodec.this.ctx.channel());
            this.stream = stream;
        }

        @Override
        protected void doClose() throws Exception {
            if (!onStreamClosedFired) {
                Http2StreamFrame resetFrame = new DefaultHttp2ResetFrame(Http2Error.CANCEL).setStream(this);
                ctx.invoker().invokeWrite(ctx, resetFrame, ctx.newPromise());
                ctx.invoker().invokeFlush(ctx);
            }
            super.doClose();
        }

        @Override
        protected void doWrite(Object msg) {
            if (!(msg instanceof Http2StreamFrame)) {
                throw new IllegalArgumentException(
                        "Unable to handle message type " + msg.getClass().getName());
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
        protected EventExecutor doWritePreferredEventExecutor() {
            return ctx.invoker().executor();
        }

        @Override
        protected void bytesConsumed(final int bytes) {
            if (ctx.invoker().executor().inEventLoop()) {
                bytesConsumed0(bytes);
            } else {
                ctx.invoker().executor().execute(new OneTimeTask() {
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
                ctx.invoker().invokeExceptionCaught(ctx, t);
            }
        }
    }
}
