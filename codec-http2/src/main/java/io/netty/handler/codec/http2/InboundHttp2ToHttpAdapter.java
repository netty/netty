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
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.IntObjectHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Decodes HTTP/2 data and associated streams into {@link HttpMessage}s and {@link HttpContent}s.
 */
public class InboundHttp2ToHttpAdapter extends Http2ConnectionAdapter implements Http2FrameObserver {

    private final long maxContentLength;
    private final boolean validateHttpHeaders;
    private final Http2Connection connection;
    private final IntObjectMap<Http2HttpMessageAccumulator> messageMap;

    private static final Set<String> HEADERS_TO_EXCLUDE;
    private static final Map<String, String> HEADER_NAME_TRANSLATIONS_REQUEST;
    private static final Map<String, String> HEADER_NAME_TRANSLATIONS_RESPONSE;

    static {
        HEADERS_TO_EXCLUDE = new HashSet<String>();
        HEADER_NAME_TRANSLATIONS_REQUEST = new HashMap<String, String>();
        HEADER_NAME_TRANSLATIONS_RESPONSE = new HashMap<String, String>();
        for (Http2Headers.HttpName http2HeaderName : Http2Headers.HttpName.values()) {
            HEADERS_TO_EXCLUDE.add(http2HeaderName.value());
        }

        HEADER_NAME_TRANSLATIONS_RESPONSE.put(Http2Headers.HttpName.AUTHORITY.value(),
                        Http2HttpHeaders.Names.AUTHORITY.toString());
        HEADER_NAME_TRANSLATIONS_RESPONSE.put(Http2Headers.HttpName.SCHEME.value(),
                        Http2HttpHeaders.Names.SCHEME.toString());
        HEADER_NAME_TRANSLATIONS_REQUEST.putAll(HEADER_NAME_TRANSLATIONS_RESPONSE);
        HEADER_NAME_TRANSLATIONS_RESPONSE.put(Http2Headers.HttpName.PATH.value(),
                        Http2HttpHeaders.Names.PATH.toString());
    }

    /**
     * Creates a new instance
     *
     * @param connection The object which will provide connection notification events for the current connection
     * @param maxContentLength the maximum length of the message content. If the length of the message content exceeds
     *        this value, a {@link TooLongFrameException} will be raised.
     * @throws NullPointerException If {@code connection} is null
     * @throws IllegalArgumentException If {@code maxContentLength} is less than or equal to {@code 0}
     */
    public InboundHttp2ToHttpAdapter(Http2Connection connection, long maxContentLength) throws NullPointerException,
                    IllegalArgumentException {
        this(connection, maxContentLength, true);
    }

