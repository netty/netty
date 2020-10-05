/*
 * Copyright 2020 The Netty Project
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
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * This adapter provides just header/data events from the HTTP message flow defined
 * in <a href="https://tools.ietf.org/html/rfc7540#section-8.1">[RFC 7540], Section 8.1</a>.
 * <p>
 * See {@link HttpToHttp2ConnectionHandler} to get translation from HTTP/1.x objects to HTTP/2 frames for writes.
 */
@UnstableApi
public final class InboundHttp2ToHttpObjectAdapter extends Http2EventAdapter {

    private final int maxContentLength;
    private final Http2Connection.PropertyKey messageKey;
    private final boolean propagateSettings;
    protected final Http2Connection connection;
    protected final boolean validateHttpHeaders;

    protected InboundHttp2ToHttpObjectAdapter(Http2Connection connection, int maxContentLength,
                                              boolean validateHttpHeaders, boolean propagateSettings) {
        this.connection = checkNotNull(connection, "connection");
        this.maxContentLength = ObjectUtil.checkPositive(maxContentLength, "maxContentLength");
        this.validateHttpHeaders = validateHttpHeaders;
        this.propagateSettings = propagateSettings;
        messageKey = connection.newKey();
    }

    protected void firstHeaderReceived(Http2Stream stream) {
        stream.setProperty(messageKey, Boolean.TRUE);
    }

    protected Boolean isFirstHeaderReceived(Http2Stream stream) {
        return stream.getProperty(messageKey);
    }

    protected void removeHeaderRecord(Http2Stream stream) {
        stream.removeProperty(messageKey);
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
        removeHeaderRecord(stream);
    }

    /**
     * Called if a {@code RST_STREAM} is received but we have some data for that stream.
     */
    protected void onRstStreamRead(Http2Stream stream, FullHttpMessage msg) {
        removeHeaderRecord(stream);
    }

