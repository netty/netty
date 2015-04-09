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
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.ByteString;

/**
 * A HTTP2 frame listener that will decompress data frames according to the {@code content-encoding} header for each
 * stream. The decompression provided by this class will be applied to the data for the entire stream.
 */
public class DelegatingDecompressorFrameListener extends Http2FrameListenerDecorator {
    private static final Http2ConnectionAdapter CLEAN_UP_LISTENER = new Http2ConnectionAdapter() {
        @Override
        public void onStreamRemoved(Http2Stream stream) {
            final Http2Decompressor decompressor = decompressor(stream);
            if (decompressor != null) {
                cleanup(stream, decompressor);
            }
        }
    };

    private final Http2Connection connection;
    private final boolean strict;
    private boolean flowControllerInitialized;

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
        int processedBytes = 0;
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
                decompressor.incrementDecompressedByes(compressedBytes);
                processedBytes = compressedBytes;
            } else {
                try {
                    decompressor.incrementDecompressedByes(padding);
                    for (;;) {
                        ByteBuf nextBuf = nextReadableBuf(channel);
                        boolean decompressedEndOfStream = nextBuf == null && endOfStream;
                        if (decompressedEndOfStream && channel.finish()) {
                            nextBuf = nextReadableBuf(channel);
                            decompressedEndOfStream = nextBuf == null;
                        }

                        decompressor.incrementDecompressedByes(buf.readableBytes());
                        processedBytes += listener.onDataRead(ctx, streamId, buf, padding, decompressedEndOfStream);
                        if (nextBuf == null) {
                            break;
                        }

                        padding = 0; // Padding is only communicated once on the first iteration
                        buf.release();
                        buf = nextBuf;
                    }
                } finally {
                    buf.release();
                }
            }
            decompressor.incrementProcessedBytes(processedBytes);
            // The processed bytes will be translated to pre-decompressed byte amounts by DecompressorGarbageCollector
            return processedBytes;
        } catch (Http2Exception e) {
            // Consider all the bytes consumed because there was an error
            decompressor.incrementProcessedBytes(compressedBytes);
            throw e;
        } catch (Throwable t) {
            // Consider all the bytes consumed because there was an error
            decompressor.incrementProcessedBytes(compressedBytes);
            throw streamError(stream.id(), INTERNAL_ERROR, t,
                    "Decompressor error detected while delegating data read on streamId %d", stream.id());
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
    protected EmbeddedChannel newContentDecompressor(ByteString contentEncoding) throws Http2Exception {
        if (GZIP.equals(contentEncoding) || X_GZIP.equals(contentEncoding)) {
            return new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        }
        if (DEFLATE.equals(contentEncoding) || X_DEFLATE.equals(contentEncoding)) {
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
    protected ByteString getTargetContentEncoding(@SuppressWarnings("UnusedParameters") ByteString contentEncoding)
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

        Http2Decompressor decompressor = decompressor(stream);
        if (decompressor == null && !endOfStream) {
            // Determine the content encoding.
            ByteString contentEncoding = headers.get(CONTENT_ENCODING);
            if (contentEncoding == null) {
                contentEncoding = IDENTITY;
            }
            final EmbeddedChannel channel = newContentDecompressor(contentEncoding);
            if (channel != null) {
                decompressor = new Http2Decompressor(channel);
                stream.setProperty(Http2Decompressor.class, decompressor);
                // Decode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                ByteString targetContentEncoding = getTargetContentEncoding(contentEncoding);
                if (IDENTITY.equals(targetContentEncoding)) {
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

    private static Http2Decompressor decompressor(Http2Stream stream) {
        return (Http2Decompressor) (stream == null? null : stream.getProperty(Http2Decompressor.class));
    }

    /**
     * Release remaining content from the {@link EmbeddedChannel} and remove the decompressor
     * from the {@link Http2Stream}.
     *
     * @param stream The stream for which {@code decompressor} is the decompressor for
     * @param decompressor The decompressor for {@code stream}
     */
    private static void cleanup(Http2Stream stream, Http2Decompressor decompressor) {
        final EmbeddedChannel channel = decompressor.decompressor();
        if (channel.finish()) {
            for (;;) {
                final ByteBuf buf = channel.readInbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }
        decompressor = stream.removeProperty(Http2Decompressor.class);
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
    private static final class ConsumedBytesConverter implements Http2LocalFlowController {
        private final Http2LocalFlowController flowController;

        ConsumedBytesConverter(Http2LocalFlowController flowController) {
            this.flowController = checkNotNull(flowController, "flowController");
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
        public void incrementWindowSize(ChannelHandlerContext ctx, Http2Stream stream, int delta)
                throws Http2Exception {
            flowController.incrementWindowSize(ctx, stream, delta);
        }

        @Override
        public void receiveFlowControlledFrame(ChannelHandlerContext ctx, Http2Stream stream,
                ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            flowController.receiveFlowControlledFrame(ctx, stream, data, padding, endOfStream);
        }

        @Override
        public void consumeBytes(ChannelHandlerContext ctx, Http2Stream stream, int numBytes)
                throws Http2Exception {
            Http2Decompressor decompressor = decompressor(stream);
            Http2Decompressor copy = null;
            try {
                if (decompressor != null) {
                    // Make a copy before hand in case any exceptions occur we will roll back the state
                    copy = new Http2Decompressor(decompressor);
                    // Convert the uncompressed consumed bytes to compressed (on the wire) bytes.
                    numBytes = decompressor.consumeProcessedBytes(numBytes);
                }
                flowController.consumeBytes(ctx, stream, numBytes);
            } catch (Http2Exception e) {
                if (copy != null) {
                    stream.setProperty(Http2Decompressor.class, copy);
                }
                throw e;
            } catch (Throwable t) {
                if (copy != null) {
                    stream.setProperty(Http2Decompressor.class, copy);
                }
                throw new Http2Exception(INTERNAL_ERROR,
                        "Error while returning bytes to flow control window", t);
            }
        }

        @Override
        public int unconsumedBytes(Http2Stream stream) {
            return flowController.unconsumedBytes(stream);
        }
    }

    /**
     * Provides the state for stream {@code DATA} frame decompression.
     */
    private static final class Http2Decompressor {
        private final EmbeddedChannel decompressor;
        private int processed;
        private int compressed;
        private int decompressed;

        Http2Decompressor(Http2Decompressor rhs) {
            this(rhs.decompressor);
            processed = rhs.processed;
            compressed = rhs.compressed;
            decompressed = rhs.decompressed;
        }

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
         * Increment the number of decompressed bytes processed by the application.
         */
        void incrementProcessedBytes(int delta) {
            if (processed + delta < 0) {
                throw new IllegalArgumentException("processed bytes cannot be negative");
            }
            processed += delta;
        }

        /**
         * Increment the number of bytes received prior to doing any decompression.
         */
        void incrementCompressedBytes(int delta) {
            if (compressed + delta < 0) {
                throw new IllegalArgumentException("compressed bytes cannot be negative");
            }
            compressed += delta;
        }

        /**
         * Increment the number of bytes after the decompression process. Under normal circumstances this
         * delta should not exceed {@link Http2Decompressor#processedBytes()}.
         */
        void incrementDecompressedByes(int delta) {
            if (decompressed + delta < 0) {
                throw new IllegalArgumentException("decompressed bytes cannot be negative");
            }
            decompressed += delta;
        }

        /**
         * Decrements {@link Http2Decompressor#processedBytes()} by {@code processedBytes} and determines the ratio
         * between {@code processedBytes} and {@link Http2Decompressor#decompressedBytes()}.
         * This ratio is used to decrement {@link Http2Decompressor#decompressedBytes()} and
         * {@link Http2Decompressor#compressedBytes()}.
         * @param processedBytes The number of post-decompressed bytes that have been processed.
         * @return The number of pre-decompressed bytes that have been consumed.
         */
        int consumeProcessedBytes(int processedBytes) {
            // Consume the processed bytes first to verify that is is a valid amount
            incrementProcessedBytes(-processedBytes);

            double consumedRatio = processedBytes / (double) decompressed;
            int consumedCompressed = Math.min(compressed, (int) Math.ceil(compressed * consumedRatio));
            incrementDecompressedByes(-Math.min(decompressed, (int) Math.ceil(decompressed * consumedRatio)));
            incrementCompressedBytes(-consumedCompressed);

            return consumedCompressed;
        }
    }
}
