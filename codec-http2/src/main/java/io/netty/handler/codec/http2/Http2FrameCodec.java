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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http2.Http2Connection.PropertyKey;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2ChannelClosedException;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2GoAwayException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.util.ReferenceCountUtil;

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.handler.codec.http2.Http2CodecUtil.isOutboundStream;
import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;

/**
 * <p><em>This API is very immature.</em> The Http2Connection-based API is currently preferred over this API.
 * This API is targeted to eventually replace or reduce the need for the {@link Http2ConnectionHandler} API.
 *
 * <p>A HTTP/2 handler that maps HTTP/2 frames to {@link Http2Frame} objects and vice versa. For every incoming HTTP/2
 * frame, a {@link Http2Frame} object is created and propagated via {@link #channelRead}. Outbound {@link Http2Frame}
 * objects received via {@link #write} are converted to the HTTP/2 wire format. HTTP/2 frames specific to a stream
 * implement the {@link Http2StreamFrame} interface. The {@link Http2FrameCodec} is instantiated using the
 * {@link Http2FrameCodecBuilder}. It's recommended for channel handlers to inherit from the
 * {@link Http2ChannelDuplexHandler}, as it provides additional functionality like iterating over all active streams or
 * creating outbound streams.
 *
 * <h3>Stream Lifecycle</h3>
 *
 * The frame codec delivers and writes frames for active streams. An active stream is closed when either side sends a
 * {@code RST_STREAM} frame or both sides send a frame with the {@code END_STREAM} flag set. Each
 * {@link Http2StreamFrame} has a {@link Http2Stream2} object attached that uniquely identifies a particular stream.
 *
 * <p>Application specific state can be maintained by attaching a custom object to a stream via
 * {@link Http2Stream2#managedState(Object)}. As the name suggests, the state object is cleaned up automatically when a
 * stream or the channel is closed.
 *
 * <p>{@link Http2StreamFrame}s read from the channel always a {@link Http2Stream2} object set, while when writing a
 * {@link Http2StreamFrame} the application code needs to set a {@link Http2Stream2} object using
 * {@link Http2StreamFrame#stream(Http2Stream2)}.
 *
 * <h3>Flow control</h3>
 *
 * The frame codec automatically increments stream and connection flow control windows. It's possible to customize
 * when flow control windows are updated via {@link Http2FrameCodecBuilder#windowUpdateRatio(float)}.
 *
 * <p>Incoming flow controlled frames need to be consumed by writing a {@link Http2WindowUpdateFrame} with the consumed
 * number of bytes and the corresponding stream identifier set to the frame codec.
 *
 * <p>The local stream-level flow control window can be changed by writing a {@link Http2SettingsFrame} with the
 * {@link Http2Settings#initialWindowSize()} set to the targeted value.
 *
 * <p>The connection-level flow control window can be changed by writing a {@link Http2WindowUpdateFrame} with the
 * desired window size <em>increment</em> in bytes and the stream identifier set to {@code 0}. By default the initial
 * connection-level flow control window is the same as initial stream-level flow control window.
 *
 * <h3>New inbound Streams</h3>
 *
 * The first frame of a HTTP/2 stream must be a {@link Http2HeadersFrame}, which will have a {@link Http2Stream2} object
 * attached. An application can detect if it's a new stream by inspecting the {@link Http2Stream2#managedState()} for
 * {@code null}, and if so attach application specific state via {@link Http2Stream2#managedState(Object)}.
 *
 * <pre>
 *      public class MyChannelHandler extends Http2ChannelDuplexHandler {
 *
 *          @Override
 *          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
 *              if (msg instanceof Http2HeadersFrame) {
 *                  Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
 *                  if (msg.stream().managedState() == null) {
 *                      // A new inbound stream.
 *                      msg.stream().managedState(new ApplicationState());
 *                  }
 *              }
 *          }
 *      }
 * </pre>
 *
 * <h3>New outbound Streams</h3>
 *
 * A outbound HTTP/2 stream can be created by first instantiating a new {@link Http2Stream2} object via
 * {@link Http2ChannelDuplexHandler#newStream()}, and then writing a {@link Http2HeadersFrame} object with the stream
 * attached.
 *
 * <pre>
 *     final Http2Stream2 stream = handler.newStream();
 *     ctx.write(headersFrame.stream(stream)).addListener(new ChannelFutureListener() {
 *
 *         @Override
 *         public void operationComplete(ChannelFuture f) {
 *             if (f.isSuccess()) {
 *                 // Stream is active and stream.id() returns a valid stream identifier.
 *                 System.out.println("New stream with id " + stream.id() + " created.");
 *             } else {
 *                 // Stream failed to become active. Handle error.
 *                 if (f.cause() instanceof Http2NoMoreStreamIdsException) {
 *
 *                 } else if (f.cause() instanceof Http2GoAwayException) {
 *
 *                 } else {
 *
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 *
 * <p>If a new stream cannot be created due to stream id exhaustion of the endpoint, the {@link ChannelPromise} of the
 * HEADERS frame will fail with a {@link Http2NoMoreStreamIdsException}.
 *
 * <p>The HTTP/2 standard allows for an endpoint to limit the maximum number of concurrently active streams via the
 * {@code SETTINGS_MAX_CONCURRENT_STREAMS} setting. When this limit is reached, no new streams can be created. However,
 * the {@link Http2FrameCodec} can be build with {@link Http2FrameCodecBuilder#bufferOutgoingStreams} enabled, in which
 * case a new stream and its associated frames will be buffered until either the limit is increased or an active
 * stream is closed. It's, however, possible that a buffered stream will never become active. That is, the channel might
 * get closed or a GO_AWAY frame might be received. In the first case, all writes of buffered streams will fail with a
 * {@link Http2ChannelClosedException}. In the second case, all writes of buffered streams with an identifier less than
 * the last stream identifier of the GO_AWAY frame will fail with a {@link Http2GoAwayException}.
 *
 * <h3>Error Handling</h3>
 *
 * Exceptions and errors are propagated via {@link ChannelInboundHandler#exceptionCaught}. Exceptions that apply to
 * a specific HTTP/2 stream are wrapped in a {@link Http2Stream2Exception} and have the corresponding
 * {@link Http2Stream2} object attached.
 *
 * <h3>Reference Counting</h3>
 *
 * Some {@link Http2StreamFrame}s implement the {@link ReferenceCounted} interface, as they carry
 * reference counted objects (e.g. {@link ByteBuf}s). The frame codec will call {@link ReferenceCounted#retain()} before
 * propagating a reference counted object through the pipeline, and thus an application handler needs to release such
 * an object after having consumed it. For more information on reference counting take a look at
 * http://netty.io/wiki/reference-counted-objects.html
 *
 * <h3>HTTP Upgrade</h3>
 *
 * Server-side HTTP to HTTP/2 upgrade is supported in conjunction with {@link Http2ServerUpgradeCodec}; the necessary
 * HTTP-to-HTTP/2 conversion is performed automatically.
 */
