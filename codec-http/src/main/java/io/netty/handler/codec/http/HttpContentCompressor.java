/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.compression.BrotliCompressionOptions;
import io.netty.handler.codec.http.compression.CompressionOptions;
import io.netty.handler.codec.http.compression.DeflateCompressionOptions;
import io.netty.handler.codec.http.compression.GzipCompressionOptions;
import io.netty.util.internal.ObjectUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Compresses an {@link HttpMessage} and an {@link HttpContent} in {@code gzip} or
 * {@code deflate} encoding while respecting the {@code "Accept-Encoding"} header.
 * If there is no matching encoding, no compression is done.  For more
 * information on how this handler modifies the message, please refer to
 * {@link HttpContentEncoder}.
 */
public class HttpContentCompressor extends HttpContentEncoder {

    private final boolean supportsCompressionOptions;

    private BrotliCompressionOptions brotliCompressionOptions;
    private GzipCompressionOptions gzipCompressionOptions;
    private DeflateCompressionOptions deflateCompressionOptions;

    private int compressionLevel;
    private int windowBits;
    private int memLevel;
    private int contentSizeThreshold;
    private ChannelHandlerContext ctx;

    /**
     * Create a new {@link HttpContentCompressor} Instancewith default {@link BrotliCompressionOptions#DEFAULT},
     * {@link GzipCompressionOptions#DEFAULT} and {@link DeflateCompressionOptions#DEFAULT}
     */
    public HttpContentCompressor() {
        brotliCompressionOptions = BrotliCompressionOptions.DEFAULT;
        gzipCompressionOptions = GzipCompressionOptions.DEFAULT;
        deflateCompressionOptions = DeflateCompressionOptions.DEFAULT;
        supportsCompressionOptions = true;
    }

    /**
     * Creates a new handler with the specified compression level, default
     * window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     */
    @Deprecated
    public HttpContentCompressor(int compressionLevel) {
        this(compressionLevel, 15, 8, 0);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param windowBits
     *        The base two logarithm of the size of the history buffer.  The
     *        value should be in the range {@code 9} to {@code 15} inclusive.
     *        Larger values result in better compression at the expense of
     *        memory usage.  The default value is {@code 15}.
     * @param memLevel
     *        How much memory should be allocated for the internal compression
     *        state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *        memory.  Larger values result in better and faster compression
     *        at the expense of memory usage.  The default value is {@code 8}
     */
    @Deprecated
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel) {
        this(compressionLevel, windowBits, memLevel, 0);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param windowBits
     *        The base two logarithm of the size of the history buffer.  The
     *        value should be in the range {@code 9} to {@code 15} inclusive.
     *        Larger values result in better compression at the expense of
     *        memory usage.  The default value is {@code 15}.
     * @param memLevel
     *        How much memory should be allocated for the internal compression
     *        state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *        memory.  Larger values result in better and faster compression
     *        at the expense of memory usage.  The default value is {@code 8}
     * @param contentSizeThreshold
     *        The response body is compressed when the size of the response
     *        body exceeds the threshold. The value should be a non negative
     *        number. {@code 0} will enable compression for all responses.
     */
    @Deprecated
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel, int contentSizeThreshold) {
        this.compressionLevel = ObjectUtil.checkInRange(compressionLevel, 0, 9, "compressionLevel");
        this.windowBits = ObjectUtil.checkInRange(windowBits, 9, 15, "windowBits");
        this.memLevel = ObjectUtil.checkInRange(memLevel, 1, 9, "memLevel");
        this.contentSizeThreshold = ObjectUtil.checkPositiveOrZero(contentSizeThreshold, "contentSizeThreshold");
        supportsCompressionOptions = false;
    }

    /**
     * Create a new {@link HttpContentCompressor} Instance with specified
     * {@link CompressionOptions}s and ContentSizeThreshold set to {@code 0}
     *
     * @param compressionOptions {@link CompressionOptions} Instance(s)
     */
    public HttpContentCompressor(CompressionOptions... compressionOptions) {
        this(0, compressionOptions);
    }

    /**
     * Create a new {@link HttpContentCompressor} Instance with specified
     * {@link CompressionOptions}s
     *
     * @param compressionOptions {@link CompressionOptions} Instance(s)
     */
    public HttpContentCompressor(int contentSizeThreshold, CompressionOptions... compressionOptions) {
        this(Arrays.asList(ObjectUtil.checkNotNull(compressionOptions, "CompressionOptions")));
    }

    /**
     * Create a new {@link HttpContentCompressor} Instance with specified
     * {@link CompressionOptions}s and ContentSizeThreshold set to {@code 0}
     *
     * @param compressionOptionsCollection {@link Collection<CompressionOptions>} Instance
     */
    public HttpContentCompressor(Collection<CompressionOptions> compressionOptionsCollection) {
        this(0, compressionOptionsCollection);
    }

