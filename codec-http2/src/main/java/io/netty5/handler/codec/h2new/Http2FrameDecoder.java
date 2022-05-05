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
package io.netty5.handler.codec.h2new;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty5.handler.codec.http2.Http2Exception;
import io.netty5.handler.codec.http2.Http2Headers;
import io.netty5.handler.codec.http2.Http2HeadersDecoder;
import io.netty5.handler.codec.http2.Http2Settings;

import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.INT_FIELD_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.PRIORITY_ENTRY_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static io.netty5.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty5.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty5.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty5.handler.codec.http2.Http2Error.FRAME_SIZE_ERROR;
import static io.netty5.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty5.handler.codec.http2.Http2Exception.connectionError;
import static io.netty5.handler.codec.http2.Http2Exception.streamError;
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

final class Http2FrameDecoder extends ByteToMessageDecoder {
    private final Http2HeadersDecoder headersDecoder;

    /**
     * {@code true} = reading headers, {@code false} = reading payload.
     */
    private boolean readingFrameHeader = true;
    /**
     * Once set to {@code true} the value will never change. This is set to {@code true} if an unrecoverable error which
     * renders the connection unusable.
     */
    private boolean readError;
    private byte frameType;
    private int streamId;
    private short flags;
    private int payloadLength;
    private HeadersBlockBuilder headersBlockBuilder;
    private int maxFrameSize;
    private BufferAllocator allocator;

    /**
     * Create a new instance.
     * <p>
     * Header names will be validated.
     */
    Http2FrameDecoder() {
        this(true);
    }

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate headers. {@code false} to not validate headers.
     * @see DefaultHttp2HeadersDecoder (boolean)
     */
    Http2FrameDecoder(boolean validateHeaders) {
        this(new DefaultHttp2HeadersDecoder(validateHeaders));
    }

    Http2FrameDecoder(Http2HeadersDecoder headersDecoder) {
        this.headersDecoder = headersDecoder;
        maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    }

    @Override
    protected void handlerAdded0(ChannelHandlerContext ctx) {
        final ByteBufAllocator alloc = ctx.alloc();
        if (alloc instanceof ByteBufAllocatorAdaptor) {
            allocator = ((ByteBufAllocatorAdaptor) alloc).getOnHeap();
        } else {
            allocator = BufferAllocator.onHeapPooled();
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        if (headersBlockBuilder != null) {
            headersBlockBuilder.close();
            headersBlockBuilder = null;
        }
        if (allocator != null) {
            allocator.close();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf input) throws Exception {
        if (readError) {
            input.skipBytes(input.readableBytes());
            return;
        }
        try {
            do {
                if (readingFrameHeader) {
                    processHeaderState(input);
                    if (readingFrameHeader) {
                        // Wait until the entire header has arrived.
                        return;
                    }
                }

                // The header is complete, fall into the next case to process the payload.
                // This is to ensure the proper handling of zero-length payloads. In this
                // case, we don't want to loop around because there may be no more data
                // available, causing us to exit the loop. Instead, we just want to perform
                // the first pass at payload processing now.
                final Http2Frame frame = processPayloadState(ctx, input);
                if (!readingFrameHeader || frame == null) {
                    // Wait until the entire payload has arrived.
                    return;
                }
                ctx.fireChannelRead(frame);
            } while (input.isReadable());
        } catch (Http2Exception e) {
            readError = !Http2Exception.isStreamError(e);
            throw e;
        } catch (Throwable e) {
            readError = true;
            throw e;
        }
    }

    private void processHeaderState(ByteBuf in) throws Http2Exception {
        if (in.readableBytes() < FRAME_HEADER_LENGTH) {
            // Wait until the entire frame header has been read.
            return;
        }

        // Read the header and prepare the unmarshaller to read the frame.
        payloadLength = in.readUnsignedMedium();
        if (payloadLength > maxFrameSize) {
            throw connectionError(FRAME_SIZE_ERROR, "Frame length: %d exceeds maximum: %d", payloadLength,
                                  maxFrameSize);
        }
        frameType = in.readByte();
        flags = in.readUnsignedByte();
        streamId = readUnsignedInt(in);

        // We have consumed the data, next time we read we will be expecting to read the frame payload.
        readingFrameHeader = false;
    }

    private Http2Frame processPayloadState(ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
        if (in.readableBytes() < payloadLength) {
            // Wait until the entire payload has been read.
            return null;
        }

        // Only process up to payloadLength bytes.
        int payloadEndIndex = in.readerIndex() + payloadLength;

        // We have consumed the data, next time we read we will be expecting to read a frame header.
        readingFrameHeader = true;

        Http2Frame readFrame;
        // Read the payload and fire the frame event to the listener.
        switch (frameType) {
            case DATA:
                readFrame = readDataFrame(ctx, in, payloadEndIndex);
                break;
            case HEADERS:
                readFrame = readHeadersFrame(ctx, in, payloadEndIndex);
                break;
            case PRIORITY:
                readFrame = readPriorityFrame(in);
                break;
            case RST_STREAM:
                readFrame = readRstStreamFrame(in);
                break;
            case SETTINGS:
                readFrame = readSettingsFrame(in);
                break;
            case PUSH_PROMISE:
                readFrame = readPushPromiseFrame(ctx, in, payloadEndIndex);
                break;
            case PING:
                readFrame = readPingFrame(in.readLong());
                break;
            case GO_AWAY:
                readFrame = readGoAwayFrame(in, payloadEndIndex);
                break;
            case WINDOW_UPDATE:
                readFrame = readWindowUpdateFrame(in);
                break;
            case CONTINUATION:
                readFrame = readContinuationFrame(ctx, in, payloadEndIndex);
                break;
            default:
                readFrame = readUnknownFrame(in, payloadEndIndex);
                break;
        }
        in.readerIndex(payloadEndIndex);
        return readFrame;
    }

    // TODO: Add verifiers to validator handlers
    private void verifyDataFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();

        if (payloadLength < Http2Flags.paddingPresenceFieldLength(flags)) {
            throw streamError(streamId, FRAME_SIZE_ERROR, "Frame length %d too small.", payloadLength);
        }
    }

    private void verifyHeadersFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();

        int requiredLength = Http2Flags.paddingPresenceFieldLength(flags) + Http2Flags.numPriorityBytes(flags);
        if (payloadLength < requiredLength) {
            throw streamError(streamId, FRAME_SIZE_ERROR, "Frame length too small." + payloadLength);
        }
    }