@UnstableApi
public class Http2FrameCodec extends ChannelDuplexHandler {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(Http2FrameCodec.class);

    private final Http2ConnectionHandler http2Handler;
    private final boolean server;
    private final PropertyKey streamKey;

    // Used to adjust flow control window on channel active. Set to null afterwards.
    private Integer initialLocalConnectionWindow;

    private ChannelHandlerContext ctx;
    private ChannelHandlerContext http2HandlerCtx;

    private Http2Stream2Impl pendingOutboundStreamsTail;

    /** Lock protecting modifications to idle outbound streams. **/
    private final Object lock = new Object();

    /** Number of buffered streams if the {@link StreamBufferingEncoder} is used. **/
    private int numBufferedStreams;

    /**
     * Create a new handler. Use {@link Http2FrameCodecBuilder}.
     */
    Http2FrameCodec(Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder, Http2Settings initialSettings,
                    long gracefulShutdownTimeoutMillis) {
        decoder.frameListener(new FrameListener());
        http2Handler = new InternalHttp2ConnectionHandler(decoder, encoder, initialSettings);
        http2Handler.connection().addListener(new ConnectionListener());
        http2Handler.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
        server = http2Handler.connection().isServer();
        streamKey = connection().newKey();
        initialLocalConnectionWindow = initialSettings.initialWindowSize();
    }

