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

import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.INT_FIELD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_BYTE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.PRIORITY_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeFrameHeader;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeUnsignedInt;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeUnsignedShort;
import static io.netty.handler.codec.http2.Http2FrameTypes.CONTINUATION;
import static io.netty.handler.codec.http2.Http2FrameTypes.DATA;
import static io.netty.handler.codec.http2.Http2FrameTypes.GO_AWAY;
import static io.netty.handler.codec.http2.Http2FrameTypes.HEADERS;
import static io.netty.handler.codec.http2.Http2FrameTypes.PING;
import static io.netty.handler.codec.http2.Http2FrameTypes.PRIORITY;
import static io.netty.handler.codec.http2.Http2FrameTypes.PUSH_PROMISE;
import static io.netty.handler.codec.http2.Http2FrameTypes.RST_STREAM;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static io.netty.handler.codec.http2.Http2FrameTypes.WINDOW_UPDATE;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.collection.IntObjectMap;

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
    public void maxHeaderTableSize(long max) throws Http2Exception {
        headersEncoder.maxHeaderTableSize((int) Math.min(max, Integer.MAX_VALUE));
    }

    @Override
    public long maxHeaderTableSize() {
        return headersEncoder.maxHeaderTableSize();
    }

    @Override
    public void close() {
        // Nothing to do.
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream, boolean endSegment) {
        try {
            verifyStreamId(streamId, "Stream ID");
            verifyPadding(padding);

            Http2Flags flags =
                    new Http2Flags().paddingPresent(padding > 0).endOfStream(endStream)
                            .endOfSegment(endSegment);

            int payloadLength = data.readableBytes() + padding + flags.getPaddingPresenceFieldLength();
            verifyPayloadLength(payloadLength);

            ByteBuf out = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);

            writeFrameHeader(out, payloadLength, DATA, flags, streamId);

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
            writeFrameHeader(frame, PRIORITY_ENTRY_LENGTH, PRIORITY,
                    new Http2Flags(), streamId);
            long word1 = exclusive ? (0x80000000L | streamDependency) : streamDependency;
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
            writeFrameHeader(frame, INT_FIELD_LENGTH, RST_STREAM, new Http2Flags(),
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
            if (settings == null) {
                throw new NullPointerException("settings");
            }
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(frame, payloadLength, SETTINGS, new Http2Flags(), 0);
            for (IntObjectMap.Entry<Long> entry : settings.entries()) {
                writeUnsignedShort(entry.key(), frame);
                writeUnsignedInt(entry.value(), frame);
            }
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        try {
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            writeFrameHeader(frame, 0, SETTINGS, new Http2Flags().ack(true), 0);
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
            Http2Flags flags = ack ? new Http2Flags().ack(true) : new Http2Flags();
            writeFrameHeader(frame, data.readableBytes(), PING, flags, 0);

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
            Http2Flags flags = new Http2Flags().paddingPresent(padding > 0);
            int promisedStreamIdLength = INT_FIELD_LENGTH;
            int nonFragmentLength = promisedStreamIdLength + padding + flags.getPaddingPresenceFieldLength();
            int maxFragmentLength = MAX_FRAME_PAYLOAD_LENGTH - nonFragmentLength;
            ByteBuf fragment =
                    headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

            flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength = fragment.readableBytes() + nonFragmentLength;
            ByteBuf firstFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(firstFrame, payloadLength, PUSH_PROMISE, flags,
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
            writeFrameHeader(frame, payloadLength, GO_AWAY, new Http2Flags(), 0);
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
            writeFrameHeader(frame, INT_FIELD_LENGTH, WINDOW_UPDATE,
                    new Http2Flags(), streamId);
            frame.writeInt(windowSizeIncrement);
            return ctx.writeAndFlush(frame, promise);
        } catch (RuntimeException e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, ChannelPromise promise,
            byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
        try {
            verifyStreamOrConnectionId(streamId, "Stream ID");
            ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payload.readableBytes());
            writeFrameHeader(frame, payload.readableBytes(), frameType, flags, streamId);
            frame.writeBytes(payload);
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

            Http2Flags flags =
                    new Http2Flags().endOfStream(endStream).endOfSegment(endSegment)
                            .priorityPresent(hasPriority).paddingPresent(padding > 0);

            // Read the first fragment (possibly everything).
            int nonFragmentBytes =
                    padding + flags.getNumPriorityBytes() + flags.getPaddingPresenceFieldLength();
            int maxFragmentLength = MAX_FRAME_PAYLOAD_LENGTH - nonFragmentBytes;
            ByteBuf fragment =
                    headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

            // Set the end of headers flag for the first frame.
            flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            ByteBuf firstFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeader(firstFrame, payloadLength, HEADERS, flags,
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
        Http2Flags flags = new Http2Flags().paddingPresent(padding > 0);
        int nonFragmentLength = padding + flags.getPaddingPresenceFieldLength();
        int maxFragmentLength = MAX_FRAME_PAYLOAD_LENGTH - nonFragmentLength;
        ByteBuf fragment =
                headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

        int payloadLength = fragment.readableBytes() + nonFragmentLength;
        ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
        flags = flags.endOfHeaders(headerBlock.readableBytes() == 0);

        writeFrameHeader(frame, payloadLength, CONTINUATION, flags, streamId);

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
        if (padding < 0 || padding > MAX_UNSIGNED_BYTE) {
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
}
