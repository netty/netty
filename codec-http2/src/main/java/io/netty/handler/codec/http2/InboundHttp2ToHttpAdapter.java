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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * This adapter provides just header/data events from the HTTP message flow defined
 * here <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>.
 * <p>
 * See {@link HttpToHttp2ConnectionHandler} to get translation from HTTP/1.x objects to HTTP/2 frames for writes.
 */
@UnstableApi
public class InboundHttp2ToHttpAdapter extends Http2EventAdapter {
    private static final ImmediateSendDetector DEFAULT_SEND_DETECTOR = new ImmediateSendDetector() {
        @Override
        public boolean mustSendImmediately(FullHttpMessage msg) {
            if (msg instanceof FullHttpResponse) {
                return ((FullHttpResponse) msg).status().codeClass() == HttpStatusClass.INFORMATIONAL;
            }
            if (msg instanceof FullHttpRequest) {
                return msg.headers().contains(HttpHeaderNames.EXPECT);
            }
            return false;
        }

        @Override
        public FullHttpMessage copyIfNeeded(FullHttpMessage msg) {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest copy = ((FullHttpRequest) msg).replace(Unpooled.buffer(0));
                copy.headers().remove(HttpHeaderNames.EXPECT);
                return copy;
            }
            return null;
        }
    };

    private final int maxContentLength;
    private final ImmediateSendDetector sendDetector;
    private final Http2Connection.PropertyKey messageKey;
    private final boolean propagateSettings;
    protected final Http2Connection connection;
    protected final boolean validateHttpHeaders;

    protected InboundHttp2ToHttpAdapter(Http2Connection connection, int maxContentLength,
                                        boolean validateHttpHeaders, boolean propagateSettings) {

        checkNotNull(connection, "connection");
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength: " + maxContentLength + " (expected: > 0)");
        }
        this.connection = connection;
        this.maxContentLength = maxContentLength;
        this.validateHttpHeaders = validateHttpHeaders;
        this.propagateSettings = propagateSettings;
        sendDetector = DEFAULT_SEND_DETECTOR;
        messageKey = connection.newKey();
    }

    /**
     * The stream is out of scope for the HTTP message flow and will no longer be tracked
     * @param stream The stream to remove associated state with
     * @param release {@code true} to call release on the value if it is present. {@code false} to not call release.
     */
    protected final void removeMessage(Http2Stream stream, boolean release) {
        FullHttpMessage msg = stream.removeProperty(messageKey);
        if (release && msg != null) {
            msg.release();
        }
    }

    /**
     * Get the {@link FullHttpMessage} associated with {@code stream}.
     * @param stream The stream to get the associated state from
     * @return The {@link FullHttpMessage} associated with {@code stream}.
     */
    protected final FullHttpMessage getMessage(Http2Stream stream) {
        return (FullHttpMessage) stream.getProperty(messageKey);
    }

    /**
     * Make {@code message} be the state associated with {@code stream}.
     * @param stream The stream which {@code message} is associated with.
     * @param message The message which contains the HTTP semantics.
     */
    protected final void putMessage(Http2Stream stream, FullHttpMessage message) {
        FullHttpMessage previous = stream.setProperty(messageKey, message);
        if (previous != message && previous != null) {
            previous.release();
        }
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
        removeMessage(stream, true);
    }

    /**
     * Set final headers and fire a channel read event
     *
     * @param ctx The context to fire the event on
     * @param msg The message to send
     * @param release {@code true} to release if present in {@link #messageMap}. {@code false} otherwise.
     * @param stream the stream of the message which is being fired
     */
    protected void fireChannelRead(ChannelHandlerContext ctx, FullHttpMessage msg, boolean release,
                                   Http2Stream stream) {
        removeMessage(stream, release);
        HttpUtil.setContentLength(msg, msg.content().readableBytes());
        ctx.fireChannelRead(msg);
    }

    /**
     * Create a new {@link FullHttpMessage} based upon the current connection parameters
     *
     * @param stream The stream to create a message for
     * @param headers The headers associated with {@code stream}
     * @param validateHttpHeaders
     * <ul>
     * <li>{@code true} to validate HTTP headers in the http-codec</li>
     * <li>{@code false} not to validate HTTP headers in the http-codec</li>
     * </ul>
     * @param alloc The {@link ByteBufAllocator} to use to generate the content of the message
     * @throws Http2Exception
     */
    protected FullHttpMessage newMessage(Http2Stream stream, Http2Headers headers, boolean validateHttpHeaders,
                                         ByteBufAllocator alloc)
            throws Http2Exception {
        return connection.isServer() ? HttpConversionUtil.toFullHttpRequest(stream.id(), headers, alloc,
                validateHttpHeaders) : HttpConversionUtil.toHttpResponse(stream.id(), headers, alloc,
                                                                         validateHttpHeaders);
    }

    /**
     * Provides translation between HTTP/2 and HTTP header objects while ensuring the stream
     * is in a valid state for additional headers.
     *
     * @param ctx The context for which this message has been received.
     * Used to send informational header if detected.
     * @param stream The stream the {@code headers} apply to
     * @param headers The headers to process
     * @param endOfStream {@code true} if the {@code stream} has received the end of stream flag
     * @param allowAppend
     * <ul>
     * <li>{@code true} if headers will be appended if the stream already exists.</li>
     * <li>if {@code false} and the stream already exists this method returns {@code null}.</li>
     * </ul>
     * @param appendToTrailer
     * <ul>
     * <li>{@code true} if a message {@code stream} already exists then the headers
     * should be added to the trailing headers.</li>
     * <li>{@code false} then appends will be done to the initial headers.</li>
     * </ul>
     * @return The object used to track the stream corresponding to {@code stream}. {@code null} if
     *         {@code allowAppend} is {@code false} and the stream already exists.
     * @throws Http2Exception If the stream id is not in the correct state to process the headers request
     */
    protected FullHttpMessage processHeadersBegin(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers,
                boolean endOfStream, boolean allowAppend, boolean appendToTrailer) throws Http2Exception {
        FullHttpMessage msg = getMessage(stream);
        boolean release = true;
        if (msg == null) {
            msg = newMessage(stream, headers, validateHttpHeaders, ctx.alloc());
        } else if (allowAppend) {
            release = false;
            HttpConversionUtil.addHttp2ToHttpHeaders(stream.id(), headers, msg, appendToTrailer);
        } else {
            release = false;
            msg = null;
        }

        if (sendDetector.mustSendImmediately(msg)) {
            // Copy the message (if necessary) before sending. The content is not expected to be copied (or used) in
            // this operation but just in case it is used do the copy before sending and the resource may be released
            final FullHttpMessage copy = endOfStream ? null : sendDetector.copyIfNeeded(msg);
            fireChannelRead(ctx, msg, release, stream);
            return copy;
        }

        return msg;
    }

    /**
     * After HTTP/2 headers have been processed by {@link #processHeadersBegin} this method either
     * sends the result up the pipeline or retains the message for future processing.
     *
     * @param ctx The context for which this message has been received
     * @param stream The stream the {@code objAccumulator} corresponds to
     * @param msg The object which represents all headers/data for corresponding to {@code stream}
     * @param endOfStream {@code true} if this is the last event for the stream
     */
    private void processHeadersEnd(ChannelHandlerContext ctx, Http2Stream stream, FullHttpMessage msg,
                                   boolean endOfStream) {
        if (endOfStream) {
            // Release if the msg from the map is different from the object being forwarded up the pipeline.
            fireChannelRead(ctx, msg, getMessage(stream) != msg, stream);
        } else {
            putMessage(stream, msg);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                    throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg == null) {
            throw connectionError(PROTOCOL_ERROR, "Data Frame received for unknown stream id %d", streamId);
        }

        ByteBuf content = msg.content();
        final int dataReadableBytes = data.readableBytes();
        if (content.readableBytes() > maxContentLength - dataReadableBytes) {
            throw connectionError(INTERNAL_ERROR,
                            "Content length exceeded max of %d for stream id %d", maxContentLength, streamId);
        }

        content.writeBytes(data, data.readerIndex(), dataReadableBytes);

        if (endOfStream) {
            fireChannelRead(ctx, msg, false, stream);
        }

        // All bytes have been processed.
        return dataReadableBytes + padding;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                    boolean endOfStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = processHeadersBegin(ctx, stream, headers, endOfStream, true, true);
        if (msg != null) {
            processHeadersEnd(ctx, stream, msg, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                    short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = processHeadersBegin(ctx, stream, headers, endOfStream, true, true);
        if (msg != null) {
            // Add headers for dependency and weight.
            // See https://github.com/netty/netty/issues/5866
            if (streamDependency != Http2CodecUtil.CONNECTION_STREAM_ID) {
                msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(),
                        streamDependency);
            }
            msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), weight);

            processHeadersEnd(ctx, stream, msg, endOfStream);
        }
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg != null) {
            onRstStreamRead(stream, msg);
        }
        ctx.fireExceptionCaught(Http2Exception.streamError(streamId, Http2Error.valueOf(errorCode),
                "HTTP/2 to HTTP layer caught stream reset"));
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
        // A push promise should not be allowed to add headers to an existing stream
        Http2Stream promisedStream = connection.stream(promisedStreamId);
        FullHttpMessage msg = processHeadersBegin(ctx, promisedStream, headers, false, false, false);
        if (msg == null) {
            throw connectionError(PROTOCOL_ERROR, "Push Promise Frame received for pre-existing stream id %d",
                            promisedStreamId);
        }

        msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), streamId);
        msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(),
                Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT);

        processHeadersEnd(ctx, promisedStream, msg, false);
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        if (propagateSettings) {
            // Provide an interface for non-listeners to capture settings
            ctx.fireChannelRead(settings);
        }
    }

    /**
     * Called if a {@code RST_STREAM} is received but we have some data for that stream.
     */
    protected void onRstStreamRead(Http2Stream stream, FullHttpMessage msg) {
        removeMessage(stream, true);
    }

    /**
     * Allows messages to be sent up the pipeline before the next phase in the
     * HTTP message flow is detected.
     */
    private interface ImmediateSendDetector {
        /**
         * Determine if the response should be sent immediately, or wait for the end of the stream
         *
         * @param msg The response to test
         * @return {@code true} if the message should be sent immediately
         *         {@code false) if we should wait for the end of the stream
         */
        boolean mustSendImmediately(FullHttpMessage msg);

        /**
         * Determine if a copy must be made after an immediate send happens.
         * <p>
         * An example of this use case is if a request is received
         * with a 'Expect: 100-continue' header. The message will be sent immediately,
         * and the data will be queued and sent at the end of the stream.
         *
         * @param msg The message which has just been sent due to {@link #mustSendImmediately(FullHttpMessage)}
         * @return A modified copy of the {@code msg} or {@code null} if a copy is not needed.
         */
        FullHttpMessage copyIfNeeded(FullHttpMessage msg);
    }
}
