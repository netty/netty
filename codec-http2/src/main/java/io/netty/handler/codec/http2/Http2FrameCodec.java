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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.handler.codec.http2.Http2Connection.PropertyKey;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2ChannelClosedException;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2GoAwayException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;

/**
 * <p><em>This API is very immature.</em> The Http2Connection-based API is currently preferred over this API.
 * This API is targeted to eventually replace or reduce the need for the {@link Http2ConnectionHandler} API.
 *
 * <p>An HTTP/2 handler that maps HTTP/2 frames to {@link Http2Frame} objects and vice versa. For every incoming HTTP/2
 * frame, an {@link Http2Frame} object is created and propagated via {@link #channelRead}. Outbound {@link Http2Frame}
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
 * {@link Http2StreamFrame} has a {@link Http2FrameStream} object attached that uniquely identifies a particular stream.
 *
 * <p>{@link Http2StreamFrame}s read from the channel always a {@link Http2FrameStream} object set, while when writing a
 * {@link Http2StreamFrame} the application code needs to set a {@link Http2FrameStream} object using
 * {@link Http2StreamFrame#stream(Http2FrameStream)}.
 *
 * <h3>Flow control</h3>
 *
 * The frame codec automatically increments stream and connection flow control windows.
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
 * The first frame of an HTTP/2 stream must be an {@link Http2HeadersFrame}, which will have an {@link Http2FrameStream}
 * object attached.
 *
 * <h3>New outbound Streams</h3>
 *
 * A outbound HTTP/2 stream can be created by first instantiating a new {@link Http2FrameStream} object via
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
 * the {@link Http2FrameCodec} can be build with
 * {@link Http2FrameCodecBuilder#encoderEnforceMaxConcurrentStreams(boolean)} enabled, in which case a new stream and
 * its associated frames will be buffered until either the limit is increased or an active stream is closed. It's,
 * however, possible that a buffered stream will never become active. That is, the channel might
 * get closed or a GO_AWAY frame might be received. In the first case, all writes of buffered streams will fail with a
 * {@link Http2ChannelClosedException}. In the second case, all writes of buffered streams with an identifier less than
 * the last stream identifier of the GO_AWAY frame will fail with a {@link Http2GoAwayException}.
 *
 * <h3>Error Handling</h3>
 *
 * Exceptions and errors are propagated via {@link ChannelInboundHandler#exceptionCaught}. Exceptions that apply to
 * a specific HTTP/2 stream are wrapped in a {@link Http2FrameStreamException} and have the corresponding
 * {@link Http2FrameStream} object attached.
 *
 * <h3>Reference Counting</h3>
 *
 * Some {@link Http2StreamFrame}s implement the {@link ReferenceCounted} interface, as they carry
 * reference counted objects (e.g. {@link ByteBuf}s). The frame codec will call {@link ReferenceCounted#retain()} before
 * propagating a reference counted object through the pipeline, and thus an application handler needs to release such
 * an object after having consumed it. For more information on reference counting take a look at
 * https://netty.io/wiki/reference-counted-objects.html
 *
 * <h3>HTTP Upgrade</h3>
 *
 * Server-side HTTP to HTTP/2 upgrade is supported in conjunction with {@link Http2ServerUpgradeCodec}; the necessary
 * HTTP-to-HTTP/2 conversion is performed automatically.
 */
@UnstableApi
public class Http2FrameCodec extends Http2ConnectionHandler {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(Http2FrameCodec.class);

    protected final PropertyKey streamKey;
    private final PropertyKey upgradeKey;

    private final Integer initialFlowControlWindowSize;

    ChannelHandlerContext ctx;

