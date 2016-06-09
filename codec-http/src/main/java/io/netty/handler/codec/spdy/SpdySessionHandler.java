/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.ThrowableUtil;

import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Manages streams within a SPDY session.
 */
public class SpdySessionHandler
        extends ChannelDuplexHandler {

    private static final SpdyProtocolException PROTOCOL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new SpdyProtocolException(), SpdySessionHandler.class, "handleOutboundMessage(...)");
    private static final SpdyProtocolException STREAM_CLOSED = ThrowableUtil.unknownStackTrace(
            new SpdyProtocolException("Stream closed"), SpdySessionHandler.class, "removeStream(...)");

    private static final int DEFAULT_WINDOW_SIZE = 64 * 1024; // 64 KB default initial window size
    private int initialSendWindowSize    = DEFAULT_WINDOW_SIZE;
    private int initialReceiveWindowSize = DEFAULT_WINDOW_SIZE;
    private volatile int initialSessionReceiveWindowSize = DEFAULT_WINDOW_SIZE;

    private final SpdySession spdySession = new SpdySession(initialSendWindowSize, initialReceiveWindowSize);
    private int lastGoodStreamId;

    private static final int DEFAULT_MAX_CONCURRENT_STREAMS = Integer.MAX_VALUE;
    private int remoteConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private int localConcurrentStreams  = DEFAULT_MAX_CONCURRENT_STREAMS;

    private final AtomicInteger pings = new AtomicInteger();

    private boolean sentGoAwayFrame;
    private boolean receivedGoAwayFrame;

    private ChannelFutureListener closeSessionFutureListener;

    private final boolean server;
    private final int minorVersion;

    /**
     * Creates a new session handler.
     *
     * @param version the protocol version
     * @param server  {@code true} if and only if this session handler should
     *                handle the server endpoint of the connection.
     *                {@code false} if and only if this session handler should
     *                handle the client endpoint of the connection.
     */
    public SpdySessionHandler(SpdyVersion version, boolean server) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.server = server;
        minorVersion = version.getMinorVersion();
    }

    public void setSessionReceiveWindowSize(int sessionReceiveWindowSize) {
      if (sessionReceiveWindowSize < 0) {
        throw new IllegalArgumentException("sessionReceiveWindowSize");
      }
      // This will not send a window update frame immediately.
      // If this value increases the allowed receive window size,
      // a WINDOW_UPDATE frame will be sent when only half of the
      // session window size remains during data frame processing.
      // If this value decreases the allowed receive window size,
      // the window will be reduced as data frames are processed.
      initialSessionReceiveWindowSize = sessionReceiveWindowSize;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SpdyDataFrame) {

            /*
             * SPDY Data frame processing requirements:
             *
             * If an endpoint receives a data frame for a Stream-ID which is not open
             * and the endpoint has not sent a GOAWAY frame, it must issue a stream error
             * with the error code INVALID_STREAM for the Stream-ID.
             *
             * If an endpoint which created the stream receives a data frame before receiving
             * a SYN_REPLY on that stream, it is a protocol error, and the recipient must
             * issue a stream error with the getStatus code PROTOCOL_ERROR for the Stream-ID.
             *
             * If an endpoint receives multiple data frames for invalid Stream-IDs,
             * it may close the session.
             *
             * If an endpoint refuses a stream it must ignore any data frames for that stream.
             *
             * If an endpoint receives a data frame after the stream is half-closed from the
             * sender, it must send a RST_STREAM frame with the getStatus STREAM_ALREADY_CLOSED.
             *
             * If an endpoint receives a data frame after the stream is closed, it must send
             * a RST_STREAM frame with the getStatus PROTOCOL_ERROR.
             */
            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamId = spdyDataFrame.streamId();

            int deltaWindowSize = -1 * spdyDataFrame.content().readableBytes();
            int newSessionWindowSize =
                spdySession.updateReceiveWindowSize(SPDY_SESSION_STREAM_ID, deltaWindowSize);

            // Check if session window size is reduced beyond allowable lower bound
            if (newSessionWindowSize < 0) {
                issueSessionError(ctx, SpdySessionStatus.PROTOCOL_ERROR);
                return;
            }

            // Send a WINDOW_UPDATE frame if less than half the session window size remains
            if (newSessionWindowSize <= initialSessionReceiveWindowSize / 2) {
                int sessionDeltaWindowSize = initialSessionReceiveWindowSize - newSessionWindowSize;
                spdySession.updateReceiveWindowSize(SPDY_SESSION_STREAM_ID, sessionDeltaWindowSize);
                SpdyWindowUpdateFrame spdyWindowUpdateFrame =
                    new DefaultSpdyWindowUpdateFrame(SPDY_SESSION_STREAM_ID, sessionDeltaWindowSize);
                ctx.writeAndFlush(spdyWindowUpdateFrame);
            }

            // Check if we received a data frame for a Stream-ID which is not open

            if (!spdySession.isActiveStream(streamId)) {
                spdyDataFrame.release();
                if (streamId <= lastGoodStreamId) {
                    issueStreamError(ctx, streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                } else if (!sentGoAwayFrame) {
                    issueStreamError(ctx, streamId, SpdyStreamStatus.INVALID_STREAM);
                }
                return;
            }

            // Check if we received a data frame for a stream which is half-closed

            if (spdySession.isRemoteSideClosed(streamId)) {
                spdyDataFrame.release();
                issueStreamError(ctx, streamId, SpdyStreamStatus.STREAM_ALREADY_CLOSED);
                return;
            }

            // Check if we received a data frame before receiving a SYN_REPLY
            if (!isRemoteInitiatedId(streamId) && !spdySession.hasReceivedReply(streamId)) {
                spdyDataFrame.release();
                issueStreamError(ctx, streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            /*
             * SPDY Data frame flow control processing requirements:
             *
             * Recipient should not send a WINDOW_UPDATE frame as it consumes the last data frame.
             */

            // Update receive window size
            int newWindowSize = spdySession.updateReceiveWindowSize(streamId, deltaWindowSize);

            // Window size can become negative if we sent a SETTINGS frame that reduces the
            // size of the transfer window after the peer has written data frames.
            // The value is bounded by the length that SETTINGS frame decrease the window.
            // This difference is stored for the session when writing the SETTINGS frame
            // and is cleared once we send a WINDOW_UPDATE frame.
            if (newWindowSize < spdySession.getReceiveWindowSizeLowerBound(streamId)) {
                spdyDataFrame.release();
                issueStreamError(ctx, streamId, SpdyStreamStatus.FLOW_CONTROL_ERROR);
                return;
            }

            // Window size became negative due to sender writing frame before receiving SETTINGS
            // Send data frames upstream in initialReceiveWindowSize chunks
            if (newWindowSize < 0) {
                while (spdyDataFrame.content().readableBytes() > initialReceiveWindowSize) {
                    SpdyDataFrame partialDataFrame = new DefaultSpdyDataFrame(streamId,
                            spdyDataFrame.content().readSlice(initialReceiveWindowSize).retain());
                    ctx.writeAndFlush(partialDataFrame);
                }
            }

            // Send a WINDOW_UPDATE frame if less than half the stream window size remains
            if (newWindowSize <= initialReceiveWindowSize / 2 && !spdyDataFrame.isLast()) {
                int streamDeltaWindowSize = initialReceiveWindowSize - newWindowSize;
                spdySession.updateReceiveWindowSize(streamId, streamDeltaWindowSize);
                SpdyWindowUpdateFrame spdyWindowUpdateFrame =
                        new DefaultSpdyWindowUpdateFrame(streamId, streamDeltaWindowSize);
                ctx.writeAndFlush(spdyWindowUpdateFrame);
            }

            // Close the remote side of the stream if this is the last frame
            if (spdyDataFrame.isLast()) {
                halfCloseStream(streamId, true, ctx.newSucceededFuture());
            }

        } else if (msg instanceof SpdySynStreamFrame) {

            /*
             * SPDY SYN_STREAM frame processing requirements:
             *
             * If an endpoint receives a SYN_STREAM with a Stream-ID that is less than
             * any previously received SYN_STREAM, it must issue a session error with
             * the getStatus PROTOCOL_ERROR.
             *
             * If an endpoint receives multiple SYN_STREAM frames with the same active
             * Stream-ID, it must issue a stream error with the getStatus code PROTOCOL_ERROR.
             *
             * The recipient can reject a stream by sending a stream error with the
             * getStatus code REFUSED_STREAM.
             */

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamId = spdySynStreamFrame.streamId();

            // Check if we received a valid SYN_STREAM frame
            if (spdySynStreamFrame.isInvalid() ||
                !isRemoteInitiatedId(streamId) ||
                spdySession.isActiveStream(streamId)) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            // Stream-IDs must be monotonically increasing
            if (streamId <= lastGoodStreamId) {
                issueSessionError(ctx, SpdySessionStatus.PROTOCOL_ERROR);
                return;
            }

            // Try to accept the stream
            byte priority = spdySynStreamFrame.priority();
            boolean remoteSideClosed = spdySynStreamFrame.isLast();
            boolean localSideClosed = spdySynStreamFrame.isUnidirectional();
            if (!acceptStream(streamId, priority, remoteSideClosed, localSideClosed)) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.REFUSED_STREAM);
                return;
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            /*
             * SPDY SYN_REPLY frame processing requirements:
             *
             * If an endpoint receives multiple SYN_REPLY frames for the same active Stream-ID
             * it must issue a stream error with the getStatus code STREAM_IN_USE.
             */

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamId = spdySynReplyFrame.streamId();

            // Check if we received a valid SYN_REPLY frame
            if (spdySynReplyFrame.isInvalid() ||
                isRemoteInitiatedId(streamId) ||
                spdySession.isRemoteSideClosed(streamId)) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.INVALID_STREAM);
                return;
            }

            // Check if we have received multiple frames for the same Stream-ID
            if (spdySession.hasReceivedReply(streamId)) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.STREAM_IN_USE);
                return;
            }

            spdySession.receivedReply(streamId);

            // Close the remote side of the stream if this is the last frame
            if (spdySynReplyFrame.isLast()) {
                halfCloseStream(streamId, true, ctx.newSucceededFuture());
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            /*
             * SPDY RST_STREAM frame processing requirements:
             *
             * After receiving a RST_STREAM on a stream, the receiver must not send
             * additional frames on that stream.
             *
             * An endpoint must not send a RST_STREAM in response to a RST_STREAM.
             */

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            removeStream(spdyRstStreamFrame.streamId(), ctx.newSucceededFuture());

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;

            int settingsMinorVersion = spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_MINOR_VERSION);
            if (settingsMinorVersion >= 0 && settingsMinorVersion != minorVersion) {
                // Settings frame had the wrong minor version
                issueSessionError(ctx, SpdySessionStatus.PROTOCOL_ERROR);
                return;
            }

            int newConcurrentStreams =
                spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS);
            if (newConcurrentStreams >= 0) {
                remoteConcurrentStreams = newConcurrentStreams;
            }

            // Persistence flag are inconsistent with the use of SETTINGS to communicate
            // the initial window size. Remove flags from the sender requesting that the
            // value be persisted. Remove values that the sender indicates are persisted.
            if (spdySettingsFrame.isPersisted(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE)) {
                spdySettingsFrame.removeValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE);
            }
            spdySettingsFrame.setPersistValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE, false);

            int newInitialWindowSize =
                spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE);
            if (newInitialWindowSize >= 0) {
                updateInitialSendWindowSize(newInitialWindowSize);
            }

        } else if (msg instanceof SpdyPingFrame) {

            /*
             * SPDY PING frame processing requirements:
             *
             * Receivers of a PING frame should send an identical frame to the sender
             * as soon as possible.
             *
             * Receivers of a PING frame must ignore frames that it did not initiate
             */

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;

            if (isRemoteInitiatedId(spdyPingFrame.id())) {
                ctx.writeAndFlush(spdyPingFrame);
                return;
            }

            // Note: only checks that there are outstanding pings since uniqueness is not enforced
            if (pings.get() == 0) {
                return;
            }
            pings.getAndDecrement();

        } else if (msg instanceof SpdyGoAwayFrame) {

            receivedGoAwayFrame = true;

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamId = spdyHeadersFrame.streamId();

            // Check if we received a valid HEADERS frame
            if (spdyHeadersFrame.isInvalid()) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            if (spdySession.isRemoteSideClosed(streamId)) {
                issueStreamError(ctx, streamId, SpdyStreamStatus.INVALID_STREAM);
                return;
            }

            // Close the remote side of the stream if this is the last frame
            if (spdyHeadersFrame.isLast()) {
                halfCloseStream(streamId, true, ctx.newSucceededFuture());
            }

        } else if (msg instanceof SpdyWindowUpdateFrame) {

            /*
             * SPDY WINDOW_UPDATE frame processing requirements:
             *
             * Receivers of a WINDOW_UPDATE that cause the window size to exceed 2^31
             * must send a RST_STREAM with the getStatus code FLOW_CONTROL_ERROR.
             *
             * Sender should ignore all WINDOW_UPDATE frames associated with a stream
             * after sending the last frame for the stream.
             */

            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            int streamId = spdyWindowUpdateFrame.streamId();
            int deltaWindowSize = spdyWindowUpdateFrame.deltaWindowSize();

            // Ignore frames for half-closed streams
            if (streamId != SPDY_SESSION_STREAM_ID && spdySession.isLocalSideClosed(streamId)) {
                return;
            }

            // Check for numerical overflow
            if (spdySession.getSendWindowSize(streamId) > Integer.MAX_VALUE - deltaWindowSize) {
                if (streamId == SPDY_SESSION_STREAM_ID) {
                    issueSessionError(ctx, SpdySessionStatus.PROTOCOL_ERROR);
                } else {
                    issueStreamError(ctx, streamId, SpdyStreamStatus.FLOW_CONTROL_ERROR);
                }
                return;
            }

            updateSendWindowSize(ctx, streamId, deltaWindowSize);
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        for (Integer streamId: spdySession.activeStreams().keySet()) {
            removeStream(streamId, ctx.newSucceededFuture());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof SpdyProtocolException) {
            issueSessionError(ctx, SpdySessionStatus.PROTOCOL_ERROR);
        }

        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        sendGoAwayFrame(ctx, promise);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof SpdyDataFrame ||
            msg instanceof SpdySynStreamFrame ||
            msg instanceof SpdySynReplyFrame ||
            msg instanceof SpdyRstStreamFrame ||
            msg instanceof SpdySettingsFrame ||
            msg instanceof SpdyPingFrame ||
            msg instanceof SpdyGoAwayFrame ||
            msg instanceof SpdyHeadersFrame ||
            msg instanceof SpdyWindowUpdateFrame) {

            handleOutboundMessage(ctx, msg, promise);
        } else {
            ctx.write(msg, promise);
        }
    }

    private void handleOutboundMessage(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamId = spdyDataFrame.streamId();

            // Frames must not be sent on half-closed streams
            if (spdySession.isLocalSideClosed(streamId)) {
                spdyDataFrame.release();
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            /*
             * SPDY Data frame flow control processing requirements:
             *
             * Sender must not send a data frame with data length greater
             * than the transfer window size.
             *
             * After sending each data frame, the sender decrements its
             * transfer window size by the amount of data transmitted.
             *
             * When the window size becomes less than or equal to 0, the
             * sender must pause transmitting data frames.
             */

            int dataLength = spdyDataFrame.content().readableBytes();
            int sendWindowSize = spdySession.getSendWindowSize(streamId);
            int sessionSendWindowSize = spdySession.getSendWindowSize(SPDY_SESSION_STREAM_ID);
            sendWindowSize = Math.min(sendWindowSize, sessionSendWindowSize);

            if (sendWindowSize <= 0) {
                // Stream is stalled -- enqueue Data frame and return
                spdySession.putPendingWrite(streamId, new SpdySession.PendingWrite(spdyDataFrame, promise));
                return;
            } else if (sendWindowSize < dataLength) {
                // Stream is not stalled but we cannot send the entire frame
                spdySession.updateSendWindowSize(streamId, -1 * sendWindowSize);
                spdySession.updateSendWindowSize(SPDY_SESSION_STREAM_ID, -1 * sendWindowSize);

                // Create a partial data frame whose length is the current window size
                SpdyDataFrame partialDataFrame = new DefaultSpdyDataFrame(streamId,
                        spdyDataFrame.content().readSlice(sendWindowSize).retain());

                // Enqueue the remaining data (will be the first frame queued)
                spdySession.putPendingWrite(streamId, new SpdySession.PendingWrite(spdyDataFrame, promise));

                // The transfer window size is pre-decremented when sending a data frame downstream.
                // Close the session on write failures that leave the transfer window in a corrupt state.
                final ChannelHandlerContext context = ctx;
                ctx.write(partialDataFrame).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            issueSessionError(context, SpdySessionStatus.INTERNAL_ERROR);
                        }
                    }
                });
                return;
            } else {
                // Window size is large enough to send entire data frame
                spdySession.updateSendWindowSize(streamId, -1 * dataLength);
                spdySession.updateSendWindowSize(SPDY_SESSION_STREAM_ID, -1 * dataLength);

                // The transfer window size is pre-decremented when sending a data frame downstream.
                // Close the session on write failures that leave the transfer window in a corrupt state.
                final ChannelHandlerContext context = ctx;
                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            issueSessionError(context, SpdySessionStatus.INTERNAL_ERROR);
                        }
                    }
                });
            }

            // Close the local side of the stream if this is the last frame
            if (spdyDataFrame.isLast()) {
                halfCloseStream(streamId, false, promise);
            }

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamId = spdySynStreamFrame.streamId();

            if (isRemoteInitiatedId(streamId)) {
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            byte priority = spdySynStreamFrame.priority();
            boolean remoteSideClosed = spdySynStreamFrame.isUnidirectional();
            boolean localSideClosed = spdySynStreamFrame.isLast();
            if (!acceptStream(streamId, priority, remoteSideClosed, localSideClosed)) {
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamId = spdySynReplyFrame.streamId();

            // Frames must not be sent on half-closed streams
            if (!isRemoteInitiatedId(streamId) || spdySession.isLocalSideClosed(streamId)) {
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            // Close the local side of the stream if this is the last frame
            if (spdySynReplyFrame.isLast()) {
                halfCloseStream(streamId, false, promise);
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            removeStream(spdyRstStreamFrame.streamId(), promise);

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;

            int settingsMinorVersion = spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_MINOR_VERSION);
            if (settingsMinorVersion >= 0 && settingsMinorVersion != minorVersion) {
                // Settings frame had the wrong minor version
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            int newConcurrentStreams =
                    spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS);
            if (newConcurrentStreams >= 0) {
                localConcurrentStreams = newConcurrentStreams;
            }

            // Persistence flag are inconsistent with the use of SETTINGS to communicate
            // the initial window size. Remove flags from the sender requesting that the
            // value be persisted. Remove values that the sender indicates are persisted.
            if (spdySettingsFrame.isPersisted(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE)) {
                spdySettingsFrame.removeValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE);
            }
            spdySettingsFrame.setPersistValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE, false);

            int newInitialWindowSize =
                    spdySettingsFrame.getValue(SpdySettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE);
            if (newInitialWindowSize >= 0) {
                updateInitialReceiveWindowSize(newInitialWindowSize);
            }

        } else if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            if (isRemoteInitiatedId(spdyPingFrame.id())) {
                ctx.fireExceptionCaught(new IllegalArgumentException(
                            "invalid PING ID: " + spdyPingFrame.id()));
                return;
            }
            pings.getAndIncrement();

        } else if (msg instanceof SpdyGoAwayFrame) {

            // Why is this being sent? Intercept it and fail the write.
            // Should have sent a CLOSE ChannelStateEvent
            promise.setFailure(PROTOCOL_EXCEPTION);
            return;

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamId = spdyHeadersFrame.streamId();

            // Frames must not be sent on half-closed streams
            if (spdySession.isLocalSideClosed(streamId)) {
                promise.setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            // Close the local side of the stream if this is the last frame
            if (spdyHeadersFrame.isLast()) {
                halfCloseStream(streamId, false, promise);
            }

        } else if (msg instanceof SpdyWindowUpdateFrame) {

            // Why is this being sent? Intercept it and fail the write.
            promise.setFailure(PROTOCOL_EXCEPTION);
            return;
        }

        ctx.write(msg, promise);
    }

    /*
     * SPDY Session Error Handling:
     *
     * When a session error occurs, the endpoint encountering the error must first
     * send a GOAWAY frame with the Stream-ID of the most recently received stream
     * from the remote endpoint, and the error code for why the session is terminating.
     *
     * After sending the GOAWAY frame, the endpoint must close the TCP connection.
     */
    private void issueSessionError(
            ChannelHandlerContext ctx, SpdySessionStatus status) {

        sendGoAwayFrame(ctx, status).addListener(new ClosingChannelFutureListener(ctx, ctx.newPromise()));
    }

    /*
     * SPDY Stream Error Handling:
     *
     * Upon a stream error, the endpoint must send a RST_STREAM frame which contains
     * the Stream-ID for the stream where the error occurred and the error getStatus which
     * caused the error.
     *
     * After sending the RST_STREAM, the stream is closed to the sending endpoint.
     *
     * Note: this is only called by the worker thread
     */
    private void issueStreamError(ChannelHandlerContext ctx, int streamId, SpdyStreamStatus status) {
        boolean fireChannelRead = !spdySession.isRemoteSideClosed(streamId);
        ChannelPromise promise = ctx.newPromise();
        removeStream(streamId, promise);

        SpdyRstStreamFrame spdyRstStreamFrame = new DefaultSpdyRstStreamFrame(streamId, status);
        ctx.writeAndFlush(spdyRstStreamFrame, promise);
        if (fireChannelRead) {
            ctx.fireChannelRead(spdyRstStreamFrame);
        }
    }

    /*
     * Helper functions
     */

    private boolean isRemoteInitiatedId(int id) {
        boolean serverId = isServerId(id);
        return server && !serverId || !server && serverId;
    }

    // need to synchronize to prevent new streams from being created while updating active streams
    private void updateInitialSendWindowSize(int newInitialWindowSize) {
        int deltaWindowSize = newInitialWindowSize - initialSendWindowSize;
        initialSendWindowSize = newInitialWindowSize;
        spdySession.updateAllSendWindowSizes(deltaWindowSize);
    }

    // need to synchronize to prevent new streams from being created while updating active streams
    private void updateInitialReceiveWindowSize(int newInitialWindowSize) {
        int deltaWindowSize = newInitialWindowSize - initialReceiveWindowSize;
        initialReceiveWindowSize = newInitialWindowSize;
        spdySession.updateAllReceiveWindowSizes(deltaWindowSize);
    }

    // need to synchronize accesses to sentGoAwayFrame, lastGoodStreamId, and initial window sizes
    private boolean acceptStream(
            int streamId, byte priority, boolean remoteSideClosed, boolean localSideClosed) {
        // Cannot initiate any new streams after receiving or sending GOAWAY
        if (receivedGoAwayFrame || sentGoAwayFrame) {
            return false;
        }

        boolean remote = isRemoteInitiatedId(streamId);
        int maxConcurrentStreams = remote ? localConcurrentStreams : remoteConcurrentStreams;
        if (spdySession.numActiveStreams(remote) >= maxConcurrentStreams) {
            return false;
        }
        spdySession.acceptStream(
                streamId, priority, remoteSideClosed, localSideClosed,
                initialSendWindowSize, initialReceiveWindowSize, remote);
        if (remote) {
            lastGoodStreamId = streamId;
        }
        return true;
    }

    private void halfCloseStream(int streamId, boolean remote, ChannelFuture future) {
        if (remote) {
            spdySession.closeRemoteSide(streamId, isRemoteInitiatedId(streamId));
        } else {
            spdySession.closeLocalSide(streamId, isRemoteInitiatedId(streamId));
        }
        if (closeSessionFutureListener != null && spdySession.noActiveStreams()) {
            future.addListener(closeSessionFutureListener);
        }
    }

    private void removeStream(int streamId, ChannelFuture future) {
        spdySession.removeStream(streamId, STREAM_CLOSED, isRemoteInitiatedId(streamId));

        if (closeSessionFutureListener != null && spdySession.noActiveStreams()) {
            future.addListener(closeSessionFutureListener);
        }
    }

    private void updateSendWindowSize(final ChannelHandlerContext ctx, int streamId, int deltaWindowSize) {
        spdySession.updateSendWindowSize(streamId, deltaWindowSize);

        while (true) {
            // Check if we have unblocked a stalled stream
            SpdySession.PendingWrite pendingWrite = spdySession.getPendingWrite(streamId);
            if (pendingWrite == null) {
                return;
            }

            SpdyDataFrame spdyDataFrame = pendingWrite.spdyDataFrame;
            int dataFrameSize = spdyDataFrame.content().readableBytes();
            int writeStreamId = spdyDataFrame.streamId();
            int sendWindowSize = spdySession.getSendWindowSize(writeStreamId);
            int sessionSendWindowSize = spdySession.getSendWindowSize(SPDY_SESSION_STREAM_ID);
            sendWindowSize = Math.min(sendWindowSize, sessionSendWindowSize);

            if (sendWindowSize <= 0) {
                return;
            } else if (sendWindowSize < dataFrameSize) {
                // We can send a partial frame
                spdySession.updateSendWindowSize(writeStreamId, -1 * sendWindowSize);
                spdySession.updateSendWindowSize(SPDY_SESSION_STREAM_ID, -1 * sendWindowSize);

                // Create a partial data frame whose length is the current window size
                SpdyDataFrame partialDataFrame = new DefaultSpdyDataFrame(writeStreamId,
                        spdyDataFrame.content().readSlice(sendWindowSize).retain());

                // The transfer window size is pre-decremented when sending a data frame downstream.
                // Close the session on write failures that leave the transfer window in a corrupt state.
                ctx.writeAndFlush(partialDataFrame).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            issueSessionError(ctx, SpdySessionStatus.INTERNAL_ERROR);
                        }
                    }
                });
            } else {
                // Window size is large enough to send entire data frame
                spdySession.removePendingWrite(writeStreamId);
                spdySession.updateSendWindowSize(writeStreamId, -1 * dataFrameSize);
                spdySession.updateSendWindowSize(SPDY_SESSION_STREAM_ID, -1 * dataFrameSize);

                // Close the local side of the stream if this is the last frame
                if (spdyDataFrame.isLast()) {
                    halfCloseStream(writeStreamId, false, pendingWrite.promise);
                }

                // The transfer window size is pre-decremented when sending a data frame downstream.
                // Close the session on write failures that leave the transfer window in a corrupt state.
                ctx.writeAndFlush(spdyDataFrame, pendingWrite.promise).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            issueSessionError(ctx, SpdySessionStatus.INTERNAL_ERROR);
                        }
                    }
                });
            }
        }
    }

    private void sendGoAwayFrame(ChannelHandlerContext ctx, ChannelPromise future) {
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(future);
            return;
        }

        ChannelFuture f = sendGoAwayFrame(ctx, SpdySessionStatus.OK);
        if (spdySession.noActiveStreams()) {
            f.addListener(new ClosingChannelFutureListener(ctx, future));
        } else {
            closeSessionFutureListener = new ClosingChannelFutureListener(ctx, future);
        }
        // FIXME: Close the connection forcibly after timeout.
    }

    private ChannelFuture sendGoAwayFrame(
            ChannelHandlerContext ctx, SpdySessionStatus status) {
        if (!sentGoAwayFrame) {
            sentGoAwayFrame = true;
            SpdyGoAwayFrame spdyGoAwayFrame = new DefaultSpdyGoAwayFrame(lastGoodStreamId, status);
            return ctx.writeAndFlush(spdyGoAwayFrame);
        } else {
            return ctx.newSucceededFuture();
        }
    }

    private static final class ClosingChannelFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;
        private final ChannelPromise promise;

        ClosingChannelFutureListener(ChannelHandlerContext ctx, ChannelPromise promise) {
            this.ctx = ctx;
            this.promise = promise;
        }

        @Override
        public void operationComplete(ChannelFuture sentGoAwayFuture) throws Exception {
            ctx.close(promise);
        }
    }
}