    Http2ConnectionHandler connectionHandler() {
        return http2Handler;
    }

    /**
     * Creates a new outbound/local stream.
     *
     * <p>The object is added to a list of idle streams, so that in case the stream object is never made active, the
     * {@link Http2Stream2#closeFuture()} still completes.
     *
     * <p>This method may only be called after the handler has been added to a {@link io.netty.channel.ChannelPipeline}.
     *
     * <p>This method is thread-safe.
     */
    public Http2FrameCodec(boolean server, Http2FrameLogger frameLogger) {
        this(server, new DefaultHttp2FrameWriter(), frameLogger, Http2Settings.defaultSettings());
    }

    // Visible for testing
    Http2FrameCodec(boolean server, Http2FrameWriter frameWriter, Http2FrameLogger frameLogger,
                    Http2Settings initialSettings) {
        // TODO(scott): configure maxReservedStreams when API is more finalized.
        Http2Connection connection = new DefaultHttp2Connection(server);
        frameWriter = new Http2OutboundFrameLogger(frameWriter, frameLogger);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Long maxHeaderListSize = initialSettings.maxHeaderListSize();
        Http2FrameReader frameReader = new DefaultHttp2FrameReader(maxHeaderListSize == null ?
                new DefaultHttp2HeadersDecoder(true) :
                new DefaultHttp2HeadersDecoder(true, maxHeaderListSize));
        Http2FrameReader reader = new Http2InboundFrameLogger(frameReader, frameLogger);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        decoder.frameListener(new FrameListener());
        http2Handler = new InternalHttp2ConnectionHandler(decoder, encoder, initialSettings);
        http2Handler.connection().addListener(new ConnectionListener());
        streamKey = connection().newKey();
        this.server = server;
    }

    // TODO(buchgr): Discuss: Should this method be thread safe?
    Http2Stream2 newStream() {
        ChannelHandlerContext ctx0 = ctx;
        if (ctx0 == null) {
            throw new IllegalStateException("Channel handler not added to a channel pipeline.");
        }

        Http2Stream2Impl stream = new Http2Stream2Impl(ctx0.channel());

        addPendingStream(stream);

        return stream;
    }

