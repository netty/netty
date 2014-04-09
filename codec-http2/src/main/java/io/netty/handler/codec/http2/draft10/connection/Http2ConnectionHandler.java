/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.draft10.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.draft10.Http2Exception.format;
import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.RESERVED_REMOTE;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2StreamException;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2RstStreamFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil;
import io.netty.handler.codec.http2.draft10.frame.Http2GoAwayFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PriorityFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PushPromiseFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2RstStreamFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2SettingsFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2StreamFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2WindowUpdateFrame;
import io.netty.util.ReferenceCountUtil;

public class Http2ConnectionHandler extends ChannelHandlerAdapter {

    private final Http2Connection connection;
    private final InboundFlowController inboundFlow;
    private final OutboundFlowController outboundFlow;
    private boolean initialSettingsSent;
    private boolean initialSettingsReceived;

    public Http2ConnectionHandler(boolean server) {
        this(new DefaultHttp2Connection(server));
    }

    public Http2ConnectionHandler(Http2Connection connection) {
        this(connection, new DefaultInboundFlowController(connection),
                new DefaultOutboundFlowController(connection));
    }

    public Http2ConnectionHandler(final Http2Connection connection,
                                  final InboundFlowController inboundFlow, final OutboundFlowController outboundFlow) {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (inboundFlow == null) {
            throw new NullPointerException("inboundFlow");
        }
        if (outboundFlow == null) {
            throw new NullPointerException("outboundFlow");
        }
        this.connection = connection;
        this.inboundFlow = inboundFlow;
        this.outboundFlow = outboundFlow;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        connection.sendGoAway(ctx, promise, null);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        for (Http2Stream stream : connection.getActiveStreams()) {
            stream.close(ctx, ctx.newSucceededFuture());
        }
        ctx.fireChannelInactive();
    }

    /**
     * Handles {@link Http2Exception} objects that were thrown from other handlers. Ignores all other
     * exceptions.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof Http2Exception) {
            processHttp2Exception(ctx, (Http2Exception) cause);
        }

        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object inMsg) throws Exception {
        try {
            if (inMsg == Http2FrameCodecUtil.CONNECTION_PREFACE) {
                // The connection preface has been received from the remote endpoint, we're
                // beginning an HTTP2 connection. Send the initial settings to the remote endpoint.
                sendInitialSettings(ctx);
            } else if (inMsg instanceof Http2DataFrame) {
                handleInboundData(ctx, (Http2DataFrame) inMsg);
            } else if (inMsg instanceof Http2HeadersFrame) {
                handleInboundHeaders(ctx, (Http2HeadersFrame) inMsg);
            } else if (inMsg instanceof Http2PushPromiseFrame) {
                handleInboundPushPromise(ctx, (Http2PushPromiseFrame) inMsg);
            } else if (inMsg instanceof Http2PriorityFrame) {
                handleInboundPriority(ctx, (Http2PriorityFrame) inMsg);
            } else if (inMsg instanceof Http2RstStreamFrame) {
                handleInboundRstStream(ctx, (Http2RstStreamFrame) inMsg);
            } else if (inMsg instanceof Http2PingFrame) {
                handleInboundPing(ctx, (Http2PingFrame) inMsg);
            } else if (inMsg instanceof Http2GoAwayFrame) {
                handleInboundGoAway(ctx, (Http2GoAwayFrame) inMsg);
            } else if (inMsg instanceof Http2WindowUpdateFrame) {
                handleInboundWindowUpdate(ctx, (Http2WindowUpdateFrame) inMsg);
            } else if (inMsg instanceof Http2SettingsFrame) {
                handleInboundSettings(ctx, (Http2SettingsFrame) inMsg);
            } else {
                ctx.fireChannelRead(inMsg);
            }

        } catch (Http2Exception e) {
            ReferenceCountUtil.release(inMsg);
            processHttp2Exception(ctx, e);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        try {
            if (msg instanceof Http2DataFrame) {
                handleOutboundData(ctx, (Http2DataFrame) msg, promise);
            } else if (msg instanceof Http2HeadersFrame) {
                handleOutboundHeaders(ctx, (Http2HeadersFrame) msg, promise);
            } else if (msg instanceof Http2PushPromiseFrame) {
                handleOutboundPushPromise(ctx, (Http2PushPromiseFrame) msg, promise);
            } else if (msg instanceof Http2PriorityFrame) {
                handleOutboundPriority(ctx, (Http2PriorityFrame) msg, promise);
            } else if (msg instanceof Http2RstStreamFrame) {
                handleOutboundRstStream(ctx, (Http2RstStreamFrame) msg, promise);
            } else if (msg instanceof Http2PingFrame) {
                handleOutboundPing(ctx, (Http2PingFrame) msg, promise);
            } else if (msg instanceof Http2GoAwayFrame) {
                handleOutboundGoAway();
            } else if (msg instanceof Http2WindowUpdateFrame) {
                handleOutboundWindowUpdate();
            } else if (msg instanceof Http2SettingsFrame) {
                handleOutboundSettings(ctx, (Http2SettingsFrame) msg, promise);
            } else {
                ctx.write(msg, promise);
            }

        } catch (Throwable e) {
            ReferenceCountUtil.release(msg);
            promise.setFailure(e);
        }
    }

    /**
     * Processes the given exception. Depending on the type of exception, delegates to either
     * {@link #processConnectionError} or {@link #processStreamError}.
     */
    private void processHttp2Exception(ChannelHandlerContext ctx, Http2Exception e) {
        if (e instanceof Http2StreamException) {
            processStreamError(ctx, (Http2StreamException) e);
        } else {
            processConnectionError(ctx, e);
        }
    }

