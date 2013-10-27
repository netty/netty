/*
 * Copyright 2013 The Netty Project
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

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_DATA_FLAG_FIN;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_DATA_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_FLAG_FIN;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_FLAG_UNIDIRECTIONAL;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_GOAWAY_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADERS_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_FLAGS_OFFSET;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_LENGTH_OFFSET;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_TYPE_OFFSET;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_PING_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_RST_STREAM_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SETTINGS_CLEAR;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SETTINGS_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SETTINGS_PERSISTED;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SETTINGS_PERSIST_VALUE;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SYN_REPLY_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_SYN_STREAM_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_WINDOW_UPDATE_FRAME;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.getSignedInt;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.getUnsignedInt;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.getUnsignedMedium;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.getUnsignedShort;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into SPDY Frames.
 */
public class SpdyFrameDecoder extends ByteToMessageDecoder {

    private static final SpdyProtocolException INVALID_FRAME =
            new SpdyProtocolException("Received invalid frame");

    private final int spdyVersion;
    private final int maxChunkSize;

    private final SpdyHeaderBlockDecoder headerBlockDecoder;

    private State state;
    private SpdySettingsFrame spdySettingsFrame;
    private SpdyHeadersFrame spdyHeadersFrame;

    // SPDY common header fields
    private byte flags;
    private int length;
    private int version;
    private int type;
    private int streamId;

    private enum State {
        READ_COMMON_HEADER,
        READ_CONTROL_FRAME,
        READ_SETTINGS_FRAME,
        READ_HEADER_BLOCK_FRAME,
        READ_HEADER_BLOCK,
        READ_DATA_FRAME,
        DISCARD_FRAME,
        FRAME_ERROR
    }

