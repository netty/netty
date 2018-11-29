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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2FrameReader.Configuration;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.INT_FIELD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PRIORITY_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty.handler.codec.http2.Http2CodecUtil.isMaxFrameSizeValid;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.FRAME_SIZE_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
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

/**
 * A {@link Http2FrameReader} that supports all frame types defined by the HTTP/2 specification.
 */
@UnstableApi
public class DefaultHttp2FrameReader implements Http2FrameReader, Http2FrameSizePolicy, Configuration {
    private final Http2HeadersDecoder headersDecoder;

    /**
     * {@code true} = reading headers, {@code false} = reading payload.
     */
    private boolean readingHeaders = true;
    /**
     * Once set to {@code true} the value will never change. This is set to {@code true} if an unrecoverable error which
     * renders the connection unusable.
     */
    private boolean readError;
    private byte frameType;
    private int streamId;
    private Http2Flags flags;
    private int payloadLength;
    private HeadersContinuation headersContinuation;
    private int maxFrameSize;

    /**
     * Create a new instance.
     * <p>
     * Header names will be validated.
     */
    public DefaultHttp2FrameReader() {
        this(true);
    }

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate headers. {@code false} to not validate headers.
     * @see DefaultHttp2HeadersDecoder(boolean)
     */
    public DefaultHttp2FrameReader(boolean validateHeaders) {
        this(new DefaultHttp2HeadersDecoder(validateHeaders));
    }

    public DefaultHttp2FrameReader(Http2HeadersDecoder headersDecoder) {
        this.headersDecoder = headersDecoder;
        maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    }

    @Override
    public Http2HeadersDecoder.Configuration headersConfiguration() {
        return headersDecoder.configuration();
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    @Override
    public Http2FrameSizePolicy frameSizePolicy() {
        return this;
    }

    @Override
    public void maxFrameSize(int max) throws Http2Exception {
        if (!isMaxFrameSizeValid(max)) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Invalid MAX_FRAME_SIZE specified in sent settings: %d", max);
        }
        maxFrameSize = max;
    }

    @Override
    public int maxFrameSize() {
        return maxFrameSize;
    }

    @Override
    public void close() {
        closeHeadersContinuation();
    }

    private void closeHeadersContinuation() {
        if (headersContinuation != null) {
            headersContinuation.close();
            headersContinuation = null;
        }
    }

