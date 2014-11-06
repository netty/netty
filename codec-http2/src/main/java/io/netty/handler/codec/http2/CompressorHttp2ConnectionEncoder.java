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

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * A HTTP2 encoder that will compress data frames according to the {@code content-encoding} header for each stream.
 * The compression provided by this class will be applied to the data for the entire stream.
 */
public class CompressorHttp2ConnectionEncoder extends DefaultHttp2ConnectionEncoder {
    private static final Http2ConnectionAdapter CLEAN_UP_LISTENER = new Http2ConnectionAdapter() {
        @Override
        public void streamRemoved(Http2Stream stream) {
            final EmbeddedChannel compressor = stream.compressor();
            if (compressor != null) {
                cleanup(stream, compressor);
            }
        }
    };

    private final int compressionLevel;
    private final int windowBits;
    private final int memLevel;

    /**
     * Builder for new instances of {@link CompressorHttp2ConnectionEncoder}
     */
    public static class Builder extends DefaultHttp2ConnectionEncoder.Builder {
        protected int compressionLevel = 6;
        protected int windowBits = 15;
        protected int memLevel = 8;

        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public Builder windowBits(int windowBits) {
            this.windowBits = windowBits;
            return this;
        }

        public Builder memLevel(int memLevel) {
            this.memLevel = memLevel;
            return this;
        }

        @Override
        public CompressorHttp2ConnectionEncoder build() {
            return new CompressorHttp2ConnectionEncoder(this);
        }
    }

    protected CompressorHttp2ConnectionEncoder(Builder builder) {
        super(builder);
        if (builder.compressionLevel < 0 || builder.compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + builder.compressionLevel + " (expected: 0-9)");
        }
        if (builder.windowBits < 9 || builder.windowBits > 15) {
            throw new IllegalArgumentException("windowBits: " + builder.windowBits + " (expected: 9-15)");
        }
        if (builder.memLevel < 1 || builder.memLevel > 9) {
            throw new IllegalArgumentException("memLevel: " + builder.memLevel + " (expected: 1-9)");
        }
        this.compressionLevel = builder.compressionLevel;
        this.windowBits = builder.windowBits;
        this.memLevel = builder.memLevel;