    private void processConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        connection.sendGoAway(ctx, ctx.newPromise(), cause);
    }

    private void processStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        // Close the stream if it was open.
        int streamId = cause.getStreamId();
        ChannelPromise promise = ctx.newPromise();
        Http2Stream stream = connection.getStream(streamId);
        if (stream != null) {
            stream.close(ctx, promise);
        }

        // Send the Rst frame to the remote endpoint.
        Http2RstStreamFrame frame = new DefaultHttp2RstStreamFrame.Builder().setStreamId(streamId)
                .setErrorCode(cause.getError().getCode()).build();
        ctx.writeAndFlush(frame, promise);
    }

    private void handleInboundData(final ChannelHandlerContext ctx, Http2DataFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        // Check if we received a data frame for a stream which is half-closed
        Http2Stream stream = connection.getStreamOrFail(frame.getStreamId());
        stream.verifyState(STREAM_CLOSED, OPEN, HALF_CLOSED_LOCAL);

        // Apply flow control.
        inboundFlow.applyInboundFlowControl(frame, new InboundFlowController.FrameWriter() {
            @Override
            public void writeFrame(Http2WindowUpdateFrame frame) {
                ctx.writeAndFlush(frame);
            }
        });

        if (isInboundStreamAfterGoAway(frame)) {
            // Ignore frames for any stream created after we sent a go-away.
            frame.release();
            return;
        }

        if (frame.isEndOfStream()) {
            stream.closeRemoteSide(ctx, ctx.newSucceededFuture());
        }

        // Allow this frame to continue other handlers.
        ctx.fireChannelRead(frame);
    }

    private void handleInboundHeaders(ChannelHandlerContext ctx, Http2HeadersFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (isInboundStreamAfterGoAway(frame)) {
            return;
        }

        int streamId = frame.getStreamId();
        Http2Stream stream = connection.getStream(streamId);
        if (stream == null) {
            // Create the new stream.
            connection.remote().createStream(frame.getStreamId(), frame.getPriority(),
                    frame.isEndOfStream());
        } else {
            if (stream.getState() == RESERVED_REMOTE) {
                // Received headers for a reserved push stream ... open it for push to the
                // local endpoint.
                stream.verifyState(PROTOCOL_ERROR, RESERVED_REMOTE);
                stream.openForPush();
            } else {
                // Receiving headers on an existing stream. Make sure the stream is in an allowed
                // state.
                stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_LOCAL);
            }

            // If the headers completes this stream, close it.
            if (frame.isEndOfStream()) {
                stream.closeRemoteSide(ctx, ctx.newSucceededFuture());
            }
        }

        ctx.fireChannelRead(frame);
    }

    private void handleInboundPushPromise(ChannelHandlerContext ctx, Http2PushPromiseFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (isInboundStreamAfterGoAway(frame)) {
            // Ignore frames for any stream created after we sent a go-away.
            return;
        }

        // Reserve the push stream based with a priority based on the current stream's priority.
        Http2Stream parentStream = connection.getStreamOrFail(frame.getStreamId());
        connection.remote().reservePushStream(frame.getPromisedStreamId(), parentStream);

        ctx.fireChannelRead(frame);
    }

    private void handleInboundPriority(ChannelHandlerContext ctx, Http2PriorityFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (isInboundStreamAfterGoAway(frame)) {
            // Ignore frames for any stream created after we sent a go-away.
            return;
        }

        Http2Stream stream = connection.getStream(frame.getStreamId());
        if (stream == null) {
            // Priority frames must be ignored for closed streams.
            return;
        }

        stream.verifyState(PROTOCOL_ERROR, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, OPEN, RESERVED_LOCAL);

        // Set the priority on the frame.
        stream.setPriority(frame.getPriority());

        ctx.fireChannelRead(frame);
    }

    private void handleInboundWindowUpdate(ChannelHandlerContext ctx, Http2WindowUpdateFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (isInboundStreamAfterGoAway(frame)) {
            // Ignore frames for any stream created after we sent a go-away.
            return;
        }

        int streamId = frame.getStreamId();
        if (streamId > 0) {
            Http2Stream stream = connection.getStream(streamId);
            if (stream == null) {
                // Window Update frames must be ignored for closed streams.
                return;
            }
            stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);
        }

        // Update the outbound flow controller.
        outboundFlow.updateOutboundWindowSize(streamId, frame.getWindowSizeIncrement());

        ctx.fireChannelRead(frame);
    }

    private void handleInboundRstStream(ChannelHandlerContext ctx, Http2RstStreamFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (isInboundStreamAfterGoAway(frame)) {
            // Ignore frames for any stream created after we sent a go-away.
            return;
        }

        Http2Stream stream = connection.getStream(frame.getStreamId());
        if (stream == null) {
            // RstStream frames must be ignored for closed streams.
            return;
        }

        stream.close(ctx, ctx.newSucceededFuture());

        ctx.fireChannelRead(frame);
    }

    private void handleInboundPing(ChannelHandlerContext ctx, Http2PingFrame frame)
            throws Http2Exception {
        verifyInitialSettingsReceived();

        if (frame.isAck()) {
            // The remote enpoint is responding to an Ack that we sent.
            ctx.fireChannelRead(frame);
            return;
        }

        // The remote endpoint is sending the ping. Acknowledge receipt.
        DefaultHttp2PingFrame ack = new DefaultHttp2PingFrame.Builder().setAck(true)
                .setData(frame.content().duplicate().retain()).build();
        ctx.writeAndFlush(ack);
    }

    private void handleInboundSettings(ChannelHandlerContext ctx, Http2SettingsFrame frame)
            throws Http2Exception {
        if (frame.isAck()) {
            // Should not get an ack before receiving the initial settings from the remote
            // endpoint.
            verifyInitialSettingsReceived();

            // The remote endpoint is acknowledging the settings - fire this up to the next
            // handler.
            ctx.fireChannelRead(frame);
            return;
        }

        // It's not an ack, apply the settings.
        if (frame.getHeaderTableSize() != null) {
            // TODO(nathanmittler): what's the right thing handle this?
            // headersEncoder.setHeaderTableSize(frame.getHeaderTableSize());
        }

        if (frame.getPushEnabled() != null) {
            connection.remote().setPushToAllowed(frame.getPushEnabled());
        }

        if (frame.getMaxConcurrentStreams() != null) {
            int value = Math.max(0, (int) Math.min(Integer.MAX_VALUE, frame.getMaxConcurrentStreams()));
            connection.local().setMaxStreams(value);
        }

        if (frame.getInitialWindowSize() != null) {
            outboundFlow.setInitialOutboundWindowSize(frame.getInitialWindowSize());
        }

        // Acknowledge receipt of the settings.
        Http2Frame ack = new DefaultHttp2SettingsFrame.Builder().setAck(true).build();
        ctx.writeAndFlush(ack);

        // We've received at least one non-ack settings frame from the remote endpoint.
        initialSettingsReceived = true;
        ctx.fireChannelRead(frame);
    }

    private void handleInboundGoAway(ChannelHandlerContext ctx, Http2GoAwayFrame frame) {
        // Don't allow any more connections to be created.
        connection.goAwayReceived();
        ctx.fireChannelRead(frame);
    }

    /**
     * Determines whether or not the stream was created after we sent a go-away frame. Frames from
     * streams created after we sent a go-away should be ignored. Frames for the connection stream ID
     * (i.e. 0) will always be allowed.
     */
    private boolean isInboundStreamAfterGoAway(Http2StreamFrame frame) {
        return connection.isGoAwaySent()
                && connection.remote().getLastStreamCreated() <= frame.getStreamId();
    }

    private void handleOutboundData(final ChannelHandlerContext ctx, Http2DataFrame frame,
                                    final ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending data after connection going away.");
        }

        Http2Stream stream = connection.getStreamOrFail(frame.getStreamId());
        stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

        // Hand control of the frame to the flow controller.
        outboundFlow.sendFlowControlled(frame, new OutboundFlowController.FrameWriter() {
            @Override
            public void writeFrame(Http2DataFrame frame) {
                ChannelFuture future = ctx.writeAndFlush(frame, promise);

                // Close the connection on write failures that leave the outbound flow control
                // window in a corrupt state.
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            processHttp2Exception(ctx, toHttp2Exception(future.cause()));
                        }
                    }
                });

                // Close the local side of the stream if this is the last frame
                if (frame.isEndOfStream()) {
                    Http2Stream stream = connection.getStream(frame.getStreamId());
                    stream.closeLocalSide(ctx, promise);
                }
            }

            @Override
            public void setFailure(Throwable cause) {
                promise.setFailure(cause);
            }
        });
    }

    private void handleOutboundHeaders(ChannelHandlerContext ctx, Http2HeadersFrame frame,
                                       ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending headers after connection going away.");
        }

        Http2Stream stream = connection.getStream(frame.getStreamId());
        if (stream == null) {
            // Creates a new locally-initiated stream.
            stream = connection.local().createStream(frame.getStreamId(), frame.getPriority(),
                    frame.isEndOfStream());
        } else {
            if (stream.getState() == RESERVED_LOCAL) {
                // Sending headers on a reserved push stream ... open it for push to the remote
                // endpoint.
                stream.openForPush();
            } else {
                // The stream already exists, make sure it's in an allowed state.
                stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);
            }

            // If the headers are the end of the stream, close it now.
            if (frame.isEndOfStream()) {
                stream.closeLocalSide(ctx, promise);
            }
        }
        // Flush to send all of the frames.
        ctx.writeAndFlush(frame, promise);
    }

    private void handleOutboundPushPromise(ChannelHandlerContext ctx, Http2PushPromiseFrame frame,
                                           ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending push promise after connection going away.");
        }

        // Reserve the promised stream.
        Http2Stream stream = connection.getStreamOrFail(frame.getStreamId());
        connection.local().reservePushStream(frame.getPromisedStreamId(), stream);

        // Write the frame.
        ctx.writeAndFlush(frame, promise);
    }

    private void handleOutboundPriority(ChannelHandlerContext ctx, Http2PriorityFrame frame,
                                        ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending priority after connection going away.");
        }

        // Set the priority on the stream and forward the frame.
        Http2Stream stream = connection.getStreamOrFail(frame.getStreamId());
        stream.setPriority(frame.getPriority());
        ctx.writeAndFlush(frame, promise);
    }

    private void handleOutboundRstStream(ChannelHandlerContext ctx, Http2RstStreamFrame frame,
                                         ChannelPromise promise) {
        Http2Stream stream = connection.getStream(frame.getStreamId());
        if (stream == null) {
            // The stream may already have been closed ... ignore.
            promise.setSuccess();
            return;
        }

        stream.close(ctx, promise);
        ctx.writeAndFlush(frame, promise);
    }

    private void handleOutboundPing(ChannelHandlerContext ctx, Http2PingFrame frame,
                                    ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending ping after connection going away.");
        }

        if (frame.isAck()) {
            throw format(PROTOCOL_ERROR, "Another handler attempting to send ping ack");
        }

        // Just pass the frame through.
        ctx.writeAndFlush(frame, promise);
    }

    private static void handleOutboundGoAway() throws Http2Exception {
        // Why is this being sent? Intercept it and fail the write.
        // Should have sent a CLOSE ChannelStateEvent
        throw format(PROTOCOL_ERROR, "Another handler attempted to send GoAway.");
    }

    private static void handleOutboundWindowUpdate() throws Http2Exception {
        // Why is this being sent? Intercept it and fail the write.
        throw format(PROTOCOL_ERROR, "Another handler attempted to send window update.");
    }

    private void handleOutboundSettings(ChannelHandlerContext ctx, Http2SettingsFrame frame,
                                        ChannelPromise promise) throws Http2Exception {
        if (connection.isGoAway()) {
            throw format(PROTOCOL_ERROR, "Sending settings after connection going away.");
        }

        if (frame.isAck()) {
            throw format(PROTOCOL_ERROR, "Another handler attempting to send settings ack");
        }

        if (frame.getPushEnabled() != null) {
            // Enable/disable server push to this endpoint.
            connection.local().setPushToAllowed(frame.getPushEnabled());
        }
        if (frame.getHeaderTableSize() != null) {
            // TODO(nathanmittler): what's the right way to handle this?
            // headersDecoder.setHeaderTableSize(frame.getHeaderTableSize());
        }
        if (frame.getMaxConcurrentStreams() != null) {
            // Update maximum number of streams the remote endpoint can initiate.
            if (frame.getMaxConcurrentStreams() < 0L
                    || frame.getMaxConcurrentStreams() > Integer.MAX_VALUE) {
                throw format(PROTOCOL_ERROR, "Invalid value for max concurrent streams: %d",
                        frame.getMaxConcurrentStreams());
            }
            connection.remote().setMaxStreams(frame.getMaxConcurrentStreams().intValue());
        }
        if (frame.getInitialWindowSize() != null) {
            // Update the initial window size for inbound traffic.
            if (frame.getInitialWindowSize() < 0) {
                throw format(PROTOCOL_ERROR, "Invalid value for initial window size: %d",
                        frame.getInitialWindowSize());
            }
            inboundFlow.setInitialInboundWindowSize(frame.getInitialWindowSize());
        }
        ctx.writeAndFlush(frame, promise);
    }

    private void verifyInitialSettingsReceived() throws Http2Exception {
        if (!initialSettingsReceived) {
            throw protocolError("Received non-SETTINGS as first frame.");
        }
    }

    /**
     * Sends the initial settings frame upon establishment of the connection, if not already sent.
     */
    private void sendInitialSettings(ChannelHandlerContext ctx) throws Http2Exception {
        if (initialSettingsSent) {
            throw protocolError("Already sent initial settings.");
        }

        // Create and send the frame to the remote endpoint.
        DefaultHttp2SettingsFrame frame =
                new DefaultHttp2SettingsFrame.Builder()
                        .setInitialWindowSize(inboundFlow.getInitialInboundWindowSize())
                        .setMaxConcurrentStreams(connection.remote().getMaxStreams())
                        .setPushEnabled(connection.local().isPushToAllowed()).build();
        ctx.writeAndFlush(frame);

        initialSettingsSent = true;
    }
}
