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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.util.concurrent.PromiseCombiner;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A decorating HTTP/2 encoder that compresses response data frames based on a negotiation between the
 * request's {@code accept-encoding} header (captured on the stream by
 * {@link Http2RequestAcceptEncodingFrameListener}) and the {@link CompressionOptions} configured for this
 * encoder.
 * <p>
 * Unlike the deprecated {@code CompressorHttp2ConnectionEncoder}, this encoder never compresses a response
 * whose headers already contain a {@code content-encoding} value: such a body is assumed to already be
 * encoded by the application, and compressing it again would double-encode it (see #14277).
 */
public class NegotiatingCompressorHttp2ConnectionEncoder extends DecoratingHttp2ConnectionEncoder {

    private final Http2RequestAcceptEncodingPropertyKey acceptEncodingKey;
    private final Http2ContentEncodingNegotiator negotiator;
    private final Http2Connection.PropertyKey compressorPropertyKey;

    /**
     * Creates a new instance.
     *
     * @param delegate the {@link Http2ConnectionEncoder} to delegate to
     * @param acceptEncodingKey the shared key used to read the {@code accept-encoding} value captured for a
     *                          stream by {@link Http2RequestAcceptEncodingFrameListener}
     * @param compressionOptions the compression options this encoder is allowed to negotiate
     */
    public NegotiatingCompressorHttp2ConnectionEncoder(Http2ConnectionEncoder delegate,
            Http2RequestAcceptEncodingPropertyKey acceptEncodingKey, CompressionOptions... compressionOptions) {
        super(delegate);
        this.acceptEncodingKey = checkNotNull(acceptEncodingKey, "acceptEncodingKey");
        this.negotiator = new Http2ContentEncodingNegotiator(compressionOptions);
        this.compressorPropertyKey = connection().newKey();
        connection().addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamRemoved(Http2Stream stream) {
                final EmbeddedChannel compressor = stream.getProperty(compressorPropertyKey);
                if (compressor != null) {
                    cleanup(stream, compressor);
                }
            }
        });
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data,
            int padding, final boolean endOfStream, final ChannelPromise promise) {
        final Http2Stream stream = connection().stream(streamId);
        final EmbeddedChannel channel = stream == null ? null : (EmbeddedChannel) stream.getProperty(
                compressorPropertyKey);
        if (channel == null) {
            // The compressor may be null if no compression was negotiated for this stream.
            return super.writeData(ctx, streamId, data, padding, endOfStream, promise);
        }

        try {
            // The channel will release the buffer after being written
            channel.writeOutbound(data);
            ByteBuf buf = nextReadableBuf(channel);
            if (buf == null) {
                if (endOfStream) {
                    if (channel.finish()) {
                        buf = nextReadableBuf(channel);
                    }
                    return super.writeData(ctx, streamId, buf == null ? Unpooled.EMPTY_BUFFER : buf, padding,
                            true, promise);
                }
                // END_STREAM is not set and the assumption is data is still forthcoming.
                promise.setSuccess();
                return promise;
            }

            PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
            for (;;) {
                ByteBuf nextBuf = nextReadableBuf(channel);
                boolean compressedEndOfStream = nextBuf == null && endOfStream;
                if (compressedEndOfStream && channel.finish()) {
                    nextBuf = nextReadableBuf(channel);
                    compressedEndOfStream = nextBuf == null;
                }

                ChannelPromise bufPromise = ctx.newPromise();
                combiner.add(bufPromise);
                super.writeData(ctx, streamId, buf, padding, compressedEndOfStream, bufPromise);
                if (nextBuf == null) {
                    break;
                }

                padding = 0; // Padding is only communicated once on the first iteration
                buf = nextBuf;
            }
            combiner.finish(promise);
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        } finally {
            if (endOfStream) {
                cleanup(stream, channel);
            }
        }
        return promise;
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        try {
            EmbeddedChannel compressor = prepareCompressor(ctx, streamId, headers, endStream);

            // Write the headers and create the stream object.
            ChannelFuture future = super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);

            // After the stream object has been created, then attach the compressor as a property for data
            // compression.
            bindCompressorToStream(compressor, streamId);

            return future;
        } catch (Throwable e) {
            promise.tryFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream, final ChannelPromise promise) {
        try {
            EmbeddedChannel compressor = prepareCompressor(ctx, streamId, headers, endOfStream);

            // Write the headers and create the stream object.
            ChannelFuture future = super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive,
                    padding, endOfStream, promise);

            // After the stream object has been created, then attach the compressor as a property for data
            // compression.
            bindCompressorToStream(compressor, streamId);

            return future;
        } catch (Throwable e) {
            promise.tryFailure(e);
        }
        return promise;
    }

    /**
     * Determines whether a new compressor is needed for the stream identified by {@code streamId}, and, if so,
     * updates {@code headers} to reflect the negotiated {@code content-encoding}.
     * <p>
     * Compression is skipped (returns {@code null}) whenever:
     * <ul>
     *     <li>the stream ends with this frame ({@code endOfStream}), since there is no body to compress;</li>
     *     <li>{@code headers} already contains a {@code content-encoding} value, meaning the body is assumed to
     *     already be encoded by the application (the fix for #14277: never double-compress);</li>
     *     <li>the request did not capture an {@code accept-encoding} value for this stream; or</li>
     *     <li>{@link Http2ContentEncodingNegotiator#negotiate(CharSequence)} could not find a match.</li>
     * </ul>
     *
     * @param ctx the context
     * @param streamId the stream id for which the headers are being written
     * @param headers the response headers being written
     * @param endOfStream indicates if the stream has ended
     * @return the compressor to use for {@code streamId}'s data frames, or {@code null} if none is needed
     * @throws Http2Exception if the compressor could not be created
     */
    private EmbeddedChannel prepareCompressor(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            boolean endOfStream) throws Http2Exception {
        if (endOfStream) {
            return null;
        }
        if (headers.contains(CONTENT_ENCODING)) {
            // The body is already encoded by the application. Compressing it again would double-encode it.
            return null;
        }
        Http2Stream stream = connection().stream(streamId);
        CharSequence acceptEncoding = acceptEncodingKey.acceptEncoding(stream);
        if (acceptEncoding == null) {
            return null;
        }
        NegotiatedEncoding negotiated = negotiator.negotiate(acceptEncoding);
        if (negotiated == null) {
            return null;
        }
        EmbeddedChannel channel = Http2CompressionChannelFactory.newCompressionChannel(ctx, negotiated);
        if (channel == null) {
            return null;
        }
        headers.set(CONTENT_ENCODING, negotiated.contentEncoding);
        // The content length will be for the uncompressed data. Since we will compress the data this
        // content-length will not be correct. Instead of queuing messages or delaying sending header frames...
        // just remove the content-length header.
        headers.remove(CONTENT_LENGTH);
        return channel;
    }

    /**
     * Called after the super class has written the headers and created any associated stream objects.
     *
     * @param compressor the compressor associated with the stream identified by {@code streamId}
     * @param streamId the stream id for which the headers were written
     */
    private void bindCompressorToStream(EmbeddedChannel compressor, int streamId) {
        if (compressor != null) {
            Http2Stream stream = connection().stream(streamId);
            if (stream != null) {
                stream.setProperty(compressorPropertyKey, compressor);
            }
        }
    }

    /**
     * Releases remaining content from {@link EmbeddedChannel} and removes the compressor from the
     * {@link Http2Stream}.
     *
     * @param stream the stream that {@code compressor} compresses data for
     * @param compressor the compressor for {@code stream}
     */
    void cleanup(Http2Stream stream, EmbeddedChannel compressor) {
        compressor.finishAndReleaseAll();
        stream.removeProperty(compressorPropertyKey);
    }

    /**
     * Reads the next compressed {@link ByteBuf} from the {@link EmbeddedChannel}, or {@code null} if one does not
     * exist.
     *
     * @param compressor the channel to read from
     * @return the next compressed {@link ByteBuf} from the {@link EmbeddedChannel}, or {@code null} if one does not
     *         exist
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