        connection().addListener(CLEAN_UP_LISTENER);
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, ByteBuf data, int padding,
            final boolean endOfStream, ChannelPromise promise) {
        final Http2Stream stream = connection().stream(streamId);
        final EmbeddedChannel compressor = stream == null ? null : stream.compressor();
        if (compressor == null) {
            // The compressor may be null if no compatible encoding type was found in this stream's headers
            return super.writeData(ctx, streamId, data, padding, endOfStream, promise);
        }

        try {
            // call retain here as it will call release after its written to the channel
            compressor.writeOutbound(data.retain());
            ByteBuf buf = nextReadableBuf(compressor);
            if (buf == null) {
                if (endOfStream) {
                    return super.writeData(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, endOfStream, promise);
                }
                // END_STREAM is not set and the assumption is data is still forthcoming.
                promise.setSuccess();
                return promise;
            }

            ChannelPromiseAggregator aggregator = new ChannelPromiseAggregator(promise);
            for (;;) {
                final ByteBuf nextBuf = nextReadableBuf(compressor);
                final boolean endOfStreamForBuf = nextBuf == null ? endOfStream : false;
                ChannelPromise newPromise = ctx.newPromise();
                aggregator.add(newPromise);

                super.writeData(ctx, streamId, buf, padding, endOfStreamForBuf, newPromise);
                if (nextBuf == null) {
                    break;
                }

                buf = nextBuf;
            }
            return promise;
        } finally {
            if (endOfStream) {
                cleanup(stream, compressor);
            }
        }
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        initCompressor(streamId, headers, endStream);
        return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream, final ChannelPromise promise) {
        initCompressor(streamId, headers, endOfStream);
        return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream,
                promise);
    }

    /**
     * Returns a new {@link EmbeddedChannel} that encodes the HTTP2 message content encoded in the specified
     * {@code contentEncoding}.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return a new {@link ByteToMessageDecoder} if the specified encoding is supported. {@code null} otherwise
     * (alternatively, you can throw a {@link Http2Exception} to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected EmbeddedChannel newContentCompressor(AsciiString contentEncoding) throws Http2Exception {
        if (GZIP.equalsIgnoreCase(contentEncoding) || X_GZIP.equalsIgnoreCase(contentEncoding)) {
            return newCompressionChannel(ZlibWrapper.GZIP);
        }
        if (DEFLATE.equalsIgnoreCase(contentEncoding) || X_DEFLATE.equalsIgnoreCase(contentEncoding)) {
            return newCompressionChannel(ZlibWrapper.ZLIB);
        }
        // 'identity' or unsupported
        return null;
    }

    /**
     * Returns the expected content encoding of the decoded content. Returning {@code contentEncoding} is the default
     * behavior, which is the case for most compressors.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return the expected content encoding of the new content.
     * @throws Http2Exception if the {@code contentEncoding} is not supported and warrants an exception
     */
    protected AsciiString getTargetContentEncoding(AsciiString contentEncoding) throws Http2Exception {
        return contentEncoding;
    }

    /**
     * Generate a new instance of an {@link EmbeddedChannel} capable of compressing data
     * @param wrapper Defines what type of encoder should be used
     */
    private EmbeddedChannel newCompressionChannel(ZlibWrapper wrapper) {
        return new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(wrapper, compressionLevel, windowBits,
                memLevel));
    }

    /**
     * Checks if a new compressor object is needed for the stream identified by {@code streamId}. This method will
     * modify the {@code content-encoding} header contained in {@code headers}.
     *
     * @param streamId The identifier for the headers inside {@code headers}
     * @param headers Object representing headers which are to be written
     * @param endOfStream Indicates if the stream has ended
     */
    private void initCompressor(int streamId, Http2Headers headers, boolean endOfStream) {
        final Http2Stream stream = connection().stream(streamId);
        if (stream == null) {
            return;
        }

        EmbeddedChannel compressor = stream.compressor();
        if (compressor == null) {
            if (!endOfStream) {
                AsciiString encoding = headers.get(CONTENT_ENCODING);
                if (encoding == null) {
                    encoding = IDENTITY;
                }
                try {
                    compressor = newContentCompressor(encoding);
                    if (compressor != null) {
                        AsciiString targetContentEncoding = getTargetContentEncoding(encoding);
                        if (IDENTITY.equalsIgnoreCase(targetContentEncoding)) {
                            headers.remove(CONTENT_ENCODING);
                        } else {
                            headers.set(CONTENT_ENCODING, targetContentEncoding);
                        }
                    }
                } catch (Throwable ignored) {
                    // Ignore
                }
            }
        } else if (endOfStream) {
            cleanup(stream, compressor);
        }

        if (compressor != null) {
            // The content length will be for the decompressed data. Since we will compress the data
            // this content-length will not be correct. Instead of queuing messages or delaying sending
            // header frames...just remove the content-length header
            headers.remove(CONTENT_LENGTH);
        }
    }

    /**
     * Release remaining content from {@link EmbeddedChannel} and remove the compressor from the {@link Http2Stream}.
     *
     * @param stream The stream for which {@code compressor} is the compressor for
     * @param decompressor The compressor for {@code stream}
     */
    private static void cleanup(Http2Stream stream, EmbeddedChannel compressor) {
        if (compressor.finish()) {
            for (;;) {
                final ByteBuf buf = compressor.readOutbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }
        stream.compressor(null);
    }

    /**
     * Read the next compressed {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist.
     *
     * @param decompressor The channel to read from
     * @return The next decoded {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist
     */
    private static ByteBuf nextReadableBuf(EmbeddedChannel compressor) {
        for (;;) {
            final ByteBuf buf = compressor.readOutbound();
            if (buf == null) {
                return null;
            }
            if (!buf.isReadable()) {
                buf.release();
                continue;
            }
            return buf;
        }
    }
}
