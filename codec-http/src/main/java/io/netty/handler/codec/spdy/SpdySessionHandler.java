/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelOutboundHandlerContext;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages streams within a SPDY session.
 */
public class SpdySessionHandler extends ChannelHandlerAdapter<Object, Object> {

    private static final SpdyProtocolException PROTOCOL_EXCEPTION = new SpdyProtocolException();

    private final SpdySession spdySession = new SpdySession();
    private volatile int lastGoodStreamID;

    private volatile int remoteConcurrentStreams;
    private volatile int localConcurrentStreams;
    private volatile int maxConcurrentStreams;

    private final AtomicInteger pings = new AtomicInteger();

    private volatile boolean sentGoAwayFrame;
    private volatile boolean receivedGoAwayFrame;

    private volatile ChannelFuture closeSessionFuture;

    private final boolean server;

    /**
     * Creates a new session handler.
     *
     * @param server {@code true} if and only if this session handler should
     *               handle the server endpoint of the connection.
     *               {@code false} if and only if this session handler should
     *               handle the client endpoint of the connection.
     */
    public SpdySessionHandler(boolean server) {
        super();
        this.server = server;
    }

    @Override
    public ChannelBufferHolder<Object> newOutboundBuffer(
            ChannelOutboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelInboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        Queue<Object> in = ctx.in().messageBuffer();
        for (;;) {
            Object msg = in.poll();
            if (msg == null) {
                break;
            }

            handleInboundMessage(ctx, msg);
        }

        ctx.fireInboundBufferUpdated();
    }

