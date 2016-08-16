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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.handler.codec.http2.Http2Connection.Endpoint;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.isOutboundStream;
import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty.handler.logging.LogLevel.INFO;

/**
 * An HTTP/2 handler that maps HTTP/2 frames to {@link Http2Frame} objects and vice versa. For every incoming HTTP/2
 * frame a {@link Http2Frame} object is created and propagated via {@link #channelRead}. Outbound {@link Http2Frame}
 * objects received via {@link #write} are converted to the HTTP/2 wire format.
 *
 * <p>A change in stream state is propagated through the channel pipeline as a user event via
 * {@link Http2StreamStateEvent} objects. When a HTTP/2 stream first becomes active a {@link Http2StreamActiveEvent}
 * and when it gets closed a {@link Http2StreamClosedEvent} is emitted.
 *
 * <p>Server-side HTTP to HTTP/2 upgrade is supported in conjunction with {@link Http2ServerUpgradeCodec}; the necessary
 * HTTP-to-HTTP/2 conversion is performed automatically.
 *
 * <p><em>This API is very immature.</em> The Http2Connection-based API is currently preferred over
 * this API. This API is targeted to eventually replace or reduce the need for the Http2Connection-based API.
 *
 * <h3>Opening and Closing Streams</h3>
 *
 * <p>When the remote side opens a new stream, the frame codec first emits a {@link Http2StreamActiveEvent} with the
 * stream identifier set.
 * <pre>
 * {@link Http2FrameCodec}                                     {@link Http2MultiplexCodec}
 *        +                                                             +
 *        |         Http2StreamActiveEvent(streamId=3, headers=null)    |
 *        +------------------------------------------------------------->
 *        |                                                             |
 *        |         Http2HeadersFrame(streamId=3)                       |
 *        +------------------------------------------------------------->
 *        |                                                             |
 *        +                                                             +
 * </pre>
 *
 * <p>When a stream is closed either due to a reset frame by the remote side, or due to both sides having sent frames
 * with the END_STREAM flag, then the frame codec emits a {@link Http2StreamClosedEvent}.
 * <pre>
 * {@link Http2FrameCodec}                                 {@link Http2MultiplexCodec}
 *        +                                                         +
 *        |         Http2StreamClosedEvent(streamId=3)              |
 *        +--------------------------------------------------------->
 *        |                                                         |
 *        +                                                         +
 * </pre>
 *
 * <p>When the local side wants to close a stream, it has to write a {@link Http2ResetFrame} to which the frame codec
 * will respond to with a {@link Http2StreamClosedEvent}.
 * <pre>
 * {@link Http2FrameCodec}                                 {@link Http2MultiplexCodec}
 *        +                                                         +
 *        |         Http2ResetFrame(streamId=3)                     |
 *        <---------------------------------------------------------+
 *        |                                                         |
 *        |         Http2StreamClosedEvent(streamId=3)              |
 *        +--------------------------------------------------------->
 *        |                                                         |
 *        +                                                         +
 * </pre>
 *
 * <p>Opening an outbound/local stream works by first sending the frame codec a {@link Http2HeadersFrame} with no
 * stream identifier set (such that {@link Http2CodecUtil#isStreamIdValid} returns {@code false}). If opening the stream
 * was successful, the frame codec responds with a {@link Http2StreamActiveEvent} that contains the stream's new
 * identifier as well as the <em>same</em> {@link Http2HeadersFrame} object that opened the stream.
 * <pre>
 * {@link Http2FrameCodec}                                                                  {@link Http2MultiplexCodec}
 *        +                                                                                               +
 *        |         Http2HeadersFrame(streamId=-1)                                                        |
 *        <-----------------------------------------------------------------------------------------------+
 *        |                                                                                               |
 *        |         Http2StreamActiveEvent(streamId=2, headers=Http2HeadersFrame(streamId=-1))            |
 *        +----------------------------------------------------------------------------------------------->
 *        |                                                                                               |
 *        +                                                                                               +
 * </pre>
 */
@UnstableApi
public class Http2FrameCodec extends ChannelDuplexHandler {

    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new Http2FrameLogger(INFO, Http2FrameCodec.class);

    private final Http2ConnectionHandler http2Handler;
    private final boolean server;

    private ChannelHandlerContext ctx;
    private ChannelHandlerContext http2HandlerCtx;

    /**
     * Construct a new handler.
     *
     * @param server {@code true} this is a server
     */
    public Http2FrameCodec(boolean server) {
        this(server, HTTP2_FRAME_LOGGER);
    }

    /**
     * Construct a new handler.
     *
     * @param server {@code true} this is a server
     */
    public Http2FrameCodec(boolean server, Http2FrameLogger frameLogger) {
        this(server, new DefaultHttp2FrameWriter(), frameLogger);
    }

    // Visible for testing
    Http2FrameCodec(boolean server, Http2FrameWriter frameWriter, Http2FrameLogger frameLogger) {
        Http2Connection connection = new DefaultHttp2Connection(server);
        frameWriter = new Http2OutboundFrameLogger(frameWriter, frameLogger);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2FrameReader frameReader = new DefaultHttp2FrameReader();
        Http2FrameReader reader = new Http2InboundFrameLogger(frameReader, frameLogger);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        decoder.frameListener(new FrameListener());
        http2Handler = new InternalHttp2ConnectionHandler(decoder, encoder, new Http2Settings());
        http2Handler.connection().addListener(new ConnectionListener());
        this.server = server;
    }

