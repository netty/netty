/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.util.CharsetUtil.*;

/**
 * A {@link Http2FrameWriter} that supports all frame types defined by the HTTP/2 specification.
 */
public class DefaultHttp2FrameWriter implements Http2FrameWriter {

    private final Http2HeadersEncoder headersEncoder;

    public DefaultHttp2FrameWriter() {
        this(new DefaultHttp2HeadersEncoder());
    }

    public DefaultHttp2FrameWriter(Http2HeadersEncoder headersEncoder) {
        this.headersEncoder = headersEncoder;
    }

    @Override
    public void maxHeaderTableSize(int max) throws Http2Exception {
        headersEncoder.maxHeaderTableSize(max);
    }

    @Override
    public int maxHeaderTableSize() {
        return headersEncoder.maxHeaderTableSize();
    }

    @Override
    public void close() {
        // Nothing to do.
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream, boolean endSegment, boolean compressed) {
        try {
            verifyStreamId(streamId, "Stream ID");
            verifyPadding(padding);

            Http2Flags flags =
                    Http2Flags.newBuilder().setPaddingFlags(padding).endOfStream(endStream)
                            .endOfSegment(endSegment).compressed(compressed).build();

            int payloadLength = data.readableBytes() + padding + flags.getNumPaddingLengthBytes();
            verifyPayloadLength(payloadLength);

            ByteBuf out = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);

            writeFrameHeader(out, payloadLength, Http2FrameType.DATA, flags, streamId);

            writePaddingLength(padding, out);

            // Write the data.
            out.writeBytes(data, data.readerIndex(), data.readableBytes());

            // Write the required padding.
            out.writeZero(padding);
            return ctx.writeAndFlush(out, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        } finally {
            data.release();
        }
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream, boolean endSegment) {
        return writeHeadersInternal(ctx, promise, streamId, headers, padding, endStream,
                endSegment, false, 0, (short) 0, false);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int streamDependency, short weight,
            boolean exclusive, int padding, boolean endStream, boolean endSegment) {
        return writeHeadersInternal(ctx, promise, streamId, headers, padding, endStream,
                endSegment, true, streamDependency, weight, exclusive);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int streamDependency, short weight, boolean exclusive) {
        try {
            verifyStreamId(streamId, "Stream ID");
            verifyStreamId(streamDependency, "Stream Dependency");
            verifyWeight(weight);

            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + PRIORITY_ENTRY_LENGTH);
            writeFrameHeader(frame, PRIORITY_ENTRY_LENGTH, Http2FrameType.PRIORITY,
                    Http2Flags.EMPTY, streamId);
            long word1 = exclusive ? 0x80000000L | streamDependency : streamDependency;
            writeUnsignedInt(word1, frame);

            // Adjust the weight so that it fits into a single byte on the wire.
            frame.writeByte(weight - 1);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long errorCode) {
        try {
            verifyStreamId(streamId, "Stream ID");
            verifyErrorCode(errorCode);

            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + INT_FIELD_LENGTH);
            writeFrameHeader(frame, INT_FIELD_LENGTH, Http2FrameType.RST_STREAM, Http2Flags.EMPTY,
                    streamId);
            writeUnsignedInt(errorCode, frame);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings) {
        try {
            int payloadLength = calcSettingsPayloadLength(settings);
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(frame, payloadLength, Http2FrameType.SETTINGS, Http2Flags.EMPTY, 0);
            writeSettingsPayload(settings, frame);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        try {
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            writeFrameHeader(frame, 0, Http2FrameType.SETTINGS, Http2Flags.ACK_ONLY, 0);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise, boolean ack,
            ByteBuf data) {
        try {
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + data.readableBytes());
            Http2Flags flags = ack ? Http2Flags.ACK_ONLY : Http2Flags.EMPTY;
            writeFrameHeader(frame, data.readableBytes(), Http2FrameType.PING, flags, 0);

            // Write the debug data.
            frame.writeBytes(data, data.readerIndex(), data.readableBytes());
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        } finally {
            data.release();
        }
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int promisedStreamId, Http2Headers headers, int padding) {
        ByteBuf headerBlock = null;
        try {
            verifyStreamId(streamId, "Stream ID");
            verifyStreamId(promisedStreamId, "Promised Stream ID");
            verifyPadding(padding);

            // Encode the entire header block into an intermediate buffer.
            headerBlock = ctx.alloc().buffer();
            headersEncoder.encodeHeaders(headers, headerBlock);

            // Read the first fragment (possibly everything).
            Http2Flags.Builder flags = Http2Flags.newBuilder().setPaddingFlags(padding);
            int promisedStreamIdLength = INT_FIELD_LENGTH;
            int maxFragmentLength =
                    MAX_FRAME_PAYLOAD_LENGTH
                            - (promisedStreamIdLength + padding + flags.getNumPaddingLengthBytes());
            ByteBuf fragment =
                    headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

            flags = flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength =
                    fragment.readableBytes() + promisedStreamIdLength + padding
                            + flags.getNumPaddingLengthBytes();
            ByteBuf firstFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(firstFrame, payloadLength, Http2FrameType.PUSH_PROMISE, flags.build(),
                    streamId);

            writePaddingLength(padding, firstFrame);

            // Write out the promised stream ID.
            firstFrame.writeInt(promisedStreamId);

            // Write the first fragment.
            firstFrame.writeBytes(fragment);

            // Write out the padding, if any.
            firstFrame.writeZero(padding);

            if (headerBlock.readableBytes() == 0) {
                return ctx.writeAndFlush(firstFrame, promise);
            }

            // Create a composite buffer wrapping the first frame and any continuation frames.
            return continueHeaders(ctx, promise, streamId, padding, headerBlock, firstFrame);
        } catch (Exception e) {
            return promise.setFailure(e);
        } finally {
            if (headerBlock != null) {
                headerBlock.release();
            }
        }
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, ChannelPromise promise,
            int lastStreamId, long errorCode, ByteBuf debugData) {
        try {
            verifyStreamOrConnectionId(lastStreamId, "Last Stream ID");
            verifyErrorCode(errorCode);

            int payloadLength = 8 + debugData.readableBytes();
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(frame, payloadLength, Http2FrameType.GO_AWAY, Http2Flags.EMPTY, 0);
            frame.writeInt(lastStreamId);
            writeUnsignedInt(errorCode, frame);
            frame.writeBytes(debugData, debugData.readerIndex(), debugData.readableBytes());
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        } finally {
            debugData.release();
        }
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int windowSizeIncrement) {
        try {
            verifyStreamOrConnectionId(streamId, "Stream ID");
            verifyWindowSizeIncrement(windowSizeIncrement);

            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + INT_FIELD_LENGTH);
            writeFrameHeader(frame, INT_FIELD_LENGTH, Http2FrameType.WINDOW_UPDATE,
                    Http2Flags.EMPTY, streamId);
            frame.writeInt(windowSizeIncrement);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeAltSvc(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long maxAge, int port, ByteBuf protocolId, String host, String origin) {
        try {
            verifyStreamOrConnectionId(streamId, "Stream ID");
            verifyMaxAge(maxAge);
            verifyPort(port);

            // 9 bytes is the total of all fields except for the protocol ID and host.
            // Breakdown: Max-Age(4) + Port(2) + reserved(1) + Proto-Len(1) + Host-Len(1) = 9
            int payloadLength = 9 + protocolId.readableBytes() + host.length();
            if (origin != null) {
                payloadLength += origin.length();
            }

            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(frame, payloadLength, Http2FrameType.ALT_SVC, Http2Flags.EMPTY,
                    streamId);
            writeUnsignedInt(maxAge, frame);
            writeUnsignedShort(port, frame);
            frame.writeZero(1);
            frame.writeByte(protocolId.readableBytes());
            frame.writeBytes(protocolId);
            frame.writeByte(host.length());
            frame.writeBytes(host.getBytes(UTF_8));
            if (origin != null) {
                frame.writeBytes(origin.getBytes(UTF_8));
            }
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        } finally {
            protocolId.release();
        }
    }

    @Override
    public ChannelFuture writeBlocked(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId) {
        try {
            verifyStreamOrConnectionId(streamId, "Stream ID");

            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            writeFrameHeader(frame, 0, Http2FrameType.BLOCKED, Http2Flags.EMPTY, streamId);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    private ChannelFuture writeHeadersInternal(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream, boolean endSegment,
            boolean hasPriority, int streamDependency, short weight, boolean exclusive) {
        ByteBuf headerBlock = null;
        try {
            verifyStreamId(streamId, "Stream ID");
            if (hasPriority) {
                verifyStreamOrConnectionId(streamDependency, "Stream Dependency");
                verifyPadding(padding);
                verifyWeight(weight);
            }

            // Encode the entire header block.
            headerBlock = ctx.alloc().buffer();
            headersEncoder.encodeHeaders(headers, headerBlock);

            Http2Flags.Builder flags =
                    Http2Flags.newBuilder().endOfStream(endStream).endOfSegment(endSegment)
                            .priorityPresent(hasPriority).setPaddingFlags(padding);

            // Read the first fragment (possibly everything).
            int nonFragmentBytes =
                    padding + flags.getNumPriorityBytes() + flags.getNumPaddingLengthBytes();
            int maxFragmentLength = MAX_FRAME_PAYLOAD_LENGTH - nonFragmentBytes;
            ByteBuf fragment =
                    headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

            // Set the end of headers flag for the first frame.
            flags = flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            ByteBuf firstFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(firstFrame, payloadLength, Http2FrameType.HEADERS, flags.build(),
                    streamId);

            // Write the padding length.
            writePaddingLength(padding, firstFrame);

            // Write the priority.
            if (hasPriority) {
                long word1 = exclusive ? 0x80000000L | streamDependency : streamDependency;
                writeUnsignedInt(word1, firstFrame);

                // Adjust the weight so that it fits into a single byte on the wire.
                firstFrame.writeByte(weight - 1);
            }

            // Write the first fragment.
            firstFrame.writeBytes(fragment);

            // Write out the padding, if any.
            firstFrame.writeZero(padding);

            if (flags.endOfHeaders()) {
                return ctx.writeAndFlush(firstFrame, promise);
            }

            // Create a composite buffer wrapping the first frame and any continuation frames.
            return continueHeaders(ctx, promise, streamId, padding, headerBlock, firstFrame);
        } catch (Exception e) {
            return promise.setFailure(e);
        } finally {
            if (headerBlock != null) {
                headerBlock.release();
            }
        }
    }

    /**
     * Drains the header block and creates a composite buffer containing the first frame and a
     * number of CONTINUATION frames.
     */
    private static ChannelFuture continueHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int padding, ByteBuf headerBlock, ByteBuf firstFrame) {
        // Create a composite buffer wrapping the first frame and any continuation frames.
        CompositeByteBuf out = ctx.alloc().compositeBuffer();
        out.addComponent(firstFrame);
        int numBytes = firstFrame.readableBytes();

        // Process any continuation frames there might be.
        while (headerBlock.isReadable()) {
            ByteBuf frame = createContinuationFrame(ctx, streamId, headerBlock, padding);
            out.addComponent(frame);
            numBytes += frame.readableBytes();
        }

        out.writerIndex(numBytes);
        return ctx.writeAndFlush(out, promise);
    }

    /**
     * Allocates a new buffer and writes a single continuation frame with a fragment of the header
     * block to the output buffer.
     */
    private static ByteBuf createContinuationFrame(ChannelHandlerContext ctx, int streamId,
            ByteBuf headerBlock, int padding) {
        Http2Flags.Builder flags = Http2Flags.newBuilder().setPaddingFlags(padding);
        int maxFragmentLength =
                MAX_FRAME_PAYLOAD_LENGTH - (padding + flags.getNumPaddingLengthBytes());
        ByteBuf fragment =
                headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

        int payloadLength = fragment.readableBytes() + padding + flags.getNumPaddingLengthBytes();
        ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
        flags = flags.endOfHeaders(headerBlock.readableBytes() == 0);

        writeFrameHeader(frame, payloadLength, Http2FrameType.CONTINUATION, flags.build(), streamId);

        writePaddingLength(padding, frame);

        frame.writeBytes(fragment);

        // Write out the padding, if any.
        frame.writeZero(padding);
        return frame;
    }

    /**
     * Writes the padding length field to the output buffer.
     */
    private static void writePaddingLength(int paddingLength, ByteBuf out) {
        if (paddingLength > MAX_UNSIGNED_BYTE) {
            int padHigh = paddingLength / 256;
            out.writeByte(padHigh);
        }
        // Always include PadLow if there is any padding at all.
        if (paddingLength > 0) {
            int padLow = paddingLength % 256;
            out.writeByte(padLow);
        }
    }

    private static void verifyStreamId(int streamId, String argumentName) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(argumentName + " must be > 0");
        }
    }

    private static void verifyStreamOrConnectionId(int streamId, String argumentName) {
        if (streamId < 0) {
            throw new IllegalArgumentException(argumentName + " must be >= 0");
        }
    }

    private static void verifyPadding(int padding) {
        if (padding < 0 || padding > MAX_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("Invalid padding value: " + padding);
        }
    }

    private static void verifyPayloadLength(int payloadLength) {
        if (payloadLength > MAX_FRAME_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Total payload length " + payloadLength
                    + " exceeds max frame length.");
        }
    }

    private static void verifyWeight(short weight) {
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("Invalid weight: " + weight);
        }
    }

    private static void verifyErrorCode(long errorCode) {
        if (errorCode < 0 || errorCode > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("Invalid errorCode: " + errorCode);
        }
    }

    private static void verifyWindowSizeIncrement(int windowSizeIncrement) {
        if (windowSizeIncrement < 0) {
            throw new IllegalArgumentException("WindowSizeIncrement must be >= 0");
        }
    }

    private static void verifyMaxAge(long maxAge) {
        if (maxAge < 0 || maxAge > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("Invalid Max Age: " + maxAge);
        }
    }

    private static void verifyPort(int port) {
        if (port < 0 || port > MAX_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }
}