    private void handleInboundMessage(ChannelInboundHandlerContext<Object> ctx, Object msg)
            throws Exception {

        if (msg instanceof SpdyDataFrame) {

            /*
             * SPDY Data frame processing requirements:
             *
             * If an endpoint receives a data frame for a Stream-ID which does not exist,
             * it must return a RST_STREAM with error code INVALID_STREAM for the Stream-ID.
             *
             * If an endpoint which created the stream receives a data frame before receiving
             * a SYN_REPLY on that stream, it is a protocol error, and the receiver should
             * close the connection immediately.
             *
             * If an endpoint receives multiple data frames for invalid Stream-IDs,
             * it may terminate the session.
             *
             * If an endpoint refuses a stream it must ignore any data frames for that stream.
             *
             * If an endpoint receives data on a stream which has already been torn down,
             * it must ignore the data received after the teardown.
             */

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamID = spdyDataFrame.getStreamID();

            // Check if we received a data frame for a Stream-ID which is not open
            if (spdySession.isRemoteSideClosed(streamID)) {
                if (!sentGoAwayFrame) {
                    issueStreamError(ctx, streamID, SpdyStreamStatus.INVALID_STREAM);
                }
                return;
            }

            // Check if we received a data frame before receiving a SYN_REPLY
            if (!isRemoteInitiatedID(streamID) && !spdySession.hasReceivedReply(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            if (spdyDataFrame.isLast()) {
                // Close remote side of stream
                halfCloseStream(streamID, true);
            }

        } else if (msg instanceof SpdySynStreamFrame) {

            /*
             * SPDY SYN_STREAM frame processing requirements:
             *
             * If an endpoint receives a SYN_STREAM with a Stream-ID that is not monotonically
             * increasing, it must issue a session error with the status PROTOCOL_ERROR.
             *
             * If an endpoint receives multiple SYN_STREAM frames with the same active
             * Stream-ID, it must issue a stream error with the status code PROTOCOL_ERROR.
             */

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamID = spdySynStreamFrame.getStreamID();

            // Check if we received a valid SYN_STREAM frame
            if (spdySynStreamFrame.isInvalid() ||
                !isRemoteInitiatedID(streamID) ||
                spdySession.isActiveStream(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            // Stream-IDs must be monotonically increassing
            if (streamID < lastGoodStreamID) {
                issueSessionError(ctx);
                return;
            }

            // Try to accept the stream
            boolean remoteSideClosed = spdySynStreamFrame.isLast();
            boolean localSideClosed = spdySynStreamFrame.isUnidirectional();
            if (!acceptStream(streamID, remoteSideClosed, localSideClosed)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.REFUSED_STREAM);
                return;
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            /*
             * SPDY SYN_REPLY frame processing requirements:
             *
             * If an endpoint receives multiple SYN_REPLY frames for the same active Stream-ID
             * it must issue a stream error with the status code PROTOCOL_ERROR.
             */

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamID = spdySynReplyFrame.getStreamID();

            // Check if we received a valid SYN_REPLY frame
            if (spdySynReplyFrame.isInvalid() ||
                isRemoteInitiatedID(streamID) ||
                spdySession.isRemoteSideClosed(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.INVALID_STREAM);
                return;
            }

            // Check if we have received multiple frames for the same Stream-ID
            if (spdySession.hasReceivedReply(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            spdySession.receivedReply(streamID);
            if (spdySynReplyFrame.isLast()) {
                // Close remote side of stream
                halfCloseStream(streamID, true);
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            /*
             * SPDY RST_STREAM frame processing requirements:
             *
             * After receiving a RST_STREAM on a stream, the receiver must not send additional
             * frames on that stream.
             */

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            removeStream(spdyRstStreamFrame.getStreamID());

        } else if (msg instanceof SpdySettingsFrame) {

            /*
             * Only concerned with MAX_CONCURRENT_STREAMS
             */

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            updateConcurrentStreams(spdySettingsFrame, true);

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

            if (isRemoteInitiatedID(spdyPingFrame.getID())) {
                ctx.write(spdyPingFrame);
                return;
            }

            // Note: only checks that there are outstanding pings since uniqueness is not inforced
            if (pings.get() == 0) {
                return;
            }
            pings.getAndDecrement();

        } else if (msg instanceof SpdyGoAwayFrame) {

            receivedGoAwayFrame = true;

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamID = spdyHeadersFrame.getStreamID();

            // Check if we received a valid HEADERS frame
            if (spdyHeadersFrame.isInvalid()) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            if (spdySession.isRemoteSideClosed(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.INVALID_STREAM);
                return;
            }
        }

        ctx.nextIn().messageBuffer().add(msg);
    }

    @Override
    public void disconnect(final ChannelOutboundHandlerContext<Object> ctx,
            final ChannelFuture future) throws Exception {
        sendGoAwayFrame(ctx).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f)
                    throws Exception {
                ctx.disconnect(future);
            }
        });
    }

    @Override
    public void close(final ChannelOutboundHandlerContext<Object> ctx,
            final ChannelFuture future) throws Exception {
        sendGoAwayFrame(ctx).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f)
                    throws Exception {
                ctx.close(future);
            }
        });
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {

        Queue<Object> in = ctx.prevOut().messageBuffer();
        for (;;) {
            Object msg = in.poll();
            if (msg == null) {
                break;
            }

            handleOutboundMessage(ctx, msg);
        }

        ctx.flush(future);
    }

    private void handleOutboundMessage(ChannelOutboundHandlerContext<Object> ctx, Object msg)
            throws Exception {
        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamID = spdyDataFrame.getStreamID();

            if (spdySession.isLocalSideClosed(streamID)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            if (spdyDataFrame.isLast()) {
                halfCloseStream(streamID, false);
            }

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamID = spdySynStreamFrame.getStreamID();
            boolean remoteSideClosed = spdySynStreamFrame.isUnidirectional();
            boolean localSideClosed = spdySynStreamFrame.isLast();
            if (!acceptStream(streamID, remoteSideClosed, localSideClosed)) {
                issueStreamError(ctx, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamID = spdySynReplyFrame.getStreamID();

            if (!isRemoteInitiatedID(streamID) || spdySession.isLocalSideClosed(streamID)) {
                ctx.fireExceptionCaught(PROTOCOL_EXCEPTION);
                return;
            }

            if (spdySynReplyFrame.isLast()) {
                halfCloseStream(streamID, false);
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            removeStream(spdyRstStreamFrame.getStreamID());

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            updateConcurrentStreams(spdySettingsFrame, false);

        } else if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            if (isRemoteInitiatedID(spdyPingFrame.getID())) {
                ctx.fireExceptionCaught(new IllegalArgumentException(
                            "invalid PING ID: " + spdyPingFrame.getID()));
                return;
            }
            pings.getAndIncrement();

        } else if (msg instanceof SpdyGoAwayFrame) {

            // Should send a CLOSE ChannelStateEvent
            ctx.fireExceptionCaught(PROTOCOL_EXCEPTION);
            return;

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamID = spdyHeadersFrame.getStreamID();

            if (spdySession.isLocalSideClosed(streamID)) {
                ctx.fireExceptionCaught(PROTOCOL_EXCEPTION);
                return;
            }
        }

        ctx.out().messageBuffer().add(msg);
    }

    /*
     * Error Handling
     */

    private void issueSessionError(ChannelHandlerContext ctx) {
        sendGoAwayFrame(ctx).addListener(ChannelFutureListener.CLOSE);
    }

    // Send a RST_STREAM frame in response to an incoming MessageEvent
    // Only called in the upstream direction
    private void issueStreamError(
            ChannelHandlerContext ctx, int streamID, SpdyStreamStatus status) {

        removeStream(streamID);
        SpdyRstStreamFrame spdyRstStreamFrame = new DefaultSpdyRstStreamFrame(streamID, status);
        ctx.write(spdyRstStreamFrame);
    }

    /*
     * Helper functions
     */

    private boolean isRemoteInitiatedID(int ID) {
        boolean serverID = SpdyCodecUtil.isServerID(ID);
        return server && !serverID || !server && serverID;
    }

    private synchronized void updateConcurrentStreams(SpdySettingsFrame settings, boolean remote) {
        int newConcurrentStreams = settings.getValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS);
        if (remote) {
            remoteConcurrentStreams = newConcurrentStreams;
        } else {
            localConcurrentStreams = newConcurrentStreams;
        }
        if (localConcurrentStreams == remoteConcurrentStreams) {
            maxConcurrentStreams = localConcurrentStreams;
            return;
        }
        if (localConcurrentStreams == 0) {
            maxConcurrentStreams = remoteConcurrentStreams;
            return;
        }
        if (remoteConcurrentStreams == 0) {
            maxConcurrentStreams = localConcurrentStreams;
            return;
        }
        if (localConcurrentStreams > remoteConcurrentStreams) {
            maxConcurrentStreams = remoteConcurrentStreams;
        } else {
            maxConcurrentStreams = localConcurrentStreams;
        }
    }

    // need to synchronize accesses to sentGoAwayFrame and lastGoodStreamID
    private synchronized boolean acceptStream(
            int streamID, boolean remoteSideClosed, boolean localSideClosed) {
        // Cannot initiate any new streams after receiving or sending GOAWAY
        if (receivedGoAwayFrame || sentGoAwayFrame) {
            return false;
        }
        if (maxConcurrentStreams != 0 &&
           spdySession.numActiveStreams() >= maxConcurrentStreams) {
            return false;
        }
        spdySession.acceptStream(streamID, remoteSideClosed, localSideClosed);
        if (isRemoteInitiatedID(streamID)) {
            lastGoodStreamID = streamID;
        }
        return true;
    }

    private void halfCloseStream(int streamID, boolean remote) {
        if (remote) {
            spdySession.closeRemoteSide(streamID);
        } else {
            spdySession.closeLocalSide(streamID);
        }
        if (closeSessionFuture != null && spdySession.noActiveStreams()) {
            closeSessionFuture.setSuccess();
        }
    }

    private void removeStream(int streamID) {
        spdySession.removeStream(streamID);
        if (closeSessionFuture != null && spdySession.noActiveStreams()) {
            closeSessionFuture.setSuccess();
        }
    }

    private synchronized ChannelFuture sendGoAwayFrame(ChannelHandlerContext ctx) {
        if (!sentGoAwayFrame) {
            sentGoAwayFrame = true;
            return ctx.write(new DefaultSpdyGoAwayFrame(lastGoodStreamID));
        }
        return ctx.newSucceededFuture();
    }
}