    @Override
    public void readFrame(ChannelHandlerContext ctx, ByteBuf input, Http2FrameListener listener)
            throws Http2Exception {
        if (readError) {
            input.skipBytes(input.readableBytes());
            return;
        }
        try {
            do {
                if (readingHeaders) {
                    processHeaderState(input);
                    if (readingHeaders) {
                        // Wait until the entire header has arrived.
                        return;
                    }
                }

                // The header is complete, fall into the next case to process the payload.
                // This is to ensure the proper handling of zero-length payloads. In this
                // case, we don't want to loop around because there may be no more data
                // available, causing us to exit the loop. Instead, we just want to perform
                // the first pass at payload processing now.
                processPayloadState(ctx, input, listener);
                if (!readingHeaders) {
                    // Wait until the entire payload has arrived.
                    return;
                }
            } while (input.isReadable());
        } catch (Http2Exception e) {
            readError = !Http2Exception.isStreamError(e);
            throw e;
        } catch (RuntimeException e) {
            readError = true;
            throw e;
        } catch (Throwable cause) {
            readError = true;
            PlatformDependent.throwException(cause);
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
        flags = new Http2Flags(in.readUnsignedByte());
        streamId = readUnsignedInt(in);

        // We have consumed the data, next time we read we will be expecting to read the frame payload.
        readingHeaders = false;

        switch (frameType) {
            case DATA:
                verifyDataFrame();
                break;
            case HEADERS:
                verifyHeadersFrame();
                break;
            case PRIORITY:
                verifyPriorityFrame();
                break;
            case RST_STREAM:
                verifyRstStreamFrame();
                break;
            case SETTINGS:
                verifySettingsFrame();
                break;
            case PUSH_PROMISE:
                verifyPushPromiseFrame();
                break;
            case PING:
                verifyPingFrame();
                break;
            case GO_AWAY:
                verifyGoAwayFrame();
                break;
            case WINDOW_UPDATE:
                verifyWindowUpdateFrame();
                break;
            case CONTINUATION:
                verifyContinuationFrame();
                break;
            default:
                // Unknown frame type, could be an extension.
                verifyUnknownFrame();
                break;
        }
    }

    private void processPayloadState(ChannelHandlerContext ctx, ByteBuf in, Http2FrameListener listener)
                    throws Http2Exception {
        if (in.readableBytes() < payloadLength) {
            // Wait until the entire payload has been read.
            return;
        }

        // Only process up to payloadLength bytes.
        int payloadEndIndex = in.readerIndex() + payloadLength;

        // We have consumed the data, next time we read we will be expecting to read a frame header.
        readingHeaders = true;

        // Read the payload and fire the frame event to the listener.
        switch (frameType) {
            case DATA:
                readDataFrame(ctx, in, payloadEndIndex, listener);
                break;
            case HEADERS:
                readHeadersFrame(ctx, in, payloadEndIndex, listener);
                break;
            case PRIORITY:
                readPriorityFrame(ctx, in, listener);
                break;
            case RST_STREAM:
                readRstStreamFrame(ctx, in, listener);
                break;
            case SETTINGS:
                readSettingsFrame(ctx, in, listener);
                break;
            case PUSH_PROMISE:
                readPushPromiseFrame(ctx, in, payloadEndIndex, listener);
                break;
            case PING:
                readPingFrame(ctx, in.readLong(), listener);
                break;
            case GO_AWAY:
                readGoAwayFrame(ctx, in, payloadEndIndex, listener);
                break;
            case WINDOW_UPDATE:
                readWindowUpdateFrame(ctx, in, listener);
                break;
            case CONTINUATION:
                readContinuationFrame(in, payloadEndIndex, listener);
                break;
            default:
                readUnknownFrame(ctx, in, payloadEndIndex, listener);
                break;
        }
        in.readerIndex(payloadEndIndex);
    }

    private void verifyDataFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();
        verifyPayloadLength(payloadLength);

        if (payloadLength < flags.getPaddingPresenceFieldLength()) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Frame length %d too small.", payloadLength);
        }
    }

    private void verifyHeadersFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();
        verifyPayloadLength(payloadLength);

        int requiredLength = flags.getPaddingPresenceFieldLength() + flags.getNumPriorityBytes();
        if (payloadLength < requiredLength) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Frame length too small." + payloadLength);
        }
    }

    private void verifyPriorityFrame() throws Http2Exception {
        verifyAssociatedWithAStream();
        verifyNotProcessingHeaders();

        if (payloadLength != PRIORITY_ENTRY_LENGTH) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Invalid frame length %d.", payloadLength);
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
        verifyPayloadLength(payloadLength);
        if (streamId != 0) {
            throw connectionError(PROTOCOL_ERROR, "A stream ID must be zero.");
        }
        if (flags.ack() && payloadLength > 0) {
            throw connectionError(FRAME_SIZE_ERROR, "Ack settings frame must have an empty payload.");
        }
        if (payloadLength % SETTING_ENTRY_LENGTH > 0) {
            throw connectionError(FRAME_SIZE_ERROR, "Frame length %d invalid.", payloadLength);
        }
    }

    private void verifyPushPromiseFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
        verifyPayloadLength(payloadLength);

        // Subtract the length of the promised stream ID field, to determine the length of the
        // rest of the payload (header block fragment + payload).
        int minLength = flags.getPaddingPresenceFieldLength() + INT_FIELD_LENGTH;
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
        verifyPayloadLength(payloadLength);

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
        verifyPayloadLength(payloadLength);

        if (headersContinuation == null) {
            throw connectionError(PROTOCOL_ERROR, "Received %s frame but not currently processing headers.",
                    frameType);
        }

        if (streamId != headersContinuation.getStreamId()) {
            throw connectionError(PROTOCOL_ERROR, "Continuation stream ID does not match pending headers. "
                    + "Expected %d, but received %d.", headersContinuation.getStreamId(), streamId);
        }

        if (payloadLength < flags.getPaddingPresenceFieldLength()) {
            throw streamError(streamId, FRAME_SIZE_ERROR,
                    "Frame length %d too small for padding.", payloadLength);
        }
    }

    private void verifyUnknownFrame() throws Http2Exception {
        verifyNotProcessingHeaders();
    }

    private void readDataFrame(ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex,
            Http2FrameListener listener) throws Http2Exception {
        int padding = readPadding(payload);
        verifyPadding(padding);

        // Determine how much data there is to read by removing the trailing
        // padding.
        int dataLength = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);

        ByteBuf data = payload.readSlice(dataLength);
        listener.onDataRead(ctx, streamId, data, padding, flags.endOfStream());
    }

    private void readHeadersFrame(final ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex,
            Http2FrameListener listener) throws Http2Exception {
        final int headersStreamId = streamId;
        final Http2Flags headersFlags = flags;
        final int padding = readPadding(payload);
        verifyPadding(padding);

        // The callback that is invoked is different depending on whether priority information
        // is present in the headers frame.
        if (flags.priorityPresent()) {
            long word1 = payload.readUnsignedInt();
            final boolean exclusive = (word1 & 0x80000000L) != 0;
            final int streamDependency = (int) (word1 & 0x7FFFFFFFL);
            if (streamDependency == streamId) {
                throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
            }
            final short weight = (short) (payload.readUnsignedByte() + 1);
            final int lenToRead = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);

            // Create a handler that invokes the listener when the header block is complete.
            headersContinuation = new HeadersContinuation() {
                @Override
                public int getStreamId() {
                    return headersStreamId;
                }

                @Override
                public void processFragment(boolean endOfHeaders, ByteBuf fragment, int len,
                        Http2FrameListener listener) throws Http2Exception {
                    final HeadersBlockBuilder hdrBlockBuilder = headersBlockBuilder();
                    hdrBlockBuilder.addFragment(fragment, len, ctx.alloc(), endOfHeaders);
                    if (endOfHeaders) {
                        listener.onHeadersRead(ctx, headersStreamId, hdrBlockBuilder.headers(), streamDependency,
                                weight, exclusive, padding, headersFlags.endOfStream());
                    }
                }
            };

            // Process the initial fragment, invoking the listener's callback if end of headers.
            headersContinuation.processFragment(flags.endOfHeaders(), payload, lenToRead, listener);
            resetHeadersContinuationIfEnd(flags.endOfHeaders());
            return;
        }

        // The priority fields are not present in the frame. Prepare a continuation that invokes
        // the listener callback without priority information.
        headersContinuation = new HeadersContinuation() {
            @Override
            public int getStreamId() {
                return headersStreamId;
            }

            @Override
            public void processFragment(boolean endOfHeaders, ByteBuf fragment, int len,
                    Http2FrameListener listener) throws Http2Exception {
                final HeadersBlockBuilder hdrBlockBuilder = headersBlockBuilder();
                hdrBlockBuilder.addFragment(fragment, len, ctx.alloc(), endOfHeaders);
                if (endOfHeaders) {
                    listener.onHeadersRead(ctx, headersStreamId, hdrBlockBuilder.headers(), padding,
                                    headersFlags.endOfStream());
                }
            }
        };

        // Process the initial fragment, invoking the listener's callback if end of headers.
        int len = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);
        headersContinuation.processFragment(flags.endOfHeaders(), payload, len, listener);
        resetHeadersContinuationIfEnd(flags.endOfHeaders());
    }

    private void resetHeadersContinuationIfEnd(boolean endOfHeaders) {
        if (endOfHeaders) {
            closeHeadersContinuation();
        }
    }

    private void readPriorityFrame(ChannelHandlerContext ctx, ByteBuf payload,
            Http2FrameListener listener) throws Http2Exception {
        long word1 = payload.readUnsignedInt();
        boolean exclusive = (word1 & 0x80000000L) != 0;
        int streamDependency = (int) (word1 & 0x7FFFFFFFL);
        if (streamDependency == streamId) {
            throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
        }
        short weight = (short) (payload.readUnsignedByte() + 1);
        listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
    }

    private void readRstStreamFrame(ChannelHandlerContext ctx, ByteBuf payload,
            Http2FrameListener listener) throws Http2Exception {
        long errorCode = payload.readUnsignedInt();
        listener.onRstStreamRead(ctx, streamId, errorCode);
    }

    private void readSettingsFrame(ChannelHandlerContext ctx, ByteBuf payload,
            Http2FrameListener listener) throws Http2Exception {
        if (flags.ack()) {
            listener.onSettingsAckRead(ctx);
        } else {
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
            listener.onSettingsRead(ctx, settings);
        }
    }

    private void readPushPromiseFrame(final ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex,
            Http2FrameListener listener) throws Http2Exception {
        final int pushPromiseStreamId = streamId;
        final int padding = readPadding(payload);
        verifyPadding(padding);
        final int promisedStreamId = readUnsignedInt(payload);

        // Create a handler that invokes the listener when the header block is complete.
        headersContinuation = new HeadersContinuation() {
            @Override
            public int getStreamId() {
                return pushPromiseStreamId;
            }

            @Override
            public void processFragment(boolean endOfHeaders, ByteBuf fragment, int len,
                    Http2FrameListener listener) throws Http2Exception {
                headersBlockBuilder().addFragment(fragment, len, ctx.alloc(), endOfHeaders);
                if (endOfHeaders) {
                    listener.onPushPromiseRead(ctx, pushPromiseStreamId, promisedStreamId,
                            headersBlockBuilder().headers(), padding);
                }
            }
        };

        // Process the initial fragment, invoking the listener's callback if end of headers.
        int len = lengthWithoutTrailingPadding(payloadEndIndex - payload.readerIndex(), padding);
        headersContinuation.processFragment(flags.endOfHeaders(), payload, len, listener);
        resetHeadersContinuationIfEnd(flags.endOfHeaders());
    }

    private void readPingFrame(ChannelHandlerContext ctx, long data,
            Http2FrameListener listener) throws Http2Exception {
        if (flags.ack()) {
            listener.onPingAckRead(ctx, data);
        } else {
            listener.onPingRead(ctx, data);
        }
    }

    private static void readGoAwayFrame(ChannelHandlerContext ctx, ByteBuf payload, int payloadEndIndex,
            Http2FrameListener listener) throws Http2Exception {
        int lastStreamId = readUnsignedInt(payload);
        long errorCode = payload.readUnsignedInt();
        ByteBuf debugData = payload.readSlice(payloadEndIndex - payload.readerIndex());
        listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
    }

    private void readWindowUpdateFrame(ChannelHandlerContext ctx, ByteBuf payload,
            Http2FrameListener listener) throws Http2Exception {
        int windowSizeIncrement = readUnsignedInt(payload);
        if (windowSizeIncrement == 0) {
            throw streamError(streamId, PROTOCOL_ERROR,
                    "Received WINDOW_UPDATE with delta 0 for stream: %d", streamId);
        }
        listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
    }

    private void readContinuationFrame(ByteBuf payload, int payloadEndIndex, Http2FrameListener listener)
            throws Http2Exception {
        // Process the initial fragment, invoking the listener's callback if end of headers.
        headersContinuation.processFragment(flags.endOfHeaders(), payload,
                payloadEndIndex - payload.readerIndex(), listener);
        resetHeadersContinuationIfEnd(flags.endOfHeaders());
    }

    private void readUnknownFrame(ChannelHandlerContext ctx, ByteBuf payload,
            int payloadEndIndex, Http2FrameListener listener) throws Http2Exception {
        payload = payload.readSlice(payloadEndIndex - payload.readerIndex());
        listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
    }

    /**
     * If padding is present in the payload, reads the next byte as padding. The padding also includes the one byte
     * width of the pad length field. Otherwise, returns zero.
     */
    private int readPadding(ByteBuf payload) {
        if (!flags.paddingPresent()) {
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
     * Base class for processing of HEADERS and PUSH_PROMISE header blocks that potentially span
     * multiple frames. The implementation of this interface will perform the final callback to the
     * {@link Http2FrameListener} once the end of headers is reached.
     */
    private abstract class HeadersContinuation {
        private final HeadersBlockBuilder builder = new HeadersBlockBuilder();

        /**
         * Returns the stream for which headers are currently being processed.
         */
        abstract int getStreamId();

        /**
         * Processes the next fragment for the current header block.
         *
         * @param endOfHeaders whether the fragment is the last in the header block.
         * @param fragment the fragment of the header block to be added.
         * @param listener the listener to be notified if the header block is completed.
         */
        abstract void processFragment(boolean endOfHeaders, ByteBuf fragment, int len,
                Http2FrameListener listener) throws Http2Exception;

        final HeadersBlockBuilder headersBlockBuilder() {
            return builder;
        }

        /**
         * Free any allocated resources.
         */
        final void close() {
            builder.close();
        }
    }

    /**
     * Utility class to help with construction of the headers block that may potentially span
     * multiple frames.
     */
    protected class HeadersBlockBuilder {
        private ByteBuf headerBlock;

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
         */
        final void addFragment(ByteBuf fragment, int len, ByteBufAllocator alloc,
                boolean endOfHeaders) throws Http2Exception {
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
        Http2Headers headers() throws Http2Exception {
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

            // Clear the member variable pointing at this instance.
            headersContinuation = null;
        }
    }

    /**
     * Verify that current state is not processing on header block
     * @throws Http2Exception thrown if {@link #headersContinuation} is not null
     */
    private void verifyNotProcessingHeaders() throws Http2Exception {
        if (headersContinuation != null) {
            throw connectionError(PROTOCOL_ERROR, "Received frame of type %s while processing headers on stream %d.",
                                  frameType, headersContinuation.getStreamId());
        }
    }

    private void verifyPayloadLength(int payloadLength) throws Http2Exception {
        if (payloadLength > maxFrameSize) {
            throw connectionError(PROTOCOL_ERROR, "Total payload length %d exceeds max frame length.", payloadLength);
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
