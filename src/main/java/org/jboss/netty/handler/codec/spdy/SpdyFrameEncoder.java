/*
 * Copyright 2012 The Netty Project
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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

import java.nio.ByteOrder;
import java.util.Set;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Encodes a SPDY Data or Control Frame into a {@link ChannelBuffer}.
 */
public class SpdyFrameEncoder implements ChannelDownstreamHandler {

    private final int version;
    private volatile boolean finished;
    private final SpdyHeaderBlockCompressor headerBlockCompressor;

    /**
     * Creates a new instance with the default {@code version (2)},
     * {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}.
     */
    @Deprecated
    public SpdyFrameEncoder() {
        this(2, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified {@code version} and the
     * default {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}.
     */
    public SpdyFrameEncoder(int version) {
        this(version, 6, 15, 8);
    }

    /**
     * Creates a new instance with the default {@code version (2)} and the
     * specified parameters.
     */
    @Deprecated
    public SpdyFrameEncoder(int compressionLevel, int windowBits, int memLevel) {
        this(2, compressionLevel, windowBits, memLevel);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameEncoder(int version, int compressionLevel, int windowBits, int memLevel) {
        if (version < SPDY_MIN_VERSION || version > SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unknown version: " + version);
        }
        this.version = version;
        headerBlockCompressor = SpdyHeaderBlockCompressor.newInstance(
                version, compressionLevel, windowBits, memLevel);
    }

    public void handleDownstream(
            final ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) evt;
            switch (e.getState()) {
            case OPEN:
            case CONNECTED:
            case BOUND:
                if (Boolean.FALSE.equals(e.getValue()) || e.getValue() == null) {
                    synchronized (headerBlockCompressor) {
                        finished = true;
                        headerBlockCompressor.end();
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
            ChannelBuffer data = spdyDataFrame.getData();
            byte flags = spdyDataFrame.isLast() ? SPDY_DATA_FLAG_FIN : 0;
            ChannelBuffer header = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE);
            header.writeInt(spdyDataFrame.getStreamId() & 0x7FFFFFFF);
            header.writeByte(flags);
            header.writeMedium(data.readableBytes());
            ChannelBuffer frame = ChannelBuffers.wrappedBuffer(header, data);
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdySynStreamFrame) {

            synchronized (headerBlockCompressor) {
                SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
                ChannelBuffer data = compressHeaderBlock(
                        encodeHeaderBlock(version, spdySynStreamFrame));
                byte flags = spdySynStreamFrame.isLast() ? SPDY_FLAG_FIN : 0;
                if (spdySynStreamFrame.isUnidirectional()) {
                    flags |= SPDY_FLAG_UNIDIRECTIONAL;
                }
                int headerBlockLength = data.readableBytes();
                int length;
                if (version < 3) {
                    length = headerBlockLength == 0 ? 12 : 10 + headerBlockLength;
                } else {
                    length = 10 + headerBlockLength;
                }
                ChannelBuffer frame = ChannelBuffers.buffer(
                        ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 12);
                frame.writeShort(version | 0x8000);
                frame.writeShort(SPDY_SYN_STREAM_FRAME);
                frame.writeByte(flags);
                frame.writeMedium(length);
                frame.writeInt(spdySynStreamFrame.getStreamId());
                frame.writeInt(spdySynStreamFrame.getAssociatedToStreamId());
                if (version < 3) {
                    // Restrict priorities for SPDY/2 to between 0 and 3
                    byte priority = spdySynStreamFrame.getPriority();
                    if (priority > 3) {
                        priority = 3;
                    }
                    frame.writeShort((priority & 0xFF) << 14);
                } else {
                    frame.writeShort((spdySynStreamFrame.getPriority() & 0xFF) << 13);
                }
                if (version < 3 && data.readableBytes() == 0) {
                    frame.writeShort(0);
                }
                // Writes of compressed data must occur in order
                final ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(frame, data);
                Channels.write(ctx, e.getFuture(), buffer, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdySynReplyFrame) {

            synchronized (headerBlockCompressor) {
                SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
                ChannelBuffer data = compressHeaderBlock(
                        encodeHeaderBlock(version, spdySynReplyFrame));
                byte flags = spdySynReplyFrame.isLast() ? SPDY_FLAG_FIN : 0;
                int headerBlockLength = data.readableBytes();
                int length;
                if (version < 3) {
                    length = headerBlockLength == 0 ? 8 : 6 + headerBlockLength;
                } else {
                    length = 4 + headerBlockLength;
                }
                ChannelBuffer frame = ChannelBuffers.buffer(
                        ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 8);
                frame.writeShort(version | 0x8000);
                frame.writeShort(SPDY_SYN_REPLY_FRAME);
                frame.writeByte(flags);
                frame.writeMedium(length);
                frame.writeInt(spdySynReplyFrame.getStreamId());
                if (version < 3) {
                    if (data.readableBytes() == 0) {
                        frame.writeInt(0);
                    } else {
                        frame.writeShort(0);
                    }
                }
                // Writes of compressed data must occur in order
                final ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(frame, data);
                Channels.write(ctx, e.getFuture(), buffer, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 8);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_RST_STREAM_FRAME);
            frame.writeInt(8);
            frame.writeInt(spdyRstStreamFrame.getStreamId());
            frame.writeInt(spdyRstStreamFrame.getStatus().getCode());
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            byte flags = spdySettingsFrame.clearPreviouslyPersistedSettings() ?
                SPDY_SETTINGS_CLEAR : 0;
            Set<Integer> IDs = spdySettingsFrame.getIds();
            int numEntries = IDs.size();
            int length = 4 + numEntries * 8;
            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_SETTINGS_FRAME);
            frame.writeByte(flags);
            frame.writeMedium(length);
            frame.writeInt(numEntries);
            for (Integer ID: IDs) {
                int id = ID.intValue();
                byte ID_flags = 0;
                if (spdySettingsFrame.isPersistValue(id)) {
                    ID_flags |= SPDY_SETTINGS_PERSIST_VALUE;
                }
                if (spdySettingsFrame.isPersisted(id)) {
                    ID_flags |= SPDY_SETTINGS_PERSISTED;
                }
                if (version < 3) {
                    // Chromium Issue 79156
                    // SPDY setting ids are not written in network byte order
                    // Write id assuming the architecture is little endian
                    frame.writeByte(id & 0xFF);
                    frame.writeByte(id >>  8 & 0xFF);
                    frame.writeByte(id >> 16 & 0xFF);
                    frame.writeByte(ID_flags);
                } else {
                    frame.writeByte(ID_flags);
                    frame.writeMedium(id);
                }
                frame.writeInt(spdySettingsFrame.getValue(id));
            }
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyNoOpFrame) {

            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_NOOP_FRAME);
            frame.writeInt(0);
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 4);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_PING_FRAME);
            frame.writeInt(4);
            frame.writeInt(spdyPingFrame.getId());
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyGoAwayFrame) {

            SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
            int length = version < 3 ? 4 : 8;
            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_GOAWAY_FRAME);
            frame.writeInt(length);
            frame.writeInt(spdyGoAwayFrame.getLastGoodStreamId());
            if (version >= 3) {
                frame.writeInt(spdyGoAwayFrame.getStatus().getCode());
            }
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        if (msg instanceof SpdyHeadersFrame) {

            synchronized (headerBlockCompressor) {
                SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
                ChannelBuffer data = compressHeaderBlock(
                        encodeHeaderBlock(version, spdyHeadersFrame));
                byte flags = spdyHeadersFrame.isLast() ? SPDY_FLAG_FIN : 0;
                int headerBlockLength = data.readableBytes();
                int length;
                if (version < 3) {
                    length = headerBlockLength == 0 ? 4 : 6 + headerBlockLength;
                } else {
                    length = 4 + headerBlockLength;
                }
                ChannelBuffer frame = ChannelBuffers.buffer(
                        ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
                frame.writeShort(version | 0x8000);
                frame.writeShort(SPDY_HEADERS_FRAME);
                frame.writeByte(flags);
                frame.writeMedium(length);
                frame.writeInt(spdyHeadersFrame.getStreamId());
                if (version < 3 && data.readableBytes() != 0) {
                    frame.writeShort(0);
                }
                // Writes of compressed data must occur in order
                final ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(frame, data);
                Channels.write(ctx, e.getFuture(), buffer, e.getRemoteAddress());
            }
            return;
        }

        if (msg instanceof SpdyWindowUpdateFrame) {
            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            ChannelBuffer frame = ChannelBuffers.buffer(
                    ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 8);
            frame.writeShort(version | 0x8000);
            frame.writeShort(SPDY_WINDOW_UPDATE_FRAME);
            frame.writeInt(8);
            frame.writeInt(spdyWindowUpdateFrame.getStreamId());
            frame.writeInt(spdyWindowUpdateFrame.getDeltaWindowSize());
            Channels.write(ctx, e.getFuture(), frame, e.getRemoteAddress());
            return;
        }

        // Unknown message type
        ctx.sendDownstream(evt);
    }

    private static void writeLengthField(int version, ChannelBuffer buffer, int length) {
        if (version < 3) {
            buffer.writeShort(length);
        } else {
            buffer.writeInt(length);
        }
    }

    private static void setLengthField(int version, ChannelBuffer buffer, int writerIndex, int length) {
        if (version < 3) {
            buffer.setShort(writerIndex, length);
        } else {
            buffer.setInt(writerIndex, length);
        }
    }

    private static ChannelBuffer encodeHeaderBlock(int version, SpdyHeaderBlock headerFrame)
            throws Exception {
        Set<String> names = headerFrame.getHeaderNames();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }
        ChannelBuffer headerBlock = ChannelBuffers.dynamicBuffer(
                ByteOrder.BIG_ENDIAN, 256);
        writeLengthField(version, headerBlock, numHeaders);
        for (String name: names) {
            byte[] nameBytes = name.getBytes("UTF-8");
            writeLengthField(version, headerBlock, nameBytes.length);
            headerBlock.writeBytes(nameBytes);
            int savedIndex = headerBlock.writerIndex();
            int valueLength = 0;
            writeLengthField(version, headerBlock, valueLength);
            for (String value: headerFrame.getHeaders(name)) {
                byte[] valueBytes = value.getBytes("UTF-8");
                headerBlock.writeBytes(valueBytes);
                headerBlock.writeByte(0);
                valueLength += valueBytes.length + 1;
            }
            valueLength --;
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            setLengthField(version, headerBlock, savedIndex, valueLength);
            headerBlock.writerIndex(headerBlock.writerIndex() - 1);
        }
        return headerBlock;
    }

    // always called while synchronized on headerBlockCompressor
    private ChannelBuffer compressHeaderBlock(ChannelBuffer uncompressed)
            throws Exception {
        if (uncompressed.readableBytes() == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        ChannelBuffer compressed = ChannelBuffers.dynamicBuffer();
        if (!finished) {
            headerBlockCompressor.setInput(uncompressed);
            headerBlockCompressor.encode(compressed);
        }
        return compressed;
    }
}
