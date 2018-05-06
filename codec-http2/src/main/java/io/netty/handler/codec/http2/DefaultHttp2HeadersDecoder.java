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
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_INITIAL_HUFFMAN_DECODE_CAPACITY;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty.util.internal.ObjectUtil.checkPositive;

@UnstableApi
public class DefaultHttp2HeadersDecoder implements Http2HeadersDecoder, Http2HeadersDecoder.Configuration {
    private static final float HEADERS_COUNT_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_COUNT_WEIGHT_HISTORICAL = 1 - HEADERS_COUNT_WEIGHT_NEW;

    private final HpackDecoder hpackDecoder;
    private final boolean validateHeaders;
    private long maxHeaderListSize;
    private Http2HeadersSink sink;

    /**
     * Used to calculate an exponential moving average of header sizes to get an estimate of how large the data
     * structure for storing headers should be.
     */
    private float headerArraySizeAccumulator = 8;

    public DefaultHttp2HeadersDecoder() {
        this(true);
    }

    public DefaultHttp2HeadersDecoder(boolean validateHeaders) {
        this(validateHeaders, DEFAULT_HEADER_LIST_SIZE);
    }

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate headers are valid according to the RFC.
     * @param maxHeaderListSize This is the only setting that can be configured before notifying the peer.
     *  This is because <a href="https://tools.ietf.org/html/rfc7540#section-6.5.1">SETTINGS_MAX_HEADER_LIST_SIZE</a>
     *  allows a lower than advertised limit from being enforced, and the default limit is unlimited
     *  (which is dangerous).
     */
    public DefaultHttp2HeadersDecoder(boolean validateHeaders, long maxHeaderListSize) {
        this(validateHeaders, maxHeaderListSize, DEFAULT_INITIAL_HUFFMAN_DECODE_CAPACITY);
    }

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate headers are valid according to the RFC.
     * @param maxHeaderListSize This is the only setting that can be configured before notifying the peer.
     *  This is because <a href="https://tools.ietf.org/html/rfc7540#section-6.5.1">SETTINGS_MAX_HEADER_LIST_SIZE</a>
     *  allows a lower than advertised limit from being enforced, and the default limit is unlimited
     *  (which is dangerous).
     * @param initialHuffmanDecodeCapacity Size of an intermediate buffer used during huffman decode.
     */
    public DefaultHttp2HeadersDecoder(boolean validateHeaders, long maxHeaderListSize,
                                      int initialHuffmanDecodeCapacity) {
        this(validateHeaders, maxHeaderListSize, new HpackDecoder(initialHuffmanDecodeCapacity));
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overridden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    DefaultHttp2HeadersDecoder(boolean validateHeaders, long maxHeaderListSize, HpackDecoder hpackDecoder) {
        this.hpackDecoder = ObjectUtil.checkNotNull(hpackDecoder, "hpackDecoder");
        this.validateHeaders = validateHeaders;
        this.maxHeaderListSize = checkPositive(maxHeaderListSize, "maxHeaderListSize");
    }

    @Override
    public void maxHeaderTableSize(long max) throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(max);
    }

    @Override
    public long maxHeaderTableSize() {
        return hpackDecoder.getMaxHeaderTableSize();
    }

