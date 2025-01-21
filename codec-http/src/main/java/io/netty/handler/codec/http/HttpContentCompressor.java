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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.BrotliOptions;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.compression.ZstdOptions;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.SnappyOptions;
import io.netty.util.internal.ObjectUtil;

import static io.netty.util.internal.ObjectUtil.checkInRange;

/**
 * Compresses an {@link HttpMessage} and an {@link HttpContent} in {@code gzip} or
 * {@code deflate} encoding while respecting the {@code "Accept-Encoding"} header.
 * If there is no matching encoding, no compression is done.  For more
 * information on how this handler modifies the message, please refer to
 * {@link HttpContentEncoder}.
 */
public class HttpContentCompressor extends HttpContentEncoder {

    private final BrotliOptions brotliOptions;
    private final GzipOptions gzipOptions;
    private final DeflateOptions deflateOptions;
    private final ZstdOptions zstdOptions;
    private final SnappyOptions snappyOptions;

    private final int contentSizeThreshold;
    private ChannelHandlerContext ctx;
    private final Map<String, CompressionEncoderFactory> factories;

    /**
     * Creates a new handler with {@link StandardCompressionOptions#brotli()} (if supported) ,
     * {@link StandardCompressionOptions#zstd()} (if supported), {@link StandardCompressionOptions#snappy()},
     * {@link StandardCompressionOptions#gzip()} and {@link StandardCompressionOptions#deflate()}.
     */
    public HttpContentCompressor() {
        this(0, (CompressionOptions[]) null);
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
     * and memory level.
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
     * and memory level.
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
        this(contentSizeThreshold,
                defaultCompressionOptions(
                    StandardCompressionOptions.gzip(
                            checkInRange(compressionLevel, 0, 9, "compressionLevel"),
                            checkInRange(windowBits, 9, 15, "windowBits"),
                            checkInRange(memLevel, 1, 9, "memLevel")
                    ),
                    StandardCompressionOptions.deflate(
                            checkInRange(compressionLevel, 0, 9, "compressionLevel"),
                            checkInRange(windowBits, 9, 15, "windowBits"),
                            checkInRange(memLevel, 1, 9, "memLevel")
                    )
                )
        );
    }

    /**
     * Create a new {@link HttpContentCompressor} Instance with specified
     * {@link CompressionOptions}s and contentSizeThreshold set to {@code 0}
     *
     * @param compressionOptions {@link CompressionOptions} or {@code null} if the default
     *        should be used.
     */
    public HttpContentCompressor(CompressionOptions... compressionOptions) {
        this(0, compressionOptions);
    }

    /**
     * Create a new {@link HttpContentCompressor} instance with specified
     * {@link CompressionOptions}s
     *
     * @param contentSizeThreshold
     *        The response body is compressed when the size of the response
     *        body exceeds the threshold. The value should be a non negative
     *        number. {@code 0} will enable compression for all responses.
     * @param compressionOptions {@link CompressionOptions} or {@code null}
     *        if the default should be used.
     */
    public HttpContentCompressor(int contentSizeThreshold, CompressionOptions... compressionOptions) {
        this.contentSizeThreshold = ObjectUtil.checkPositiveOrZero(contentSizeThreshold, "contentSizeThreshold");
        BrotliOptions brotliOptions = null;
        GzipOptions gzipOptions = null;
        DeflateOptions deflateOptions = null;
        ZstdOptions zstdOptions = null;
        SnappyOptions snappyOptions = null;
        if (compressionOptions == null || compressionOptions.length == 0) {
            compressionOptions = defaultCompressionOptions(
                    StandardCompressionOptions.gzip(), StandardCompressionOptions.deflate());
        }

        ObjectUtil.deepCheckNotNull("compressionOptions", compressionOptions);
        for (CompressionOptions compressionOption : compressionOptions) {
            // BrotliOptions' class initialization depends on Brotli classes being on the classpath.
            // The Brotli.isAvailable check ensures that BrotliOptions will only get instantiated if Brotli is
            // on the classpath.
            // This results in the static analysis of native-image identifying the instanceof BrotliOptions check
            // and thus BrotliOptions itself as unreachable, enabling native-image to link all classes
            // at build time and not complain about the missing Brotli classes.
            if (Brotli.isAvailable() && compressionOption instanceof BrotliOptions) {
                brotliOptions = (BrotliOptions) compressionOption;
            } else if (compressionOption instanceof GzipOptions) {
                gzipOptions = (GzipOptions) compressionOption;
            } else if (compressionOption instanceof DeflateOptions) {
                deflateOptions = (DeflateOptions) compressionOption;
            } else if (Zstd.isAvailable() && compressionOption instanceof ZstdOptions) {
                zstdOptions = (ZstdOptions) compressionOption;
            } else if (compressionOption instanceof SnappyOptions) {
                snappyOptions = (SnappyOptions) compressionOption;
            } else {
                throw new IllegalArgumentException("Unsupported " + CompressionOptions.class.getSimpleName() +
                        ": " + compressionOption);
            }
        }

        this.gzipOptions = gzipOptions;
        this.deflateOptions = deflateOptions;
        this.brotliOptions = brotliOptions;
        this.zstdOptions = zstdOptions;
        this.snappyOptions = snappyOptions;

        this.factories = new HashMap<String, CompressionEncoderFactory>();

        if (this.gzipOptions != null) {
            this.factories.put("gzip", new GzipEncoderFactory());
        }
        if (this.deflateOptions != null) {
            this.factories.put("deflate", new DeflateEncoderFactory());
        }
        if (Brotli.isAvailable() && this.brotliOptions != null) {
            this.factories.put("br", new BrEncoderFactory());
        }
        if (this.zstdOptions != null) {
            this.factories.put("zstd", new ZstdEncoderFactory());
        }
        if (this.snappyOptions != null) {
            this.factories.put("snappy", new SnappyEncoderFactory());
        }
    }

