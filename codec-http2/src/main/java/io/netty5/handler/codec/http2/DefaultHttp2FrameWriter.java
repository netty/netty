/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;
import io.netty5.handler.codec.http2.Http2FrameWriter.Configuration;
import io.netty5.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.UnstableApi;

import static io.netty5.handler.codec.http2.Http2CodecUtil.CONTINUATION_FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DATA_FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.GO_AWAY_FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.HEADERS_FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.INT_FIELD_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_BYTE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PRIORITY_ENTRY_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PRIORITY_FRAME_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PUSH_PROMISE_FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.RST_STREAM_FRAME_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.WINDOW_UPDATE_FRAME_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.isMaxFrameSizeValid;
import static io.netty5.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty5.handler.codec.http2.Http2CodecUtil.writeFrameHeaderInternal;
import static io.netty5.handler.codec.http2.Http2Error.FRAME_SIZE_ERROR;
import static io.netty5.handler.codec.http2.Http2Exception.connectionError;
import static io.netty5.handler.codec.http2.Http2FrameTypes.CONTINUATION;
import static io.netty5.handler.codec.http2.Http2FrameTypes.DATA;
import static io.netty5.handler.codec.http2.Http2FrameTypes.GO_AWAY;
import static io.netty5.handler.codec.http2.Http2FrameTypes.HEADERS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.PING;
import static io.netty5.handler.codec.http2.Http2FrameTypes.PRIORITY;
import static io.netty5.handler.codec.http2.Http2FrameTypes.PUSH_PROMISE;
import static io.netty5.handler.codec.http2.Http2FrameTypes.RST_STREAM;
import static io.netty5.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.WINDOW_UPDATE;
import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Http2FrameWriter} that supports all frame types defined by the HTTP/2 specification.
 */
@UnstableApi
public class DefaultHttp2FrameWriter implements Http2FrameWriter, Http2FrameSizePolicy, Configuration {
    private static final String STREAM_ID = "Stream ID";
    private static final String STREAM_DEPENDENCY = "Stream Dependency";
    /**
     * This buffer is allocated to the maximum size of the padding field, and filled with zeros.
     * When padding is needed it can be taken as a slice of this buffer.
     * Users should call {@link Buffer#copy(int, int, boolean)} before using their slice.
     */
    private static final Buffer ZERO_BUFFER =
            BufferAllocator.onHeapUnpooled().allocate(MAX_UNSIGNED_BYTE)
                           .fill((byte) 0).writerOffset(MAX_UNSIGNED_BYTE).makeReadOnly();

    private final Http2HeadersEncoder headersEncoder;
    private int maxFrameSize;

    public DefaultHttp2FrameWriter() {
        this(new DefaultHttp2HeadersEncoder());
    }

    public DefaultHttp2FrameWriter(SensitivityDetector headersSensitivityDetector) {
        this(new DefaultHttp2HeadersEncoder(headersSensitivityDetector));
    }

    public DefaultHttp2FrameWriter(SensitivityDetector headersSensitivityDetector, boolean ignoreMaxHeaderListSize) {
        this(new DefaultHttp2HeadersEncoder(headersSensitivityDetector, ignoreMaxHeaderListSize));
    }