    @Override
    public void maxHeaderListSize(long max) throws Http2Exception {
        if (max < MIN_HEADER_LIST_SIZE || max > MAX_HEADER_LIST_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header List Size must be >= %d and <= %d but was %d",
                    MIN_HEADER_LIST_SIZE, MAX_HEADER_LIST_SIZE, max);
        }
        this.maxHeaderListSize = max;
    }

    @Override
    public long maxHeaderListSize() {
        return maxHeaderListSize;
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    @Override
    public Http2Headers decodeHeadersStart(int streamId, ByteBuf headersBlock, boolean endHeaders)
            throws Http2Exception {
        if (sink != null) {
            throw new IllegalStateException("Previous headers decoding did not complete with endHeaders=true");
        }
        sink = new Http2HeadersSink(newHeaders(), maxHeaderListSize, validateHeaders, streamId);
        return decodeHeadersContinue(headersBlock, endHeaders);
    }

    @Override
    public Http2Headers decodeHeadersContinue(ByteBuf headerBlock, boolean endHeaders) throws Http2Exception {
        if (sink == null) {
            throw new IllegalStateException("Failed to call decodeHeadersStart before decodeHeadersContinue");
        }
        try {
            hpackDecoder.decode(headerBlock, sink);
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable e) {
            // Default handler for any other types of errors that may have occurred. For example,
            // the Header builder throws IllegalArgumentException if the key or value was invalid
            // for any reason (e.g. the key was an invalid pseudo-header).
            throw connectionError(COMPRESSION_ERROR, e, e.getMessage());
        }

        if (!endHeaders) {
            return null;
        }
        hpackDecoder.checkDecodeComplete();
        // we have read all of our headers. See if we have exceeded our maxHeaderListSize. We must
        // delay throwing until this point to prevent dynamic table corruption
        sink.checkExceededMaxLength();
        Http2Headers headers = sink.headers();
        headerArraySizeAccumulator = HEADERS_COUNT_WEIGHT_NEW * headers.size() +
                                     HEADERS_COUNT_WEIGHT_HISTORICAL * headerArraySizeAccumulator;
        sink = null;
        return headers;
    }

    /**
     * A weighted moving average estimating how many headers are expected during the decode process.
     * @return an estimate of how many headers are expected during the decode process.
     */
    protected final int numberOfHeadersGuess() {
        return (int) headerArraySizeAccumulator;
    }

    /**
     * Determines if the headers should be validated as a result of the decode operation.
     * @return {@code true} if the headers should be validated as a result of the decode operation.
     */
    protected final boolean validateHeaders() {
        return validateHeaders;
    }

    /**
     * Create a new {@link Http2Headers} object which will store the results of the decode operation.
     * @return a new {@link Http2Headers} object which will store the results of the decode operation.
     */
    protected Http2Headers newHeaders() {
        return new DefaultHttp2Headers(validateHeaders, (int) headerArraySizeAccumulator);
    }

    /**
     * HTTP/2 header types.
     */
    private enum HeaderType {
        REGULAR_HEADER,
        REQUEST_PSEUDO_HEADER,
        RESPONSE_PSEUDO_HEADER
    }

    private static class Http2HeadersSink implements HpackDecoder.Sink {
        private final Http2Headers headers;
        private final long maxHeaderListSize;
        private final boolean validateHeaders;
        private final int streamId;

        private long headersLength;
        private boolean exceededMaxLength;
        private HeaderType headerType;

        public Http2HeadersSink(Http2Headers headers, long maxHeaderListSize, boolean validateHeaders, int streamId) {
            this.headers = headers;
            this.maxHeaderListSize = maxHeaderListSize;
            this.validateHeaders = validateHeaders;
            this.streamId = streamId;
        }

        @Override
        public boolean triggersExceededSizeLimit(long length) {
            boolean exceeds = headersLength + length > maxHeaderListSize;
            if (exceeds) {
                exceededMaxLength = true;
            }
            return exceeds;
        }

        @Override
        public void appendToHeaderList(CharSequence name, CharSequence value) throws Http2Exception {
            headersLength += HpackHeaderField.sizeOf(name, value);
            if (headersLength > maxHeaderListSize) {
                exceededMaxLength = true;
            }
            if (validateHeaders) {
                headerType = validate(name, headerType);
            }
            if (!exceededMaxLength) {
                headers.add(name, value);
            }
        }

        public void checkExceededMaxLength() throws Http2Exception {
            if (exceededMaxLength) {
                headerListSizeExceeded(streamId, maxHeaderListSize, true);
            }
        }

        public Http2Headers headers() {
            return headers;
        }

        private HeaderType validate(CharSequence name, HeaderType previousHeaderType) throws Http2Exception {
            if (hasPseudoHeaderFormat(name)) {
                if (previousHeaderType == HeaderType.REGULAR_HEADER) {
                    throw connectionError(PROTOCOL_ERROR, "Pseudo-header field '%s' found after regular header.", name);
                }

                final Http2Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
                if (pseudoHeader == null) {
                    throw connectionError(PROTOCOL_ERROR, "Invalid HTTP/2 pseudo-header '%s' encountered.", name);
                }

                final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                        HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
                if (previousHeaderType != null && currentHeaderType != previousHeaderType) {
                    throw connectionError(PROTOCOL_ERROR, "Mix of request and response pseudo-headers.");
                }

                return currentHeaderType;
            }

            return HeaderType.REGULAR_HEADER;
        }
    }
}
