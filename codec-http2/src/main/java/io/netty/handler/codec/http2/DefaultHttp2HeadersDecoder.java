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
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

@UnstableApi
public class DefaultHttp2HeadersDecoder implements Http2HeadersDecoder, Http2HeadersDecoder.Configuration {
    private static final float HEADERS_COUNT_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_COUNT_WEIGHT_HISTORICAL = 1 - HEADERS_COUNT_WEIGHT_NEW;

    private final HpackDecoder hpackDecoder;
    private final boolean validateHeaders;
    private long maxHeaderListSizeGoAway;

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
        this(validateHeaders, maxHeaderListSize, /* initialHuffmanDecodeCapacity= */ -1);
    }

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate headers are valid according to the RFC.
     * @param maxHeaderListSize This is the only setting that can be configured before notifying the peer.
     *  This is because <a href="https://tools.ietf.org/html/rfc7540#section-6.5.1">SETTINGS_MAX_HEADER_LIST_SIZE</a>
     *  allows a lower than advertised limit from being enforced, and the default limit is unlimited
     *  (which is dangerous).
     * @param initialHuffmanDecodeCapacity Does nothing, do not use.
     */
    public DefaultHttp2HeadersDecoder(boolean validateHeaders, long maxHeaderListSize,
                                      @Deprecated int initialHuffmanDecodeCapacity) {
        this(validateHeaders, new HpackDecoder(maxHeaderListSize));
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overridden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    DefaultHttp2HeadersDecoder(boolean validateHeaders, HpackDecoder hpackDecoder) {
        this.hpackDecoder = ObjectUtil.checkNotNull(hpackDecoder, "hpackDecoder");
        this.validateHeaders = validateHeaders;
        this.maxHeaderListSizeGoAway =
                Http2CodecUtil.calculateMaxHeaderListSizeGoAway(hpackDecoder.getMaxHeaderListSize());
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
    public void maxHeaderListSize(long max, long goAwayMax) throws Http2Exception {
        if (goAwayMax < max || goAwayMax < 0) {
            throw connectionError(INTERNAL_ERROR, "Header List Size GO_AWAY %d must be non-negative and >= %d",
                    goAwayMax, max);
        }
        hpackDecoder.setMaxHeaderListSize(max);
        this.maxHeaderListSizeGoAway = goAwayMax;
    }

    @Override
    public long maxHeaderListSize() {
        return hpackDecoder.getMaxHeaderListSize();
    }

    @Override
    public long maxHeaderListSizeGoAway() {
        return maxHeaderListSizeGoAway;
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    @Override
    public Http2Headers decodeHeaders(int streamId, ByteBuf headerBlock) throws Http2Exception {
        try {
            final Http2Headers headers = newHeaders();
            hpackDecoder.decode(streamId, headerBlock, headers, validateHeaders);
            headerArraySizeAccumulator = HEADERS_COUNT_WEIGHT_NEW * headers.size() +
                                         HEADERS_COUNT_WEIGHT_HISTORICAL * headerArraySizeAccumulator;
            return headers;
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable e) {
            // Default handler for any other types of errors that may have occurred. For example,
            // the Header builder throws IllegalArgumentException if the key or value was invalid
            // for any reason (e.g. the key was an invalid pseudo-header).
            throw connectionError(COMPRESSION_ERROR, e, e.getMessage());
        }
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
}
