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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.CharsetUtil;

import java.util.Set;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Encodes a SPDY Data or Control Frame into a {@link ByteBuf}.
 */
public class SpdyFrameEncoder extends MessageToByteEncoder<SpdyDataOrControlFrame> {

    private final int version;
    private volatile boolean finished;
    private final SpdyHeaderBlockCompressor headerBlockCompressor;

    /**
     * Creates a new instance with the specified {@code version} and the
     * default {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}.
     */
    public SpdyFrameEncoder(int version) {
        this(version, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameEncoder(int version, int compressionLevel, int windowBits, int memLevel) {
        if (version < SpdyConstants.SPDY_MIN_VERSION || version > SpdyConstants.SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unknown version: " + version);
        }
        this.version = version;
        headerBlockCompressor = SpdyHeaderBlockCompressor.newInstance(
                version, compressionLevel, windowBits, memLevel);
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                synchronized (headerBlockCompressor) {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    headerBlockCompressor.end();
                }
            }
        });
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SpdyDataOrControlFrame msg, ByteBuf out) throws Exception {
        if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            ByteBuf data = spdyDataFrame.data();
            byte flags = spdyDataFrame.isLast() ? SPDY_DATA_FLAG_FIN : 0;
            out.ensureWritable(SPDY_HEADER_SIZE + data.readableBytes());
            out.writeInt(spdyDataFrame.getStreamId() & 0x7FFFFFFF);
            out.writeByte(flags);
            out.writeMedium(data.readableBytes());
            out.writeBytes(data, data.readerIndex(), data.readableBytes());

        } else if (msg instanceof SpdySynStreamFrame) {

            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            ByteBuf data = compressHeaderBlock(
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
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_SYN_STREAM_FRAME);
            out.writeByte(flags);
            out.writeMedium(length);
            out.writeInt(spdySynStreamFrame.getStreamId());
            out.writeInt(spdySynStreamFrame.getAssociatedToStreamId());
            if (version < 3) {
                // Restrict priorities for SPDY/2 to between 0 and 3
                byte priority = spdySynStreamFrame.getPriority();
                if (priority > 3) {
                    priority = 3;
                }
                out.writeShort((priority & 0xFF) << 14);
            } else {
                out.writeShort((spdySynStreamFrame.getPriority() & 0xFF) << 13);
            }
            if (version < 3 && data.readableBytes() == 0) {
                out.writeShort(0);
            }
            out.writeBytes(data, data.readerIndex(), headerBlockLength);

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            ByteBuf data = compressHeaderBlock(
                    encodeHeaderBlock(version, spdySynReplyFrame));
            byte flags = spdySynReplyFrame.isLast() ? SPDY_FLAG_FIN : 0;
            int headerBlockLength = data.readableBytes();
            int length;
            if (version < 3) {
                length = headerBlockLength == 0 ? 8 : 6 + headerBlockLength;
            } else {
                length = 4 + headerBlockLength;
            }
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_SYN_REPLY_FRAME);
            out.writeByte(flags);
            out.writeMedium(length);
            out.writeInt(spdySynReplyFrame.getStreamId());
            if (version < 3) {
                if (headerBlockLength == 0) {
                    out.writeInt(0);
                } else {
                    out.writeShort(0);
                }
            }
            out.writeBytes(data, data.readerIndex(), headerBlockLength);

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 8);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_RST_STREAM_FRAME);
            out.writeInt(8);
            out.writeInt(spdyRstStreamFrame.getStreamId());
            out.writeInt(spdyRstStreamFrame.getStatus().getCode());

        } else if (msg instanceof SpdySettingsFrame) {

            SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
            byte flags = spdySettingsFrame.clearPreviouslyPersistedSettings() ?
                SPDY_SETTINGS_CLEAR : 0;
            Set<Integer> IDs = spdySettingsFrame.getIds();
            int numEntries = IDs.size();
            int length = 4 + numEntries * 8;
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_SETTINGS_FRAME);
            out.writeByte(flags);
            out.writeMedium(length);
            out.writeInt(numEntries);
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
                    out.writeByte(id       & 0xFF);
                    out.writeByte(id >>  8 & 0xFF);
                    out.writeByte(id >> 16 & 0xFF);
                    out.writeByte(ID_flags);
                } else {
                    out.writeByte(ID_flags);
                    out.writeMedium(id);
                }
                out.writeInt(spdySettingsFrame.getValue(id));
            }

        } else if (msg instanceof SpdyNoOpFrame) {

            out.ensureWritable(SPDY_HEADER_SIZE);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_NOOP_FRAME);
            out.writeInt(0);

        } else if (msg instanceof SpdyPingFrame) {

            SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 4);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_PING_FRAME);
            out.writeInt(4);
            out.writeInt(spdyPingFrame.getId());

        } else if (msg instanceof SpdyGoAwayFrame) {

            SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
            int length = version < 3 ? 4 : 8;
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_GOAWAY_FRAME);
            out.writeInt(length);
            out.writeInt(spdyGoAwayFrame.getLastGoodStreamId());
            if (version >= 3) {
                out.writeInt(spdyGoAwayFrame.getStatus().getCode());
            }

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            ByteBuf data = compressHeaderBlock(
                    encodeHeaderBlock(version, spdyHeadersFrame));
            byte flags = spdyHeadersFrame.isLast() ? SPDY_FLAG_FIN : 0;
            int headerBlockLength = data.readableBytes();
            int length;
            if (version < 3) {
                length = headerBlockLength == 0 ? 4 : 6 + headerBlockLength;
            } else {
                length = 4 + headerBlockLength;
            }
            out.ensureWritable(SPDY_HEADER_SIZE + length);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_HEADERS_FRAME);
            out.writeByte(flags);
            out.writeMedium(length);
            out.writeInt(spdyHeadersFrame.getStreamId());
            if (version < 3 && headerBlockLength != 0) {
                out.writeShort(0);
            }
            out.writeBytes(data, data.readerIndex(), headerBlockLength);

        } else if (msg instanceof SpdyWindowUpdateFrame) {

            SpdyWindowUpdateFrame spdyWindowUpdateFrame = (SpdyWindowUpdateFrame) msg;
            out.ensureWritable(SPDY_HEADER_SIZE + 8);
            out.writeShort(version | 0x8000);
            out.writeShort(SPDY_WINDOW_UPDATE_FRAME);
            out.writeInt(8);
            out.writeInt(spdyWindowUpdateFrame.getStreamId());
            out.writeInt(spdyWindowUpdateFrame.getDeltaWindowSize());
        } else {
            throw new UnsupportedMessageTypeException(msg);
        }
    }

    private static void writeLengthField(int version, ByteBuf buffer, int length) {
        if (version < 3) {
            buffer.writeShort(length);
        } else {
            buffer.writeInt(length);
        }
    }

    private static void setLengthField(int version, ByteBuf buffer, int writerIndex, int length) {
        if (version < 3) {
            buffer.setShort(writerIndex, length);
        } else {
            buffer.setInt(writerIndex, length);
        }
    }

    private static ByteBuf encodeHeaderBlock(int version, SpdyHeaderBlock headerFrame)
            throws Exception {
        Set<String> names = headerFrame.headers().names();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }
        ByteBuf headerBlock = Unpooled.buffer();
        writeLengthField(version, headerBlock, numHeaders);
        for (String name: names) {
            byte[] nameBytes = name.getBytes(CharsetUtil.UTF_8);
            writeLengthField(version, headerBlock, nameBytes.length);
            headerBlock.writeBytes(nameBytes);
            int savedIndex = headerBlock.writerIndex();
            int valueLength = 0;
            writeLengthField(version, headerBlock, valueLength);
            for (String value: headerFrame.headers().getAll(name)) {
                byte[] valueBytes = value.getBytes(CharsetUtil.UTF_8);
                if (valueBytes.length > 0) {
                    headerBlock.writeBytes(valueBytes);
                    headerBlock.writeByte(0);
                    valueLength += valueBytes.length + 1;
                }
            }
            if (valueLength == 0) {
                if (version < 3) {
                    throw new IllegalArgumentException(
                            "header value cannot be empty: " + name);
                }
            } else {
                valueLength --;
            }
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            if (valueLength > 0) {
                setLengthField(version, headerBlock, savedIndex, valueLength);
                headerBlock.writerIndex(headerBlock.writerIndex() - 1);
            }
        }
        return headerBlock;
    }

    private synchronized ByteBuf compressHeaderBlock(
            ByteBuf uncompressed) throws Exception {
        if (uncompressed.readableBytes() == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf compressed = Unpooled.buffer();
        synchronized (headerBlockCompressor) {
            if (!finished) {
                headerBlockCompressor.setInput(uncompressed);
                headerBlockCompressor.encode(compressed);
            }
        }
        return compressed;
    }
}
