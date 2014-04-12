/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * A {@link ChannelHandler} that encodes and decodes SPDY Frames.
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyFrameDecoder
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyFrameEncoder
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyHeaderBlockDecoder
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyHeaderBlockEncoder
 */
public class SpdyFrameCodec extends FrameDecoder
        implements SpdyFrameDecoderDelegate, ChannelDownstreamHandler {

    private static final SpdyProtocolException INVALID_FRAME =
        new SpdyProtocolException("Received invalid frame");

    private final SpdyFrameDecoder spdyFrameDecoder;
    private final SpdyFrameEncoder spdyFrameEncoder;
    private final SpdyHeaderBlockDecoder spdyHeaderBlockDecoder;
    private final SpdyHeaderBlockEncoder spdyHeaderBlockEncoder;

    private SpdyHeadersFrame spdyHeadersFrame;
    private SpdySettingsFrame spdySettingsFrame;

    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new instance with the specified {@code version} and
     * the default decoder and encoder options
     * ({@code maxChunkSize (8192)}, {@code maxHeaderSize (16384)},
     * {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}).
     */
    public SpdyFrameCodec(SpdyVersion version) {
        this(version, 8192, 16384, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified decoder and encoder options.
     */
    public SpdyFrameCodec(
            SpdyVersion version, int maxChunkSize, int maxHeaderSize,
            int compressionLevel, int windowBits, int memLevel) {
        this(version, maxChunkSize,
            SpdyHeaderBlockDecoder.newInstance(version, maxHeaderSize),
            SpdyHeaderBlockEncoder.newInstance(version, compressionLevel, windowBits, memLevel));
    }

    protected SpdyFrameCodec(SpdyVersion version, int maxChunkSize,
            SpdyHeaderBlockDecoder spdyHeaderBlockDecoder, SpdyHeaderBlockEncoder spdyHeaderBlockEncoder) {
        spdyFrameDecoder = new SpdyFrameDecoder(version, this, maxChunkSize);
        spdyFrameEncoder = new SpdyFrameEncoder(version);
        this.spdyHeaderBlockDecoder = spdyHeaderBlockDecoder;
        this.spdyHeaderBlockEncoder = spdyHeaderBlockEncoder;
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        super.beforeAdd(ctx);
        this.ctx = ctx;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        spdyFrameDecoder.decode(buffer);
        return null;
    }

    @Override
    protected void cleanup(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try {
            super.cleanup(ctx, e);
        } finally {
            spdyHeaderBlockDecoder.end();
            synchronized (spdyHeaderBlockEncoder) {
                spdyHeaderBlockEncoder.end();
            }
        }
    }

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) evt;
            switch (e.getState()) {
            case OPEN:
            case CONNECTED:
            case BOUND:
                if (Boolean.FALSE.equals(e.getValue()) || e.getValue() == null) {
                    synchronized (spdyHeaderBlockEncoder) {
                      spdyHeaderBlockEncoder.end();
                    }
                }
            }
        }

        if (!(evt instanceof MessageEvent)) {
          ctx.sendDownstream(evt);
          return;
        }

        final MessageEvent e = (MessageEvent) evt;
        Object msg = e.getMessage();

        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodeDataFrame(
                spdyDataFrame.getStreamId(),
                spdyDataFrame.isLast(),
                spdyDataFrame.getData()
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdySynStreamFrame) {

            synchronized (spdyHeaderBlockEncoder) {
                SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
                ChannelBuffer frame = spdyFrameEncoder.encodeSynStreamFrame(
                    spdySynStreamFrame.getStreamId(),
                    spdySynStreamFrame.getAssociatedToStreamId(),
                    spdySynStreamFrame.getPriority(),
                    spdySynStreamFrame.isLast(),
                    spdySynStreamFrame.isUnidirectional(),
                    spdyHeaderBlockEncoder.encode(spdySynStreamFrame)
                );
                // Writes of compressed data must occur in order
                Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdySynReplyFrame) {

            synchronized (spdyHeaderBlockEncoder) {
                SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
                ChannelBuffer frame = spdyFrameEncoder.encodeSynReplyFrame(
                    spdySynReplyFrame.getStreamId(),
                    spdySynReplyFrame.isLast(),
                    spdyHeaderBlockEncoder.encode(spdySynReplyFrame)
                );
                // Writes of compressed data must occur in order
                Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodeRstStreamFrame(
                spdyRstStreamFrame.getStreamId(),
                spdyRstStreamFrame.getStatus().getCode()
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodeSettingsFrame(
                spdySettingsFrame
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodePingFrame(
                spdyPingFrame.getId()
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyGoAwayFrame) {

            SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodeGoAwayFrame(
                spdyGoAwayFrame.getLastGoodStreamId(),
                spdyGoAwayFrame.getStatus().getCode()
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyHeadersFrame) {

            synchronized (spdyHeaderBlockEncoder) {
                SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
                ChannelBuffer frame = spdyFrameEncoder.encodeHeadersFrame(
                    spdyHeadersFrame.getStreamId(),
                    spdyHeadersFrame.isLast(),
                    spdyHeaderBlockEncoder.encode(spdyHeadersFrame)
                );
                // Writes of compressed data must occur in order
                Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdyWindowUpdateFrame) {

            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            ChannelBuffer frame = spdyFrameEncoder.encodeWindowUpdateFrame(
                spdyWindowUpdateFrame.getStreamId(),
                spdyWindowUpdateFrame.getDeltaWindowSize()
            );
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        // Unknown message type
        ctx.sendDownstream(evt);
    }

    public void readDataFrame(int streamId, boolean last, ChannelBuffer data) {
        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamId);
        spdyDataFrame.setLast(last);
        spdyDataFrame.setData(data);
        Channels.fireMessageReceived(ctx, spdyDataFrame);
    }

    public void readSynStreamFrame(
        int streamId, int associatedToStreamId, byte priority, boolean last, boolean unidirectional) {
        SpdySynStreamFrame spdySynStreamFrame = new DefaultSpdySynStreamFrame(streamId, associatedToStreamId, priority);
        spdySynStreamFrame.setLast(last);
        spdySynStreamFrame.setUnidirectional(unidirectional);
        spdyHeadersFrame = spdySynStreamFrame;
    }

    public void readSynReplyFrame(int streamId, boolean last) {
        SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
        spdySynReplyFrame.setLast(last);
        spdyHeadersFrame = spdySynReplyFrame;
    }

    public void readRstStreamFrame(int streamId, int statusCode) {
        SpdyRstStreamFrame spdyRstStreamFrame = new DefaultSpdyRstStreamFrame(streamId, statusCode);
        Channels.fireMessageReceived(ctx, spdyRstStreamFrame);
    }

    public void readSettingsFrame(boolean clearPersisted) {
        spdySettingsFrame = new DefaultSpdySettingsFrame();
        spdySettingsFrame.setClearPreviouslyPersistedSettings(clearPersisted);
    }

    public void readSetting(int id, int value, boolean persistValue, boolean persisted) {
        spdySettingsFrame.setValue(id, value, persistValue, persisted);
    }

    public void readSettingsEnd() {
        Object frame = spdySettingsFrame;
        spdySettingsFrame = null;
        Channels.fireMessageReceived(ctx, frame);
    }

    public void readPingFrame(int id) {
        SpdyPingFrame spdyPingFrame = new DefaultSpdyPingFrame(id);
        Channels.fireMessageReceived(ctx, spdyPingFrame);
    }

    public void readGoAwayFrame(int lastGoodStreamId, int statusCode) {
        SpdyGoAwayFrame spdyGoAwayFrame = new DefaultSpdyGoAwayFrame(lastGoodStreamId, statusCode);
        Channels.fireMessageReceived(ctx, spdyGoAwayFrame);
    }

    public void readHeadersFrame(int streamId, boolean last) {
        spdyHeadersFrame = new DefaultSpdyHeadersFrame(streamId);
        spdyHeadersFrame.setLast(last);
    }

    public void readWindowUpdateFrame(int streamId, int deltaWindowSize) {
        SpdyWindowUpdateFrame spdyWindowUpdateFrame = new DefaultSpdyWindowUpdateFrame(streamId, deltaWindowSize);
        Channels.fireMessageReceived(ctx, spdyWindowUpdateFrame);
    }

    public void readHeaderBlock(ChannelBuffer headerBlock) {
        try {
            spdyHeaderBlockDecoder.decode(headerBlock, spdyHeadersFrame);
        } catch (Exception e) {
            Channels.fireExceptionCaught(ctx, e);
        }
    }

    public void readHeaderBlockEnd() {
        Object frame = null;
        try {
            spdyHeaderBlockDecoder.endHeaderBlock(spdyHeadersFrame);
            frame = spdyHeadersFrame;
            spdyHeadersFrame = null;
        } catch (Exception e) {
            Channels.fireExceptionCaught(ctx, e);
        }
        if (frame != null) {
            Channels.fireMessageReceived(ctx, frame);
        }
    }

    public void readFrameError(String message) {
        Channels.fireExceptionCaught(ctx, INVALID_FRAME);
    }
}
