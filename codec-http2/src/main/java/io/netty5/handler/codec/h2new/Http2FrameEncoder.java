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

package io.netty.handler.codec.h2new;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.adaptor.ByteBufAdaptor;
import io.netty.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
import io.netty.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersEncoder;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;

import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.h2new.Http2Flags.ACK;
import static io.netty.handler.codec.h2new.Http2Flags.END_HEADERS;
import static io.netty.handler.codec.h2new.Http2Flags.END_STREAM;
import static io.netty.handler.codec.h2new.Http2Flags.PADDED;
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
import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
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
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Http2FrameWriter} that supports all frame types defined by the HTTP/2 specification.
 */
final class Http2FrameEncoder extends ChannelHandlerAdapter {
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
    private ByteBufAllocatorAdaptor byteBufAllocatorAdaptor;

    Http2FrameEncoder() {
        this(new DefaultHttp2HeadersEncoder());
    }

    Http2FrameEncoder(SensitivityDetector headersSensitivityDetector) {
        this(new DefaultHttp2HeadersEncoder(headersSensitivityDetector));
    }

    Http2FrameEncoder(SensitivityDetector headersSensitivityDetector, boolean ignoreMaxHeaderListSize) {
        this(new DefaultHttp2HeadersEncoder(headersSensitivityDetector, ignoreMaxHeaderListSize));
    }