    /**
     * Creates a new instance
     *
     * @param connection The object which will provide connection notification events for the current connection
     * @param maxContentLength the maximum length of the message content. If the length of the message content exceeds
     *        this value, a {@link TooLongFrameException} will be raised.
     * @param validateHeaders {@code true} if http headers should be validated
     * @throws NullPointerException If {@code connection} is null
     * @throws IllegalArgumentException If {@code maxContentLength} is less than or equal to {@code 0}
     */
    public InboundHttp2ToHttpAdapter(Http2Connection connection, long maxContentLength, boolean validateHttpHeaders)
                    throws NullPointerException, IllegalArgumentException {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength must be a positive integer: " + maxContentLength);
        }
        this.maxContentLength = maxContentLength;
        this.validateHttpHeaders = validateHttpHeaders;
        this.connection = connection;
        this.messageMap = new IntObjectHashMap<Http2HttpMessageAccumulator>();
        this.connection.addListener(this);
    }

    @Override
    public void streamRemoved(Http2Stream stream) {
        removeMessage(stream.id());
    }

    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream,
                    boolean endOfSegment) throws Http2Exception {
        // Padding is already stripped out of data by super class
        Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
        if (msgAccumulator == null) {
            throw Http2Exception.protocolError("Data Frame recieved for unknown stream id %d", streamId);
        }

        try {
            msgAccumulator.add(endOfStream ? new DefaultLastHttpContent(data, validateHttpHeaders)
                            : new DefaultHttpContent(data), ctx);
        } catch (TooLongFrameException e) {
            removeMessage(streamId);
            throw Http2Exception.format(Http2Error.INTERNAL_ERROR,
                            "Content length exceeded max of %d for stream id %d", maxContentLength, streamId);
        }

        if (endOfStream) {
            msgAccumulator.endOfStream(ctx);
            removeMessage(streamId);
        }
    }

    /**
     * Extracts the common initial header processing and internal tracking
     *
     * @param streamId The stream id the {@code headers} apply to
     * @param headers The headers to process
     * @param allowAppend {@code true} if headers will be appended if the stream already exists. if {@code false} and
     *        the stream already exists this method returns {@code null}.
     * @return The object used to track the stream corresponding to {@code streamId}. {@code null} if
     *         {@code allowAppend} is {@code false} and the stream already exists.
     * @throws Http2Exception If the stream id is not in the correct state to process the headers request
     */
    protected Http2HttpMessageAccumulator processHeadersBegin(int streamId, Http2Headers headers, boolean allowAppend)
                    throws Http2Exception {
        Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
        try {
            if (msgAccumulator == null) {
                msgAccumulator = connection.isServer() ? newHttpRequestAccumulator(headers)
                                : newHttpResponseAccumulator(headers);
            } else if (allowAppend) {
                if (msgAccumulator.headerConsumed()) {
                    if (msgAccumulator.trailerConsumed()) {
                        throw new IllegalStateException("Header received and trailer already consumed");
                    } else {
                        msgAccumulator.trailer(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHttpHeaders));
                    }
                }
                msgAccumulator.add(headers);
            } else {
                return null;
            }
            msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_ID, streamId);
        } catch (IllegalStateException e) {
            removeMessage(streamId);
            throw Http2Exception.protocolError("Headers Frame recieved for stream id %d which is in an invalid state",
                            streamId);
        } catch (Http2Exception e) {
            removeMessage(streamId);
            throw e;
        }

        return msgAccumulator;
    }

    /**
     * Extracts the common final header processing and internal tracking
     *
     * @param ctx The context for which this message has been received
     * @param streamId The stream id the {@code msgAccumulator} corresponds to
     * @param msgAccumulator The object which represents all data for corresponding to {@code streamId}
     * @param endOfStream {@code true} if this is the last event for the stream
     */
    protected void processHeadersEnd(ChannelHandlerContext ctx, int streamId,
                    Http2HttpMessageAccumulator msgAccumulator, boolean endOfStream) {
        if (endOfStream) {
            msgAccumulator.endOfStream(ctx);
            removeMessage(streamId);
        } else {
            putMessage(streamId, msgAccumulator);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                    boolean endOfStream, boolean endSegment) throws Http2Exception {
        Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(streamId, headers, true);
        processHeadersEnd(ctx, streamId, msgAccumulator, endOfStream);
    }

    /**
     * Set/clear all HTTP/1.x headers related to stream dependencies
     *
     * @param msgAccumulator The object which caches the HTTP/1.x headers
     * @param streamDependency The stream id for which the {@code msgAccumulator} is dependent on
     * @param weight The dependency weight
     * @param exclusive The exlusive HTTP/2 flag
     * @throws IllegalStateException If the {@code msgAccumulator} is not in a valid state to change headers
     */
    protected void setDependencyHeaders(Http2HttpMessageAccumulator msgAccumulator, int streamDependency, short weight,
                    boolean exclusive) throws IllegalStateException {
        if (streamDependency != 0) {
            msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID, streamDependency);
            msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_EXCLUSIVE, exclusive);
            msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_WEIGHT, weight);
        } else {
            msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID);
            msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_EXCLUSIVE);
            msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_WEIGHT);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                    short weight, boolean exclusive, int padding, boolean endOfStream, boolean endSegment)
                    throws Http2Exception {
        Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(streamId, headers, true);
        try {
            setDependencyHeaders(msgAccumulator, streamDependency, weight, exclusive);
        } catch (IllegalStateException e) {
            removeMessage(streamId);
            throw Http2Exception.protocolError("Headers Frame recieved for stream id %d which is in an invalid state",
                            streamId);
        }
        processHeadersEnd(ctx, streamId, msgAccumulator, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                    boolean exclusive) throws Http2Exception {
        Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
        if (msgAccumulator == null) {
            throw Http2Exception.protocolError("Priority Frame recieved for unknown stream id %d", streamId);
        }

        try {
            setDependencyHeaders(msgAccumulator, streamDependency, weight, exclusive);
        } catch (IllegalStateException e) {
            removeMessage(streamId);
            throw Http2Exception.protocolError("Priority Frame recieved for stream id %d which is in an invalid state",
                            streamId);
        }
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
        if (msgAccumulator != null) {
            removeMessage(streamId);
        }
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
        // NOOP
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        // NOOP
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
        // NOOP
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
        // NOOP
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers,
                    int padding) throws Http2Exception {
        // Do not allow adding of headers to existing Http2HttpMessageAccumulator
        // according to spec (http://tools.ietf.org/html/draft-ietf-httpbis-http2-13#section-6.6) there must
        // be a CONTINUATION frame for more headers
        Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(promisedStreamId, headers, false);
        if (msgAccumulator == null) {
            throw Http2Exception.protocolError("Push Promise Frame recieved for pre-existing stream id %d",
                            promisedStreamId);
        }

        try {
            msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_PROMISE_ID, streamId);
        } catch (IllegalStateException e) {
            removeMessage(streamId);
            throw Http2Exception.protocolError(
                            "Push Promise Frame recieved for stream id %d which is in an invalid state",
                            promisedStreamId);
        }

        processHeadersEnd(ctx, promisedStreamId, msgAccumulator, false);
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                    throws Http2Exception {
        // NOOP
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                    throws Http2Exception {
        // NOOP
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                    ByteBuf payload) {
        // NOOP
    }

    protected Http2HttpMessageAccumulator putMessage(int streamId, Http2HttpMessageAccumulator message) {
        return messageMap.put(streamId, message);
    }

    protected Http2HttpMessageAccumulator getMessage(int streamId) {
        return messageMap.get(streamId);
    }

    protected Http2HttpMessageAccumulator removeMessage(int streamId) {
        return messageMap.remove(streamId);
    }

    /**
     * Translate HTTP/2 headers into an object which can build the corresponding HTTP/1.x response objects
     *
     * @param http2Headers The HTTP/2 headers corresponding to a new stream
     * @return Collector for HTTP/1.x objects
     * @throws Http2Exception If any of the HTTP/2 headers to not translate to valid HTTP/1.x headers
     * @throws IllegalStateException If this object is not in the correct state to accept additional headers
     */
    private Http2HttpMessageAccumulator newHttpResponseAccumulator(Http2Headers http2Headers) throws Http2Exception,
                    IllegalStateException {
        HttpResponseStatus status = null;
        try {
            status = HttpResponseStatus.parseLine(http2Headers.status());
        } catch (Exception e) {
            throw Http2Exception.protocolError(
                            "Unrecognized HTTP status code '%s' encountered in translation to HTTP/1.x",
                            http2Headers.status());
        }
        // HTTP/2 does not define a way to carry the version or reason phrase that is included in an HTTP/1.1
        // status line.
        Http2HttpMessageAccumulator messageAccumulator = new Http2HttpMessageAccumulator(new DefaultHttpResponse(
                        HttpVersion.HTTP_1_1, status, validateHttpHeaders));
        messageAccumulator.add(http2Headers);
        return messageAccumulator;
    }

    /**
     * Translate HTTP/2 headers into an object which can build the corresponding HTTP/1.x request objects
     *
     * @param http2Headers The HTTP/2 headers corresponding to a new stream
     * @return Collector for HTTP/1.x objects
     * @throws Http2Exception If any of the HTTP/2 headers to not translate to valid HTTP/1.x headers
     * @throws IllegalStateException If this object is not in the correct state to accept additional headers
     */
    private Http2HttpMessageAccumulator newHttpRequestAccumulator(Http2Headers http2Headers) throws Http2Exception,
                    IllegalStateException {
        // HTTP/2 does not define a way to carry the version identifier that is
        // included in the HTTP/1.1 request line.
        Http2HttpMessageAccumulator messageAccumulator = new Http2HttpMessageAccumulator(new DefaultHttpRequest(
                        HttpVersion.HTTP_1_1, HttpMethod.valueOf(http2Headers.method()), http2Headers.path(),
                        validateHttpHeaders));
        messageAccumulator.add(http2Headers);
        return messageAccumulator;
    }

    /**
     * Provides a container to collect HTTP/1.x objects until the end of the stream has been reached
     */
    protected final class Http2HttpMessageAccumulator {
        private HttpMessage message;
        private LastHttpContent trailer;
        private long contentLength;

        /**
         * Creates a new instance
         *
         * @param message The HTTP/1.x object which represents the headers
         */
        public Http2HttpMessageAccumulator(HttpMessage message) {
            if (message == null) {
                throw new NullPointerException("message");
            }
            this.message = message;
            this.trailer = null;
            this.contentLength = 0;
        }

        /**
         * Set a HTTP/1.x header
         *
         * @param name The name of the header
         * @param value The value of the header
         * @return The headers object after the set operation
         * @throws IllegalStateException If this object is not in the correct state to accept additional headers
         */
        public HttpHeaders setHeader(CharSequence name, Object value) throws IllegalStateException {
            HttpHeaders headers = currentHeaders();
            if (headers == null) {
                throw new IllegalStateException("Headers object is null");
            }
            return headers.set(name, value);
        }

        /**
         * Removes the header with the specified name.
         *
         * @param name The name of the header to remove
         * @return {@code true} if and only if at least one entry has been removed
         * @throws IllegalStateException If this object is not in the correct state to accept additional headers
         */
        public boolean removeHeader(CharSequence name) throws IllegalStateException {
            HttpHeaders headers = currentHeaders();
            if (headers == null) {
                throw new IllegalStateException("Headers object is null");
            }
            return headers.remove(name);
        }

        /**
         * Send the headers that have been accumulated so far, if they have not already been sent
         *
         * @param ctx The channel context for which to propagate events
         * @param setContentLength {@code true} to set the Content-Length header
         * @return {@code true} If a non-trailer header was fired to {@code ctx}, {@code false} otherwise
         */
        protected boolean sendHeaders(ChannelHandlerContext ctx, boolean setContentLength) {
            HttpHeaders headers = currentHeaders();
            if (headers == null) {
                return false;
            }

            // Transfer-Encoding header is not valid
            headers.remove(HttpHeaders.Names.TRANSFER_ENCODING);
            headers.remove(HttpHeaders.Names.TRAILER);

            // Initial header and trailer header will never need to be sent at the same time
            // the behavior is that all header events will be appended to the initial header
            // until some data is received, and only then will the trailer be used
            if (!headerConsumed()) {
                // The Connection and Keep-Alive headers are no longer valid
                HttpHeaderUtil.setKeepAlive(message, true);
                if (setContentLength) {
                    HttpHeaderUtil.setContentLength(message, contentLength);
                }

                ctx.fireChannelRead(message);
                message = null;
                return true;
            } else if (trailerExists()) {
                ctx.fireChannelRead(trailer);
                trailer = LastHttpContent.EMPTY_LAST_CONTENT;
            }
            return false;
        }

        /**
         * Called when the end of the stream is encountered
         *
         * @param ctx The channel context for which to propagate events
         */
        public void endOfStream(ChannelHandlerContext ctx) {
            if (sendHeaders(ctx, true)) {
                ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
            }
        }

        /**
         * Add a HTTP/1.x object which represents part of the message body
         *
         * @param httpContent The content to add
         * @param ctx The channel context for which to propagate events
         * @throws TooLongFrameException If the {@code contentLength} is exceeded with the addition of the
         *         {@code httpContent}
         */
        public void add(HttpContent httpContent, ChannelHandlerContext ctx) throws TooLongFrameException {
            ByteBuf content = httpContent.content();
            if (contentLength > maxContentLength - content.readableBytes()) {
                throw new TooLongFrameException("HTTP/2 content length exceeded " + maxContentLength + " bytes.");
            }

            contentLength += content.readableBytes();
            sendHeaders(ctx, false);
            ctx.fireChannelRead(httpContent.retain());
        }

        /**
         * Extend the current set of HTTP/1.x headers
         *
         * @param http2Headers The HTTP/2 headers to be added
         * @throws Http2Exception If any HTTP/2 headers do not map to HTTP/1.x headers
         * @throws IllegalStateException If this object is not in the correct state to accept additional headers
         */
        public void add(Http2Headers http2Headers) throws Http2Exception, IllegalStateException {
            add(http2Headers, InboundHttp2ToHttpAdapter.this.connection.isServer() ? HEADER_NAME_TRANSLATIONS_REQUEST
                            : HEADER_NAME_TRANSLATIONS_RESPONSE);
        }

        /**
         * Extend the current set of HTTP/1.x headers
         *
         * @param http2Headers The HTTP/2 headers to be added
         * @param headerTranslations Translation map from HTTP/2 headers to HTTP/1.x headers
         * @throws Http2Exception If any HTTP/2 headers do not map to HTTP/1.x headers
         * @throws IllegalStateException If this object is not in the correct state to accept additional headers
         */
        protected void add(Http2Headers http2Headers, Map<String, String> headerTranslations) throws Http2Exception,
                        IllegalStateException {
            HttpHeaders headers = currentHeaders();
            if (headers == null) {
                throw new IllegalStateException("Headers object is null");
            }

            // http://tools.ietf.org/html/draft-ietf-httpbis-http2-13#section-8.1.2.1
            // All headers that start with ':' are only valid in HTTP/2 context
            Iterator<Entry<String, String>> itr = http2Headers.iterator();
            while (itr.hasNext()) {
                Entry<String, String> entry = itr.next();
                String translatedName = headerTranslations.get(entry.getKey());
                if (translatedName != null || !HEADERS_TO_EXCLUDE.contains(entry.getKey())) {
                    if (translatedName == null) {
                        translatedName = entry.getKey();
                    }

                    if (translatedName.isEmpty() || translatedName.charAt(0) == ':') {
                        throw Http2Exception.protocolError(
                                        "Unknown HTTP/2 header '%s' encountered in translation to HTTP/1.x",
                                        translatedName);
                    } else {
                        headers.add(translatedName, entry.getValue());
                    }
                }
            }
        }

        /**
         * Set the current trailer header
         *
         * @param trailer The object which represents the trailing headers
         */
        public void trailer(LastHttpContent trailer) {
            this.trailer = trailer;
        }

        /**
         * Determine if the initial header and continuations have been processed and sent up the pipeline
         *
         * @return {@code true} if the initial header and continuations have been processed and sent up the pipeline
         */
        public boolean headerConsumed() {
            return message == null;
        }

        /**
         * Determine if the trailing header and continuations have been processed and sent up the pipeline
         *
         * @return {@code true} if the trailing header and continuations have been processed and sent up the pipeline
         */
        public boolean trailerConsumed() {
            return trailer != null && trailer.equals(LastHttpContent.EMPTY_LAST_CONTENT);
        }

        /**
         * Determine if there is a trailing header that has not yet been sent up the pipeline
         *
         * @return {@code true} if there is a trailing header that has not yet been sent up the pipeline
         */
        public boolean trailerExists() {
            return trailer != null && !trailerConsumed();
        }

        /**
         * Obtain the current object for accumulating headers
         *
         * @return The primary (non-trailer) header if it exists, otherwise the trailer header
         */
        private HttpHeaders currentHeaders() {
            if (headerConsumed()) {
                return trailerExists() ? trailer.trailingHeaders() : null;
            }
            return message.headers();
        }
    }
}
