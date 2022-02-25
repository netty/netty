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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.compression.Brotli;
import io.netty5.handler.codec.compression.BrotliDecompressor;
import io.netty5.handler.codec.compression.Decompressor;
import io.netty5.handler.codec.compression.ZlibDecompressor;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.util.internal.UnstableApi;

import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty5.handler.codec.http.HttpHeaderValues.BR;
import static io.netty5.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty5.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty5.handler.codec.http.HttpHeaderValues.IDENTITY;
import static io.netty5.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty5.handler.codec.http.HttpHeaderValues.X_GZIP;
import static io.netty5.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty5.handler.codec.http2.Http2Exception.streamError;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

/**
 * An HTTP2 frame listener that will decompress data frames according to the {@code content-encoding} header for each
 * stream. The decompression provided by this class will be applied to the data for the entire stream.
 */
@UnstableApi
public class DelegatingDecompressorFrameListener extends Http2FrameListenerDecorator {

    private final Http2Connection connection;
    private final boolean strict;
    private boolean flowControllerInitialized;
    private final Http2Connection.PropertyKey propertyKey;

    public DelegatingDecompressorFrameListener(Http2Connection connection, Http2FrameListener listener) {
        this(connection, listener, true);
    }

    public DelegatingDecompressorFrameListener(Http2Connection connection, Http2FrameListener listener,
                    boolean strict) {
        super(listener);
        this.connection = connection;
        this.strict = strict;

        propertyKey = connection.newKey();
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamRemoved(Http2Stream stream) {
                final Http2Decompressor decompressor = decompressor(stream);
                if (decompressor != null) {
                    cleanup(decompressor);
                }
            }
        });
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        final Http2Decompressor decompressor = decompressor(stream);
        if (decompressor == null) {
            // The decompressor may be null if no compatible encoding type was found in this stream's headers
            return listener.onDataRead(ctx, streamId, data, padding, endOfStream);
        }

        final Decompressor decomp = decompressor.decompressor();
        final int compressedBytes = data.readableBytes() + padding;
        decompressor.incrementCompressedBytes(compressedBytes);
        ByteBuf decompressed = null;
        try {
            Http2LocalFlowController flowController = connection.local().flowController();
            decompressor.incrementDecompressedBytes(padding);

            for (;;) {
                if (decomp.isFinished()) {
                    flowController.consumeBytes(stream,
                            listener.onDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, endOfStream));
                    break;
                }
                int idx = data.readerIndex();
                decompressed = decomp.decompress(data, ctx.alloc());
                if (decompressed == null || idx == data.readerIndex()) {
                    flowController.consumeBytes(stream,
                            listener.onDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, endOfStream));
                    break;
                } else {
                    // Immediately return the bytes back to the flow controller. ConsumedBytesConverter will convert
                    // from the decompressed amount which the user knows about to the compressed amount which flow
                    // control knows about.
                    decompressor.incrementDecompressedBytes(decompressed.readableBytes());
                    flowController.consumeBytes(stream,
                            listener.onDataRead(ctx, streamId, decompressed, padding, false));
                    decompressed.release();
                    decompressed = null;
                }
                padding = 0; // Padding is only communicated once on the first iteration.
            }
            // We consume bytes each time we call the listener to ensure if multiple frames are decompressed
            // that the bytes are accounted for immediately. Otherwise the user may see an inconsistent state of
            // flow control.
            return 0;
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw streamError(stream.id(), INTERNAL_ERROR, t,
                    "Decompressor error detected while delegating data read on streamId %d", stream.id());
        } finally {
            if (decompressed != null) {
                decompressed.release();
            }
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                    boolean endStream) throws Http2Exception {
        initDecompressor(ctx, streamId, headers, endStream);
        listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                    short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        initDecompressor(ctx, streamId, headers, endStream);
        listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    /**
     * Returns a new {@link EmbeddedChannel} that decodes the HTTP2 message content encoded in the specified
     * {@code contentEncoding}.
     *
     * @param contentEncoding the value of the {@code content-encoding} header
     * @return a new {@link Decompressor} if the specified encoding is supported. {@code null} otherwise
     *         (alternatively, you can throw a {@link Http2Exception} to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected Decompressor newContentDecompressor(final ChannelHandlerContext ctx, CharSequence contentEncoding)
            throws Http2Exception {
        if (GZIP.contentEqualsIgnoreCase(contentEncoding) || X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return ZlibDecompressor.newFactory(ZlibWrapper.GZIP).get();
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding) || X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            // To be strict, 'deflate' means ZLIB, but some servers were not implemented correctly.
            return ZlibDecompressor.newFactory(wrapper).get();
        }
        if (Brotli.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
            return BrotliDecompressor.newFactory().get();
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
    protected CharSequence getTargetContentEncoding(@SuppressWarnings("UnusedParameters") CharSequence contentEncoding)
                    throws Http2Exception {
        return IDENTITY;
    }

    /**
     * Checks if a new decompressor object is needed for the stream identified by {@code streamId}.
     * This method will modify the {@code content-encoding} header contained in {@code headers}.
     *
     * @param ctx The context
     * @param streamId The identifier for the headers inside {@code headers}
     * @param headers Object representing headers which have been read
     * @param endOfStream Indicates if the stream has ended
     * @throws Http2Exception If the {@code content-encoding} is not supported
     */
    private void initDecompressor(ChannelHandlerContext ctx, int streamId, Http2Headers headers, boolean endOfStream)
            throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            return;
        }

        Http2Decompressor decompressor = decompressor(stream);
        if (decompressor == null && !endOfStream) {
            // Determine the content encoding.
            CharSequence contentEncoding = headers.get(CONTENT_ENCODING);
            if (contentEncoding == null) {
                contentEncoding = IDENTITY;
            }
            final Decompressor decomp = newContentDecompressor(ctx, contentEncoding);
            if (decomp != null) {
                decompressor = new Http2Decompressor(decomp);
                stream.setProperty(propertyKey, decompressor);
                // Decode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                CharSequence targetContentEncoding = getTargetContentEncoding(contentEncoding);
                if (IDENTITY.contentEqualsIgnoreCase(targetContentEncoding)) {
                    headers.remove(CONTENT_ENCODING);
                } else {
                    headers.set(CONTENT_ENCODING, targetContentEncoding);
                }
            }
        }

        if (decompressor != null) {
            // The content length will be for the compressed data. Since we will decompress the data
            // this content-length will not be correct. Instead of queuing messages or delaying sending
            // header frames just remove the content-length header.
            headers.remove(CONTENT_LENGTH);

            // The first time that we initialize a decompressor, decorate the local flow controller to
            // properly convert consumed bytes.
            if (!flowControllerInitialized) {
                flowControllerInitialized = true;
                connection.local().flowController(new ConsumedBytesConverter(connection.local().flowController()));
            }
        }
    }

    Http2Decompressor decompressor(Http2Stream stream) {
        return stream == null ? null : (Http2Decompressor) stream.getProperty(propertyKey);
    }

    /**
     * Release remaining content from the {@link Decompressor}.
     *
     * @param decompressor The decompressor for {@code stream}
     */
    private static void cleanup(Http2Decompressor decompressor) {
        decompressor.decompressor().close();
    }

    /**
     * A decorator around the local flow controller that converts consumed bytes from uncompressed to compressed.
     */
    private final class ConsumedBytesConverter implements Http2LocalFlowController {
        private final Http2LocalFlowController flowController;

        ConsumedBytesConverter(Http2LocalFlowController flowController) {
            this.flowController = requireNonNull(flowController, "flowController");
        }

        @Override
        public Http2LocalFlowController frameWriter(Http2FrameWriter frameWriter) {
            return flowController.frameWriter(frameWriter);
        }

        @Override
        public void channelHandlerContext(ChannelHandlerContext ctx) throws Http2Exception {
            flowController.channelHandlerContext(ctx);
        }

        @Override
        public void initialWindowSize(int newWindowSize) throws Http2Exception {
            flowController.initialWindowSize(newWindowSize);
        }

        @Override
        public int initialWindowSize() {
            return flowController.initialWindowSize();
        }

        @Override
        public int windowSize(Http2Stream stream) {
            return flowController.windowSize(stream);
        }

        @Override
        public void incrementWindowSize(Http2Stream stream, int delta) throws Http2Exception {
            flowController.incrementWindowSize(stream, delta);
        }

        @Override
        public void receiveFlowControlledFrame(Http2Stream stream, ByteBuf data, int padding,
                boolean endOfStream) throws Http2Exception {
            flowController.receiveFlowControlledFrame(stream, data, padding, endOfStream);
        }

        @Override
        public boolean consumeBytes(Http2Stream stream, int numBytes) throws Http2Exception {
            Http2Decompressor decompressor = decompressor(stream);
            if (decompressor != null) {
                // Convert the decompressed bytes to compressed (on the wire) bytes.
                numBytes = decompressor.consumeBytes(stream.id(), numBytes);
            }
            try {
                return flowController.consumeBytes(stream, numBytes);
            } catch (Http2Exception e) {
                throw e;
            } catch (Throwable t) {
                // The stream should be closed at this point. We have already changed our state tracking the compressed
                // bytes, and there is no guarantee we can recover if the underlying flow controller throws.
                throw streamError(stream.id(), INTERNAL_ERROR, t, "Error while returning bytes to flow control window");
            }
        }

        @Override
        public int unconsumedBytes(Http2Stream stream) {
            return flowController.unconsumedBytes(stream);
        }

        @Override
        public int initialWindowSize(Http2Stream stream) {
            return flowController.initialWindowSize(stream);
        }
    }

    /**
     * Provides the state for stream {@code DATA} frame decompression.
     */
    private static final class Http2Decompressor {
        private final Decompressor decompressor;
        private int compressed;
        private int decompressed;

        Http2Decompressor(Decompressor decompressor) {
            this.decompressor = decompressor;
        }

        /**
         * Responsible for taking compressed bytes in and producing decompressed bytes.
         */
        Decompressor decompressor() {
            return decompressor;
        }

        /**
         * Increment the number of bytes received prior to doing any decompression.
         */
        void incrementCompressedBytes(int delta) {
            assert delta >= 0;
            compressed += delta;
        }

        /**
         * Increment the number of bytes after the decompression process.
         */
        void incrementDecompressedBytes(int delta) {
            assert delta >= 0;
            decompressed += delta;
        }

        /**
         * Determines the ratio between {@code numBytes} and {@link Http2Decompressor#decompressed}.
         * This ratio is used to decrement {@link Http2Decompressor#decompressed} and
         * {@link Http2Decompressor#compressed}.
         * @param streamId the stream ID
         * @param decompressedBytes The number of post-decompressed bytes to return to flow control
         * @return The number of pre-decompressed bytes that have been consumed.
         */
        int consumeBytes(int streamId, int decompressedBytes) throws Http2Exception {
            checkPositiveOrZero(decompressedBytes, "decompressedBytes");
            if (decompressed - decompressedBytes < 0) {
                throw streamError(streamId, INTERNAL_ERROR,
                        "Attempting to return too many bytes for stream %d. decompressed: %d " +
                                "decompressedBytes: %d", streamId, decompressed, decompressedBytes);
            }
            double consumedRatio = decompressedBytes / (double) decompressed;
            int consumedCompressed = Math.min(compressed, (int) Math.ceil(compressed * consumedRatio));
            if (compressed - consumedCompressed < 0) {
                throw streamError(streamId, INTERNAL_ERROR,
                        "overflow when converting decompressed bytes to compressed bytes for stream %d." +
                                "decompressedBytes: %d decompressed: %d compressed: %d consumedCompressed: %d",
                        streamId, decompressedBytes, decompressed, compressed, consumedCompressed);
            }
            decompressed -= decompressedBytes;
            compressed -= consumedCompressed;

            return consumedCompressed;
        }
    }
}
