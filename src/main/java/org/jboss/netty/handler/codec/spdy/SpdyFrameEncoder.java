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

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.nio.ByteOrder;
import java.util.Set;

/**
 * Encodes a SPDY Frame into a {@link ChannelBuffer}.
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

    private void writeControlFrameHeader(ChannelBuffer buffer, int type, byte flags, int length) {
        buffer.writeShort(version | 0x8000);
        buffer.writeShort(type);
        buffer.writeByte(flags);
        buffer.writeMedium(length);
    }

    public ChannelBuffer encodeDataFrame(int streamId, boolean last, ChannelBuffer data) {
        byte flags = last ? SPDY_DATA_FLAG_FIN : 0;
        ChannelBuffer header = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE);
        header.writeInt(streamId & 0x7FFFFFFF);
        header.writeByte(flags);
        header.writeMedium(data.readableBytes());
        return ChannelBuffers.wrappedBuffer(header, data);
    }

    public ChannelBuffer encodeSynStreamFrame(
            int streamId, int associatedToStreamId, byte priority, boolean last, boolean unidirectional,
            ChannelBuffer headerBlock) {
        byte flags = last ? SPDY_FLAG_FIN : 0;
        if (unidirectional) {
            flags |= SPDY_FLAG_UNIDIRECTIONAL;
        }
        int length = 10 + headerBlock.readableBytes();
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 10);
        writeControlFrameHeader(frame, SPDY_SYN_STREAM_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(associatedToStreamId);
        frame.writeShort((priority & 0xFF) << 13);
        return ChannelBuffers.wrappedBuffer(frame, headerBlock);
    }

    public ChannelBuffer encodeSynReplyFrame(int streamId, boolean last, ChannelBuffer headerBlock) {
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlock.readableBytes();
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 4);
        writeControlFrameHeader(frame, SPDY_SYN_REPLY_FRAME, flags, length);
        frame.writeInt(streamId);
        return ChannelBuffers.wrappedBuffer(frame, headerBlock);
    }

    public ChannelBuffer encodeRstStreamFrame(int streamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
        writeControlFrameHeader(frame, SPDY_RST_STREAM_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(statusCode);
        return frame;
    }

    public ChannelBuffer encodeSettingsFrame(SpdySettingsFrame spdySettingsFrame) {
        Set<Integer> ids = spdySettingsFrame.getIds();
        int numSettings = ids.size();

        byte flags = spdySettingsFrame.clearPreviouslyPersistedSettings() ?
            SPDY_SETTINGS_CLEAR : 0;
        int length = 4 + 8 * numSettings;
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
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

    public ChannelBuffer encodePingFrame(int id) {
        byte flags = 0;
        int length = 4;
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
        writeControlFrameHeader(frame, SPDY_PING_FRAME, flags, length);
        frame.writeInt(id);
        return frame;
    }

    public ChannelBuffer encodeGoAwayFrame(int lastGoodStreamId, int statusCode) {
        byte flags = 0;
        int length = 8;
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
        writeControlFrameHeader(frame, SPDY_GOAWAY_FRAME, flags, length);
        frame.writeInt(lastGoodStreamId);
        frame.writeInt(statusCode);
        return frame;
    }

    public ChannelBuffer encodeHeadersFrame(int streamId, boolean last, ChannelBuffer headerBlock) {
        byte flags = last ? SPDY_FLAG_FIN : 0;
        int length = 4 + headerBlock.readableBytes();
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + 4);
        writeControlFrameHeader(frame, SPDY_HEADERS_FRAME, flags, length);
        frame.writeInt(streamId);
        return ChannelBuffers.wrappedBuffer(frame, headerBlock);
    }

    public ChannelBuffer encodeWindowUpdateFrame(int streamId, int deltaWindowSize) {
        byte flags = 0;
        int length = 8;
        ChannelBuffer frame = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, SPDY_HEADER_SIZE + length);
        writeControlFrameHeader(frame, SPDY_WINDOW_UPDATE_FRAME, flags, length);
        frame.writeInt(streamId);
        frame.writeInt(deltaWindowSize);
        return frame;
    }
}