    Http2FrameEncoder(Http2HeadersEncoder headersEncoder) {
        this.headersEncoder = headersEncoder;
        maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        byteBufAllocatorAdaptor = new ByteBufAllocatorAdaptor();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (byteBufAllocatorAdaptor != null) {
            byteBufAllocatorAdaptor.close();
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Http2Frame)) {
            return ctx.write(msg);
        }
        Http2Frame frame = (Http2Frame) msg;
        switch (frame.frameType()) {
            case Data:
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                return writeData(ctx, dataFrame.streamId(), toByteBuf(dataFrame.data()),
                        dataFrame.isPadded() ? dataFrame.padding() : 0, dataFrame.isEndStream());
            case Headers:
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
                if (headersFrame.isPrioritySet()) {
                    return writeHeaders(ctx, headersFrame.streamId(), headersFrame.headers(), headersFrame.streamDependency(),
                            headersFrame.weight(), headersFrame.isExclusiveDependency(),
                            headersFrame.isPadded() ? headersFrame.padding() : 0, headersFrame.isEndStream());
                } else {
                    return writeHeaders(ctx, headersFrame.streamId(), headersFrame.headers(),
                            headersFrame.isPadded() ? headersFrame.padding() : 0, headersFrame.isEndStream());
                }
            case Priority:
                Http2PriorityFrame priorityFrame = (Http2PriorityFrame) frame;
                return writePriority(ctx, priorityFrame.streamId(), priorityFrame.streamDependency(),
                        priorityFrame.weight(), priorityFrame.isExclusiveDependency());
            case RstStream:
                Http2ResetStreamFrame resetStreamFrame = (Http2ResetStreamFrame) frame;
                return writeRstStream(ctx, resetStreamFrame.streamId(), resetStreamFrame.errorCode());
            case Settings:
                Http2SettingsFrame settingsFrame = (Http2SettingsFrame) frame;
                if (settingsFrame.ack()) {
                    return writeSettingsAck(ctx);
                } else {
                    return writeSettings(ctx, settingsFrame.settings());
                }
            case PushPromise:
                Http2PushPromiseFrame pushPromiseFrame = (Http2PushPromiseFrame) frame;
                return writePushPromise(ctx, pushPromiseFrame.streamId(), pushPromiseFrame.promisedStreamId(),
                        pushPromiseFrame.headers(), pushPromiseFrame.isPadded() ? pushPromiseFrame.padding() : 0);
            case Ping:
                Http2PingFrame pingFrame = (Http2PingFrame) frame;
                return writePing(ctx, pingFrame.ack(), pingFrame.data());
            case GoAway:
                Http2GoAwayFrame goAwayFrame = (Http2GoAwayFrame) frame;
                return writeGoAway(ctx, goAwayFrame.lastStreamId(), goAwayFrame.errorCode(),
                        toByteBuf(goAwayFrame.debugData()));
            case WindowUpdate:
                Http2WindowUpdateFrame windowUpdateFrame = (Http2WindowUpdateFrame) frame;
                return writeWindowUpdate(ctx, windowUpdateFrame.streamId(), windowUpdateFrame.windowSizeIncrement());
            case Unknown:
                Http2UnknownFrame unknownFrame = (Http2UnknownFrame) frame;
                return writeFrame(ctx, unknownFrame.unknownFrameType(), unknownFrame.streamId(), unknownFrame.flags(),
                        toByteBuf(unknownFrame.payload()));
            default:
                ReferenceCountUtil.release(frame);
                throw new IllegalArgumentException("Unknown frame type: " + frame.frameType());
        }
    }

    private Future<Void> writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                                   boolean endStream) {
        final SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        ByteBuf frameHeader = null;
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyPadding(padding);

            int remainingData = data.readableBytes();
            short flags = 0;
            // Fast path to write frames of payload size maxFrameSize first.
            if (remainingData > maxFrameSize) {
                frameHeader = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                writeFrameHeaderInternal(frameHeader, maxFrameSize, DATA, flags, streamId);
                do {
                    // Write the header.
                    ctx.write(frameHeader.retainedSlice()).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    ctx.write(data.readRetainedSlice(maxFrameSize)).cascadeTo(promiseAggregator.newPromise());

                    remainingData -= maxFrameSize;
                    // Stop iterating if remainingData == maxFrameSize so we can take care of reference counts below.
                } while (remainingData > maxFrameSize);
            }

            if (padding == 0) {
                // Write the header.
                if (frameHeader != null) {
                    frameHeader.release();
                    frameHeader = null;
                }
                ByteBuf frameHeader2 = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                flags = Http2Flags.setFlag(flags, endStream, END_STREAM);
                writeFrameHeaderInternal(frameHeader2, remainingData, DATA, flags, streamId);
                ctx.write(frameHeader2).cascadeTo(promiseAggregator.newPromise());

                // Write the payload.
                ByteBuf lastFrame = data.readSlice(remainingData);
                data = null;
                ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
            } else {
                if (remainingData != maxFrameSize) {
                    if (frameHeader != null) {
                        frameHeader.release();
                        frameHeader = null;
                    }
                } else {
                    remainingData -= maxFrameSize;
                    // Write the header.
                    ByteBuf lastFrame;
                    if (frameHeader == null) {
                        lastFrame = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
                        writeFrameHeaderInternal(lastFrame, maxFrameSize, DATA, flags, streamId);
                    } else {
                        lastFrame = frameHeader.slice();
                        frameHeader = null;
                    }
                    ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    lastFrame = data.readableBytes() != maxFrameSize ? data.readSlice(maxFrameSize) : data;
                    data = null;
                    ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
                }

                do {
                    int frameDataBytes = min(remainingData, maxFrameSize);
                    int framePaddingBytes = min(padding, max(0, (maxFrameSize - 1) - frameDataBytes));

                    // Decrement the remaining counters.
                    padding -= framePaddingBytes;
                    remainingData -= frameDataBytes;

                    // Write the header.
                    ByteBuf frameHeader2 = ctx.alloc().buffer(DATA_FRAME_HEADER_LENGTH);
                    flags = Http2Flags.setFlag(flags, endStream && remainingData == 0 && padding == 0, END_STREAM);
                    flags = Http2Flags.setFlag(flags, framePaddingBytes > 0, PADDED);
                    writeFrameHeaderInternal(frameHeader2, framePaddingBytes + frameDataBytes, DATA, flags, streamId);
                    writePaddingLength(frameHeader2, framePaddingBytes);
                    ctx.write(frameHeader2).cascadeTo(promiseAggregator.newPromise());

                    // Write the payload.
                    if (frameDataBytes != 0 && data != null) { // Make sure Data is not null
                        if (remainingData == 0) {
                            ByteBuf lastFrame = data.readSlice(frameDataBytes);
                            data = null;
                            ctx.write(lastFrame).cascadeTo(promiseAggregator.newPromise());
                        } else {
                            ctx.write(data.readRetainedSlice(frameDataBytes)).cascadeTo(promiseAggregator.newPromise());
                        }
                    }
                    // Write the frame padding.
                    if (paddingBytes(framePaddingBytes) > 0) {
                        ctx.write(ZERO_BUFFER.slice(0, paddingBytes(framePaddingBytes)))
                                .cascadeTo(promiseAggregator.newPromise());
                    }
                } while (remainingData != 0 || padding != 0);
            }
        } catch (Throwable cause) {
            if (frameHeader != null) {
                frameHeader.release();
            }
            // Use a try/finally here in case the data has been released before calling this method. This is not
            // necessary above because we internally allocate frameHeader.
            try {
                if (data != null) {
                    data.release();
                }
            } finally {
                promiseAggregator.setFailure(cause);
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    private Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                      boolean endStream) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                false, 0, (short) 0, false);
    }

    private Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                      int streamDependency, short weight, boolean exclusive, int padding,
                                      boolean endStream) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream, true, streamDependency,
                weight, exclusive);
    }

    private Future<Void> writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                                       boolean exclusive) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
            verifyWeight(weight);

            ByteBuf buf = ctx.alloc().buffer(PRIORITY_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, PRIORITY_ENTRY_LENGTH, PRIORITY, (short) 0, streamId);
            buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);
            // Adjust the weight so that it fits into a single byte on the wire.
            buf.writeByte(weight - 1);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private Future<Void> writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode) {
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyErrorCode(errorCode);

            ByteBuf buf = ctx.alloc().buffer(RST_STREAM_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, RST_STREAM, (short) 0, streamId);
            buf.writeInt((int) errorCode);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private Future<Void> writeSettings(ChannelHandlerContext ctx, Http2Settings settings) {
        try {
            requireNonNull(settings, "settings");
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payloadLength);
            writeFrameHeaderInternal(buf, payloadLength, SETTINGS, (short) 0, 0);
            for (Http2Settings.PrimitiveEntry<Long> entry : settings.entries()) {
                buf.writeChar(entry.key());
                buf.writeInt(entry.value().intValue());
            }
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private Future<Void> writeSettingsAck(ChannelHandlerContext ctx) {
        try {
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, 0, SETTINGS, Http2Flags.setFlag((short) 0, true, ACK), 0);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private Future<Void> writePing(ChannelHandlerContext ctx, boolean ack, long data) {
        ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + PING_FRAME_PAYLOAD_LENGTH);
        // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
        // in the catch block.
        writeFrameHeaderInternal(buf, PING_FRAME_PAYLOAD_LENGTH, PING, Http2Flags.setFlag((short) 0, ack, ACK), 0);
        buf.writeLong(data);
        return ctx.write(buf);
    }

    private Future<Void> writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                          Http2Headers headers, int padding) {
        ByteBuf headerBlock = null;
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamId(streamId, STREAM_ID);
            verifyStreamId(promisedStreamId, "Promised Stream ID");
            verifyPadding(padding);

            // Encode the entire header block into an intermediate buffer.
            headerBlock = ctx.alloc().buffer();
            headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            // Read the first fragment (possibly everything).
            short flags = Http2Flags.setFlag((short) 0, padding > 0, PADDED);
            // INT_FIELD_LENGTH is for the length of the promisedStreamId
            int nonFragmentLength = INT_FIELD_LENGTH + padding;
            int maxFragmentLength = maxFrameSize - nonFragmentLength;
            ByteBuf fragment = headerBlock.readRetainedSlice(min(headerBlock.readableBytes(), maxFragmentLength));

            flags = Http2Flags.setFlag(flags, !headerBlock.isReadable(), END_HEADERS);

            int payloadLength = fragment.readableBytes() + nonFragmentLength;
            ByteBuf buf = ctx.alloc().buffer(PUSH_PROMISE_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, PUSH_PROMISE, flags, streamId);
            writePaddingLength(buf, padding);

            // Write out the promised stream ID.
            buf.writeInt(promisedStreamId);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.slice(0, paddingBytes(padding))).cascadeTo(promiseAggregator.newPromise());
            }

            if (!Http2Flags.endOfHeaders(flags)) {
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
                headerBlock.release();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    private Future<Void> writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamOrConnectionId(lastStreamId, "Last Stream ID");
            verifyErrorCode(errorCode);

            int payloadLength = 8 + debugData.readableBytes();
            ByteBuf buf = ctx.alloc().buffer(GO_AWAY_FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payloadLength, GO_AWAY, (short) 0, 0);
            buf.writeInt(lastStreamId);
            buf.writeInt((int) errorCode);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                debugData.release();
            } finally {
                promiseAggregator.setFailure(t);
            }
            return promiseAggregator.doneAllocatingPromises();
        }

        try {
            ctx.write(debugData).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    private Future<Void> writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            verifyWindowSizeIncrement(windowSizeIncrement);

            ByteBuf buf = ctx.alloc().buffer(WINDOW_UPDATE_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, WINDOW_UPDATE, (short) 0, streamId);
            buf.writeInt(windowSizeIncrement);
            return ctx.write(buf);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private Future<Void> writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, short flags,
                                    ByteBuf payload) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(ctx.newPromise(), ctx.executor());
        try {
            verifyStreamOrConnectionId(streamId, STREAM_ID);
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payload.readableBytes(), frameType, flags, streamId);
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                payload.release();
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

    private Future<Void> writeHeadersInternal(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                              int padding, boolean endStream, boolean hasPriority, int streamDependency,
                                              short weight, boolean exclusive) {
        ByteBuf headerBlock = null;
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
            headerBlock = ctx.alloc().buffer();
            headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            short flags = Http2Flags.setFlag((short) 0, endStream, END_STREAM);
            flags = Http2Flags.setFlag(flags, hasPriority, PRIORITY);
            flags = Http2Flags.setFlag(flags, padding > 0, PADDED);

            // Read the first fragment (possibly everything).
            int nonFragmentBytes = padding + Http2Flags.numPriorityBytes(flags);
            int maxFragmentLength = maxFrameSize - nonFragmentBytes;
            ByteBuf fragment = headerBlock.readRetainedSlice(min(headerBlock.readableBytes(), maxFragmentLength));

            // Set the end of headers flag for the first frame.
            flags = Http2Flags.setFlag(flags, !headerBlock.isReadable(), END_HEADERS);

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            ByteBuf buf = ctx.alloc().buffer(HEADERS_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, HEADERS, flags, streamId);
            writePaddingLength(buf, padding);

            if (hasPriority) {
                buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);

                // Adjust the weight so that it fits into a single byte on the wire.
                buf.writeByte(weight - 1);
            }
            ctx.write(buf).cascadeTo(promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());

            // Write out the padding, if any.
            if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.slice(0, paddingBytes(padding))).cascadeTo(promiseAggregator.newPromise());
            }

            if (!Http2Flags.endOfHeaders(flags)) {
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
                headerBlock.release();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    /**
     * Writes as many continuation frames as needed until {@code padding} and {@code headerBlock} are consumed.
     */
    private void writeContinuationFrames(ChannelHandlerContext ctx, int streamId, ByteBuf headerBlock,
                                         SimpleChannelPromiseAggregator promiseAggregator) {
        short flags = 0;

        if (headerBlock.isReadable()) {
            // The frame header (and padding) only changes on the last frame, so allocate it once and re-use
            int fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
            ByteBuf buf = ctx.alloc().buffer(CONTINUATION_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);

            do {
                fragmentReadableBytes = min(headerBlock.readableBytes(), maxFrameSize);
                ByteBuf fragment = headerBlock.readRetainedSlice(fragmentReadableBytes);

                if (headerBlock.isReadable()) {
                    ctx.write(buf.retain()).cascadeTo(promiseAggregator.newPromise());
                } else {
                    // The frame header is different for the last frame, so re-allocate and release the old buffer
                    flags = Http2Flags.setFlag(flags, true, END_HEADERS);
                    buf.release();
                    buf = ctx.alloc().buffer(CONTINUATION_FRAME_HEADER_LENGTH);
                    writeFrameHeaderInternal(buf, fragmentReadableBytes, CONTINUATION, flags, streamId);
                    ctx.write(buf).cascadeTo(promiseAggregator.newPromise());
                }

                ctx.write(fragment).cascadeTo(promiseAggregator.newPromise());

            } while (headerBlock.isReadable());
        }
    }

    private ByteBufAdaptor toByteBuf(Buffer data) {
        return new ByteBufAdaptor(byteBufAllocatorAdaptor, data, data.capacity());
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

    private static void writeFrameHeaderInternal(ByteBuf out, int payloadLength, byte type, short flags, int streamId) {
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags);
        out.writeInt(streamId);
    }

    private static void verifyPingPayload(ByteBuf data) {
        if (data == null || data.readableBytes() != PING_FRAME_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Opaque data must be " + PING_FRAME_PAYLOAD_LENGTH + " bytes");
        }
    }
}