    private void verifyPriorityFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();

        if (payloadLength != PRIORITY_ENTRY_LENGTH) {
            throw streamError(streamId, FRAME_SIZE_ERROR, "Invalid frame length %d.", payloadLength);
        }
    }

    private void verifyRstStreamFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();

        if (payloadLength != INT_FIELD_LENGTH) {
            throw connectionError(FRAME_SIZE_ERROR, "Invalid frame length %d.", payloadLength);
        }
    }

    private void verifySettingsFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
        if (streamId != 0) {
            throw connectionError(PROTOCOL_ERROR, "A stream ID must be zero.");
        }
        if (Http2Flags.isAck(flags) && payloadLength > 0) {
            throw connectionError(FRAME_SIZE_ERROR, "Ack settings frame must have an empty payload.");
        }
        if (payloadLength % SETTING_ENTRY_LENGTH > 0) {
            throw connectionError(FRAME_SIZE_ERROR, "Frame length %d invalid.", payloadLength);
        }
    }

    private void verifyPushPromiseFrame() throws Http2Exception {
        verifyNotProcessingHeaders();

        // Subtract the length of the promised stream ID field, to determine the length of the
        // rest of the payload (header block fragment + payload).
        int minLength = Http2Flags.paddingPresenceFieldLength(flags) + INT_FIELD_LENGTH;
        if (payloadLength < minLength) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Frame length %d too small.", payloadLength);
        }
    }

    private void verifyPingFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
        if (streamId != 0) {
            throw connectionError(PROTOCOL_ERROR, "A stream ID must be zero.");
        }
        if (payloadLength != PING_FRAME_PAYLOAD_LENGTH) {
            throw connectionError(FRAME_SIZE_ERROR,
                    "Frame length %d incorrect size for ping.", payloadLength);
        }
    }

    private void verifyGoAwayFrame() throws Http2Exception {
        verifyNotProcessingHeaders();

        if (streamId != 0) {
            throw connectionError(PROTOCOL_ERROR, "A stream ID must be zero.");
        }
        if (payloadLength < 8) {
            throw connectionError(FRAME_SIZE_ERROR, "Frame length %d too small.", payloadLength);
        }
    }

    private void verifyWindowUpdateFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
        verifyStreamOrConnectionId(streamId, "Stream ID");

        if (payloadLength != INT_FIELD_LENGTH) {
            throw connectionError(FRAME_SIZE_ERROR, "Invalid frame length %d.", payloadLength);
        }
    }

    private void verifyContinuationFrame() throws Http2Exception {
        verifyAssociatedWithAStream();

        if (headersBlockBuilder == null) {
            throw connectionError(PROTOCOL_ERROR, "Received %s frame but not currently processing headers.",
                    frameType);
        }

        if (streamId != headersBlockBuilder.streamId) {
            throw connectionError(PROTOCOL_ERROR, "Continuation stream ID does not match pending headers. "
                    + "Expected %d, but received %d.", headersBlockBuilder.streamId, streamId);
        }

        if (payloadLength < Http2Flags.paddingPresenceFieldLength(flags)) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Frame length %d too small for padding.", payloadLength);
        }
    }

    private void verifyUnknownFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
    }

    private Http2DataFrame readDataFrame(ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex)
            throws Http2Exception {
        int padding = readPadding(payload);
        verifyPadding(padding);

        // Determine how much data there is to read by removing the trailing
        // padding.
        int dataLength = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);
        Buffer data = ByteBufAdaptor.extractOrCopy(allocator, payload.readRetainedSlice(dataLength));
        return padding == 0 ? new DefaultHttp2DataFrame(streamId, data, Http2Flags.endOfStream(flags)) :
                new DefaultHttp2DataFrame(streamId, data, Http2Flags.endOfStream(flags), padding);
    }

    private Http2HeadersFrame readHeadersFrame(final ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex)
            throws Http2Exception {
        final int headersStreamId = streamId;
        final short headersFlags = flags;
        final boolean endOfHeaders = Http2Flags.endOfHeaders(headersFlags);
        final int padding = readPadding(payload);
        verifyPadding(padding);

        // The callback that is invoked is different depending on whether priority information
        // is present in the headers frame.
        if (Http2Flags.priorityPresent(flags)) {
            long word1 = payload.readUnsignedInt();
            final boolean exclusive = (word1 & 0x80000000L) != 0;
            final int streamDependency = (int) (word1 & 0x7FFFFFFFL);
            if (streamDependency == streamId) {
                throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
            }
            final short weight = (short) (payload.readUnsignedByte() + 1);
            final int lenToRead = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);

            headersBlockBuilder = new HeadersBlockBuilder(headersStreamId);
            headersBlockBuilder.addFragment(payload, lenToRead, ctx.alloc(), endOfHeaders,
                    Http2Flags.endOfStream(headersFlags));
            if (endOfHeaders) {
                final Http2Headers headers = headersBlockBuilder.decodeHeaders();
                headersBlockBuilder.close();
                headersBlockBuilder = null;
                return padding != 0 ?
                        new DefaultHttp2HeadersFrame(headersStreamId, headers, Http2Flags.endOfStream(headersFlags),
                        streamDependency, exclusive, weight, padding) :
                        new DefaultHttp2HeadersFrame(headersStreamId, headers, Http2Flags.endOfStream(headersFlags),
                        streamDependency, exclusive, weight);
            }
            return null;
        }

        // The priority fields are not present in the frame
        headersBlockBuilder = new HeadersBlockBuilder(headersStreamId);

        // Process the initial fragment, invoking the listener's callback if end of headers.
        int len = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);
        headersBlockBuilder.addFragment(payload, len, ctx.alloc(), endOfHeaders, Http2Flags.endOfStream(headersFlags));
        if (endOfHeaders) {
            final Http2Headers headers = headersBlockBuilder.decodeHeaders();
            headersBlockBuilder.close();
            headersBlockBuilder = null;
            return padding != 0 ?
                    new DefaultHttp2HeadersFrame(headersStreamId, headers, Http2Flags.endOfStream(headersFlags),
                            padding) :
                    new DefaultHttp2HeadersFrame(headersStreamId, headers, Http2Flags.endOfStream(headersFlags));
        }
        return null;
    }

    private Http2PriorityFrame readPriorityFrame(ByteBuf payload) throws Http2Exception {
        long word1 = payload.readUnsignedInt();
        boolean exclusive = (word1 & 0x80000000L) != 0;
        int streamDependency = (int) (word1 & 0x7FFFFFFFL);
        if (streamDependency == streamId) {
            throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
        }
        short weight = (short) (payload.readUnsignedByte() + 1);
        return new DefaultHttp2PriorityFrame(streamId, streamDependency, exclusive, weight);
    }

    private Http2ResetStreamFrame readRstStreamFrame(ByteBuf payload) {
        return new DefaultHttp2ResetStreamFrame(streamId, payload.readUnsignedInt());
    }

    private Http2SettingsFrame readSettingsFrame(ByteBuf payload) throws Http2Exception {
        if (Http2Flags.isAck(flags)) {
            return new DefaultHttp2SettingsFrame();
        }
        int numSettings = payloadLength / SETTING_ENTRY_LENGTH;
        Http2Settings settings = new Http2Settings();
        for (int index = 0; index < numSettings; ++index) {
            char id = (char) payload.readUnsignedShort();
            long value = payload.readUnsignedInt();
            try {
                settings.put(id, Long.valueOf(value));
            } catch (IllegalArgumentException e) {
                switch(id) {
                    case SETTINGS_MAX_FRAME_SIZE:
                        throw connectionError(PROTOCOL_ERROR, e, e.getMessage());
                    case SETTINGS_INITIAL_WINDOW_SIZE:
                        throw connectionError(FLOW_CONTROL_ERROR, e, e.getMessage());
                    default:
                        throw connectionError(PROTOCOL_ERROR, e, e.getMessage());
                }
            }
        }
        return new DefaultHttp2SettingsFrame(settings);
    }

    private Http2PushPromiseFrame readPushPromiseFrame(final ChannelHandlerContext ctx, ByteBuf payload,
                                                       int payloadEndIndex) throws Http2Exception {
        final int pushPromiseStreamId = streamId;
        final int padding = readPadding(payload);
        verifyPadding(padding);
        final int promisedStreamId = readUnsignedInt(payload);

        headersBlockBuilder = new HeadersBlockBuilder(pushPromiseStreamId);

        int len = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);
        final boolean endOfHeaders = Http2Flags.endOfHeaders(flags);
        headersBlockBuilder.addFragment(payload, len, ctx.alloc(), endOfHeaders, Http2Flags.endOfStream(flags));
        if (endOfHeaders) {
            final Http2Headers headers = headersBlockBuilder.decodeHeaders();
            headersBlockBuilder.close();
            headersBlockBuilder = null;
            return padding != 0 ?
                    new DefaultHttp2PushPromiseFrame(pushPromiseStreamId, headers, promisedStreamId, padding) :
                    new DefaultHttp2PushPromiseFrame(pushPromiseStreamId, headers, promisedStreamId);
        }
        return null;
    }

    private Http2PingFrame readPingFrame(long data) {
        return new DefaultHttp2PingFrame(Http2Flags.isAck(flags), data);
    }

    private Http2GoAwayFrame readGoAwayFrame(ByteBuf payload, int payloadEndIndex) {
        int lastStreamId = readUnsignedInt(payload);
        long errorCode = payload.readUnsignedInt();
        Buffer debugData = ByteBufAdaptor.extractOrCopy(allocator,
                payload.readSlice(payloadEndIndex - payload.readerIndex()));
        return new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, debugData);
    }

    private Http2WindowUpdateFrame readWindowUpdateFrame(ByteBuf payload)
            throws Http2Exception {
        int windowSizeIncrement = readUnsignedInt(payload);
        if (windowSizeIncrement == 0) {
            throw streamError(streamId, PROTOCOL_ERROR,
                    "Received WINDOW_UPDATE with delta 0 for stream: %d", streamId);
        }
        return new DefaultHttp2WindowUpdateFrame(streamId, windowSizeIncrement);
    }

    private Http2HeadersFrame readContinuationFrame(ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex)
            throws Http2Exception {
        assert headersBlockBuilder != null;
        boolean endOfHeaders = Http2Flags.endOfHeaders(flags);
        headersBlockBuilder.addFragment(payload, payloadEndIndex - payload.readerIndex(), ctx.alloc(),
                endOfHeaders, false);
        DefaultHttp2HeadersFrame frame = null;
        if (endOfHeaders) {
            frame = new DefaultHttp2HeadersFrame(streamId,
                    headersBlockBuilder.decodeHeaders(), headersBlockBuilder.endOfStream);
            headersBlockBuilder.close();
            headersBlockBuilder = null;
        }
        return frame;
    }

    private Http2UnknownFrame readUnknownFrame(ByteBuf payload, int payloadEndIndex) {
        return new DefaultHttp2UnknownFrame(streamId, frameType, flags,
                ByteBufAdaptor.extractOrCopy(allocator, payload.readSlice(payloadEndIndex - payload.readerIndex())));
    }

    /**
     * If padding is present in the payload, reads the next byte as padding. The padding also includes the one byte
     * width of the pad length field. Otherwise, returns zero.
     */
    private int readPadding(ByteBuf payload) {
        if (!Http2Flags.paddingPresent(flags)) {
            return 0;
        }
        return payload.readUnsignedByte() + 1;
    }

    private void verifyPadding(int padding) throws Http2Exception {
        int len = lengthWithoutTrailingPadding(payloadLength, padding);
        if (len < 0) {
            throw connectionError(PROTOCOL_ERROR, "Frame payload too small for padding.");
        }
    }

    /**
     * The padding parameter consists of the 1 byte pad length field and the trailing padding bytes. This method
     * returns the number of readable bytes without the trailing padding.
     */
    private static int lengthWithoutTrailingPadding(int readableBytes, int padding) {
        return padding == 0
                ? readableBytes
                : readableBytes - (padding - 1);
    }

    /**
     * Utility class to help with construction of the headers block that may potentially span
     * multiple frames.
     */
    protected class HeadersBlockBuilder {
        private final int streamId;
        private ByteBuf headerBlock;
        private boolean endOfStream;

        HeadersBlockBuilder(int streamId) {
            this.streamId = streamId;
        }

        /**
         * The local header size maximum has been exceeded while accumulating bytes.
         * @throws Http2Exception A connection error indicating too much data has been received.
         */
        private void headerSizeExceeded() throws Http2Exception {
            close();
            headerListSizeExceeded(headersDecoder.configuration().maxHeaderListSizeGoAway());
        }

        /**
         * Adds a fragment to the block.
         *
         * @param fragment the fragment of the headers block to be added.
         * @param alloc allocator for new blocks if needed.
         * @param endOfHeaders flag indicating whether the current frame is the end of the headers.
         *            This is used for an optimization for when the first fragment is the full
         *            block. In that case, the buffer is used directly without copying.
         * @param endOfStream if the associated header frame has END_STREAM flag set.
         */
        final void addFragment(ByteBuf fragment, int len, ByteBufAllocator alloc, boolean endOfHeaders,
                               boolean endOfStream) throws Http2Exception {
            // if EOS is already set then do not update as continuation frame won't have the flag set.
            this.endOfStream |= endOfStream;
            if (headerBlock == null) {
                if (len > headersDecoder.configuration().maxHeaderListSizeGoAway()) {
                    headerSizeExceeded();
                }
                if (endOfHeaders) {
                    // Optimization - don't bother copying, just use the buffer as-is. Need
                    // to retain since we release when the header block is built.
                    headerBlock = fragment.readRetainedSlice(len);
                } else {
                    headerBlock = alloc.buffer(len).writeBytes(fragment, len);
                }
                return;
            }
            if (headersDecoder.configuration().maxHeaderListSizeGoAway() - len <
                    headerBlock.readableBytes()) {
                headerSizeExceeded();
            }
            if (headerBlock.isWritable(len)) {
                // The buffer can hold the requested bytes, just write it directly.
                headerBlock.writeBytes(fragment, len);
            } else {
                // Allocate a new buffer that is big enough to hold the entire header block so far.
                ByteBuf buf = alloc.buffer(headerBlock.readableBytes() + len);
                buf.writeBytes(headerBlock).writeBytes(fragment, len);
                headerBlock.release();
                headerBlock = buf;
            }
        }

        /**
         * Builds the headers from the completed headers block. After this is called, this builder
         * should not be called again.
         */
        Http2Headers decodeHeaders() throws Http2Exception {
            try {
                return headersDecoder.decodeHeaders(streamId, headerBlock);
            } finally {
                close();
            }
        }

        /**
         * Closes this builder and frees any resources.
         */
        void close() {
            if (headerBlock != null) {
                headerBlock.release();
                headerBlock = null;
            }
        }
    }

    /**
     * Verify that current state is not processing on header block
     * @throws Http2Exception thrown if {@link #headersBlockBuilder} is not null
     */
    private void verifyNotProcessingHeaders() throws Http2Exception {
        if (headersBlockBuilder != null) {
            throw connectionError(PROTOCOL_ERROR, "Received frame of type %s while processing headers on stream %d.",
                                  frameType, headersBlockBuilder.streamId);
        }
    }

    private void verifyAssociatedWithAStream() throws Http2Exception {
        if (streamId == 0) {
            throw connectionError(PROTOCOL_ERROR, "Frame of type %s must be associated with a stream.", frameType);
        }
    }

    private static void verifyStreamOrConnectionId(int streamId, String argumentName)
            throws Http2Exception {
        if (streamId < 0) {
            throw connectionError(PROTOCOL_ERROR, "%s must be >= 0", argumentName);
        }
    }
}