    Http2ConnectionHandler connectionHandler() {
        return http2Handler;
    }

    /**
     * Load any dependencies.
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

    // Override this to signal it will never throw an exception.
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

    /**
     * Processes all {@link Http2Frame}s. {@link Http2StreamFrame}s may only originate in child
     * streams.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            if (msg instanceof Http2WindowUpdateFrame) {
                Http2WindowUpdateFrame frame = (Http2WindowUpdateFrame) msg;
                consumeBytes(frame.streamId(), frame.windowSizeIncrement(), promise);
            } else if (msg instanceof Http2StreamFrame) {
                writeStreamFrame((Http2StreamFrame) msg, promise);
            } else if (msg instanceof Http2GoAwayFrame) {
                writeGoAwayFrame((Http2GoAwayFrame) msg, promise);
            } else {
                throw new UnsupportedMessageTypeException(msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void consumeBytes(int streamId, int bytes, ChannelPromise promise) {
        try {
            Http2Stream stream = http2Handler.connection().stream(streamId);
            http2Handler.connection().local().flowController()
                        .consumeBytes(stream, bytes);
            promise.setSuccess();
        } catch (Throwable t) {
            promise.setFailure(t);
        }
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
        if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            http2Handler.encoder().writeData(http2HandlerCtx, frame.streamId(), dataFrame.content().retain(),
                                             dataFrame.padding(), dataFrame.isEndStream(), promise);
        } else if (frame instanceof Http2HeadersFrame) {
            writeHeadersFrame((Http2HeadersFrame) frame, promise);
        } else if (frame instanceof Http2ResetFrame) {
            Http2ResetFrame rstFrame = (Http2ResetFrame) frame;
            http2Handler.resetStream(http2HandlerCtx, frame.streamId(), rstFrame.errorCode(), promise);
        } else {
            throw new UnsupportedMessageTypeException(frame);
        }
    }

    private void writeHeadersFrame(Http2HeadersFrame headersFrame, ChannelPromise promise) {
        int streamId = headersFrame.streamId();
        if (!isStreamIdValid(streamId)) {
            final Endpoint<Http2LocalFlowController> localEndpoint = http2Handler.connection().local();
            streamId = localEndpoint.incrementAndGetNextStreamId();
            try {
                // Try to create a stream in OPEN state before writing headers, to catch errors on stream creation
                // early on i.e. max concurrent streams limit reached, stream id exhaustion, etc.
                localEndpoint.createStream(streamId, false);
            } catch (Http2Exception e) {
                promise.setFailure(e);
                return;
            }
            ctx.fireUserEventTriggered(new Http2StreamActiveEvent(streamId, headersFrame));
        }
        http2Handler.encoder().writeHeaders(http2HandlerCtx, streamId, headersFrame.headers(),
                                            headersFrame.padding(), headersFrame.isEndStream(), promise);
    }

    private final class ConnectionListener extends Http2ConnectionAdapter {
        @Override
        public void onStreamActive(Http2Stream stream) {
            if (ctx == null) {
                // UPGRADE stream is active before handlerAdded().
                return;
            }
            if (isOutboundStream(server, stream.id())) {
                // Creation of outbound streams is notified in writeHeadersFrame().
                return;
            }
            ctx.fireUserEventTriggered(new Http2StreamActiveEvent(stream.id()));
        }

        @Override
        public void onStreamClosed(Http2Stream stream) {
            ctx.fireUserEventTriggered(new Http2StreamClosedEvent(stream.id()));
        }

        @Override
        public void onGoAwayReceived(final int lastStreamId, long errorCode, ByteBuf debugData) {
            ctx.fireChannelRead(new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, debugData.retain()));
        }
    }

    private static final class InternalHttp2ConnectionHandler extends Http2ConnectionHandler {
        InternalHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                       Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        protected void onStreamError(ChannelHandlerContext ctx, Throwable cause,
                                     Http2Exception.StreamException http2Ex) {
            try {
                Http2Stream stream = connection().stream(http2Ex.streamId());
                if (stream == null) {
                    return;
                }
                ctx.fireExceptionCaught(http2Ex);
            } finally {
                super.onStreamError(ctx, cause, http2Ex);
            }
        }
    }

    private static final class FrameListener extends Http2FrameAdapter {
        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
            Http2ResetFrame rstFrame = new DefaultHttp2ResetFrame(errorCode);
            rstFrame.streamId(streamId);
            ctx.fireChannelRead(rstFrame);
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
            Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers, endOfStream, padding);
            headersFrame.streamId(streamId);
            ctx.fireChannelRead(headersFrame);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) {
            Http2DataFrame dataFrame = new DefaultHttp2DataFrame(data.retain(), endOfStream, padding);
            dataFrame.streamId(streamId);
            ctx.fireChannelRead(dataFrame);

            // We return the bytes in bytesConsumed() once the stream channel consumed the bytes.
            return 0;
        }
    }
}