    /**
     * Iterates over all active HTTP/2 streams.
     *
     * <p>This method must not be called outside of the event loop.
     */
    void forEachActiveStream(final Http2Stream2Visitor streamVisitor) throws Http2Exception {
        assert ctx.channel().eventLoop().inEventLoop();

        connection().forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) {
                Http2Stream2 stream2 = stream.getProperty(streamKey);
                if (stream2 == null) {
                    /**
                     * This code is expected to almost never execute. However, in rare cases it's possible that a
                     * stream is active without a {@link Http2Stream2} object attached, as it's set in a listener of
                     * the HEADERS frame write.
                     */
                    stream2 = findPendingStream(stream.id());
                    if (stream2 == null) {
                        throw new AssertionError("All active streams must have a stream object attached.");
                    }
                }
                try {
                    return streamVisitor.visit(stream2);
                } catch (Throwable cause) {
                    connectionHandler().onError(http2HandlerCtx, cause);
                    return false;
                }
            }
        });
    }

    /**
     * Load any dependencies.
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        ctx.pipeline().addBefore(ctx.executor(), ctx.name(), null, http2Handler);
        http2HandlerCtx = ctx.pipeline().context(http2Handler);
        sendInitialConnectionWindow();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        sendInitialConnectionWindow();
        super.channelActive(ctx);
    }

    /**
     * Clean up any dependencies.
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanupPendingStreams();
        ctx.pipeline().remove(http2Handler);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanupPendingStreams();
        super.channelInactive(ctx);
    }

    private void sendInitialConnectionWindow() throws Http2Exception {
        if (ctx.channel().isActive() && initialLocalConnectionWindow != null) {
            Http2Stream connectionStream = http2Handler.connection().connectionStream();
            int currentSize = connection().local().flowController().windowSize(connectionStream);
            int delta = initialLocalConnectionWindow - currentSize;
            http2Handler.decoder().flowController().incrementWindowSize(connectionStream, delta);
            initialLocalConnectionWindow = null;
            ctx.flush();
        }
    }

    private Http2Connection connection() {
        return http2Handler.connection();
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
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof Http2Frame)) {
            ctx.write(msg, promise);
            return;
        }
        try {
            if (msg instanceof Http2WindowUpdateFrame) {
                Http2WindowUpdateFrame frame = (Http2WindowUpdateFrame) msg;
                writeWindowUpdate(frame.stream().id(), frame.windowSizeIncrement(), promise);
            } else if (msg instanceof Http2StreamFrame) {
                writeStreamFrame((Http2StreamFrame) msg, promise);
            } else if (msg instanceof Http2PingFrame) {
                writePingFrame((Http2PingFrame) msg, promise);
            } else if (msg instanceof Http2SettingsFrame) {
                writeSettingsFrame((Http2SettingsFrame) msg, promise);
            } else if (msg instanceof Http2GoAwayFrame) {
                writeGoAwayFrame((Http2GoAwayFrame) msg, promise);
            } else {
                throw new UnsupportedMessageTypeException(msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void writePingFrame(Http2PingFrame pingFrame, ChannelPromise promise) {
        http2Handler.encoder().writePing(http2HandlerCtx, pingFrame.ack(), pingFrame.content().retain(), promise);
    }

    private void writeWindowUpdate(int streamId, int bytes, ChannelPromise promise) {
        try {
            if (streamId == 0) {
                increaseInitialConnectionWindow(bytes);
            } else {
                consumeBytes(streamId, bytes);
            }
            promise.setSuccess();
        } catch (Throwable t) {
            promise.setFailure(t);
        }
    }

    private void increaseInitialConnectionWindow(int deltaBytes) throws Http2Exception {
        Http2LocalFlowController localFlow = connection().local().flowController();
        int targetConnectionWindow = localFlow.initialWindowSize() + deltaBytes;
        localFlow.incrementWindowSize(connection().connectionStream(), deltaBytes);
        localFlow.initialWindowSize(targetConnectionWindow);
    }

    private void consumeBytes(int streamId, int bytes) throws Http2Exception {
        Http2Stream stream = http2Handler.connection().stream(streamId);
        http2Handler.connection().local().flowController()
                    .consumeBytes(stream, bytes);
    }

    private void writeSettingsFrame(Http2SettingsFrame frame, ChannelPromise promise) {
        http2Handler.encoder().writeSettings(http2HandlerCtx, frame.settings(), promise);
    }

    private void writeGoAwayFrame(Http2GoAwayFrame frame, ChannelPromise promise) {
        if (frame.lastStreamId() > -1) {
            throw new IllegalArgumentException("Last stream id must not be set on GOAWAY frame");
        }

        int lastStreamCreated = http2Handler.connection().remote().lastStreamCreated();
        int lastStreamId = lastStreamCreated + frame.extraStreamIds() * 2;
        // Check if the computation overflowed.
        if (lastStreamId < lastStreamCreated) {
            lastStreamId = Integer.MAX_VALUE;
        }
        http2Handler.goAway(
                http2HandlerCtx, lastStreamId, frame.errorCode(), frame.content().retain(), promise);
    }

    private void writeStreamFrame(Http2StreamFrame frame, ChannelPromise promise) {
        if (!(frame.stream() instanceof Http2Stream2Impl)) {
            throw new IllegalArgumentException("A stream object created by the frame codec needs to be set. " + frame);
        }

        if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            http2Handler.encoder().writeData(http2HandlerCtx, frame.stream().id(), dataFrame.content().retain(),
                                             dataFrame.padding(), dataFrame.endStream(), promise);
        } else if (frame instanceof Http2HeadersFrame) {
            writeHeadersFrame((Http2HeadersFrame) frame, promise);
        } else if (frame instanceof Http2ResetFrame) {
            Http2ResetFrame rstFrame = (Http2ResetFrame) frame;
            http2Handler.encoder().writeRstStream(http2HandlerCtx, frame.stream().id(), rstFrame.errorCode(), promise);
        } else {
            throw new UnsupportedMessageTypeException(frame);
        }
    }

    private void writeHeadersFrame(Http2HeadersFrame headersFrame, ChannelPromise promise) {
        final int streamId;
        if (isStreamIdValid(headersFrame.stream().id())) {
            streamId = headersFrame.stream().id();
        } else {
            final Http2Stream2Impl stream = (Http2Stream2Impl) headersFrame.stream();
            final Http2Connection connection = http2Handler.connection();
            streamId = connection.local().incrementAndGetNextStreamId();
            if (streamId < 0) {
                promise.setFailure(new Http2NoMoreStreamIdsException());
                return;
            }
            numBufferedStreams++;
            // Set the stream id before completing the promise, as any listener added by a user will be executed
            // before the below listener, and so the stream identifier is accessible in a user's listener.
            stream.id(streamId);
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    numBufferedStreams--;

                    Http2Stream connectionStream = connection.stream(streamId);
                    if (future.isSuccess() && connectionStream != null) {
                        connectionStream.setProperty(streamKey, stream);
                    } else {
                        stream.setClosed();
                    }

                    removePendingStream(stream);
                }
            });
        }
        http2Handler.encoder().writeHeaders(http2HandlerCtx, streamId, headersFrame.headers(), headersFrame.padding(),
                                            headersFrame.endStream(), promise);
    }

    private final class ConnectionListener extends Http2ConnectionAdapter {

        @Override
        public void onStreamActive(Http2Stream stream) {
            if (isOutboundStream(server, stream.id())) {
                return;
            }

            stream.setProperty(streamKey, new Http2Stream2Impl(ctx.channel()).id(stream.id()));
        }

        @Override
        public void onStreamClosed(Http2Stream stream) {
            Http2Stream2Impl stream2 = stream.getProperty(streamKey);
            if (stream2 != null) {
                stream2.setClosed();
            }
        }

        @Override
        public void onGoAwayReceived(final int lastStreamId, long errorCode, ByteBuf debugData) {
            ctx.fireChannelRead(new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, debugData.retain()));
        }
    }

    private final class InternalHttp2ConnectionHandler extends Http2ConnectionHandler {
        InternalHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                       Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        protected void onConnectionError(ChannelHandlerContext ctx, Throwable cause, Http2Exception http2Ex) {
            ctx.fireExceptionCaught(cause);
        }

        /**
         * Exceptions for streams unknown streams, that is streams that have no {@link Http2Stream2} object attached
         * are simply logged and replied to by sending a RST_STREAM frame. There is not much value in propagating such
         * exceptions through the pipeline, as a user will not have any additional information / state about this
         * stream and thus can't do any meaningful error handling.
         */
        @Override
        protected void onStreamError(ChannelHandlerContext ctx, Throwable cause,
                                     Http2Exception.StreamException streamException) {
            int streamId = streamException.streamId();
            Http2Stream connectionStream = connection().stream(streamId);
            if (connectionStream == null) {
                Http2Stream2 stream2 = findPendingStream(streamId);
                if (stream2 == null) {
                    LOG.warn("Stream exception thrown for unkown stream.", cause);
                    // Write a RST_STREAM
                    super.onStreamError(ctx, cause, streamException);
                    return;
                }

                fireHttp2Stream2Exception(stream2, streamException.error(), cause);
            } else {
                Http2Stream2 stream2 = connectionStream.getProperty(streamKey);
                if (stream2 == null) {
                    LOG.warn("Stream exception thrown without stream object attached.", cause);
                    // Write a RST_STREAM
                    super.onStreamError(ctx, cause, streamException);
                    return;
                }

                fireHttp2Stream2Exception(stream2, streamException.error(), cause);
            }
        }

        @Override
        protected boolean isGracefulShutdownComplete() {
            return super.isGracefulShutdownComplete() && numBufferedStreams == 0;
        }

        private void fireHttp2Stream2Exception(Http2Stream2 stream, Http2Error error, Throwable cause) {
            ctx.fireExceptionCaught(new Http2Stream2Exception(stream, error, cause));
        }
    }

    private final class FrameListener extends Http2FrameAdapter {
        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
            ctx.fireChannelRead(new DefaultHttp2SettingsFrame(settings));
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) {
            ctx.fireChannelRead(new DefaultHttp2PingFrame(data.retain(), false));
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) {
            ctx.fireChannelRead(new DefaultHttp2PingFrame(data.retain(), true));
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
            ctx.fireChannelRead(new DefaultHttp2ResetFrame(errorCode).stream(requireStream(streamId)));
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
            if (streamId == 0) {
                // Ignore connection window updates.
                return;
            }
            ctx.fireChannelRead(new DefaultHttp2WindowUpdateFrame(windowSizeIncrement).stream(requireStream(streamId)));
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                                  Http2Headers headers, int streamDependency, short weight, boolean
                                          exclusive, int padding, boolean endStream) {
            onHeadersRead(ctx, streamId, headers, padding, endStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int padding, boolean endOfStream) {
            ctx.fireChannelRead(new DefaultHttp2HeadersFrame(headers, endOfStream, padding)
                                        .stream(requireStream(streamId)));
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) {
            ctx.fireChannelRead(new DefaultHttp2DataFrame(data.retain(), endOfStream, padding)
                                        .stream(requireStream(streamId)));
            // We return the bytes in consumeBytes() once the stream channel consumed the bytes.
            return 0;
        }

        private <V> Http2Stream2 requireStream(int streamId) {
            Http2Stream2 stream = connection().stream(streamId).getProperty(streamKey);
            if (stream == null) {
                throw new IllegalStateException("Stream object required for identifier: " + streamId);
            }
            return stream;
        }
    }

    /**
     * {@link Http2Stream2} implementation.
     */
    static final class Http2Stream2Impl extends DefaultChannelPromise implements Http2Stream2 {

        private Http2Stream2Impl prev;
        private Http2Stream2Impl next;

        private volatile int id = -1;
        private volatile Object managedState;

        Http2Stream2Impl(Channel channel) {
            super(channel);
            setUncancellable();
        }

        @Override
        public Http2Stream2Impl id(int id) {
            if (!isStreamIdValid(id)) {
                throw new IllegalArgumentException("Stream identifier invalid. Was: " + id);
            }
            this.id = id;
            return this;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public Http2Stream2Impl managedState(Object state) {
            managedState = state;
            return this;
        }

        @Override
        public Object managedState() {
            return managedState;
        }

        @Override
        public ChannelFuture closeFuture() {
            return this;
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelPromise setSuccess(Void result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean trySuccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException();
        }

        void setClosed() {
            super.trySuccess();
        }

        @Override
        public String toString() {
            return String.valueOf(id);
        }
    }

    private void addPendingStream(Http2Stream2Impl stream) {
        synchronized (lock) {
            if (pendingOutboundStreamsTail == null) {
                pendingOutboundStreamsTail = stream;
                return;
            }

            pendingOutboundStreamsTail.next = stream;
            stream.prev = pendingOutboundStreamsTail;
        }
    }

    private void removePendingStream(Http2Stream2Impl stream) {
        try {
            synchronized (lock) {
                if (pendingOutboundStreamsTail == null) {
                    return;
                }

                if (pendingOutboundStreamsTail == stream) {
                    pendingOutboundStreamsTail = null;
                }

                stream.prev = stream.next;
                if (stream.next != null) {
                    stream.next.prev = stream.prev;
                }
            }
        } finally {
            // Avoid GC nepotism
            stream.next = null;
            stream.prev = null;
        }
    }

    private Http2Stream2 findPendingStream(int streamId) {
        if (isOutboundStream(server, streamId)) {
            synchronized (lock) {
                Http2Stream2Impl idleStream = pendingOutboundStreamsTail;
                while (idleStream != null) {
                    if (idleStream.id() == streamId) {
                        return idleStream;
                    }
                    idleStream = idleStream.prev;
                }
            }
        }
        return null;
    }

    private void cleanupPendingStreams() {
        synchronized (lock) {
            while (pendingOutboundStreamsTail != null) {
                pendingOutboundStreamsTail.setClosed();
                pendingOutboundStreamsTail = pendingOutboundStreamsTail.prev;
            }
        }
    }
}
