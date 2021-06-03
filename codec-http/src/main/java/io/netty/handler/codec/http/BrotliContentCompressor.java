/*
 * Copyright 2021 The Netty Project
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

import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * Extension of {@link HttpContentCompressor} that enables support of Brotli compression
 * along with Gzip and Deflate.
 */
public class BrotliContentCompressor extends HttpContentCompressor {

    private final Encoder.Parameters parameters;

    /**
     * Create new {@link BrotliContentCompressor} Instance
     * with {@link Encoder.Parameters#setQuality(int)} set to 4.
     */
    public BrotliContentCompressor() {
        this(new Encoder.Parameters().setQuality(4));
    }

    /**
     * Create new {@link BrotliContentCompressor} Instance
     *
     * @param parameters {@link Encoder.Parameters} Instance
     */
    public BrotliContentCompressor(Encoder.Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Create new {@link BrotliContentCompressor} Instance
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param parameters       {@link Encoder.Parameters} Instance
     */
    public BrotliContentCompressor(int compressionLevel, Encoder.Parameters parameters) {
        super(compressionLevel);
        this.parameters = parameters;
    }

    /**
     * Create new {@link BrotliContentCompressor} Instance
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
     * @param parameters       {@link Encoder.Parameters} Instance
     */
    public BrotliContentCompressor(int compressionLevel, int windowBits, int memLevel, Encoder.Parameters parameters) {
        super(compressionLevel, windowBits, memLevel);
        this.parameters = parameters;
    }

    /**
     * Create new {@link BrotliContentCompressor} Instance
     *
     * @param compressionLevel     {@code 1} yields the fastest compression and {@code 9} yields the
     *                             best compression.  {@code 0} means no compression.  The default
     *                             compression level is {@code 6}.
     * @param windowBits           The base two logarithm of the size of the history buffer.  The
     *                             value should be in the range {@code 9} to {@code 15} inclusive.
     *                             Larger values result in better compression at the expense of
     *                             memory usage.  The default value is {@code 15}.
     * @param memLevel             How much memory should be allocated for the internal compression
     *                             state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                             memory.  Larger values result in better and faster compression
     *                             at the expense of memory usage.  The default value is {@code 8}
     * @param contentSizeThreshold The response body is compressed when the size of the response
     *                             body exceeds the threshold. The value should be a non negative
     *                             number. {@code 0} will enable compression for all responses.
     * @param parameters           {@link Encoder.Parameters} Instance
     */
    public BrotliContentCompressor(int compressionLevel, int windowBits, int memLevel, int contentSizeThreshold,
                                   Encoder.Parameters parameters) {
        super(compressionLevel, windowBits, memLevel, contentSizeThreshold);
        this.parameters = parameters;
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

        String targetContentEncoding = determineEncoding(acceptEncoding);
        if (targetContentEncoding == null) {
            return null;
        }

        if (targetContentEncoding.equals("gzip")) {
            return new Result(targetContentEncoding,
                    new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                            ctx.channel().config(), ZlibCodecFactory.newZlibEncoder(
                            ZlibWrapper.GZIP, compressionLevel, windowBits, memLevel)));
        } else if (targetContentEncoding.equals("deflate")) {
            return new Result(targetContentEncoding,
                    new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                            ctx.channel().config(), ZlibCodecFactory.newZlibEncoder(
                            ZlibWrapper.ZLIB, compressionLevel, windowBits, memLevel)));
        } else if (targetContentEncoding.equals("br")) {
            return new Result(targetContentEncoding,
                    new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                            ctx.channel().config(), new BrotliEncoder(parameters)));
        } else {
            throw new Error();
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
                return Brotli.isAvailable() ? "br" : null;
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ) {
                return "gzip";
            } else if (deflateQ != -1.0f) {
                return "deflate";
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f) {
                return Brotli.isAvailable() ? "br" : null;
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

    /**
     * Check if {@link Brotli} is available for compression or not.
     *
     * @return Returns {@code true} if {@link Brotli} is available else {@code false}
     */
    public static boolean isAvailable() {
        return Brotli.isAvailable();
    }
}
