/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

/**
 * Decodes {@link ChannelBuffer}s into SPDY Data and Control Frames.
 */
public class SpdyFrameDecoder extends FrameDecoder {

    private final DecoderEmbedder<ChannelBuffer> headerBlockDecompressor =
        new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(SPDY_DICT));

    public SpdyFrameDecoder() {
        super();
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception {

        // Must read common header to determine frame length
        if (buffer.readableBytes() < SPDY_HEADER_SIZE) {
            return null;
        }

        // Get frame length from common header
        int frameOffset  = buffer.readerIndex();
        int lengthOffset = frameOffset + SPDY_HEADER_LENGTH_OFFSET;
        int dataLength   = getUnsignedMedium(buffer, lengthOffset);
        int frameLength  = SPDY_HEADER_SIZE + dataLength;

        // Wait until entire frame is readable
        if (buffer.readableBytes() < frameLength) {
            return null;
        }

        // Read common header fields
        boolean control = (buffer.getByte(frameOffset) & 0x80) != 0;
        int flagsOffset = frameOffset + SPDY_HEADER_FLAGS_OFFSET;
        byte flags = buffer.getByte(flagsOffset);

        if (control) {
            // Decode control frame common header
            int version = getUnsignedShort(buffer, frameOffset) & 0x7FFF;

            // Spdy versioning spec is broken
            if (version != SPDY_VERSION) {
                buffer.skipBytes(frameLength);
                throw new SpdyProtocolException(
                        "Unsupported version: " + version);
            }

            int typeOffset = frameOffset + SPDY_HEADER_TYPE_OFFSET;
            int type = getUnsignedShort(buffer, typeOffset);
            buffer.skipBytes(SPDY_HEADER_SIZE);

            return decodeControlFrame(type, flags, buffer.readBytes(dataLength));
        } else {
            // Decode data frame common header
            int streamID = getUnsignedInt(buffer, frameOffset);
            buffer.skipBytes(SPDY_HEADER_SIZE);

            SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamID);
            spdyDataFrame.setLast((flags & SPDY_DATA_FLAG_FIN) != 0);
            spdyDataFrame.setCompressed((flags & SPDY_DATA_FLAG_COMPRESS) != 0);
            spdyDataFrame.setData(buffer.readBytes(dataLength));

            return spdyDataFrame;
        }
    }

    private Object decodeControlFrame(int type, byte flags, ChannelBuffer data)
            throws Exception {
        int streamID;
        boolean last;

        switch (type) {
        case SPDY_SYN_STREAM_FRAME:
            if (data.readableBytes() < 12) {
                throw new SpdyProtocolException(
                        "Received invalid SYN_STREAM control frame");
            }
            streamID = getUnsignedInt(data, data.readerIndex());
            int associatedToStreamID = getUnsignedInt(data, data.readerIndex() + 4);
            byte priority = (byte) (data.getByte(data.readerIndex() + 8) >> 6 & 0x03);
            data.skipBytes(10);

            SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(streamID, associatedToStreamID, priority);

            last = (flags & SPDY_FLAG_FIN) != 0;
            boolean unid = (flags & SPDY_FLAG_UNIDIRECTIONAL) != 0;
            spdySynStreamFrame.setLast(last);
            spdySynStreamFrame.setUnidirectional(unid);

            decodeHeaderBlock(spdySynStreamFrame, decompress(data));

            return spdySynStreamFrame;

        case SPDY_SYN_REPLY_FRAME:
            if (data.readableBytes() < 8) {
                throw new SpdyProtocolException(
                        "Received invalid SYN_REPLY control frame");
            }
            streamID = getUnsignedInt(data, data.readerIndex());
            data.skipBytes(6);

            SpdySynReplyFrame spdySynReplyFrame =
                new DefaultSpdySynReplyFrame(streamID);

            last = (flags & SPDY_FLAG_FIN) != 0;
            spdySynReplyFrame.setLast(last);

            decodeHeaderBlock(spdySynReplyFrame, decompress(data));

            return spdySynReplyFrame;

        case SPDY_RST_STREAM_FRAME:
            if (flags != 0 || data.readableBytes() != 8) {
                throw new SpdyProtocolException(
                        "Received invalid RST_STREAM control frame");
            }
            streamID = getUnsignedInt(data, data.readerIndex());
            int statusCode = getSignedInt(data, data.readerIndex() + 4);
            if (statusCode == 0) {
                throw new SpdyProtocolException(
                        "Received invalid RST_STREAM status code");
            }

            return new DefaultSpdyRstStreamFrame(streamID, statusCode);

        case SPDY_SETTINGS_FRAME:
            if (data.readableBytes() < 4) {
                throw new SpdyProtocolException(
                        "Received invalid SETTINGS control frame");
            }
            // Each ID/Value entry is 8 bytes
            // The number of entries cannot exceed SPDY_MAX_LENGTH / 8;
            int numEntries = getUnsignedInt(data, data.readerIndex());
            if ((numEntries > (SPDY_MAX_LENGTH - 4) / 8) ||
                (data.readableBytes() != numEntries * 8 + 4)) {
                throw new SpdyProtocolException(
                        "Received invalid SETTINGS control frame");
            }
            data.skipBytes(4);

            SpdySettingsFrame spdySettingsFrame = new DefaultSpdySettingsFrame();

            boolean clear = (flags & SPDY_SETTINGS_CLEAR) != 0;
            spdySettingsFrame.setClearPreviouslyPersistedSettings(clear);

            for (int i = 0; i < numEntries; i ++) {
                // Chromium Issue 79156
                // SPDY setting ids are not written in network byte order
                // Read id assuming the architecture is little endian
                int ID = (data.readByte() & 0xFF) |
                         (data.readByte() & 0xFF) << 8 |
                         (data.readByte() & 0xFF) << 16;
                byte ID_flags = data.readByte();
                int value = getSignedInt(data, data.readerIndex());
                data.skipBytes(4);

                if (!(spdySettingsFrame.isSet(ID))) {
                    boolean persistVal = (ID_flags & SPDY_SETTINGS_PERSIST_VALUE) != 0;
                    boolean persisted  = (ID_flags & SPDY_SETTINGS_PERSISTED) != 0;
                    spdySettingsFrame.setValue(ID, value, persistVal, persisted);
                }
            }

            return spdySettingsFrame;

        case SPDY_NOOP_FRAME:
            if (data.readableBytes() != 0) {
                throw new SpdyProtocolException(
                        "Received invalid NOOP control frame");
            }

            return null;

        case SPDY_PING_FRAME:
            if (data.readableBytes() != 4) {
                throw new SpdyProtocolException(
                        "Received invalid PING control frame");
            }
            int ID = getSignedInt(data, data.readerIndex());

            return new DefaultSpdyPingFrame(ID);

        case SPDY_GOAWAY_FRAME:
            if (data.readableBytes() != 4) {
                throw new SpdyProtocolException(
                        "Received invalid GOAWAY control frame");
            }
            int lastGoodStreamID = getUnsignedInt(data, data.readerIndex());

            return new DefaultSpdyGoAwayFrame(lastGoodStreamID);

        case SPDY_HEADERS_FRAME:
            // Protocol allows length 4 frame when there are no name/value pairs
            if (data.readableBytes() == 4) {
                streamID = getUnsignedInt(data, data.readerIndex());
                return new DefaultSpdyHeadersFrame(streamID);
            }

            if (data.readableBytes() < 8) {
                throw new SpdyProtocolException(
                        "Received invalid HEADERS control frame");
            }
            streamID = getUnsignedInt(data, data.readerIndex());
            data.skipBytes(6);

            SpdyHeadersFrame spdyHeadersFrame = new DefaultSpdyHeadersFrame(streamID);

            decodeHeaderBlock(spdyHeadersFrame, decompress(data));

            return spdyHeadersFrame;

        case SPDY_WINDOW_UPDATE_FRAME:
            return null;

        default:
            return null;
        }
    }

    private ChannelBuffer decompress(ChannelBuffer compressed) throws Exception {
        if ((compressed.readableBytes() == 2) &&
            (compressed.getShort(compressed.readerIndex()) == 0)) {
            return compressed;
        }
        headerBlockDecompressor.offer(compressed);
        return headerBlockDecompressor.poll();
    }

    private void decodeHeaderBlock(SpdyHeaderBlock headerFrame, ChannelBuffer headerBlock)
            throws Exception {
        if (headerBlock.readableBytes() < 2) {
            throw new SpdyProtocolException(
                    "Received invalid header block");
        }
        int numEntries = getUnsignedShort(headerBlock, headerBlock.readerIndex());
        headerBlock.skipBytes(2);
        for (int i = 0; i < numEntries; i ++) {
            if (headerBlock.readableBytes() < 2) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            int nameLength = getUnsignedShort(headerBlock, headerBlock.readerIndex());
            headerBlock.skipBytes(2);
            if (nameLength == 0) {
                headerFrame.setInvalid();
                return;
            }
            if (headerBlock.readableBytes() < nameLength) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            byte[] nameBytes = new byte[nameLength];
            headerBlock.readBytes(nameBytes);
            String name = new String(nameBytes, "UTF-8");
            if (headerFrame.containsHeader(name)) {
                throw new SpdyProtocolException(
                        "Received duplicate header name: " + name);
            }
            if (headerBlock.readableBytes() < 2) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            int valueLength = getUnsignedShort(headerBlock, headerBlock.readerIndex());
            headerBlock.skipBytes(2);
            if (valueLength == 0) {
                headerFrame.setInvalid();
                return;
            }
            if (headerBlock.readableBytes() < valueLength) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            byte[] valueBytes = new byte[valueLength];
            headerBlock.readBytes(valueBytes);
            int index = 0;
            int offset = 0;
            while (index < valueBytes.length) {
                while (index < valueBytes.length && valueBytes[index] != (byte) 0) {
                    index ++;
                }
                if (index < valueBytes.length && valueBytes[index + 1] == (byte) 0) {
                    // Received multiple, in-sequence NULL characters
                    headerFrame.setInvalid();
                    return;
                }
                String value = new String(valueBytes, offset, index - offset, "UTF-8");
                headerFrame.addHeader(name, value);
                index ++;
                offset = index;
            }
        }
    }
}
