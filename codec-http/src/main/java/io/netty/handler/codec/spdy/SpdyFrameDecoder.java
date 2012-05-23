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

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.handler.codec.StreamToMessageDecoder;

/**
 * Decodes {@link ChannelBuffer}s into SPDY Data and Control Frames.
 */
public class SpdyFrameDecoder extends StreamToMessageDecoder<Object> {

    private final int maxChunkSize;
    private final int maxFrameSize;
    private final int maxHeaderSize;

    private final SpdyHeaderBlockDecompressor headerBlockDecompressor =
            SpdyHeaderBlockDecompressor.newInstance();

    /**
     * Creates a new instance with the default {@code maxChunkSize (8192)},
     * {@code maxFrameSize (65536)}, and {@code maxHeaderSize (16384)}.
     */
    public SpdyFrameDecoder() {
        this(8192, 65536, 16384);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameDecoder(
            int maxChunkSize, int maxFrameSize, int maxHeaderSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " + maxChunkSize);
        }
        if (maxFrameSize <= 0) {
            throw new IllegalArgumentException(
                    "maxFrameSize must be a positive integer: " + maxFrameSize);
        }
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException(
                    "maxHeaderSize must be a positive integer: " + maxHeaderSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.maxFrameSize = maxFrameSize;
        this.maxHeaderSize = maxHeaderSize;
    }

    @Override
    public Object decodeLast(ChannelInboundHandlerContext<Byte> ctx,
            ChannelBuffer in) throws Exception {
        try {
            return decode(ctx, in);
        } finally {
            headerBlockDecompressor.end();
        }
    }

    @Override
    public Object decode(ChannelInboundHandlerContext<Byte> ctx,
            ChannelBuffer in) throws Exception {
        // Must read common header to determine frame length
        if (in.readableBytes() < SPDY_HEADER_SIZE) {
            return null;
        }

        // Get frame length from common header
        int frameOffset  = in.readerIndex();
        int lengthOffset = frameOffset + SPDY_HEADER_LENGTH_OFFSET;
        int dataLength   = getUnsignedMedium(in, lengthOffset);
        int frameLength  = SPDY_HEADER_SIZE + dataLength;

        // Throw exception if frameLength exceeds maxFrameSize
        if (frameLength > maxFrameSize) {
            throw new SpdyProtocolException(
                    "Frame length exceeds " + maxFrameSize + ": " + frameLength);
        }

        // Wait until entire frame is readable
        if (in.readableBytes() < frameLength) {
            return null;
        }

        // Read common header fields
        boolean control = (in.getByte(frameOffset) & 0x80) != 0;
        int flagsOffset = frameOffset + SPDY_HEADER_FLAGS_OFFSET;
        byte flags = in.getByte(flagsOffset);

        if (control) {
            // Decode control frame common header
            int version = getUnsignedShort(in, frameOffset) & 0x7FFF;

            // Spdy versioning spec is broken
            if (version != SPDY_VERSION) {
                in.skipBytes(frameLength);
                throw new SpdyProtocolException(
                        "Unsupported version: " + version);
            }

            int typeOffset = frameOffset + SPDY_HEADER_TYPE_OFFSET;
            int type = getUnsignedShort(in, typeOffset);
            in.skipBytes(SPDY_HEADER_SIZE);

            int readerIndex = in.readerIndex();
            in.skipBytes(dataLength);
            return decodeControlFrame(type, flags, in.slice(readerIndex, dataLength));
        } else {
            // Decode data frame common header
            int streamID = getUnsignedInt(in, frameOffset);
            in.skipBytes(SPDY_HEADER_SIZE);

            // Generate data frames that do not exceed maxChunkSize
            int numFrames = dataLength / maxChunkSize;
            if (dataLength % maxChunkSize != 0) {
                numFrames ++;
            }
            SpdyDataFrame[] frames = new SpdyDataFrame[numFrames];
            for (int i = 0; i < numFrames; i++) {
                int chunkSize = Math.min(maxChunkSize, dataLength);
                SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamID);
                spdyDataFrame.setCompressed((flags & SPDY_DATA_FLAG_COMPRESS) != 0);
                spdyDataFrame.setData(in.readBytes(chunkSize));
                dataLength -= chunkSize;
                if (dataLength == 0) {
                    spdyDataFrame.setLast((flags & SPDY_DATA_FLAG_FIN) != 0);
                }
                frames[i] = spdyDataFrame;
            }

            return frames;
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

            decodeHeaderBlock(spdySynStreamFrame, data);

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

            decodeHeaderBlock(spdySynReplyFrame, data);

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
            if (numEntries > (SPDY_MAX_LENGTH - 4) / 8 ||
                data.readableBytes() != numEntries * 8 + 4) {
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
                int ID = data.readByte() & 0xFF |
                         (data.readByte() & 0xFF) << 8 |
                         (data.readByte() & 0xFF) << 16;
                byte ID_flags = data.readByte();
                int value = getSignedInt(data, data.readerIndex());
                data.skipBytes(4);

                if (!spdySettingsFrame.isSet(ID)) {
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

            decodeHeaderBlock(spdyHeadersFrame, data);

            return spdyHeadersFrame;

        case SPDY_WINDOW_UPDATE_FRAME:
            return null;

        default:
            return null;
        }
    }

    private boolean ensureBytes(ChannelBuffer decompressed, int bytes) throws Exception {
        if (decompressed.readableBytes() >= bytes) {
            return true;
        }
        decompressed.discardReadBytes();
        headerBlockDecompressor.decode(decompressed);
        return decompressed.readableBytes() >= bytes;
    }

    private void decodeHeaderBlock(SpdyHeaderBlock headerFrame, ChannelBuffer headerBlock)
            throws Exception {
        if (headerBlock.readableBytes() == 2 &&
            headerBlock.getShort(headerBlock.readerIndex()) == 0) {
            return;
        }

        headerBlockDecompressor.setInput(headerBlock);
        ChannelBuffer decompressed = ChannelBuffers.dynamicBuffer(8192);
        headerBlockDecompressor.decode(decompressed);

        if (decompressed.readableBytes() < 2) {
            throw new SpdyProtocolException(
                    "Received invalid header block");
        }
        int headerSize = 0;
        int numEntries = decompressed.readUnsignedShort();
        for (int i = 0; i < numEntries; i ++) {
            if (!ensureBytes(decompressed, 2)) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            int nameLength = decompressed.readUnsignedShort();
            if (nameLength == 0) {
                headerFrame.setInvalid();
                return;
            }
            headerSize += nameLength;
            if (headerSize > maxHeaderSize) {
                throw new SpdyProtocolException(
                        "Header block exceeds " + maxHeaderSize);
            }
            if (!ensureBytes(decompressed, nameLength)) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            byte[] nameBytes = new byte[nameLength];
            decompressed.readBytes(nameBytes);
            String name = new String(nameBytes, "UTF-8");
            if (headerFrame.containsHeader(name)) {
                throw new SpdyProtocolException(
                        "Received duplicate header name: " + name);
            }
            if (!ensureBytes(decompressed, 2)) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            int valueLength = decompressed.readUnsignedShort();
            if (valueLength == 0) {
                headerFrame.setInvalid();
                return;
            }
            headerSize += valueLength;
            if (headerSize > maxHeaderSize) {
                throw new SpdyProtocolException(
                        "Header block exceeds " + maxHeaderSize);
            }
            if (!ensureBytes(decompressed, valueLength)) {
                throw new SpdyProtocolException(
                        "Received invalid header block");
            }
            byte[] valueBytes = new byte[valueLength];
            decompressed.readBytes(valueBytes);
            int index = 0;
            int offset = 0;
            while (index < valueLength) {
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
