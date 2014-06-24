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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.ByteOrder;
import java.util.Set;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Encodes a SPDY Frame into a {@link ByteBuf}.
 */
public class SpdyFrameEncoder {

    private final int version;

    /**
     * Creates a new instance with the specified {@code spdyVersion}.
     */
    public SpdyFrameEncoder(SpdyVersion spdyVersion) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        version = spdyVersion.getVersion();
    }

    private void writeControlFrameHeader(ByteBuf buffer, int type, byte flags, int length) {
        buffer.writeShort(version | 0x8000);
        buffer.writeShort(type);
        buffer.writeByte(flags);
        buffer.writeMedium(length);
    }

    public ByteBuf encodeDataFrame(ByteBufAllocator allocator, int streamId, boolean last, ByteBuf data) {
        byte flags = last ? SPDY_DATA_FLAG_FIN : 0;
        int length = data.readableBytes();
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        frame.writeInt(streamId & 0x7FFFFFFF);
        frame.writeByte(flags);
        frame.writeMedium(length);
        frame.writeBytes(data, data.readerIndex(), length);
        return frame;
    }

    public ByteBuf encodeSynStreamFrame(ByteBufAllocator allocator,  int streamId, int associatedToStreamId,
            byte priority, boolean last, boolean unidirectional, ByteBuf headerBlock) {
        int headerBlockLength = headerBlock.readableBytes();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        if (unidirectional) {
            flags |= SPDY_FLAG_UNIDIRECTIONAL;
        }
        int length = 10 + headerBlockLength;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SYN_STREAM_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(associatedToStreamId);
        frame.writeShort((priority & 0xFF) << 13);
        frame.writeBytes(headerBlock, headerBlock.readerIndex(), headerBlockLength);
        return frame;
    }

    public ByteBuf encodeSynReplyFrame(ByteBufAllocator allocator, int streamId, boolean last, ByteBuf headerBlock) {
        int headerBlockLength = headerBlock.readableBytes();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlockLength;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SYN_REPLY_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeBytes(headerBlock, headerBlock.readerIndex(), headerBlockLength);
        return frame;
    }

    public ByteBuf encodeRstStreamFrame(ByteBufAllocator allocator, int streamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_RST_STREAM_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(statusCode);
        return frame;
    }

    public ByteBuf encodeSettingsFrame(ByteBufAllocator allocator, SpdySettingsFrame spdySettingsFrame) {
        Set<Integer> ids = spdySettingsFrame.ids();
        int numSettings = ids.size();

        byte flags = spdySettingsFrame.clearPreviouslyPersistedSettings() ?
                SPDY_SETTINGS_CLEAR : 0;
        int length = 4 + 8 * numSettings;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_SETTINGS_FRAME, flags, length);
        frame.writeInt(numSettings);
        for (Integer id : ids) {
            flags = 0;
            if (spdySettingsFrame.isPersistValue(id)) {
                flags |= SPDY_SETTINGS_PERSIST_VALUE;
            }
            if (spdySettingsFrame.isPersisted(id)) {
                flags |= SPDY_SETTINGS_PERSISTED;
            }
            frame.writeByte(flags);
            frame.writeMedium(id);
            frame.writeInt(spdySettingsFrame.getValue(id));
        }
        return frame;
    }

    public ByteBuf encodePingFrame(ByteBufAllocator allocator, int id) {
        byte flags = 0;
        int length = 4;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_PING_FRAME, flags, length);
        frame.writeInt(id);
        return frame;
    }

    public ByteBuf encodeGoAwayFrame(ByteBufAllocator allocator, int lastGoodStreamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_GOAWAY_FRAME, flags, length);
        frame.writeInt(lastGoodStreamId);
        frame.writeInt(statusCode);
        return frame;
    }

    public ByteBuf encodeHeadersFrame(ByteBufAllocator allocator, int streamId, boolean last, ByteBuf headerBlock) {
        int headerBlockLength = headerBlock.readableBytes();
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlockLength;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_HEADERS_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeBytes(headerBlock, headerBlock.readerIndex(), headerBlockLength);
        return frame;
    }

    public ByteBuf encodeWindowUpdateFrame(ByteBufAllocator allocator, int streamId, int deltaWindowSize) {
        byte flags = 0;
        int length = 8;
        ByteBuf frame = allocator.ioBuffer(SPDY_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN);
        writeControlFrameHeader(frame, SPDY_WINDOW_UPDATE_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(deltaWindowSize);
        return frame;
    }
}
