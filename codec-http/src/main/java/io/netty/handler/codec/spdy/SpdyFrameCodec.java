/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;

import java.net.SocketAddress;
import java.util.List;

/**
 * A {@link ChannelHandler} that encodes and decodes SPDY Frames.
 */
public class SpdyFrameCodec extends ByteToMessageDecoder
        implements SpdyFrameDecoderDelegate, ChannelOutboundHandler {

    private static final SpdyProtocolException INVALID_FRAME =
            new SpdyProtocolException("Received invalid frame");

    private final SpdyFrameDecoder spdyFrameDecoder;
    private final SpdyFrameEncoder spdyFrameEncoder;
    private final SpdyHeaderBlockDecoder spdyHeaderBlockDecoder;
    private final SpdyHeaderBlockEncoder spdyHeaderBlockEncoder;

    private SpdyHeadersFrame spdyHeadersFrame;
    private SpdySettingsFrame spdySettingsFrame;

    private ChannelHandlerContext ctx;
    private boolean read;
    private final boolean validateHeaders;

    /**
     * Creates a new instance with the specified {@code version},
     * {@code validateHeaders (true)}, and
     * the default decoder and encoder options
     * ({@code maxChunkSize (8192)}, {@code maxHeaderSize (16384)},
     * {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}).
     */
    public SpdyFrameCodec(SpdyVersion version) {
        this(version, true);
    }

    /**
     * Creates a new instance with the specified {@code version},
     * {@code validateHeaders}, and
     * the default decoder and encoder options
     * ({@code maxChunkSize (8192)}, {@code maxHeaderSize (16384)},
     * {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}).
     */
    public SpdyFrameCodec(SpdyVersion version, boolean validateHeaders) {
        this(version, 8192, 16384, 6, 15, 8, validateHeaders);
    }

    /**
     * Creates a new instance with the specified {@code version}, {@code validateHeaders (true)},
     * decoder and encoder options.
     */
    public SpdyFrameCodec(
            SpdyVersion version, int maxChunkSize, int maxHeaderSize,
            int compressionLevel, int windowBits, int memLevel) {
        this(version, maxChunkSize, maxHeaderSize, compressionLevel, windowBits, memLevel, true);
    }

    /**
     * Creates a new instance with the specified {@code version}, {@code validateHeaders},
     * decoder and encoder options.
     */
    public SpdyFrameCodec(
            SpdyVersion version, int maxChunkSize, int maxHeaderSize,
            int compressionLevel, int windowBits, int memLevel, boolean validateHeaders) {
        this(version, maxChunkSize,
                SpdyHeaderBlockDecoder.newInstance(version, maxHeaderSize),
                SpdyHeaderBlockEncoder.newInstance(version, compressionLevel, windowBits, memLevel), validateHeaders);
    }

    protected SpdyFrameCodec(SpdyVersion version, int maxChunkSize,
            SpdyHeaderBlockDecoder spdyHeaderBlockDecoder, SpdyHeaderBlockEncoder spdyHeaderBlockEncoder,
            boolean validateHeaders) {
        spdyFrameDecoder = new SpdyFrameDecoder(version, this, maxChunkSize);
        spdyFrameEncoder = new SpdyFrameEncoder(version);
        this.spdyHeaderBlockDecoder = spdyHeaderBlockDecoder;
        this.spdyHeaderBlockEncoder = spdyHeaderBlockEncoder;
        this.validateHeaders = validateHeaders;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;
        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                spdyHeaderBlockDecoder.end();
                spdyHeaderBlockEncoder.end();
            }
        });
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        spdyFrameDecoder.decode(in);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (!read) {
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        read = false;
        super.channelReadComplete(ctx);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf frame;

        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            frame = spdyFrameEncoder.encodeDataFrame(
                    ctx.alloc(),
                    spdyDataFrame.streamId(),
                    spdyDataFrame.isLast(),
                    spdyDataFrame.content()
            );
            spdyDataFrame.release();
            ctx.write(frame, promise);

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            ByteBuf headerBlock = spdyHeaderBlockEncoder.encode(ctx.alloc(), spdySynStreamFrame);
            try {
                frame = spdyFrameEncoder.encodeSynStreamFrame(
                        ctx.alloc(),
                        spdySynStreamFrame.streamId(),
                        spdySynStreamFrame.associatedStreamId(),
                        spdySynStreamFrame.priority(),
                        spdySynStreamFrame.isLast(),
                        spdySynStreamFrame.isUnidirectional(),
                        headerBlock
                );
            } finally {
                headerBlock.release();
            }
            ctx.write(frame, promise);

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            ByteBuf headerBlock = spdyHeaderBlockEncoder.encode(ctx.alloc(), spdySynReplyFrame);
            try {
                frame = spdyFrameEncoder.encodeSynReplyFrame(
                        ctx.alloc(),
                        spdySynReplyFrame.streamId(),
                        spdySynReplyFrame.isLast(),
                        headerBlock
                );
            } finally {
                headerBlock.release();
            }
            ctx.write(frame, promise);

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            frame = spdyFrameEncoder.encodeRstStreamFrame(
                    ctx.alloc(),
                    spdyRstStreamFrame.streamId(),
                    spdyRstStreamFrame.status().code()
            );
            ctx.write(frame, promise);

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            frame = spdyFrameEncoder.encodeSettingsFrame(
                    ctx.alloc(),
                    spdySettingsFrame
            );
            ctx.write(frame, promise);

        } else if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            frame = spdyFrameEncoder.encodePingFrame(
                    ctx.alloc(),
                    spdyPingFrame.id()
            );
            ctx.write(frame, promise);

        } else if (msg instanceof SpdyGoAwayFrame) {

            SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
            frame = spdyFrameEncoder.encodeGoAwayFrame(
                    ctx.alloc(),
                    spdyGoAwayFrame.lastGoodStreamId(),
                    spdyGoAwayFrame.status().code()
            );
            ctx.write(frame, promise);

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            ByteBuf headerBlock = spdyHeaderBlockEncoder.encode(ctx.alloc(), spdyHeadersFrame);
            try {
                frame = spdyFrameEncoder.encodeHeadersFrame(
                        ctx.alloc(),
                        spdyHeadersFrame.streamId(),
                        spdyHeadersFrame.isLast(),
                        headerBlock
                );
            } finally {
                headerBlock.release();
            }
            ctx.write(frame, promise);

        } else if (msg instanceof SpdyWindowUpdateFrame) {

            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            frame = spdyFrameEncoder.encodeWindowUpdateFrame(
                    ctx.alloc(),
                    spdyWindowUpdateFrame.streamId(),
                    spdyWindowUpdateFrame.deltaWindowSize()
            );
            ctx.write(frame, promise);
        } else {
            throw new UnsupportedMessageTypeException(msg);
        }
    }

    @Override
    public void readDataFrame(int streamId, boolean last, ByteBuf data) {
        read = true;

        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamId, data);
        spdyDataFrame.setLast(last);
        ctx.fireChannelRead(spdyDataFrame);
    }

    @Override
    public void readSynStreamFrame(
            int streamId, int associatedToStreamId, byte priority, boolean last, boolean unidirectional) {
        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(streamId, associatedToStreamId, priority, validateHeaders);
        spdySynStreamFrame.setLast(last);
        spdySynStreamFrame.setUnidirectional(unidirectional);
        spdyHeadersFrame = spdySynStreamFrame;
    }

    @Override
    public void readSynReplyFrame(int streamId, boolean last) {
        SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId, validateHeaders);
        spdySynReplyFrame.setLast(last);
        spdyHeadersFrame = spdySynReplyFrame;
    }

    @Override
    public void readRstStreamFrame(int streamId, int statusCode) {
        read = true;

        SpdyRstStreamFrame spdyRstStreamFrame = new DefaultSpdyRstStreamFrame(streamId, statusCode);
        ctx.fireChannelRead(spdyRstStreamFrame);
    }

    @Override
    public void readSettingsFrame(boolean clearPersisted) {
        read = true;

        spdySettingsFrame = new DefaultSpdySettingsFrame();
        spdySettingsFrame.setClearPreviouslyPersistedSettings(clearPersisted);
    }

    @Override
    public void readSetting(int id, int value, boolean persistValue, boolean persisted) {
        spdySettingsFrame.setValue(id, value, persistValue, persisted);
    }

    @Override
    public void readSettingsEnd() {
        read = true;

        Object frame = spdySettingsFrame;
        spdySettingsFrame = null;
        ctx.fireChannelRead(frame);
    }

    @Override
    public void readPingFrame(int id) {
        read = true;

        SpdyPingFrame spdyPingFrame = new DefaultSpdyPingFrame(id);
        ctx.fireChannelRead(spdyPingFrame);
    }

    @Override
    public void readGoAwayFrame(int lastGoodStreamId, int statusCode) {
        read = true;

        SpdyGoAwayFrame spdyGoAwayFrame = new DefaultSpdyGoAwayFrame(lastGoodStreamId, statusCode);
        ctx.fireChannelRead(spdyGoAwayFrame);
    }

    @Override
    public void readHeadersFrame(int streamId, boolean last) {
        spdyHeadersFrame = new DefaultSpdyHeadersFrame(streamId, validateHeaders);
        spdyHeadersFrame.setLast(last);
    }

    @Override
    public void readWindowUpdateFrame(int streamId, int deltaWindowSize) {
        read = true;

        SpdyWindowUpdateFrame spdyWindowUpdateFrame = new DefaultSpdyWindowUpdateFrame(streamId, deltaWindowSize);
        ctx.fireChannelRead(spdyWindowUpdateFrame);
    }

    @Override
    public void readHeaderBlock(ByteBuf headerBlock) {
        try {
            spdyHeaderBlockDecoder.decode(ctx.alloc(), headerBlock, spdyHeadersFrame);
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        } finally {
            headerBlock.release();
        }
    }

    @Override
    public void readHeaderBlockEnd() {
        Object frame = null;
        try {
            spdyHeaderBlockDecoder.endHeaderBlock(spdyHeadersFrame);
            frame = spdyHeadersFrame;
            spdyHeadersFrame = null;
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        }
        if (frame != null) {
            read = true;

            ctx.fireChannelRead(frame);
        }
    }

    @Override
    public void readFrameError(String message) {
        ctx.fireExceptionCaught(INVALID_FRAME);
    }
}