    public DefaultHttp2FrameWriter(Http2HeadersEncoder headersEncoder) {
        this.headersEncoder = headersEncoder;
        maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    @Override
    public Http2HeadersEncoder.Configuration headersConfiguration() {
        return headersEncoder.configuration();
    }

    @Override
    public Http2FrameSizePolicy frameSizePolicy() {
        return this;
    }

    @Override
    public void maxFrameSize(int max) throws Http2Exception {
        if (!isMaxFrameSizeValid(max)) {
            throw connectionError(FRAME_SIZE_ERROR, "Invalid MAX_FRAME_SIZE specified in sent settings: %d", max);
        }
        maxFrameSize = max;
    }

    @Override
    public int maxFrameSize() {
        return maxFrameSize;
    }

    @Override
    public void close() {
        headersEncoder.close();
    }

    @Override
    public Future<Void> writeData(ChannelHandlerContext ctx, int streamId, Buffer data,
                                  int padding, boolean endStream) {
        final SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        Buffer frameHeader = null;
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyPadding(padding);

            int remainingData = data.readableBytes();
            Http2Flags flags = new Http2Flags();
            flags.endOfStream(false);
            flags.paddingPresent(false);
            // Fast path to write frames of payload size maxFrameSize first.
            if (remainingData > maxFrameSize) {
                frameHeader = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH);
                writeFrameHeaderInternal(frameHeader, maxFrameSize, DATA, flags, streamId);
                frameHeader.makeReadOnly();
                do {
                    // Write the header.
                    ctx.write(frameHeader.copy(true)).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    ctx.write(data.readSplit(maxFrameSize)).cascadeTo(promiseAggregator.newPromise());

                    remainingData -= maxFrameSize;
                    // Stop iterating if remainingData == maxFrameSize so we can take care of reference counts below.
                } while (remainingData > maxFrameSize);
            }

            if (padding == 0) {
                // Write the header.
                if (frameHeader != null) {
                    frameHeader.close();
                    frameHeader = null;
                }
                Buffer frameHeader2 = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH);
                flags.endOfStream(endStream);
                writeFrameHeaderInternal(frameHeader2, remainingData, DATA, flags, streamId);
                ctx.write(frameHeader2).cascadeTo(promiseAggregator.newPromise());

                // Write the payload.
                Buffer lastFrame = data.readSplit(remainingData);
                data.close();
                data = null;
                ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
            } else {
                if (remainingData != maxFrameSize) {
                    if (frameHeader != null) {
                        frameHeader.close();
                        frameHeader = null;
                    }
                } else {
                    remainingData -= maxFrameSize;
                    // Write the header.
                    Buffer lastFrame;
                    if (frameHeader == null) {
                        lastFrame = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH);
                        writeFrameHeaderInternal(lastFrame, maxFrameSize, DATA, flags, streamId);
                    } else {
                        lastFrame = frameHeader;
                        frameHeader = null;
                    }
                    ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    if (data.readableBytes() != maxFrameSize) {
                        lastFrame = data.readSplit(maxFrameSize);
                        data.close();
                    } else {
                        lastFrame = data;
                    }
                    data = null;
                    ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
                }