    /** Number of buffered streams if the {@link StreamBufferingEncoder} is used. **/
    private int numBufferedStreams;
    private final IntObjectMap<DefaultHttp2FrameStream> frameStreamToInitializeMap =
            new IntObjectHashMap<DefaultHttp2FrameStream>(8);
    private final ChannelFutureListener bufferedStreamsListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            numBufferedStreams--;
        }
    };

    Http2FrameCodec(Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder, Http2Settings initialSettings,
                    boolean decoupleCloseAndGoAway) {
        super(decoder, encoder, initialSettings, decoupleCloseAndGoAway);

        decoder.frameListener(new FrameListener());
        connection().addListener(new ConnectionListener());
        connection().remote().flowController().listener(new Http2RemoteFlowControllerListener());
        streamKey = connection().newKey();
        upgradeKey = connection().newKey();
        initialFlowControlWindowSize = initialSettings.initialWindowSize();
    }

    /**
     * Creates a new outbound/local stream.
     */
    DefaultHttp2FrameStream newStream() {
        return new DefaultHttp2FrameStream();
    }

    /**
     * Iterates over all active HTTP/2 streams.
     *
     * <p>This method must not be called outside of the event loop.
     */
    final void forEachActiveStream(final Http2FrameStreamVisitor streamVisitor) throws Http2Exception {
        assert ctx.executor().inEventLoop();
        if (connection().numActiveStreams() > 0) {
            connection().forEachActiveStream(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) {
                    try {
                        return streamVisitor.visit((Http2FrameStream) stream.getProperty(streamKey));
                    } catch (Throwable cause) {
                        onError(ctx, false, cause);
                        return false;
                    }
                }
            });
        }
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        super.handlerAdded(ctx);
        handlerAdded0(ctx);
        // Must be after Http2ConnectionHandler does its initialization in handlerAdded above.
        // The server will not send a connection preface so we are good to send a window update.
        Http2Connection connection = connection();
        if (connection.isServer()) {
            tryExpandConnectionFlowControlWindow(connection);
        }
    }

    private void tryExpandConnectionFlowControlWindow(Http2Connection connection) throws Http2Exception {
        if (initialFlowControlWindowSize != null) {
            // The window size in the settings explicitly excludes the connection window. So we manually manipulate the
            // connection window to accommodate more concurrent data per connection.
            Http2Stream connectionStream = connection.connectionStream();
            Http2LocalFlowController localFlowController = connection.local().flowController();
            final int delta = initialFlowControlWindowSize - localFlowController.initialWindowSize(connectionStream);
            // Only increase the connection window, don't decrease it.
            if (delta > 0) {
                // Double the delta just so a single stream can't exhaust the connection window.
                localFlowController.incrementWindowSize(connectionStream, Math.max(delta << 1, delta));
                flush(ctx);
            }
        }
    }

    void handlerAdded0(@SuppressWarnings("unsed") ChannelHandlerContext ctx) throws Exception {
        // sub-class can override this for extra steps that needs to be done when the handler is added.
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via
     * HTTP/2 on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
            // The user event implies that we are on the client.
            tryExpandConnectionFlowControlWindow(connection());

            // We schedule this on the EventExecutor to allow to have any extra handlers added to the pipeline
            // before we pass the event to the next handler. This is needed as the event may be called from within
            // handlerAdded(...) which will be run before other handlers will be added to the pipeline.
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    ctx.fireUserEventTriggered(evt);
                }
            });
        } else if (evt instanceof UpgradeEvent) {
            UpgradeEvent upgrade = (UpgradeEvent) evt;
            try {
                onUpgradeEvent(ctx, upgrade.retain());
                Http2Stream stream = connection().stream(HTTP_UPGRADE_STREAM_ID);
                if (stream.getProperty(streamKey) == null) {
                    // TODO: improve handler/stream lifecycle so that stream isn't active before handler added.
                    // The stream was already made active, but ctx may have been null so it wasn't initialized.
                    // https://github.com/netty/netty/issues/4942
                    onStreamActive0(stream);
                }
                upgrade.upgradeRequest().headers().setInt(
                        HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), HTTP_UPGRADE_STREAM_ID);
                stream.setProperty(upgradeKey, true);
                InboundHttpToHttp2Adapter.handle(
                        ctx, connection(), decoder().frameListener(), upgrade.upgradeRequest().retain());
            } finally {
                upgrade.release();
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    /**
     * Processes all {@link Http2Frame}s. {@link Http2StreamFrame}s may only originate in child
     * streams.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) msg;
            encoder().writeData(ctx, dataFrame.stream().id(), dataFrame.content(),
                    dataFrame.padding(), dataFrame.isEndStream(), promise);
        } else if (msg instanceof Http2HeadersFrame) {
            writeHeadersFrame(ctx, (Http2HeadersFrame) msg, promise);
        } else if (msg instanceof Http2WindowUpdateFrame) {
            Http2WindowUpdateFrame frame = (Http2WindowUpdateFrame) msg;
            Http2FrameStream frameStream = frame.stream();
            // It is legit to send a WINDOW_UPDATE frame for the connection stream. The parent channel doesn't attempt
            // to set the Http2FrameStream so we assume if it is null the WINDOW_UPDATE is for the connection stream.
            try {
                if (frameStream == null) {
                    increaseInitialConnectionWindow(frame.windowSizeIncrement());
                } else {
                    consumeBytes(frameStream.id(), frame.windowSizeIncrement());
                }
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else if (msg instanceof Http2ResetFrame) {
            Http2ResetFrame rstFrame = (Http2ResetFrame) msg;
            int id = rstFrame.stream().id();
            // Only ever send a reset frame if stream may have existed before as otherwise we may send a RST on a
            // stream in an invalid state and cause a connection error.
            if (connection().streamMayHaveExisted(id)) {
                encoder().writeRstStream(ctx, rstFrame.stream().id(), rstFrame.errorCode(), promise);
            } else {
                ReferenceCountUtil.release(rstFrame);
                promise.setFailure(Http2Exception.streamError(
                        rstFrame.stream().id(), Http2Error.PROTOCOL_ERROR, "Stream never existed"));
            }
        } else if (msg instanceof Http2PingFrame) {
            Http2PingFrame frame = (Http2PingFrame) msg;
            encoder().writePing(ctx, frame.ack(), frame.content(), promise);
        } else if (msg instanceof Http2SettingsFrame) {
            encoder().writeSettings(ctx, ((Http2SettingsFrame) msg).settings(), promise);
        } else if (msg instanceof Http2SettingsAckFrame) {
            // In the event of manual SETTINGS ACK is is assumed the encoder will apply the earliest received but not
            // yet ACKed settings.
            encoder().writeSettingsAck(ctx, promise);
        } else if (msg instanceof Http2GoAwayFrame) {
            writeGoAwayFrame(ctx, (Http2GoAwayFrame) msg, promise);
        } else if (msg instanceof Http2UnknownFrame) {
            Http2UnknownFrame unknownFrame = (Http2UnknownFrame) msg;
            encoder().writeFrame(ctx, unknownFrame.frameType(), unknownFrame.stream().id(),
                    unknownFrame.flags(), unknownFrame.content(), promise);
        } else if (!(msg instanceof Http2Frame)) {
            ctx.write(msg, promise);
        } else {
            ReferenceCountUtil.release(msg);
            throw new UnsupportedMessageTypeException(msg);
        }
    }

    private void increaseInitialConnectionWindow(int deltaBytes) throws Http2Exception {
        // The LocalFlowController is responsible for detecting over/under flow.
        connection().local().flowController().incrementWindowSize(connection().connectionStream(), deltaBytes);
    }

    final boolean consumeBytes(int streamId, int bytes) throws Http2Exception {
        Http2Stream stream = connection().stream(streamId);
        // Upgraded requests are ineligible for stream control. We add the null check
        // in case the stream has been deregistered.
        if (stream != null && streamId == Http2CodecUtil.HTTP_UPGRADE_STREAM_ID) {
            Boolean upgraded = stream.getProperty(upgradeKey);
            if (Boolean.TRUE.equals(upgraded)) {
                return false;
            }
        }

        return connection().local().flowController().consumeBytes(stream, bytes);
    }

    private void writeGoAwayFrame(ChannelHandlerContext ctx, Http2GoAwayFrame frame, ChannelPromise promise) {
        if (frame.lastStreamId() > -1) {
            frame.release();
            throw new IllegalArgumentException("Last stream id must not be set on GOAWAY frame");
        }

        int lastStreamCreated = connection().remote().lastStreamCreated();
        long lastStreamId = lastStreamCreated + ((long) frame.extraStreamIds()) * 2;
        // Check if the computation overflowed.
        if (lastStreamId > Integer.MAX_VALUE) {
            lastStreamId = Integer.MAX_VALUE;
        }
        goAway(ctx, (int) lastStreamId, frame.errorCode(), frame.content(), promise);
    }

    private void writeHeadersFrame(
            final ChannelHandlerContext ctx, Http2HeadersFrame headersFrame, final ChannelPromise promise) {

        if (isStreamIdValid(headersFrame.stream().id())) {
            encoder().writeHeaders(ctx, headersFrame.stream().id(), headersFrame.headers(), headersFrame.padding(),
                    headersFrame.isEndStream(), promise);
        } else {
            final DefaultHttp2FrameStream stream = (DefaultHttp2FrameStream) headersFrame.stream();
            final Http2Connection connection = connection();
            final int streamId = connection.local().incrementAndGetNextStreamId();
            if (streamId < 0) {
                promise.setFailure(new Http2NoMoreStreamIdsException());

                // Simulate a GOAWAY being received due to stream exhaustion on this connection. We use the maximum
                // valid stream ID for the current peer.
                onHttp2Frame(ctx, new DefaultHttp2GoAwayFrame(connection.isServer() ? Integer.MAX_VALUE :
                        Integer.MAX_VALUE - 1, NO_ERROR.code(),
                        writeAscii(ctx.alloc(), "Stream IDs exhausted on local stream creation")));
                return;
            }
            stream.id = streamId;

            // Use a Map to store all pending streams as we may have multiple. This is needed as if we would store the
            // stream in a field directly we may override the stored field before onStreamAdded(...) was called
            // and so not correctly set the property for the buffered stream.
            //
            // See https://github.com/netty/netty/issues/8692
            Object old = frameStreamToInitializeMap.put(streamId, stream);

            // We should not re-use ids.
            assert old == null;

            encoder().writeHeaders(ctx, streamId, headersFrame.headers(), headersFrame.padding(),
                    headersFrame.isEndStream(), promise);
            if (!promise.isDone()) {
                numBufferedStreams++;
                promise.addListener(bufferedStreamsListener);
            }
        }
    }

    private void onStreamActive0(Http2Stream stream) {
        if (stream.id() != Http2CodecUtil.HTTP_UPGRADE_STREAM_ID &&
                connection().local().isValidStreamId(stream.id())) {
            return;
        }

        DefaultHttp2FrameStream stream2 = newStream().setStreamAndProperty(streamKey, stream);
        onHttp2StreamStateChanged(ctx, stream2);
    }

    private final class ConnectionListener extends Http2ConnectionAdapter {

        @Override
        public void onStreamAdded(Http2Stream stream) {
            DefaultHttp2FrameStream frameStream = frameStreamToInitializeMap.remove(stream.id());

             if (frameStream != null) {
                 frameStream.setStreamAndProperty(streamKey, stream);
             }
         }

        @Override
        public void onStreamActive(Http2Stream stream) {
            onStreamActive0(stream);
        }

        @Override
        public void onStreamClosed(Http2Stream stream) {
            onHttp2StreamStateChanged0(stream);
        }

        @Override
        public void onStreamHalfClosed(Http2Stream stream) {
            onHttp2StreamStateChanged0(stream);
        }

        private void onHttp2StreamStateChanged0(Http2Stream stream) {
            DefaultHttp2FrameStream stream2 = stream.getProperty(streamKey);
            if (stream2 != null) {
                onHttp2StreamStateChanged(ctx, stream2);
            }
        }
    }

    @Override
    protected void onConnectionError(
            ChannelHandlerContext ctx, boolean outbound, Throwable cause, Http2Exception http2Ex) {
        if (!outbound) {
            // allow the user to handle it first in the pipeline, and then automatically clean up.
            // If this is not desired behavior the user can override this method.
            //
            // We only forward non outbound errors as outbound errors will already be reflected by failing the promise.
            ctx.fireExceptionCaught(cause);
        }
        super.onConnectionError(ctx, outbound, cause, http2Ex);
    }

    /**
     * Exceptions for unknown streams, that is streams that have no {@link Http2FrameStream} object attached
     * are simply logged and replied to by sending a RST_STREAM frame.
     */
    @Override
    protected final void onStreamError(ChannelHandlerContext ctx, boolean outbound, Throwable cause,
                                 Http2Exception.StreamException streamException) {
        int streamId = streamException.streamId();
        Http2Stream connectionStream = connection().stream(streamId);
        if (connectionStream == null) {
            onHttp2UnknownStreamError(ctx, cause, streamException);
            // Write a RST_STREAM
            super.onStreamError(ctx, outbound, cause, streamException);
            return;
        }

        Http2FrameStream stream = connectionStream.getProperty(streamKey);
        if (stream == null) {
            LOG.warn("Stream exception thrown without stream object attached.", cause);
            // Write a RST_STREAM
            super.onStreamError(ctx, outbound, cause, streamException);
            return;
        }

        if (!outbound) {
            // We only forward non outbound errors as outbound errors will already be reflected by failing the promise.
            onHttp2FrameStreamException(ctx, new Http2FrameStreamException(stream, streamException.error(), cause));
        }
    }

    private void onHttp2UnknownStreamError(@SuppressWarnings("unused") ChannelHandlerContext ctx, Throwable cause,
                                   Http2Exception.StreamException streamException) {
        // Just log....
        LOG.warn("Stream exception thrown for unknown stream {}.", streamException.streamId(), cause);
    }

    @Override
    protected final boolean isGracefulShutdownComplete() {
        return super.isGracefulShutdownComplete() && numBufferedStreams == 0;
    }

    private final class FrameListener implements Http2FrameListener {

        @Override
        public void onUnknownFrame(
                ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
            onHttp2Frame(ctx, new DefaultHttp2UnknownFrame(frameType, flags, payload)
                    .stream(requireStream(streamId)).retain());
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
            onHttp2Frame(ctx, new DefaultHttp2SettingsFrame(settings));
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) {
            onHttp2Frame(ctx, new DefaultHttp2PingFrame(data, false));
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) {
            onHttp2Frame(ctx, new DefaultHttp2PingFrame(data, true));
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
            onHttp2Frame(ctx, new DefaultHttp2ResetFrame(errorCode).stream(requireStream(streamId)));
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
            if (streamId == 0) {
                // Ignore connection window updates.
                return;
            }
            onHttp2Frame(ctx, new DefaultHttp2WindowUpdateFrame(windowSizeIncrement).stream(requireStream(streamId)));
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
            onHttp2Frame(ctx, new DefaultHttp2HeadersFrame(headers, endOfStream, padding)
                                        .stream(requireStream(streamId)));
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) {
            onHttp2Frame(ctx, new DefaultHttp2DataFrame(data, endOfStream, padding)
                                        .stream(requireStream(streamId)).retain());
            // We return the bytes in consumeBytes() once the stream channel consumed the bytes.
            return 0;
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
            onHttp2Frame(ctx, new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, debugData).retain());
        }

        @Override
        public void onPriorityRead(
                ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
            // TODO: Maybe handle me
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) {
            onHttp2Frame(ctx, Http2SettingsAckFrame.INSTANCE);
        }

        @Override
        public void onPushPromiseRead(
                ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding)  {
            // TODO: Maybe handle me
        }

        private Http2FrameStream requireStream(int streamId) {
            Http2FrameStream stream = connection().stream(streamId).getProperty(streamKey);
            if (stream == null) {
                throw new IllegalStateException("Stream object required for identifier: " + streamId);
            }
            return stream;
        }
    }

    private void onUpgradeEvent(ChannelHandlerContext ctx, UpgradeEvent evt) {
        ctx.fireUserEventTriggered(evt);
    }

    private void onHttp2StreamWritabilityChanged(ChannelHandlerContext ctx, DefaultHttp2FrameStream stream,
                                         @SuppressWarnings("unused") boolean writable) {
        ctx.fireUserEventTriggered(stream.writabilityChanged);
    }

    void onHttp2StreamStateChanged(ChannelHandlerContext ctx, DefaultHttp2FrameStream stream) {
        ctx.fireUserEventTriggered(stream.stateChanged);
    }

    void onHttp2Frame(ChannelHandlerContext ctx, Http2Frame frame) {
        ctx.fireChannelRead(frame);
    }

    void onHttp2FrameStreamException(ChannelHandlerContext ctx, Http2FrameStreamException cause) {
        ctx.fireExceptionCaught(cause);
    }

    private final class Http2RemoteFlowControllerListener implements Http2RemoteFlowController.Listener {
        @Override
        public void writabilityChanged(Http2Stream stream) {
            DefaultHttp2FrameStream frameStream = stream.getProperty(streamKey);
            if (frameStream == null) {
                return;
            }
            onHttp2StreamWritabilityChanged(
                    ctx, frameStream, connection().remote().flowController().isWritable(stream));
        }
    }

    /**
     * {@link Http2FrameStream} implementation.
     */
    // TODO(buchgr): Merge Http2FrameStream and Http2Stream.
    static class DefaultHttp2FrameStream implements Http2FrameStream {

        private volatile int id = -1;
        volatile Http2Stream stream;

        final Http2FrameStreamEvent stateChanged = Http2FrameStreamEvent.stateChanged(this);
        final Http2FrameStreamEvent writabilityChanged = Http2FrameStreamEvent.writabilityChanged(this);

        Channel attachment;

        DefaultHttp2FrameStream setStreamAndProperty(PropertyKey streamKey, Http2Stream stream) {
            assert id == -1 || stream.id() == id;
            this.stream = stream;
            stream.setProperty(streamKey, this);
            return this;
        }

        @Override
        public int id() {
            Http2Stream stream = this.stream;
            return stream == null ? id : stream.id();
        }

        @Override
        public State state() {
            Http2Stream stream = this.stream;
            return stream == null ? State.IDLE : stream.state();
        }

        @Override
        public String toString() {
            return String.valueOf(id());
        }
    }
}
