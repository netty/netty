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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;
import io.netty.handler.codec.http2.Http2FrameWriter.Configuration;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.CONTINUATION_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.DATA_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.GO_AWAY_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.HEADERS_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.INT_FIELD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_BYTE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PRIORITY_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PRIORITY_FRAME_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PUSH_PROMISE_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.RST_STREAM_FRAME_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.WINDOW_UPDATE_FRAME_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.isMaxFrameSizeValid;
import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeFrameHeaderInternal;
import static io.netty.handler.codec.http2.Http2Error.FRAME_SIZE_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
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
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A {@link Http2FrameWriter} that supports all frame types defined by the HTTP/2 specification.
 */
@UnstableApi
public class DefaultHttp2FrameWriter implements Http2FrameWriter, Http2FrameSizePolicy, Configuration {

    // This is an internal const used to decide when it is worthy to create a retained slice of a buffer vs
    // just copying the bytes to the new properly sized buffer
    private static final int COPY_SLICE_CUTOFF_BYTES = 128;
    private static final String STREAM_ID = "Stream ID";
    private static final String STREAM_DEPENDENCY = "Stream Dependency";
    /**
     * This buffer is allocated to the maximum size of the padding field, and filled with zeros.
     * When padding is needed it can be taken as a slice of this buffer. Users should call {@link ByteBuf#retain()}
     * before using their slice.
     */
    private static final ByteBuf ZERO_BUFFER =
            unreleasableBuffer(directBuffer(MAX_UNSIGNED_BYTE).writeZero(MAX_UNSIGNED_BYTE)).asReadOnly();

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
    public void close() { }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
            int padding, boolean endStream, ChannelPromise promise) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyPadding(padding);
        } catch (Throwable t) {
            return handleFailure(data, null, promise, t, null);
        }
        if (padding == 0) {
            return writeDataNoPadding(ctx, streamId, data, endStream, promise);
        }
        return writeDataWithPadding(ctx, streamId, data, padding, endStream, promise);
    }

    private ChannelFuture writeDataWithPadding(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                               int padding, boolean endStream, ChannelPromise promise) {
        final SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        ByteBuf frameHeader = null;
        try {
            int remainingData = data.readableBytes();
            Http2Flags flags = new Http2Flags();
            flags.endOfStream(false);
            flags.paddingPresent(false);
            // Fast path to write frames of payload size maxFrameSize first.
            if (remainingData > maxFrameSize) {
                frameHeader = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                remainingData = writeDataFrames(ctx, streamId, data, frameHeader,
                                                flags, promiseAggregator, remainingData, maxFrameSize);
                if (remainingData != maxFrameSize) {
                    frameHeader.release();
                    frameHeader = null;
                }
            }
            if (remainingData == maxFrameSize) {
                remainingData = 0;
                // Write the header.
                ByteBuf lastFrame;
                if (frameHeader == null) {
                    lastFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                    writeFrameHeaderInternal(lastFrame, maxFrameSize, DATA, flags, streamId);
                } else {
                    lastFrame = frameHeader.slice();
                    frameHeader = null;
                }
                ctx.write(lastFrame, promiseAggregator.newPromise());

                // Write the payload.
                lastFrame = data.readableBytes() != maxFrameSize? data.readSlice(maxFrameSize) : data;
                data = null;
                ctx.write(lastFrame, promiseAggregator.newPromise());
            }

            do {
                int frameDataBytes = min(remainingData, maxFrameSize);
                int framePaddingBytes = min(padding, max(0, maxFrameSize - 1 - frameDataBytes));

                // Decrement the remaining counters.
                padding -= framePaddingBytes;
                remainingData -= frameDataBytes;

                // Write the header.
                ByteBuf frameHeader2 = ctx.alloc().buffer(DATA_FRAME_HEADER_LENGTH);
                flags.endOfStream(endStream && remainingData == 0 && padding == 0);
                flags.paddingPresent(framePaddingBytes > 0);
                writeFrameHeaderInternal(frameHeader2, framePaddingBytes + frameDataBytes, DATA, flags,
                                         streamId);
                writePaddingLength(frameHeader2, framePaddingBytes);
                ctx.write(frameHeader2, promiseAggregator.newPromise());

                // Write the payload.
                if (data != null) { // Make sure data is not null
                    if (remainingData == 0) {
                        ByteBuf lastFrame = data.readSlice(frameDataBytes);
                        data = null;
                        ctx.write(lastFrame, promiseAggregator.newPromise());
                    } else {
                        ctx.write(data.readRetainedSlice(frameDataBytes), promiseAggregator.newPromise());
                    }
                }
                // Write the frame padding.
                if (paddingBytes(framePaddingBytes) > 0) {
                    ctx.write(ZERO_BUFFER.slice(0, paddingBytes(framePaddingBytes)),
                              promiseAggregator.newPromise());
                }
            } while (remainingData != 0 || padding != 0);
            return promiseAggregator.doneAllocatingPromises();
        } catch (Throwable cause) {
            return handleFailure(data, frameHeader, promise, cause, promiseAggregator);
        }
    }

    private ChannelFuture writeDataNoPadding(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                             boolean endStream, ChannelPromise promise) {
        int dataLength = data.readableBytes();
        if (dataLength <= maxFrameSize && (data.hasArray() || dataLength <= COPY_SLICE_CUTOFF_BYTES)) {
            return writeCopySingleFrameDataNoPadding(ctx, streamId, data, endStream, promise);
        }
        return writeSlicedDataNoPadding(ctx, streamId, data, endStream, promise);
    }

    private ChannelFuture writeSlicedDataNoPadding(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                                   boolean endStream, ChannelPromise promise) {
        final SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        ByteBuf frameHeader = null;
        try {
            int remainingData = data.readableBytes();
            final Http2Flags flags = new Http2Flags();
            flags.endOfStream(false);
            flags.paddingPresent(false);
            // Fast path to write frames of payload size maxFrameSize first.
            if (remainingData > maxFrameSize) {
                frameHeader = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                remainingData = writeDataFrames(ctx, streamId, data, frameHeader,
                                                flags, promiseAggregator, remainingData, maxFrameSize);
                frameHeader.release();
                frameHeader = null;
            }
            // Write the header.
            final ByteBuf frameHeader2 = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            flags.endOfStream(endStream);
            writeFrameHeaderInternal(frameHeader2, remainingData, DATA, flags, streamId);
            ctx.write(frameHeader2, promiseAggregator.newPromise());

            // Write the payload.
            ByteBuf lastFrame = data.writerIndex(data.readerIndex() + remainingData);
            data = null;
            ctx.write(lastFrame, promiseAggregator.newPromise());
            return promiseAggregator.doneAllocatingPromises();
        } catch (Throwable cause) {
            return handleFailure(data, frameHeader, promise, cause, promiseAggregator);
        }
    }

    private static int writeDataFrames(ChannelHandlerContext ctx, int streamId, ByteBuf data, ByteBuf frameHeader,
                                       Http2Flags flags, SimpleChannelPromiseAggregator promiseAggregator,
                                       int remainingData, int maxFrameSize) {
        writeFrameHeaderInternal(frameHeader, maxFrameSize, DATA, flags, streamId);
        do {
            // Write the header.
            ctx.write(frameHeader.retainedSlice(), promiseAggregator.newPromise());

            // Write the payload.
            ctx.write(data.readRetainedSlice(maxFrameSize), promiseAggregator.newPromise());

            remainingData -= maxFrameSize;
            // Stop iterating if remainingData == maxFrameSize so we can take care of reference counts below.
        } while (remainingData > maxFrameSize);
        return remainingData;
    }

    private ChannelFuture writeCopySingleFrameDataNoPadding(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                                            boolean endStream, ChannelPromise promise) {
        try {
            final int remainingData = data.readableBytes();
            assert remainingData <= maxFrameSize && (data.hasArray() || remainingData <= COPY_SLICE_CUTOFF_BYTES);
            final ByteBuf frameHeader2AndLastFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + remainingData);
            try {
                final Http2Flags flags = new Http2Flags();
                flags.paddingPresent(false);
                flags.endOfStream(endStream);
                writeFrameHeaderInternal(frameHeader2AndLastFrame, remainingData, DATA, flags, streamId);
                // copy the last frame content
                frameHeader2AndLastFrame.writeBytes(data, remainingData);
                ByteBuf lastFrame = data;
                // here we're in charge to release the data buffer
                data = null;
                lastFrame.release();
            } catch (Throwable cause) {
                frameHeader2AndLastFrame.release();
                throw cause;
            }
            ctx.write(frameHeader2AndLastFrame, promise);
            return promise;
        } catch (Throwable cause) {
            return handleFailure(data, null, promise, cause, null);
        }
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                false, 0, (short) 0, false, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endStream, ChannelPromise promise) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                true, streamDependency, weight, exclusive, promise);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId,
            int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
            verifyWeight(weight);

            ByteBuf buf = ctx.alloc().buffer(PRIORITY_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, PRIORITY_ENTRY_LENGTH, PRIORITY, new Http2Flags(), streamId);
            buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);
            // Adjust the weight so that it fits into a single byte on the wire.
            buf.writeByte(weight - 1);
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyErrorCode(errorCode);

            ByteBuf buf = ctx.alloc().buffer(RST_STREAM_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, RST_STREAM, new Http2Flags(), streamId);
            buf.writeInt((int) errorCode);
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings,
            ChannelPromise promise) {
        try {
            checkNotNull(settings, "settings");
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeaderInternal(buf, payloadLength, SETTINGS, new Http2Flags(), 0);
            for (Http2Settings.PrimitiveEntry<Long> entry : settings.entries()) {
                buf.writeChar(entry.key());
                buf.writeInt(entry.value().intValue());
            }
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        try {
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, 0, SETTINGS, new Http2Flags().ack(true), 0);
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
        Http2Flags flags = ack ? new Http2Flags().ack(true) : new Http2Flags();
        ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + PING_FRAME_PAYLOAD_LENGTH);
        // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
        // in the catch block.
        writeFrameHeaderInternal(buf, PING_FRAME_PAYLOAD_LENGTH, PING, flags, 0);
        buf.writeLong(data);
        return ctx.write(buf, promise);
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId,
            int promisedStreamId, Http2Headers headers, int padding, ChannelPromise promise) {
        ByteBuf headerBlock = null;
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamId(promisedStreamId, "Promised Stream ID");
            verifyPadding(padding);

            // Encode the entire header block into an intermediate buffer.
            headerBlock = ctx.alloc().buffer();
            headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            // Read the first fragment (possibly everything).
            Http2Flags flags = new Http2Flags().paddingPresent(padding > 0);
            // INT_FIELD_LENGTH is for the length of the promisedStreamId
            int nonFragmentLength = INT_FIELD_LENGTH + padding;
            int maxFragmentLength = maxFrameSize - nonFragmentLength;
            ByteBuf fragment = headerBlock.readRetainedSlice(min(headerBlock.readableBytes(), maxFragmentLength));

            flags.endOfHeaders(!headerBlock.isReadable());

            int payloadLength = fragment.readableBytes() + nonFragmentLength;
            ByteBuf buf = ctx.alloc().buffer(PUSH_PROMISE_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, PUSH_PROMISE, flags, streamId);
            writePaddingLength(buf, padding);

            // Write out the promised stream ID.
            buf.writeInt(promisedStreamId);
            ctx.write(buf, promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment, promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.slice(0, paddingBytes(padding)), promiseAggregator.newPromise());
            }

            if (!flags.endOfHeaders()) {
                writeContinuationFrames(ctx, streamId, headerBlock, promiseAggregator);
            }
        } catch (Http2Exception e) {
            promiseAggregator.setFailure(e);
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
            promiseAggregator.doneAllocatingPromises();
            PlatformDependent.throwException(t);
        } finally {
            if (headerBlock != null) {
                headerBlock.release();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData, ChannelPromise promise) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
            verifyStreamOrConnectionId(lastStreamId, "Last Stream ID");
            verifyErrorCode(errorCode);

            int payloadLength = 8 + debugData.readableBytes();
            ByteBuf buf = ctx.alloc().buffer(GO_AWAY_FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payloadLength, GO_AWAY, new Http2Flags(), 0);
            buf.writeInt(lastStreamId);
            buf.writeInt((int) errorCode);
            ctx.write(buf, promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                debugData.release();
            } finally {
                promiseAggregator.setFailure(t);
                promiseAggregator.doneAllocatingPromises();
            }
            return promiseAggregator;
        }

        try {
            ctx.write(debugData, promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement, ChannelPromise promise) {
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            verifyWindowSizeIncrement(windowSizeIncrement);

            ByteBuf buf = ctx.alloc().buffer(WINDOW_UPDATE_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, WINDOW_UPDATE, new Http2Flags(), streamId);
            buf.writeInt(windowSizeIncrement);
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf payload, ChannelPromise promise) {
        // heap-based frames always got copied
        final boolean copyPayload = payload.hasArray() || payload.readableBytes() <= COPY_SLICE_CUTOFF_BYTES;
        SimpleChannelPromiseAggregator promiseAggregator = null;
        ByteBuf buf = null;
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + (copyPayload? payload.readableBytes() : 0));
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payload.readableBytes(), frameType, flags, streamId);
            if (copyPayload || buf.maxFastWritableBytes() >= payload.readableBytes()) {
                // maybe we've been lucky, even if we were not supposed to copy the frame
                buf.writeBytes(payload);
                payload.release();
                payload = null;
                ctx.write(buf, promise);
                return promise;
            }
            // Slow-path
            promiseAggregator = new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
            ctx.write(buf, promiseAggregator.newPromise());
        } catch (Throwable t) {
            return handleFailure(payload, buf, promise, t, promiseAggregator);
        }
        assert !copyPayload && promiseAggregator != null;
        try {
            ctx.write(payload, promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    /**
     * NOTE: if {@code promiseAggregator} is not null, it takes the precedence over {@code promise}.
     */
    private static ChannelPromise handleFailure(ByteBuf first, ByteBuf second,
                                                ChannelPromise promise, Throwable t,
                                                SimpleChannelPromiseAggregator promiseAggregator) {
        try {
            if (first != null) {
                first.release();
            }
        } catch (Throwable silent) {
            ThrowableUtil.addSuppressed(t, silent);
        }
        try {
            if (second != null) {
                second.release();
            }
        } catch (Throwable silent) {
            ThrowableUtil.addSuppressed(t, silent);
        } finally {
            if (promiseAggregator != null) {
                promiseAggregator.setFailure(t);
                promiseAggregator.doneAllocatingPromises();
                return promiseAggregator;
            } else {
                promise.setFailure(t);
                return promise;
            }
        }
    }

    private ChannelFuture writeHeadersInternal(ChannelHandlerContext ctx,
            int streamId, Http2Headers headers, int padding, boolean endStream,
            boolean hasPriority, int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
        ByteBuf headerBlock = null;
        try {
            verifyStreamId(streamId, STREAM_ID);
            if (hasPriority) {
                verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
                verifyPadding(padding);
                verifyWeight(weight);
            }

            // Encode the entire header block.
            // Let's use an explicit length, here to make obvious the trade-off used later
            // which encode the header right past beforeHeaderBytes
            headerBlock = ctx.alloc().buffer(256);
            int paddingBytes = paddingBytes(padding);
            if (paddingBytes < 0) {
                paddingBytes = 0;
            }
            // better be precise here and not rely on HEADERS_FRAME_HEADER_LENGTH:
            // maybe we can reuse headerBlock!
            final int beforeHeaderBytes = FRAME_HEADER_LENGTH + (padding > 0? 1 : 0) + (hasPriority? 5 : 0);
            // if we cannot zero the padding efficiently, there's no point to try to use the headerBlock
            final boolean canUseHeaderBlock = paddingBytes <= 64;
            if (canUseHeaderBlock) {
                assert beforeHeaderBytes <= HEADERS_FRAME_HEADER_LENGTH;
                // encode the headers after the space left (optimistically!) for the frame header
                headerBlock.setIndex(beforeHeaderBytes, beforeHeaderBytes);
            }
            try {
                headersEncoder.encodeHeaders(streamId, headers, headerBlock);
            } catch (Http2Exception e) {
                return promise.setFailure(e);
            }

            Http2Flags flags =
                    new Http2Flags().endOfStream(endStream).priorityPresent(hasPriority).paddingPresent(padding > 0);
            int nonFragmentBytes = padding + flags.getNumPriorityBytes();
            int maxFragmentLength = maxFrameSize - nonFragmentBytes;
            int fragmentBytes = min(headerBlock.readableBytes(), maxFragmentLength);
            // if the first fragment fully consume the headerBlock, we can try making it fit into the
            // header frame if is small enough: it would avoid using a promise aggregator and reduce the number
            // of pipeline traversal
            if (canUseHeaderBlock && fragmentBytes == headerBlock.readableBytes() &&
                (headerBlock.maxFastWritableBytes() >= paddingBytes || fragmentBytes <= COPY_SLICE_CUTOFF_BYTES)) {
                // no need for a continuation either!
                try {
                    flags.endOfHeaders(true);
                    final ByteBuf buf;
                    if (headerBlock.maxFastWritableBytes() >= paddingBytes) {
                        buf = headerBlock;
                        headerBlock = null;
                        buf.setIndex(0, 0);
                    } else {
                        final int bufBytes = beforeHeaderBytes + fragmentBytes + paddingBytes;
                        buf = ctx.alloc().buffer(bufBytes);
                    }
                    int payloadLength = fragmentBytes + nonFragmentBytes;
                    // 9 bytes
                    writeFrameHeaderInternal(buf, payloadLength, HEADERS, flags, streamId);
                    // 0-1 bytes
                    writePaddingLength(buf, padding);
                    // 0-5 bytes
                    if (hasPriority) {
                        buf.writeInt(exclusive? (int) (0x80000000L | streamDependency) : streamDependency);
                        // Adjust the weight so that it fits into a single byte on the wire.
                        buf.writeByte(weight - 1);
                    }
                    // headerBlock is null only if we're using it to hold everything
                    if (headerBlock != null) {
                        buf.writeBytes(headerBlock, fragmentBytes);
                    } else {
                        buf.writerIndex(beforeHeaderBytes + fragmentBytes);
                    }
                    if (paddingBytes > 0) {
                        // this is quite fast assuming small padding
                        // see https://github.com/netty/netty/pull/13693
                        buf.writeZero(paddingBytes);
                    }
                    ctx.write(buf, promise);
                } catch (Throwable t) {
                    promise.setFailure(t);
                    PlatformDependent.throwException(t);
                }
                return promise;
            } else {
                return writeHeadersPartsInternal(ctx, streamId, padding, hasPriority, streamDependency,
                                                 weight, exclusive, promise, headerBlock, fragmentBytes,
                                                 flags, nonFragmentBytes);
            }
        } finally {
            if (headerBlock != null) {
                headerBlock.release();
            }
        }
    }

    private ChannelPromise writeHeadersPartsInternal(ChannelHandlerContext ctx, int streamId, int padding,
                                                     boolean hasPriority, int streamDependency, short weight,
                                                     boolean exclusive, ChannelPromise promise, ByteBuf headerBlock,
                                                     int fragmentBytes, Http2Flags flags, int nonFragmentBytes) {
        final SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
            // Read the first fragment (possibly everything).
            ByteBuf fragment = headerBlock.readRetainedSlice(fragmentBytes);

            // Set the end of headers flag for the first frame.
            flags.endOfHeaders(!headerBlock.isReadable());

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            ByteBuf buf = ctx.alloc().buffer(HEADERS_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, HEADERS, flags, streamId);
            writePaddingLength(buf, padding);

            if (hasPriority) {
                buf.writeInt(exclusive? (int) (0x80000000L | streamDependency) : streamDependency);

                // Adjust the weight so that it fits into a single byte on the wire.
                buf.writeByte(weight - 1);
            }
            ctx.write(buf, promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment, promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.slice(0, paddingBytes(padding)), promiseAggregator.newPromise());
            }

            if (!flags.endOfHeaders()) {
                writeContinuationFrames(ctx, streamId, headerBlock, promiseAggregator);
            }
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
            promiseAggregator.doneAllocatingPromises();
            PlatformDependent.throwException(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    /**
     * Writes as many continuation frames as needed until {@code padding} and {@code headerBlock} are consumed.
     */
    private ChannelFuture writeContinuationFrames(ChannelHandlerContext ctx, int streamId,
            ByteBuf headerBlock, SimpleChannelPromiseAggregator promiseAggregator) {
        Http2Flags flags = new Http2Flags();

        if (headerBlock.isReadable()) {
            // The frame header (and padding) only changes on the last frame, so allocate it once and re-use
            int fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
            ByteBuf buf = ctx.alloc().buffer(CONTINUATION_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);

            do {
                fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
                ByteBuf fragment = headerBlock.readRetainedSlice(fragmentReadableBytes);

                if (headerBlock.isReadable()) {
                    ctx.write(buf.retain(), promiseAggregator.newPromise());
                } else {
                    // The frame header is different for the last frame, so re-allocate and release the old buffer
                    flags = flags.endOfHeaders(true);
                    buf.release();
                    buf = ctx.alloc().buffer(CONTINUATION_FRAME_HEADER_LENGTH);
                    writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);
                    ctx.write(buf, promiseAggregator.newPromise());
                }

                ctx.write(fragment, promiseAggregator.newPromise());

            } while (headerBlock.isReadable());
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

    private static void writePaddingLength(ByteBuf buf, int padding) {
        if (padding > 0) {
            // It is assumed that the padding length has been bounds checked before this
            // Minus 1, as the pad length field is included in the padding parameter and is 1 byte wide.
            buf.writeByte(padding - 1);
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
