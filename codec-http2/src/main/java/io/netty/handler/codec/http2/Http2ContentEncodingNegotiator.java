/*
 * Copyright 2026 The Netty Project
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

import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliOptions;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.SnappyOptions;
import io.netty.handler.codec.compression.ZstdOptions;
import io.netty.util.internal.ObjectUtil;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.SNAPPY;
import static io.netty.handler.codec.http.HttpHeaderValues.ZSTD;

/**
 * Negotiates the {@code content-encoding} to apply to an HTTP/2 response based on the request's
 * {@code accept-encoding} header value and the {@link CompressionOptions} configured for the server.
 * <p>
 * This mirrors the algorithm of {@code HttpContentCompressor.determineEncoding(String)}, adapted to operate
 * on {@link CompressionOptions} instances instead of plain encoding names, and to be purely functional
 * (no I/O, no side effects).
 */
final class Http2ContentEncodingNegotiator {

    private BrotliOptions brotliOptions;
    private GzipOptions gzipOptions;
    private DeflateOptions deflateOptions;
    private ZstdOptions zstdOptions;
    private SnappyOptions snappyOptions;

    Http2ContentEncodingNegotiator(CompressionOptions... options) {
        ObjectUtil.checkNotNull(options, "options");
        ObjectUtil.deepCheckNotNull("options", options);

        for (CompressionOptions option : options) {
            // BrotliOptions' class initialization depends on Brotli classes being on the classpath.
            // The Brotli.isAvailable check ensures that BrotliOptions will only get instantiated if Brotli is on
            // the classpath.
            if (Brotli.isAvailable() && option instanceof BrotliOptions) {
                brotliOptions = (BrotliOptions) option;
            } else if (option instanceof GzipOptions) {
                gzipOptions = (GzipOptions) option;
            } else if (option instanceof DeflateOptions) {
                deflateOptions = (DeflateOptions) option;
            } else if (option instanceof ZstdOptions) {
                zstdOptions = (ZstdOptions) option;
            } else if (option instanceof SnappyOptions) {
                snappyOptions = (SnappyOptions) option;
            } else {
                throw new IllegalArgumentException("Unsupported " + CompressionOptions.class.getSimpleName() +
                        ": " + option);
            }
        }
    }

    /**
     * Negotiates the encoding to apply for the given {@code accept-encoding} header value.
     *
     * @param acceptEncoding the request's {@code accept-encoding} header value, may be {@code null}
     * @return the negotiated encoding, or {@code null} if {@code acceptEncoding} is {@code null}, unrecognized,
     *         or does not match any of the encodings configured for this negotiator
     */
    @SuppressWarnings("FloatingPointEquality")
    NegotiatedEncoding negotiate(CharSequence acceptEncoding) {
        if (acceptEncoding == null) {
            return null;
        }
        String value = acceptEncoding.toString();

        float starQ = -1.0f;
        float brQ = -1.0f;
        float zstdQ = -1.0f;
        float snappyQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;

        int start = 0;
        int length = value.length();
        while (start < length) {
            int comma = value.indexOf(',', start);
            if (comma == -1) {
                comma = length;
            }
            String encoding = value.substring(start, comma);
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
            } else if (encoding.contains("zstd") && q > zstdQ) {
                zstdQ = q;
            } else if (encoding.contains("snappy") && q > snappyQ) {
                snappyQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
            start = comma + 1;
        }
        if (brQ > 0.0f || zstdQ > 0.0f || snappyQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= zstdQ && brotliOptions != null) {
                return new NegotiatedEncoding(BR, brotliOptions);
            } else if (zstdQ != -1.0f && zstdQ >= snappyQ && zstdOptions != null) {
                return new NegotiatedEncoding(ZSTD, zstdOptions);
            } else if (snappyQ != -1.0f && snappyQ >= gzipQ && snappyOptions != null) {
                return new NegotiatedEncoding(SNAPPY, snappyOptions);
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ && gzipOptions != null) {
                return new NegotiatedEncoding(GZIP, gzipOptions);
            } else if (deflateQ != -1.0f && deflateOptions != null) {
                return new NegotiatedEncoding(DEFLATE, deflateOptions);
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f && brotliOptions != null) {
                return new NegotiatedEncoding(BR, brotliOptions);
            }
            if (zstdQ == -1.0f && zstdOptions != null) {
                return new NegotiatedEncoding(ZSTD, zstdOptions);
            }
            if (snappyQ == -1.0f && snappyOptions != null) {
                return new NegotiatedEncoding(SNAPPY, snappyOptions);
            }
            if (gzipQ == -1.0f && gzipOptions != null) {
                return new NegotiatedEncoding(GZIP, gzipOptions);
            }
            if (deflateQ == -1.0f && deflateOptions != null) {
                return new NegotiatedEncoding(DEFLATE, deflateOptions);
            }
        }
        return null;
    }
}

/**
 * The result of a successful {@link Http2ContentEncodingNegotiator#negotiate(CharSequence)} call: the
 * {@code content-encoding} value to set on the response, together with the matching {@link CompressionOptions}
 * to configure the compressor with.
 */
final class NegotiatedEncoding {
    final CharSequence contentEncoding;
    final CompressionOptions options;

    NegotiatedEncoding(CharSequence contentEncoding, CompressionOptions options) {
        this.contentEncoding = contentEncoding;
        this.options = options;
    }
}
