/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.internal.StringUtil;

import java.util.regex.Pattern;

/**
 * Compresses an {@link HttpMessage} and an {@link HttpContent} in {@code gzip} or
 * {@code deflate} encoding while respecting the {@code "Accept-Encoding"} header.
 * If there is no matching encoding, no compression is done.  For more
 * information on how this handler modifies the message, please refer to
 * {@link HttpContentEncoder}.
 */
public class HttpContentCompressor extends HttpContentEncoder {

    public static final int DEFAULT_COMPRESSION_LEVEL = 6;
    public static final int DEFAULT_WINDOW_BITS = 15;
    public static final int DEFAULT_MEM_LEVEL = 8;
    public static final int DEFAULT_MIN_COMPRESSABLE_CONTENT_LENGTH = 1024;
    public static final Pattern DEFAULT_COMPRESSABLE_CONTENT_TYPES = Pattern.compile("(text/.*|application/json.*)");

    private final int compressionLevel;
    private final int windowBits;
    private final int memLevel;
    private final int minCompressableContentLength;
    private final Pattern compressableContentTypes;

    /**
     * Creates a new handler with the default compression level (<tt>6</tt>),
     * default window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     */
    public HttpContentCompressor() {
        this(DEFAULT_COMPRESSION_LEVEL);
    }

    /**
     * Creates a new handler with the specified compression level, default
     * window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     */
    public HttpContentCompressor(int compressionLevel) {
        this(compressionLevel, DEFAULT_WINDOW_BITS, DEFAULT_MEM_LEVEL);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel) {
        this(compressionLevel,
             windowBits,
             memLevel,
             DEFAULT_MIN_COMPRESSABLE_CONTENT_LENGTH,
             DEFAULT_COMPRESSABLE_CONTENT_TYPES);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public HttpContentCompressor(int compressionLevel,
                                 int windowBits,
                                 int memLevel,
                                 int minCompressableContentLength,
                                 Pattern compressableContentTypes) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel +
                                                       " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException("windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException("memLevel: " + memLevel + " (expected: 1-9)");
        }
        if (compressableContentTypes == null) {
            throw new IllegalArgumentException("compressableContentTypes: must not be null");
        }
        this.compressionLevel = compressionLevel;
        this.windowBits = windowBits;
        this.memLevel = memLevel;
        this.minCompressableContentLength = minCompressableContentLength;
        this.compressableContentTypes = compressableContentTypes;
    }

    @Override
    protected Result beginEncode(HttpResponse response, CharSequence acceptEncoding) throws Exception {
        if (!isCompressable(response)) {
            return null;
        }

        ZlibWrapper wrapper = determineWrapper(acceptEncoding);
        if (wrapper == null) {
            return null;
        }

        String targetContentEncoding;
        switch (wrapper) {
            case GZIP:
                targetContentEncoding = "gzip";
                break;
            case ZLIB:
                targetContentEncoding = "deflate";
                break;
            default:
                throw new Error();
        }

        return new Result(targetContentEncoding,
                          new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(wrapper,
                                                                              compressionLevel,
                                                                              windowBits,
                                                                              memLevel)));
    }

    /**
     * Determines if the given response can and should be compressed.
     *
     * @param response the response to check.
     * @return <tt>true</tt> if the response should be compressed, <tt>false</tt> otherwise.
     */
    protected boolean isCompressable(HttpResponse response) {
        CharSequence contentEncoding = response.headers().get(HttpHeaderNames.CONTENT_ENCODING);
        if (contentEncoding != null && !HttpHeaderValues.IDENTITY.equalsIgnoreCase(contentEncoding)) {
            // Another encoding is already being applied -> no compression
            return false;
        }

        if (!(response instanceof FullHttpResponse)) {
            if (!HttpHeaderValues.CHUNKED.equals(response.headers().get(HttpHeaderNames.TRANSFER_ENCODING))) {
                // We either need a full response or a chunked transfer to turn on compression. If neither of both is
                // present, we risk that the caller sends a i.e. DefaultFileReqion which would bypass our
                // encoder completely resulting in an invalid response.
                return false;
            }
        }

        // Check if it is effective to compress the given content (compressing JPEG images wouldn't be a good idea)
        int contentLength = response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        CharSequence contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        return isCompressionEffective(contentLength, contentType);
    }

    /**
     * Determines if it is effective to compress a request with the given content-length and content-type.
     * <p/>
     * Only requests which are larger than a certain size and contain reasonable compressable content should
     * be compressed. If wouldn't make sense to compress a 100 byte JSON response or a JPEG image. The parameters
     * for this decision are set by <tt>minCompressableContentLength</tt> and <tt>compressableContentTypes</tt>.
     * <p/>
     * This method is made public so that other handlers can decide which kind of response to generate. For example
     * when sending a file it might be better to send it via <code>new HttpChunkedInput(new ChunkedFile(..))</code>
     * which enables compression rather than via <code>new DefaultFileRegion(...)</code> which permits a zero
     * copy transfer - but without compression.
     *
     * @param contentLength the expected length of the content in bytes or <tt>0</tt> if the length is unknown
     * @param contentType   the expected type of the content as mime type (e.g. text/xml) or <tt>null</tt> if
     *                      the type is not yet known
     * @return <tt>true</tt> if it is considered effective to turn on compression, <tt>false</tt> otherwise
     */
    public boolean isCompressionEffective(int contentLength, CharSequence contentType) {
        if (minCompressableContentLength > 0) {
            if (contentLength > 0 && contentLength < minCompressableContentLength) {
                // Content is too small to effectively compress -> no compression
                return false;
            }
        }
        if (contentType != null && !compressableContentTypes.matcher(contentType).matches()) {
            // Content is not compressable -> no compression
            return false;
        }
        return true;
    }

    @SuppressWarnings("FloatingPointEquality")
    protected ZlibWrapper determineWrapper(CharSequence acceptEncoding) {
        float starQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String encoding : StringUtil.split(acceptEncoding.toString(), ',')) {
            float q = 1.0f;
            int equalsPos = encoding.indexOf('=');
            if (equalsPos != -1) {
                try {
                    q = Float.valueOf(encoding.substring(equalsPos + 1));
                } catch (NumberFormatException e) {
                    // Ignore encoding
                    q = 0.0f;
                }
            }
            if (encoding.contains("*")) {
                starQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
        }
        if (gzipQ > 0.0f || deflateQ > 0.0f) {
            if (gzipQ >= deflateQ) {
                return ZlibWrapper.GZIP;
            } else {
                return ZlibWrapper.ZLIB;
            }
        }
        if (starQ > 0.0f) {
            if (gzipQ == -1.0f) {
                return ZlibWrapper.GZIP;
            }
            if (deflateQ == -1.0f) {
                return ZlibWrapper.ZLIB;
            }
        }
        return null;
    }
}