    /**
     * Creates a new instance with the specified {@code version} and the default
     * {@code maxChunkSize (8192)} and {@code maxHeaderSize (16384)}.
     */
    public SpdyFrameDecoder(SpdyVersion version) {
        this(version, 8192, 16384);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SpdyFrameDecoder(SpdyVersion version, int maxChunkSize, int maxHeaderSize) {
        this(version, maxChunkSize, SpdyHeaderBlockDecoder.newInstance(version, maxHeaderSize));
    }

    protected SpdyFrameDecoder(
            SpdyVersion version, int maxChunkSize, SpdyHeaderBlockDecoder headerBlockDecoder) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " + maxChunkSize);
        }
        spdyVersion = version.getVersion();
        this.maxChunkSize = maxChunkSize;
        this.headerBlockDecoder = headerBlockDecoder;
        state = State.READ_COMMON_HEADER;
    }

    @Override
    public void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            decode(ctx, in, out);
        } finally {
            headerBlockDecoder.end();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch(state) {
        case READ_COMMON_HEADER:
            state = readCommonHeader(buffer);
            if (state == State.FRAME_ERROR) {
                if (version != spdyVersion) {
                    fireProtocolException(ctx, "Unsupported version: " + version);
                } else {
                    fireInvalidFrameException(ctx);
                }
            }

            // FrameDecoders must consume data when producing frames
            // All length 0 frames must be generated now
            if (length == 0) {
                if (state == State.READ_DATA_FRAME) {
                    SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamId);
                    spdyDataFrame.setLast((flags & SPDY_DATA_FLAG_FIN) != 0);
                    state = State.READ_COMMON_HEADER;
                    out.add(spdyDataFrame);
                    return;
                }
                // There are no length 0 control frames
                state = State.READ_COMMON_HEADER;
            }

            return;

        case READ_CONTROL_FRAME:
            try {
                Object frame = readControlFrame(buffer);
                if (frame != null) {
                    state = State.READ_COMMON_HEADER;
                    out.add(frame);
                }
                return;
            } catch (IllegalArgumentException e) {
                state = State.FRAME_ERROR;
                fireInvalidFrameException(ctx);
            }
            return;

        case READ_SETTINGS_FRAME:
            if (spdySettingsFrame == null) {
                // Validate frame length against number of entries
                if (buffer.readableBytes() < 4) {
                    return;
                }
                int numEntries = getUnsignedInt(buffer, buffer.readerIndex());
                buffer.skipBytes(4);
                length -= 4;

                // Each ID/Value entry is 8 bytes
                if ((length & 0x07) != 0 || length >> 3 != numEntries) {
                    state = State.FRAME_ERROR;
                    fireInvalidFrameException(ctx);
                    return;
                }

                spdySettingsFrame = new DefaultSpdySettingsFrame();

                boolean clear = (flags & SPDY_SETTINGS_CLEAR) != 0;
                spdySettingsFrame.setClearPreviouslyPersistedSettings(clear);
            }

            int readableEntries = Math.min(buffer.readableBytes() >> 3, length >> 3);
            for (int i = 0; i < readableEntries; i ++) {
                byte ID_flags = buffer.getByte(buffer.readerIndex());
                int ID = getUnsignedMedium(buffer, buffer.readerIndex() + 1);
                int value = getSignedInt(buffer, buffer.readerIndex() + 4);
                buffer.skipBytes(8);

                // Check for invalid ID -- avoid IllegalArgumentException in setValue
                if (ID == 0) {
                    state = State.FRAME_ERROR;
                    spdySettingsFrame = null;
                    fireInvalidFrameException(ctx);
                    return;
                }

                if (!spdySettingsFrame.isSet(ID)) {
                    boolean persistVal = (ID_flags & SPDY_SETTINGS_PERSIST_VALUE) != 0;
                    boolean persisted  = (ID_flags & SPDY_SETTINGS_PERSISTED) != 0;
                    spdySettingsFrame.setValue(ID, value, persistVal, persisted);
                }
            }

            length -= 8 * readableEntries;
            if (length == 0) {
                state = State.READ_COMMON_HEADER;
                Object frame = spdySettingsFrame;
                spdySettingsFrame = null;
                out.add(frame);
                return;
            }
            return;

        case READ_HEADER_BLOCK_FRAME:
            try {
                spdyHeadersFrame = readHeaderBlockFrame(buffer);
                if (spdyHeadersFrame != null) {
                    if (length == 0) {
                        state = State.READ_COMMON_HEADER;
                        Object frame = spdyHeadersFrame;
                        spdyHeadersFrame = null;
                        out.add(frame);
                        return;
                    }
                    state = State.READ_HEADER_BLOCK;
                }
                return;
            } catch (IllegalArgumentException e) {
                state = State.FRAME_ERROR;
                fireInvalidFrameException(ctx);
                return;
            }

        case READ_HEADER_BLOCK:
            int compressedBytes = Math.min(buffer.readableBytes(), length);
            ByteBuf compressed = buffer.slice(buffer.readerIndex(), compressedBytes);

            try {
                headerBlockDecoder.decode(compressed, spdyHeadersFrame);
            } catch (Exception e) {
                state = State.FRAME_ERROR;
                spdyHeadersFrame = null;
                ctx.fireExceptionCaught(e);
                return;
            }

            int readBytes = compressedBytes - compressed.readableBytes();
            buffer.skipBytes(readBytes);
            length -= readBytes;

            if (spdyHeadersFrame != null &&
                    (spdyHeadersFrame.isInvalid() || spdyHeadersFrame.isTruncated())) {

                Object frame = spdyHeadersFrame;
                spdyHeadersFrame = null;
                if (length == 0) {
                    headerBlockDecoder.reset();
                    state = State.READ_COMMON_HEADER;
                }
                out.add(frame);
                return;
            }

            if (length == 0) {
                Object frame = spdyHeadersFrame;
                spdyHeadersFrame = null;
                headerBlockDecoder.reset();
                state = State.READ_COMMON_HEADER;
                if (frame != null) {
                    out.add(frame);
                }
            }
            return;

        case READ_DATA_FRAME:
            if (streamId == 0) {
                state = State.FRAME_ERROR;
                fireProtocolException(ctx, "Received invalid data frame");
                return;
            }

            // Generate data frames that do not exceed maxChunkSize
            int dataLength = Math.min(maxChunkSize, length);

            // Wait until entire frame is readable
            if (buffer.readableBytes() < dataLength) {
                return;
            }

            ByteBuf data = ctx.alloc().buffer(dataLength);
            data.writeBytes(buffer, dataLength);
            SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(streamId, data);
            length -= dataLength;

            if (length == 0) {
                spdyDataFrame.setLast((flags & SPDY_DATA_FLAG_FIN) != 0);
                state = State.READ_COMMON_HEADER;
            }
            out.add(spdyDataFrame);
            return;

        case DISCARD_FRAME:
            int numBytes = Math.min(buffer.readableBytes(), length);
            buffer.skipBytes(numBytes);
            length -= numBytes;
            if (length == 0) {
                state = State.READ_COMMON_HEADER;
            }
            return;

        case FRAME_ERROR:
            buffer.skipBytes(buffer.readableBytes());
            return;

        default:
            throw new Error("Shouldn't reach here.");
        }
    }

    private State readCommonHeader(ByteBuf buffer) {
        // Wait until entire header is readable
        if (buffer.readableBytes() < SPDY_HEADER_SIZE) {
            return State.READ_COMMON_HEADER;
        }

        int frameOffset  = buffer.readerIndex();
        int flagsOffset  = frameOffset + SPDY_HEADER_FLAGS_OFFSET;
        int lengthOffset = frameOffset + SPDY_HEADER_LENGTH_OFFSET;
        buffer.skipBytes(SPDY_HEADER_SIZE);

        // Read common header fields
        boolean control = (buffer.getByte(frameOffset) & 0x80) != 0;
        flags  = buffer.getByte(flagsOffset);
        length = getUnsignedMedium(buffer, lengthOffset);

        if (control) {
            // Decode control frame common header
            version = getUnsignedShort(buffer, frameOffset) & 0x7FFF;

            int typeOffset = frameOffset + SPDY_HEADER_TYPE_OFFSET;
            type = getUnsignedShort(buffer, typeOffset);

            streamId = 0;
        } else {
            // Decode data frame common header
            version = spdyVersion; // Default to expected version

            type = SPDY_DATA_FRAME;

            streamId = getUnsignedInt(buffer, frameOffset);
        }
        // Check version first then validity
        if (version != spdyVersion || !isValidFrameHeader()) {
            return State.FRAME_ERROR;
        }

        // Make sure decoder will produce a frame or consume input
        State nextState;
        if (willGenerateFrame()) {
            switch (type) {
            case SPDY_DATA_FRAME:
                nextState = State.READ_DATA_FRAME;
                break;

            case SPDY_SYN_STREAM_FRAME:
            case SPDY_SYN_REPLY_FRAME:
            case SPDY_HEADERS_FRAME:
                nextState = State.READ_HEADER_BLOCK_FRAME;
                break;

            case SPDY_SETTINGS_FRAME:
                nextState = State.READ_SETTINGS_FRAME;
                break;

            default:
                nextState = State.READ_CONTROL_FRAME;
            }
        } else if (length != 0) {
            nextState = State.DISCARD_FRAME;
        } else {
            nextState = State.READ_COMMON_HEADER;
        }
        return nextState;
    }

    private Object readControlFrame(ByteBuf buffer) {
        int streamId;
        int statusCode;
        switch (type) {
        case SPDY_RST_STREAM_FRAME:
            if (buffer.readableBytes() < 8) {
                return null;
            }

            streamId = getUnsignedInt(buffer, buffer.readerIndex());
            statusCode = getSignedInt(buffer, buffer.readerIndex() + 4);
            buffer.skipBytes(8);

            return new DefaultSpdyRstStreamFrame(streamId, statusCode);

        case SPDY_PING_FRAME:
            if (buffer.readableBytes() < 4) {
                return null;
            }

            int ID = getSignedInt(buffer, buffer.readerIndex());
            buffer.skipBytes(4);

            return new DefaultSpdyPingFrame(ID);

        case SPDY_GOAWAY_FRAME:
            if (buffer.readableBytes() < 8) {
                return null;
            }

            int lastGoodStreamId = getUnsignedInt(buffer, buffer.readerIndex());
            statusCode = getSignedInt(buffer, buffer.readerIndex() + 4);
            buffer.skipBytes(8);

            return new DefaultSpdyGoAwayFrame(lastGoodStreamId, statusCode);

        case SPDY_WINDOW_UPDATE_FRAME:
            if (buffer.readableBytes() < 8) {
                return null;
            }

            streamId = getUnsignedInt(buffer, buffer.readerIndex());
            int deltaWindowSize = getUnsignedInt(buffer, buffer.readerIndex() + 4);
            buffer.skipBytes(8);

            return new DefaultSpdyWindowUpdateFrame(streamId, deltaWindowSize);

        default:
            throw new Error("Shouldn't reach here.");
        }
    }

    private SpdyHeadersFrame readHeaderBlockFrame(ByteBuf buffer) {
        int streamId;
        switch (type) {
        case SPDY_SYN_STREAM_FRAME:
            if (buffer.readableBytes() < 10) {
                return null;
            }

            int offset = buffer.readerIndex();
            streamId = getUnsignedInt(buffer, offset);
            int associatedToStreamId = getUnsignedInt(buffer, offset + 4);
            byte priority = (byte) (buffer.getByte(offset + 8) >> 5 & 0x07);
            buffer.skipBytes(10);
            length -= 10;

            SpdySynStreamFrame spdySynStreamFrame =
                    new DefaultSpdySynStreamFrame(streamId, associatedToStreamId, priority);
            spdySynStreamFrame.setLast((flags & SPDY_FLAG_FIN) != 0);
            spdySynStreamFrame.setUnidirectional((flags & SPDY_FLAG_UNIDIRECTIONAL) != 0);

            return spdySynStreamFrame;

        case SPDY_SYN_REPLY_FRAME:
            if (buffer.readableBytes() < 4) {
                return null;
            }

            streamId = getUnsignedInt(buffer, buffer.readerIndex());
            buffer.skipBytes(4);
            length -= 4;

            SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
            spdySynReplyFrame.setLast((flags & SPDY_FLAG_FIN) != 0);

            return spdySynReplyFrame;

        case SPDY_HEADERS_FRAME:
            if (buffer.readableBytes() < 4) {
                return null;
            }

            streamId = getUnsignedInt(buffer, buffer.readerIndex());
            buffer.skipBytes(4);
            length -= 4;

            SpdyHeadersFrame spdyHeadersFrame = new DefaultSpdyHeadersFrame(streamId);
            spdyHeadersFrame.setLast((flags & SPDY_FLAG_FIN) != 0);

            return spdyHeadersFrame;

        default:
            throw new Error("Shouldn't reach here.");
        }
    }

    private boolean isValidFrameHeader() {
        switch (type) {
        case SPDY_DATA_FRAME:
            return streamId != 0;

        case SPDY_SYN_STREAM_FRAME:
            return length >= 10;

        case SPDY_SYN_REPLY_FRAME:
            return length >= 4;

        case SPDY_RST_STREAM_FRAME:
            return flags == 0 && length == 8;

        case SPDY_SETTINGS_FRAME:
            return length >= 4;

        case SPDY_PING_FRAME:
            return length == 4;

        case SPDY_GOAWAY_FRAME:
            return length == 8;

        case SPDY_HEADERS_FRAME:
            return length >= 4;

        case SPDY_WINDOW_UPDATE_FRAME:
            return length == 8;

        default:
            return true;
        }
    }

    private boolean willGenerateFrame() {
        switch (type) {
        case SPDY_DATA_FRAME:
        case SPDY_SYN_STREAM_FRAME:
        case SPDY_SYN_REPLY_FRAME:
        case SPDY_RST_STREAM_FRAME:
        case SPDY_SETTINGS_FRAME:
        case SPDY_PING_FRAME:
        case SPDY_GOAWAY_FRAME:
        case SPDY_HEADERS_FRAME:
        case SPDY_WINDOW_UPDATE_FRAME:
            return true;

        default:
            return false;
        }
    }

    private static void fireInvalidFrameException(ChannelHandlerContext ctx) {
        ctx.fireExceptionCaught(INVALID_FRAME);
    }

    private static void fireProtocolException(ChannelHandlerContext ctx, String message) {
        ctx.fireExceptionCaught(new SpdyProtocolException(message));
    }
}
