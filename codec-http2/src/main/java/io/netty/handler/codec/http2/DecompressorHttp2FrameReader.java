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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * A HTTP2 frame reader that will decompress data frames according
 * to the {@code content-encoding} header for each stream.
 */
public class DecompressorHttp2FrameReader extends DefaultHttp2FrameReader {
    private static final AsciiString CONTENT_ENCODING_LOWER_CASE = HttpHeaders.Names.CONTENT_ENCODING.toLowerCase();
    private static final AsciiString CONTENT_LENGTH_LOWER_CASE = HttpHeaders.Names.CONTENT_LENGTH.toLowerCase();
    private static final Http2ConnectionAdapter CLEAN_UP_LISTENER = new Http2ConnectionAdapter() {
        @Override
        public void streamRemoved(Http2Stream stream) {
            final EmbeddedChannel decoder = stream.decompressor();
            if (decoder != null) {
                cleanup(stream, decoder);
            }
        }
    };

    private final Http2Connection connection;
    private final boolean strict;

    /**
     * Create a new instance with non-strict deflate decoding.
     * {@link #DecompressorHttp2FrameReader(Http2Connection, boolean)}
     */
    public DecompressorHttp2FrameReader(Http2Connection connection) {
        this(connection, false);
    }

    /**
     * Create a new instance.
     * @param strict
     * <ul>
     * <li>{@code true} to use use strict handling of deflate if used</li>
     * <li>{@code false} be more lenient with decompression</li>
     * </ul>
     */
    public DecompressorHttp2FrameReader(Http2Connection connection, boolean strict) {
        this.connection = connection;
        this.strict = strict;

        connection.addListener(CLEAN_UP_LISTENER);
    }

    /**
     * Returns a new {@link EmbeddedChannel} that decodes the HTTP2 message
     * content encoded in the specified {@code contentEncoding}.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return a new {@link ByteToMessageDecoder} if the specified encoding is supported.
     *         {@code null} otherwise (alternatively, you can throw a {@link Http2Exception}
     *         to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected EmbeddedChannel newContentDecoder(CharSequence contentEncoding) throws Http2Exception {
        if (HttpHeaders.Values.GZIP.equalsIgnoreCase(contentEncoding) ||
            HttpHeaders.Values.XGZIP.equalsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        }
        if (HttpHeaders.Values.DEFLATE.equalsIgnoreCase(contentEncoding) ||
            HttpHeaders.Values.XDEFLATE.equalsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            // To be strict, 'deflate' means ZLIB, but some servers were not implemented correctly.
            return new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(wrapper));
        }
        // 'identity' or unsupported
        return null;
    }

    /**
     * Returns the expected content encoding of the decoded content.
     * This getMethod returns {@code "identity"} by default, which is the case for
     * most decoders.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return the expected content encoding of the new content.
     * @throws Http2Exception if the {@code contentEncoding} is not supported and warrants an exception
     */
    protected CharSequence getTargetContentEncoding(
            @SuppressWarnings("UnusedParameters") CharSequence contentEncoding) throws Http2Exception {
        return HttpHeaders.Values.IDENTITY;
    }

    /**
     * Checks if a new decoder object is needed for the stream identified by {@code streamId}.
     * This method will modify the {@code content-encoding} header contained in {@code builder}.
     * @param streamId The identifier for the headers inside {@code builder}
     * @param builder Object representing headers which have been read
     * @param endOfStream Indicates if the stream has ended
     * @throws Http2Exception If the {@code content-encoding} is not supported
     */
    private void initDecoder(int streamId, Http2Headers.Builder builder, boolean endOfStream)
            throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        if (stream != null) {
            EmbeddedChannel decoder = stream.decompressor();
            if (decoder == null) {
                if (!endOfStream) {
                    // Determine the content encoding.
                    CharSequence contentEncoding = builder.get(CONTENT_ENCODING_LOWER_CASE);
                    if (contentEncoding == null) {
                        contentEncoding = HttpHeaders.Values.IDENTITY;
                    }
                    decoder = newContentDecoder(contentEncoding);
                    if (decoder != null) {
                        stream.decompressor(decoder);
                        // Decode the content and remove or replace the existing headers
                        // so that the message looks like a decoded message.
                        CharSequence targetContentEncoding = getTargetContentEncoding(contentEncoding);
                        if (HttpHeaders.Values.IDENTITY.equalsIgnoreCase(targetContentEncoding)) {
                            builder.remove(CONTENT_ENCODING_LOWER_CASE);
                        } else {
                            builder.set(CONTENT_ENCODING_LOWER_CASE, targetContentEncoding);
                        }
                    }
                }
            } else if (endOfStream) {
                cleanup(stream, decoder);
            }
            if (decoder != null) {
                // The content length will be for the compressed data.  Since we will decompress the data
                // this content-length will not be correct.  Instead of queuing messages or delaying sending
                // header frames...just remove the content-length header
                builder.remove(CONTENT_LENGTH_LOWER_CASE);
            }
        }
    }

    /**
     * Release remaining content from the {@link EmbeddedChannel} and remove the decoder from the {@link Http2Stream}.
     * @param stream The stream for which {@code decoder} is the decompressor for
     * @param decoder The decompressor for {@code stream}
     */
    private static void cleanup(Http2Stream stream, EmbeddedChannel decoder) {
        if (decoder.finish()) {
            for (;;) {
                final ByteBuf buf = decoder.readInbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }
        stream.decompressor(null);
    }

    /**
     * Read the next decoded {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist.
     * @param decoder The channel to read from
     * @return The next decoded {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist
     */
    private static ByteBuf nextReadableBuf(EmbeddedChannel decoder) {
        for (;;) {
            final ByteBuf buf = decoder.readInbound();
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

    @Override
    protected void notifyListenerOnDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream, Http2FrameListener listener) throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        final EmbeddedChannel decoder = stream == null ? null : stream.decompressor();
        if (decoder == null) {
            super.notifyListenerOnDataRead(ctx, streamId, data, padding, endOfStream, listener);
        } else {
            // call retain here as it will call release after its written to the channel
            decoder.writeInbound(data.retain());
            ByteBuf buf = nextReadableBuf(decoder);
            if (buf == null) {
                if (endOfStream) {
                    super.notifyListenerOnDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, true, listener);
                }
                // END_STREAM is not set and the data could not be decoded yet.
                // The assumption has to be there will be more data frames to complete the decode.
                // We don't have enough information here to know if this is an error.
            } else {
                for (;;) {
                    final ByteBuf nextBuf = nextReadableBuf(decoder);
                    if (nextBuf == null) {
                        super.notifyListenerOnDataRead(ctx, streamId, buf, padding, endOfStream, listener);
                        break;
                    } else {
                        super.notifyListenerOnDataRead(ctx, streamId, buf, padding, false, listener);
                    }
                    buf = nextBuf;
                }
            }

            if (endOfStream) {
                cleanup(stream, decoder);
            }
        }
    }

    @Override
    protected void notifyListenerOnHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers.Builder builder,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream,
            Http2FrameListener listener) throws Http2Exception {
        initDecoder(streamId, builder, endOfStream);
        super.notifyListenerOnHeadersRead(ctx, streamId, builder, streamDependency, weight,
                exclusive, padding, endOfStream, listener);
    }

    @Override
    protected void notifyListenerOnHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers.Builder builder,
            int padding, boolean endOfStream, Http2FrameListener listener) throws Http2Exception {
        initDecoder(streamId, builder, endOfStream);
        super.notifyListenerOnHeadersRead(ctx, streamId, builder, padding, endOfStream, listener);
    }
}