    private static CompressionOptions[] defaultCompressionOptions(
            GzipOptions gzipOptions, DeflateOptions deflateOptions) {
        List<CompressionOptions> options = new ArrayList<CompressionOptions>(5);
        options.add(gzipOptions);
        options.add(deflateOptions);
        options.add(StandardCompressionOptions.snappy());

        if (Brotli.isAvailable()) {
            options.add(StandardCompressionOptions.brotli());
        }
        if (Zstd.isAvailable()) {
            options.add(StandardCompressionOptions.zstd());
        }
        return options.toArray(new CompressionOptions[0]);
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

        String targetContentEncoding = determineEncoding(acceptEncoding);
        if (targetContentEncoding == null) {
            return null;
        }

        CompressionEncoderFactory encoderFactory = factories.get(targetContentEncoding);

        if (encoderFactory == null) {
            throw new IllegalStateException("Couldn't find CompressionEncoderFactory: " + targetContentEncoding);
        }

        return new Result(targetContentEncoding,
                new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                        ctx.channel().config(), encoderFactory.createEncoder()));
    }

    @SuppressWarnings("FloatingPointEquality")
    protected String determineEncoding(String acceptEncoding) {
        float starQ = -1.0f;
        float brQ = -1.0f;
        float zstdQ = -1.0f;
        float snappyQ = -1.0f;
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
            } else if (encoding.contains("zstd") && q > zstdQ) {
                zstdQ = q;
            } else if (encoding.contains("snappy") && q > snappyQ) {
                snappyQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
        }
        if (brQ > 0.0f || zstdQ > 0.0f || snappyQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= zstdQ && this.brotliOptions != null) {
                return "br";
            } else if (zstdQ != -1.0f && zstdQ >= snappyQ && this.zstdOptions != null) {
                return "zstd";
            } else if (snappyQ != -1.0f && snappyQ >= gzipQ && this.snappyOptions != null) {
                return "snappy";
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ && this.gzipOptions != null) {
                return "gzip";
            } else if (deflateQ != -1.0f && this.deflateOptions != null) {
                return "deflate";
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f && this.brotliOptions != null) {
                return "br";
            }
            if (zstdQ == -1.0f && this.zstdOptions != null) {
                return "zstd";
            }
            if (snappyQ == -1.0f && this.snappyOptions != null) {
                return "snappy";
            }
            if (gzipQ == -1.0f && this.gzipOptions != null) {
                return "gzip";
            }
            if (deflateQ == -1.0f && this.deflateOptions != null) {
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
     * Compression Encoder Factory that creates {@link ZlibEncoder}s
     * used to compress http content for gzip content encoding
     */
    private final class GzipEncoderFactory implements CompressionEncoderFactory {

        @Override
        public MessageToByteEncoder<ByteBuf> createEncoder() {
            return ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.GZIP, gzipOptions.compressionLevel(),
                    gzipOptions.windowBits(), gzipOptions.memLevel());
        }
    }

    /**
     * Compression Encoder Factory that creates {@link ZlibEncoder}s
     * used to compress http content for deflate content encoding
     */
    private final class DeflateEncoderFactory implements CompressionEncoderFactory {

        @Override
        public MessageToByteEncoder<ByteBuf> createEncoder() {
            return ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.ZLIB, deflateOptions.compressionLevel(),
                    deflateOptions.windowBits(), deflateOptions.memLevel());
        }
    }

    /**
     * Compression Encoder Factory that creates {@link BrotliEncoder}s
     * used to compress http content for br content encoding
     */
    private final class BrEncoderFactory implements CompressionEncoderFactory {

        @Override
        public MessageToByteEncoder<ByteBuf> createEncoder() {
            return new BrotliEncoder(brotliOptions.parameters());
        }
    }

    /**
     * Compression Encoder Factory for create {@link ZstdEncoder}
     * used to compress http content for zstd content encoding
     */
    private final class ZstdEncoderFactory implements CompressionEncoderFactory {

        @Override
        public MessageToByteEncoder<ByteBuf> createEncoder() {
            return new ZstdEncoder(zstdOptions.compressionLevel(),
                    zstdOptions.blockSize(), zstdOptions.maxEncodeSize());
        }
    }

    /**
     * Compression Encoder Factory for create {@link SnappyFrameEncoder}
     * used to compress http content for snappy content encoding
     */
    private static final class SnappyEncoderFactory implements CompressionEncoderFactory {

        @Override
        public MessageToByteEncoder<ByteBuf> createEncoder() {
            return new SnappyFrameEncoder();
        }
    }
}
