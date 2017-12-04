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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A HTTP2 frame listener that will decompress data frames according to the {@code content-encoding} header for each
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

        final EmbeddedChannel channel = decompressor.decompressor();
        final int compressedBytes = data.readableBytes() + padding;
        decompressor.incrementCompressedBytes(compressedBytes);
        try {
            // call retain here as it will call release after its written to the channel
            channel.writeInbound(data.retain());
            ByteBuf buf = nextReadableBuf(channel);
            if (buf == null && endOfStream && channel.finish()) {
                buf = nextReadableBuf(channel);
            }
            if (buf == null) {
                if (endOfStream) {
                    listener.onDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, padding, true);
                }
                // No new decompressed data was extracted from the compressed data. This means the application could
                // not be provided with data and thus could not return how many bytes were processed. We will assume
                // there is more data coming which will complete the decompression block. To allow for more data we
                // return all bytes to the flow control window (so the peer can send more data).
                decompressor.incrementDecompressedBytes(compressedBytes);
                return compressedBytes;
            }
            try {
                Http2LocalFlowController flowController = connection.local().flowController();
                decompressor.incrementDecompressedBytes(padding);
                for (;;) {
                    ByteBuf nextBuf = nextReadableBuf(channel);
                    boolean decompressedEndOfStream = nextBuf == null && endOfStream;
                    if (decompressedEndOfStream && channel.finish()) {
                        nextBuf = nextReadableBuf(channel);
                        decompressedEndOfStream = nextBuf == null;
                    }

                    decompressor.incrementDecompressedBytes(buf.readableBytes());
                    // Immediately return the bytes back to the flow controller. ConsumedBytesConverter will convert
                    // from the decompressed amount which the user knows about to the compressed amount which flow
                    // control knows about.
                    flowController.consumeBytes(stream,
                            listener.onDataRead(ctx, streamId, buf, padding, decompressedEndOfStream));
                    if (nextBuf == null) {
                        break;
                    }

                    padding = 0; // Padding is only communicated once on the first iteration.
                    buf.release();
                    buf = nextBuf;
                }
                // We consume bytes each time we call the listener to ensure if multiple frames are decompressed
                // that the bytes are accounted for immediately. Otherwise the user may see an inconsistent state of
                // flow control.
                return 0;
            } finally {
                buf.release();
            }
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw streamError(stream.id(), INTERNAL_ERROR, t,
                    "Decompressor error detected while delegating data read on streamId %d", stream.id());
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
     * @return a new {@link ByteToMessageDecoder} if the specified encoding is supported. {@code null} otherwise
     *         (alternatively, you can throw a {@link Http2Exception} to block unknown encoding).
     * @throws Http2Exception If the specified encoding is not not supported and warrants an exception
     */
    protected EmbeddedChannel newContentDecompressor(final ChannelHandlerContext ctx, CharSequence contentEncoding)
            throws Http2Exception {
        if (GZIP.contentEqualsIgnoreCase(contentEncoding) || X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding) || X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            // To be strict, 'deflate' means ZLIB, but some servers were not implemented correctly.
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(wrapper));
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
            final EmbeddedChannel channel = newContentDecompressor(ctx, contentEncoding);
            if (channel != null) {
                decompressor = new Http2Decompressor(channel);
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
            // header frames...just remove the content-length header
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
     * Release remaining content from the {@link EmbeddedChannel}.
     *
     * @param decompressor The decompressor for {@code stream}
     */
    private static void cleanup(Http2Decompressor decompressor) {
        decompressor.decompressor().finishAndReleaseAll();
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

    /**
     * A decorator around the local flow controller that converts consumed bytes from uncompressed to compressed.
     */
    private final class ConsumedBytesConverter implements Http2LocalFlowController {
        private final Http2LocalFlowController flowController;

        ConsumedBytesConverter(Http2LocalFlowController flowController) {
            this.flowController = checkNotNull(flowController, "flowController");
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
        private final EmbeddedChannel decompressor;
        private int compressed;
        private int decompressed;

        Http2Decompressor(EmbeddedChannel decompressor) {
            this.decompressor = decompressor;
        }

        /**
         * Responsible for taking compressed bytes in and producing decompressed bytes.
         */
        EmbeddedChannel decompressor() {
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
            if (decompressedBytes < 0) {
                throw new IllegalArgumentException("decompressedBytes must not be negative: " + decompressedBytes);
            }
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