                do {
                    int frameDataBytes = min(remainingData, maxFrameSize);
                    int framePaddingBytes = min(padding, max(0, maxFrameSize - 1 - frameDataBytes));

                    // Decrement the remaining counters.
                    padding -= framePaddingBytes;
                    remainingData -= frameDataBytes;

                    // Write the header.
                    Buffer frameHeader2 = ctx.bufferAllocator().allocate(DATA_FRAME_HEADER_LENGTH);
                    flags.endOfStream(endStream && remainingData == 0 && padding == 0);
                    flags.paddingPresent(framePaddingBytes > 0);
                    writeFrameHeaderInternal(frameHeader2, framePaddingBytes + frameDataBytes, DATA, flags, streamId);
                    writePaddingLength(frameHeader2, framePaddingBytes);
                    ctx.write(frameHeader2).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    if (data != null) { // Make sure Data is not null
                        if (remainingData == 0) {
                            Buffer lastFrame = data.readSplit(frameDataBytes);
                            data.close();
                            data = null;
                            ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
                        } else {
                            ctx.write(data.readSplit(frameDataBytes))
                                    .cascadeTo(promiseAggregator.newPromise());
                        }
                    }
                    // Write the frame padding.
                    if (paddingBytes(framePaddingBytes) > 0) {
                        ctx.write(ZERO_BUFFER.copy(0, paddingBytes(framePaddingBytes), true))
                                .cascadeTo(promiseAggregator.newPromise());
                    }
                } while (remainingData != 0 || padding != 0);
            }
        } catch (Throwable cause) {
            if (frameHeader != null) {
                frameHeader.close();
            }
            // Use a try/finally here in case the data has been released before calling this method. This is not
            // necessary above because we internally allocate frameHeader.
            try {
                if (data != null && data.isAccessible()) {
                    data.close();
                }
            } finally {
                promiseAggregator.setFailure(cause);
                promiseAggregator.doneAllocatingPromises();
            }
            return promiseAggregator;
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    @Override
    public Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId,
                                     Http2Headers headers, int padding, boolean endStream) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                false, 0, (short) 0, false);
    }

    @Override
    public Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId,
                                     Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                                     int padding, boolean endStream) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                true, streamDependency, weight, exclusive);
    }

    @Override
    public Future<Void> writePriority(ChannelHandlerContext ctx, int streamId,
                                      int streamDependency, short weight, boolean exclusive) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
            verifyWeight(weight);

            Buffer buf = ctx.bufferAllocator().allocate(PRIORITY_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, PRIORITY_ENTRY_LENGTH, PRIORITY, new Http2Flags(), streamId);
            buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);
            // Adjust the weight so that it fits into a single byte on the wire.
            buf.writeByte((byte) (weight - 1));
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    public Future<Void> writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyErrorCode(errorCode);

            Buffer buf = ctx.bufferAllocator().allocate(RST_STREAM_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, RST_STREAM, new Http2Flags(), streamId);
            buf.writeInt((int) errorCode);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    public Future<Void> writeSettings(ChannelHandlerContext ctx, Http2Settings settings) {
        try {
            requireNonNull(settings, "settings");
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            Buffer buf = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeaderInternal(buf, payloadLength, SETTINGS, new Http2Flags(), 0);
            for (Http2Settings.PrimitiveEntry<Long> entry : settings.entries()) {
                buf.writeChar(entry.key());
                buf.writeInt(entry.value().intValue());
            }
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    public Future<Void> writeSettingsAck(ChannelHandlerContext ctx) {
        try {
            Buffer buf = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, 0, SETTINGS, new Http2Flags().ack(true), 0);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    public Future<Void> writePing(ChannelHandlerContext ctx, boolean ack, long data) {
        Http2Flags flags = ack ? new Http2Flags().ack(true) : new Http2Flags();
        Buffer buf = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH + PING_FRAME_PAYLOAD_LENGTH);
        // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
        // in the catch block.
        writeFrameHeaderInternal(buf, PING_FRAME_PAYLOAD_LENGTH, PING, flags, 0);
        buf.writeLong(data);
        return ctx.write(buf);
    }

    @Override
    public Future<Void> writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                         Http2Headers headers, int padding) {
        Buffer headerBlock = null;
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamId(promisedStreamId, "Promised Stream ID");
            verifyPadding(padding);

            // Encode the entire header block into an intermediate buffer.
            headerBlock = ctx.bufferAllocator().allocate(256);
            headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            // Read the first fragment (possibly everything).
            Http2Flags flags = new Http2Flags().paddingPresent(padding > 0);
            // INT_FIELD_LENGTH is for the length of the promisedStreamId
            int nonFragmentLength = INT_FIELD_LENGTH + padding;
            int maxFragmentLength = maxFrameSize - nonFragmentLength;
            Buffer fragment = headerBlock.readSplit(min(headerBlock.readableBytes(), maxFragmentLength));

            flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength = fragment.readableBytes() + nonFragmentLength;
            Buffer buf = ctx.bufferAllocator().allocate(PUSH_PROMISE_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, PUSH_PROMISE, flags, streamId);
            writePaddingLength(buf, padding);

            // Write out the promised stream ID.
            buf.writeInt(promisedStreamId);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.copy(0, paddingBytes(padding), true))
                   .cascadeTo(promiseAggregator.newPromise());
            }

            if (!flags.endOfHeaders()) {
                writeContinuationFrames(ctx, streamId, headerBlock, promiseAggregator);
            }
        } catch (Http2Exception e) {
            promiseAggregator.setFailure(e);
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
            promiseAggregator.doneAllocatingPromises();
            throw t;
        } finally {
            if (headerBlock != null) {
                headerBlock.close();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    @Override
    public Future<Void> writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                                    Buffer debugData) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamOrConnectionId(lastStreamId, "Last Stream ID");
            verifyErrorCode(errorCode);

            int payloadLength = 8 + debugData.readableBytes();
            Buffer buf = ctx.bufferAllocator().allocate(GO_AWAY_FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payloadLength, GO_AWAY, new Http2Flags(), 0);
            buf.writeInt(lastStreamId);
            buf.writeInt((int) errorCode);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                debugData.close();
            } finally {
                promiseAggregator.setFailure(t);
                promiseAggregator.doneAllocatingPromises();
            }
            return promiseAggregator;
        }

        try {
            ctx.write(debugData).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    @Override
    public Future<Void> writeWindowUpdate(ChannelHandlerContext ctx, int streamId,
                                          int windowSizeIncrement) {
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            verifyWindowSizeIncrement(windowSizeIncrement);

            Buffer buf = ctx.bufferAllocator().allocate(WINDOW_UPDATE_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, WINDOW_UPDATE, new Http2Flags(), streamId);
            buf.writeInt(windowSizeIncrement);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    public Future<Void> writeFrame(ChannelHandlerContext ctx, short frameType, int streamId,
                                   Http2Flags flags, Buffer payload) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            Buffer buf = ctx.bufferAllocator().allocate(FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payload.readableBytes(), frameType, flags, streamId);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                payload.close();
            } finally {
                promiseAggregator.setFailure(t);
                promiseAggregator.doneAllocatingPromises();
            }
            return promiseAggregator;
        }
        try {
            ctx.write(payload).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    private Future<Void> writeHeadersInternal(ChannelHandlerContext ctx,
            int streamId, Http2Headers headers, int padding, boolean endStream,
            boolean hasPriority, int streamDependency, short weight, boolean exclusive) {
        Buffer headerBlock = null;
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamId(streamId, STREAM_ID);
            if (hasPriority) {
                verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
                verifyPadding(padding);
                verifyWeight(weight);
            }

            // Encode the entire header block.
            headerBlock = ctx.bufferAllocator().allocate(256);
            headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            Http2Flags flags =
                    new Http2Flags().endOfStream(endStream).priorityPresent(hasPriority).paddingPresent(padding > 0);

            // Read the first fragment (possibly everything).
            int nonFragmentBytes = padding + flags.getNumPriorityBytes();
            int maxFragmentLength = maxFrameSize - nonFragmentBytes;
            Buffer fragment = headerBlock.readSplit(min(headerBlock.readableBytes(), maxFragmentLength));

            // Set the end of headers flag for the first frame.
            flags.endOfHeaders(headerBlock.readableBytes() == 0);

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            Buffer buf = ctx.bufferAllocator().allocate(HEADERS_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, HEADERS, flags, streamId);
            writePaddingLength(buf, padding);

            if (hasPriority) {
                buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);

                // Adjust the weight so that it fits into a single byte on the wire.
                buf.writeByte((byte) (weight - 1));
            }
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.copy(0, paddingBytes(padding), true))
                        .cascadeTo(promiseAggregator.newPromise());
            }

            if (!flags.endOfHeaders()) {
                writeContinuationFrames(ctx, streamId, headerBlock, promiseAggregator);
            }
        } catch (Http2Exception e) {
            promiseAggregator.setFailure(e);
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
            promiseAggregator.doneAllocatingPromises();
            throw t;
        } finally {
            if (headerBlock != null) {
                headerBlock.close();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    /**
     * Writes as many continuation frames as needed until {@code padding} and {@code headerBlock} are consumed.
     */
    private Future<Void> writeContinuationFrames(ChannelHandlerContext ctx, int streamId,
            Buffer headerBlock, SimpleChannelPromiseAggregator promiseAggregator) {
        Http2Flags flags = new Http2Flags();

        if (headerBlock.readableBytes() > 0) {
            // The frame header (and padding) only changes on the last frame, so allocate it once and re-use
            int fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
            Buffer buf = ctx.bufferAllocator().allocate(CONTINUATION_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);
            buf.makeReadOnly();

            do {
                fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
                Buffer fragment = headerBlock.readSplit(fragmentReadableBytes);

                if (headerBlock.readableBytes() > 0) {
                    ctx.write(buf.copy(true)).cascadeTo(promiseAggregator.newPromise());
                } else {
                    // The frame header is different for the last frame, so re-allocate and release the old buffer
                    flags = flags.endOfHeaders(true);
                    buf.close();
                    buf = ctx.bufferAllocator().allocate(CONTINUATION_FRAME_HEADER_LENGTH);
                    writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);
                    ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
                }

                ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());
            } while (headerBlock.readableBytes() > 0);
        }
        return promiseAggregator;
    }

    /**
     * Returns the number of padding bytes that should be appended to the end of a frame.
     */
    private static int paddingBytes(int padding) {
        // The padding parameter contains the 1 byte pad length field as well as the trailing padding bytes.
        // Subtract 1, so to only get the number of padding bytes that need to be appended to the end of a frame.
        return padding - 1;
    }

    private static void writePaddingLength(Buffer buf, int padding) {
        if (padding > 0) {
            // It is assumed that the padding length has been bounds checked before this
            // Minus 1, as the pad length field is included in the padding parameter and is 1 byte wide.
            buf.writeByte((byte) (padding - 1));
        }
    }

    private static void verifyStreamId(int streamId, String argumentName) {
        checkPositive(streamId, argumentName);
    }

    private static void verifyStreamOrConnectionId(int streamId, String argumentName) {
        checkPositiveOrZero(streamId, argumentName);
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
        checkPositiveOrZero(windowSizeIncrement, "windowSizeIncrement");
    }
}