    /**
     * Create a new {@link HttpContentCompressor} Instance with specified
     * {@link CompressionOptions}s
     *
     * @param compressionOptionsCollection {@link Collection<CompressionOptions>} Instance
     */
    public HttpContentCompressor(int contentSizeThreshold,
                                 Collection<CompressionOptions> compressionOptionsCollection) {

        this.contentSizeThreshold = ObjectUtil.checkPositiveOrZero(contentSizeThreshold, "contentSizeThreshold");
        ObjectUtil.checkNotNull(compressionOptionsCollection, "CompressionOptions");
        ObjectUtil.checkNonEmpty(compressionOptionsCollection, "CompressionOptions");

        for (CompressionOptions compressionOptions : compressionOptionsCollection) {
            if (compressionOptions == null) {
                continue; // Skip null
            }

            if (compressionOptions instanceof BrotliCompressionOptions) {
                brotliCompressionOptions = (BrotliCompressionOptions) compressionOptions;
            } else if (compressionOptions instanceof GzipCompressionOptions) {
                gzipCompressionOptions = (GzipCompressionOptions) compressionOptions;
            } else if (compressionOptions instanceof DeflateCompressionOptions) {
                deflateCompressionOptions = (DeflateCompressionOptions) compressionOptions;
            } else {
                throw new IllegalArgumentException("Unsupported CompressionOption: " + compressionOptions);
            }
        }

        supportsCompressionOptions = true;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected Result beginEncode(HttpResponse httpResponse, String acceptEncoding) throws Exception {
        if (this.contentSizeThreshold > 0) {
            if (httpResponse instanceof HttpContent &&
                    ((HttpContent) httpResponse).content().readableBytes() < contentSizeThreshold) {
                return null;
            }
        }

        String contentEncoding = httpResponse.headers().get(HttpHeaderNames.CONTENT_ENCODING);
        if (contentEncoding != null) {
            // Content-Encoding was set, either as something specific or as the IDENTITY encoding
            // Therefore, we should NOT encode here
            return null;
        }

        if (supportsCompressionOptions) {
            String targetContentEncoding = determineEncoding(acceptEncoding);
            if (targetContentEncoding == null) {
                return null;
            }

            if (targetContentEncoding.equals("gzip")) {
                return new Result(targetContentEncoding,
                        new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                                ctx.channel().config(), ZlibCodecFactory.newZlibEncoder(
                                ZlibWrapper.GZIP, gzipCompressionOptions.compressionLevel()
                                , gzipCompressionOptions.windowBits(), gzipCompressionOptions.memLevel())));
            } else if (targetContentEncoding.equals("deflate")) {
                return new Result(targetContentEncoding,
                        new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                                ctx.channel().config(), ZlibCodecFactory.newZlibEncoder(
                                ZlibWrapper.ZLIB, deflateCompressionOptions.compressionLevel(),
                                deflateCompressionOptions.windowBits(), deflateCompressionOptions.memLevel())));
            } else if (targetContentEncoding.equals("br")) {
                return new Result(targetContentEncoding,
                        new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                                ctx.channel().config(), new BrotliEncoder(brotliCompressionOptions.parameters())));
            } else {
                throw new Error();
            }
        } else {
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

            return new Result(
                    targetContentEncoding,
                    new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                            ctx.channel().config(), ZlibCodecFactory.newZlibEncoder(
                            wrapper, compressionLevel, windowBits, memLevel)));
        }
    }

    @SuppressWarnings("FloatingPointEquality")
    protected String determineEncoding(String acceptEncoding) {
        float starQ = -1.0f;
        float brQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String encoding : acceptEncoding.split(",")) {
            float q = 1.0f;
            int equalsPos = encoding.indexOf('=');
            if (equalsPos != -1) {
                try {
                    q = Float.parseFloat(encoding.substring(equalsPos + 1));
                } catch (NumberFormatException e) {
                    // Ignore encoding
                    q = 0.0f;
                }
            }
            if (encoding.contains("*")) {
                starQ = q;
            } else if (encoding.contains("br") && q > brQ) {
                brQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
        }
        if (brQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= gzipQ) {
                return isAvailable() ? "br" : null;
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ) {
                return "gzip";
            } else if (deflateQ != -1.0f) {
                return "deflate";
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f) {
                return isAvailable() ? "br" : null;
            }
            if (gzipQ == -1.0f) {
                return "gzip";
            }
            if (deflateQ == -1.0f) {
                return "deflate";
            }
        }
        return null;
    }

    @Deprecated
    @SuppressWarnings("FloatingPointEquality")
    protected ZlibWrapper determineWrapper(String acceptEncoding) {
        float starQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String encoding : acceptEncoding.split(",")) {
            float q = 1.0f;
            int equalsPos = encoding.indexOf('=');
            if (equalsPos != -1) {
                try {
                    q = Float.parseFloat(encoding.substring(equalsPos + 1));
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

    /**
     * Check if {@link Brotli} is available for compression or not.
     *
     * @return Returns {@code true} if {@link Brotli} is available else {@code false}
     */
    public static boolean isAvailable() {
        return Brotli.isAvailable();
    }
}
