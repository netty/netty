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

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Values.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaders.Values.GZIP;
import static io.netty.handler.codec.http.HttpHeaders.Values.IDENTITY;
import static io.netty.handler.codec.http.HttpHeaders.Values.XDEFLATE;
import static io.netty.handler.codec.http.HttpHeaders.Values.XGZIP;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * A HTTP2 frame listener that will decompress data frames according to the {@code content-encoding} header for each
 * stream.
 */
public class DelegatingDecompressorFrameListener extends Http2FrameListenerDecorator {
    private static final AsciiString CONTENT_ENCODING_LOWER_CASE = CONTENT_ENCODING.toLowerCase();
    private static final AsciiString CONTENT_LENGTH_LOWER_CASE = CONTENT_LENGTH.toLowerCase();
    private static final Http2ConnectionAdapter CLEAN_UP_LISTENER = new Http2ConnectionAdapter() {
        @Override
        public void streamRemoved(Http2Stream stream) {
            final EmbeddedChannel decompressor = stream.decompressor();
            if (decompressor != null) {
                cleanup(stream, decompressor);
            }
        }
    };

    private final Http2Connection connection;
    private final boolean strict;

    public DelegatingDecompressorFrameListener(Http2Connection connection, Http2FrameListener listener) {
        this(connection, listener, true);
    }

    public DelegatingDecompressorFrameListener(Http2Connection connection, Http2FrameListener listener,
                    boolean strict) {
        super(listener);
        this.connection = connection;
        this.strict = strict;

        connection.addListener(CLEAN_UP_LISTENER);
    }

    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                    throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        final EmbeddedChannel decompressor = stream == null ? null : stream.decompressor();
        if (decompressor == null) {
            listener.onDataRead(ctx, streamId, data, padding, endOfStream);
            return;
        }

        try {
            // call retain here as it will call release after its written to the channel
            decompressor.writeInbound(data.retain());
            ByteBuf buf = nextReadableBuf(decompressor);
            if (buf == null) {
                if (endOfStream) {
                    listener.onDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, true);
                }
                // END_STREAM is not set and the data could not be decoded yet.
                // The assumption has to be there will be more data frames to complete the decode.
                // We don't have enough information here to know if this is an error.
            } else {
                for (;;) {
                    final ByteBuf nextBuf = nextReadableBuf(decompressor);
                    if (nextBuf == null) {
                        listener.onDataRead(ctx, streamId, buf, padding, endOfStream);
                        break;
                    } else {
                        listener.onDataRead(ctx, streamId, buf, padding, false);
                    }
                    buf = nextBuf;
                }
            }
        } finally {
            if (endOfStream) {
                cleanup(stream, decompressor);
            }
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                    boolean endStream) throws Http2Exception {
        initDecompressor(streamId, headers, endStream);
        listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                    short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        initDecompressor(streamId, headers, endStream);
        listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    /**
     * Returns a new {@link EmbeddedChannel} that decodes the HTTP2 message content encoded in the specified
     * {@code contentEncoding}.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return a new {@link ByteToMessageDecoder} if the specified encoding is supported. {@code null} otherwise
     *         (alternatively, you can throw a {@link Http2Exception} to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected EmbeddedChannel newContentDecompressor(AsciiString contentEncoding) throws Http2Exception {
        if (GZIP.equalsIgnoreCase(contentEncoding) || XGZIP.equalsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        }
        if (DEFLATE.equalsIgnoreCase(contentEncoding) || XDEFLATE.equalsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            // To be strict, 'deflate' means ZLIB, but some servers were not implemented correctly.
            return new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(wrapper));
        }
        // 'identity' or unsupported
        return null;
    }

    /**
     * Returns the expected content encoding of the decoded content. This getMethod returns {@code "identity"} by
     * default, which is the case for most decompressors.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return the expected content encoding of the new content.
     * @throws Http2Exception if the {@code contentEncoding} is not supported and warrants an exception
     */
    protected AsciiString getTargetContentEncoding(@SuppressWarnings("UnusedParameters") AsciiString contentEncoding)
                    throws Http2Exception {
        return IDENTITY;
    }

    /**
     * Checks if a new decompressor object is needed for the stream identified by {@code streamId}.
     * This method will modify the {@code content-encoding} header contained in {@code headers}.
     *
     * @param streamId The identifier for the headers inside {@code headers}
     * @param headers Object representing headers which have been read
     * @param endOfStream Indicates if the stream has ended
     * @throws Http2Exception If the {@code content-encoding} is not supported
     */
    private void initDecompressor(int streamId, Http2Headers headers, boolean endOfStream) throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            return;
        }

        EmbeddedChannel decompressor = stream.decompressor();
        if (decompressor == null) {
            if (!endOfStream) {
                // Determine the content encoding.
                AsciiString contentEncoding = headers.get(CONTENT_ENCODING_LOWER_CASE);
                if (contentEncoding == null) {
                    contentEncoding = IDENTITY;
                }
                decompressor = newContentDecompressor(contentEncoding);
                if (decompressor != null) {
                    stream.decompressor(decompressor);
                    // Decode the content and remove or replace the existing headers
                    // so that the message looks like a decoded message.
                    AsciiString targetContentEncoding = getTargetContentEncoding(contentEncoding);
                    if (IDENTITY.equalsIgnoreCase(targetContentEncoding)) {
                        headers.remove(CONTENT_ENCODING_LOWER_CASE);
                    } else {
                        headers.set(CONTENT_ENCODING_LOWER_CASE, targetContentEncoding);
                    }
                }
            }
        } else if (endOfStream) {
            cleanup(stream, decompressor);
        }
        if (decompressor != null) {
            // The content length will be for the compressed data. Since we will decompress the data
            // this content-length will not be correct. Instead of queuing messages or delaying sending
            // header frames...just remove the content-length header
            headers.remove(CONTENT_LENGTH_LOWER_CASE);
        }
    }

    /**
     * Release remaining content from the {@link EmbeddedChannel} and remove the decompressor
     * from the {@link Http2Stream}.
     *
     * @param stream The stream for which {@code decompressor} is the decompressor for
     * @param decompressor The decompressor for {@code stream}
     */
    private static void cleanup(Http2Stream stream, EmbeddedChannel decompressor) {
        if (decompressor.finish()) {
            for (;;) {
                final ByteBuf buf = decompressor.readInbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }
        stream.decompressor(null);
    }

    /**
     * Read the next decompressed {@link ByteBuf} from the {@link EmbeddedChannel}
     * or {@code null} if one does not exist.
     *
     * @param decompressor The channel to read from
     * @return The next decoded {@link ByteBuf} from the {@link EmbeddedChannel} or {@code null} if one does not exist
     */
    private static ByteBuf nextReadableBuf(EmbeddedChannel decompressor) {
        for (;;) {
            final ByteBuf buf = decompressor.readInbound();
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
