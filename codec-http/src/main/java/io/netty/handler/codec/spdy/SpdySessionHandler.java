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

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;

/**
 * Manages streams within a SPDY session.
 */
public class SpdySessionHandler extends SimpleChannelUpstreamHandler 
        implements ChannelDownstreamHandler {

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
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Object msg = e.getMessage();
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
                    issueStreamError(ctx, e, streamID, SpdyStreamStatus.INVALID_STREAM);
                }
                return;
            }

            // Check if we received a data frame before receiving a SYN_REPLY
            if (!isRemoteInitiatedID(streamID) && !spdySession.hasReceivedReply(streamID)) {
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
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
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            // Stream-IDs must be monotonically increassing
            if (streamID < lastGoodStreamID) {
                issueSessionError(ctx, e.getChannel(), e.getRemoteAddress());
                return;
            }

            // Try to accept the stream
            boolean remoteSideClosed = spdySynStreamFrame.isLast();
            boolean localSideClosed = spdySynStreamFrame.isUnidirectional();
            if (!acceptStream(streamID, remoteSideClosed, localSideClosed)) {
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.REFUSED_STREAM);
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
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.INVALID_STREAM);
                return;
            }

            // Check if we have received multiple frames for the same Stream-ID
            if (spdySession.hasReceivedReply(streamID)) {
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
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
                Channels.write(ctx, Channels.future(e.getChannel()), spdyPingFrame, e.getRemoteAddress());
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
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                return;
            }

            if (spdySession.isRemoteSideClosed(streamID)) {
                issueStreamError(ctx, e, streamID, SpdyStreamStatus.INVALID_STREAM);
                return;
            }
        }

        super.messageReceived(ctx, e);
    }

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt)
            throws Exception {
        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) evt;
            switch (e.getState()) {
            case OPEN:
            case CONNECTED:
            case BOUND:
                if (Boolean.FALSE.equals(e.getValue()) || e.getValue() == null) {
                    sendGoAwayFrame(ctx, e);
                    return;
                }
            }
        }
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object msg = e.getMessage();

        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamID = spdyDataFrame.getStreamID();

            if (spdySession.isLocalSideClosed(streamID)) {
                e.getFuture().setFailure(PROTOCOL_EXCEPTION);
                return;
            }

            if (spdyDataFrame.isLast()) {
                halfCloseStream(streamID, false);
            }

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            boolean remoteSideClosed = spdySynStreamFrame.isUnidirectional();
            boolean localSideClosed = spdySynStreamFrame.isLast();
            if (!acceptStream(spdySynStreamFrame.getStreamID(), remoteSideClosed, localSideClosed)) {
                e.getFuture().setFailure(PROTOCOL_EXCEPTION);
                return;
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamID = spdySynReplyFrame.getStreamID();

            if (!isRemoteInitiatedID(streamID) || spdySession.isLocalSideClosed(streamID)) {
                e.getFuture().setFailure(PROTOCOL_EXCEPTION);
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
                e.getFuture().setFailure(new IllegalArgumentException(
                            "invalid PING ID: " + spdyPingFrame.getID()));
                return;
            }
            pings.getAndIncrement();

        } else if (msg instanceof SpdyGoAwayFrame) {

            // Should send a CLOSE ChannelStateEvent
            e.getFuture().setFailure(PROTOCOL_EXCEPTION);
            return;

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamID = spdyHeadersFrame.getStreamID();

            if (spdySession.isLocalSideClosed(streamID)) {
                e.getFuture().setFailure(PROTOCOL_EXCEPTION);
                return;
            }
        }

        ctx.sendDownstream(evt);
    }

    /*
     * Error Handling
     */

    private void issueSessionError(
            ChannelHandlerContext ctx, Channel channel, SocketAddress remoteAddress) {

        ChannelFuture future = sendGoAwayFrame(ctx, channel, remoteAddress);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    // Send a RST_STREAM frame in response to an incoming MessageEvent
    // Only called in the upstream direction
    private void issueStreamError(
            ChannelHandlerContext ctx, MessageEvent e, int streamID, SpdyStreamStatus status) {

        removeStream(streamID);
        SpdyRstStreamFrame spdyRstStreamFrame = new DefaultSpdyRstStreamFrame(streamID, status);
        Channels.write(ctx, Channels.future(e.getChannel()), spdyRstStreamFrame, e.getRemoteAddress());
    }

    /*
     * Helper functions
     */

    private boolean isRemoteInitiatedID(int ID) {
        boolean serverID = SpdyCodecUtil.isServerID(ID);
        return (server && !serverID) || (!server && serverID);
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
        if ((maxConcurrentStreams != 0) &&
           (spdySession.numActiveStreams() >= maxConcurrentStreams)) {
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
        if ((closeSessionFuture != null) && spdySession.noActiveStreams()) {
            closeSessionFuture.setSuccess();
        }
    }

    private void removeStream(int streamID) {
        spdySession.removeStream(streamID);
        if ((closeSessionFuture != null) && spdySession.noActiveStreams()) {
            closeSessionFuture.setSuccess();
        }
    }

    private void sendGoAwayFrame(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Avoid NotYetConnectedException
        if (!e.getChannel().isConnected()) {
            ctx.sendDownstream(e);
            return;
        }

        ChannelFuture future = sendGoAwayFrame(ctx, e.getChannel(), null);
        if (spdySession.noActiveStreams()) {
            future.addListener(new ClosingChannelFutureListener(ctx, e));
        } else {
            closeSessionFuture = Channels.future(e.getChannel());
            closeSessionFuture.addListener(new ClosingChannelFutureListener(ctx, e));
        }
    }

    private synchronized ChannelFuture sendGoAwayFrame(
            ChannelHandlerContext ctx, Channel channel, SocketAddress remoteAddress) {
        if (!sentGoAwayFrame) {
            sentGoAwayFrame = true;
            ChannelFuture future = Channels.future(channel);
            Channels.write(ctx, future, new DefaultSpdyGoAwayFrame(lastGoodStreamID));
            return future;
        }
        return Channels.succeededFuture(channel);
    }

    private static final class ClosingChannelFutureListener implements ChannelFutureListener {

        private final ChannelHandlerContext ctx;
        private final ChannelStateEvent e;

        ClosingChannelFutureListener(ChannelHandlerContext ctx, ChannelStateEvent e) {
            this.ctx = ctx;
            this.e = e;
        }

        public void operationComplete(ChannelFuture sentGoAwayFuture) throws Exception {
            if (!(sentGoAwayFuture.getCause() instanceof ClosedChannelException)) {
                Channels.close(ctx, e.getFuture());
            } else {
                e.getFuture().setSuccess();
            }
        }
    }
}