    /**
     * Set final headers and fire a channel read event
     *
     * @param ctx The context to fire the event on
     * @param msg The message to send
     */
    protected void fireChannelRead(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof FullHttpMessage) {
            HttpUtil.setContentLength((FullHttpMessage) msg, ((FullHttpMessage) msg).content().readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * Create a new {@link HttpMessage} based upon the current connection parameters
     *
     * @param stream              The stream to create a message for
     * @param headers             The headers associated with {@code stream}
     * @param validateHttpHeaders <ul>
     *                            <li>{@code true} to validate HTTP headers in the http-codec</li>
     *                            <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *                            </ul>
     * @param alloc               The {@link ByteBufAllocator} to use to generate the content of the message
     * @param endOfStream         Set to {@code true} if only {@link Http2Headers} are available
     *                            in this {@link Http2Stream}
     * @throws Http2Exception If there is an error when creating {@link HttpMessage} from
     *                        {@link Http2Stream} or {@link Http2Headers}
     */
    protected HttpMessage newMessage(Http2Stream stream, Http2Headers headers, boolean validateHttpHeaders,
                                     ByteBufAllocator alloc, boolean endOfStream) throws Http2Exception {
        /*
         * If `endOfStream` is `true` then we only have headers and no data.
         * So in this case, we'll create `FullHttpMessage`.
         *
         * If `endOfStream` is `false` we will be getting data after headers.
         * So in this case, we'll create `HttpRequest` and set `TRANSFER_ENCODING:CHUNKED`
         * and write received data in `HttpContent`.
         */
        if (endOfStream) {
            if (connection.isServer()) {
                return HttpConversionUtil.toFullHttpRequest(stream.id(), headers, alloc, validateHttpHeaders);
            } else {
                return HttpConversionUtil.toFullHttpResponse(stream.id(), headers, alloc, validateHttpHeaders);
            }
        } else {
            if (connection.isServer()) {
                HttpRequest httpRequest = HttpConversionUtil.toHttpRequest(stream.id(), headers, validateHttpHeaders);
                httpRequest.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                return httpRequest;
            } else {
                HttpResponse httpResponse = HttpConversionUtil.toHttpResponse(stream.id(), headers,
                        validateHttpHeaders);
                httpResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                return httpResponse;
            }
        }
    }

    /**
     * Provides translation between HTTP/2 and HTTP header objects while ensuring the stream
     * is in a valid state for additional headers.
     *
     * @param ctx             The context for which this message has been received.
     *                        Used to send informational header if detected.
     * @param stream          The stream the {@code headers} apply to
     * @param headers         The headers to process
     * @param endOfStream     Set to {@code true} if only {@link Http2Headers} are available
     *                        in this {@link Http2Stream}
     * @param allowAppend     <ul>
     *                        <li>{@code true} if headers will be appended if the stream already exists.</li>
     *                        <li>if {@code false} and the stream already exists this method returns {@code null}.</li>
     *                        </ul>
     * @param appendToTrailer <ul>
     *                        <li>{@code true} if a message {@code stream} already exists then the headers
     *                        should be added to the trailing headers.</li>
     *                        <li>{@code false} then appends will be done to the initial headers.</li>
     *                        </ul>
     * @return The object used to track the stream corresponding to {@code stream}. {@code null} if
     * {@code allowAppend} is {@code false} and the stream already exists.
     * @throws Http2Exception If the stream id is not in the correct state to process the headers request
     */
    protected HttpMessage processHeadersBegin(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers,
                                              boolean endOfStream, boolean allowAppend, boolean appendToTrailer)
            throws Http2Exception {

        /*
         * If First Header is not received for this stream, we'll go ahead and create a new HttpMessage.
         *
         * If First Header is received and `allowAppend` is `true` then we've received trailer header.
         * We'll convert it to `DefaultLastHttpContent`.
         */
        if (isFirstHeaderReceived(stream) == null) {
            return newMessage(stream, headers, validateHttpHeaders, ctx.alloc(), endOfStream);
        } else if (allowAppend) {
            DefaultLastHttpContent defaultLastHttpContent = new DefaultLastHttpContent(new EmptyByteBuf(ctx.alloc()),
                    false);
            HttpHeaders httpHeaders = defaultLastHttpContent.trailingHeaders();
            HttpConversionUtil.addHttp2ToHttpHeaders(stream.id(), headers, httpHeaders, HttpVersion.HTTP_1_1,
                    appendToTrailer, connection.isServer());
        }

        return null;
    }

    /**
     * After HTTP/2 headers have been processed by {@link #processHeadersBegin} this method either
     * sends the result up the pipeline or retains the message for future processing.
     *
     * @param ctx         The context for which this message has been received
     * @param stream      The stream the {@code objAccumulator} corresponds to
     * @param msg         The object which represents all headers/data for corresponding to {@code stream}
     * @param endOfStream {@code true} if this is the last event for the stream
     */
    private void processHeadersEnd(ChannelHandlerContext ctx, Http2Stream stream, HttpObject msg,
                                   boolean endOfStream) {
        fireChannelRead(ctx, msg);
        if (!endOfStream) {
            firstHeaderReceived(stream);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        if (isFirstHeaderReceived(stream) == null) {
            throw connectionError(PROTOCOL_ERROR, "Data Frame received for unknown stream id %d", streamId);
        }

        ByteBuf content = ctx.alloc().buffer();
        int dataReadableBytes = data.readableBytes();
        if (dataReadableBytes > maxContentLength) {
            throw connectionError(INTERNAL_ERROR,
                    "Content length exceeded max of %d for stream id %d", maxContentLength, streamId);
        }

        content.writeBytes(data);

        if (endOfStream) {
            if (dataReadableBytes != 0) {
                fireChannelRead(ctx, new DefaultHttp2TranslatedHttpContent(content, streamId));
            }
            fireChannelRead(ctx, new DefaultHttp2TranslatedLastHttpContent(streamId));
        } else {
            fireChannelRead(ctx, new DefaultHttp2TranslatedHttpContent(content, streamId));
        }

        return dataReadableBytes;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg = processHeadersBegin(ctx, stream, headers, endOfStream, true, true);
        if (msg != null) {
            processHeadersEnd(ctx, stream, msg, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream)
            throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg = processHeadersBegin(ctx, stream, headers, endOfStream, true, true);
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
        ctx.fireExceptionCaught(Http2Exception.streamError(streamId, Http2Error.valueOf(errorCode),
                "HTTP/2 to HTTP layer caught stream reset"));
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers,
                                  int padding) throws Http2Exception {
        // A push promise should not be allowed to add headers to an existing stream
        Http2Stream promisedStream = connection.stream(promisedStreamId);
        if (headers.status() == null) {
            // A PUSH_PROMISE frame has no Http response status.
            // https://tools.ietf.org/html/rfc7540#section-8.2.1
            // Server push is semantically equivalent to a server responding to a
            // request; however, in this case, that request is also sent by the
            // server, as a PUSH_PROMISE frame.
            headers.status(OK.codeAsText());
        }
        HttpMessage msg = processHeadersBegin(ctx, promisedStream, headers, false, false, false);
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
}
