/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * Decodes {@link SpdySynStreamFrame}s, {@link SpdySynReplyFrame}s,
 * and {@link SpdyDataFrame}s into {@link HttpRequest}s and {@link HttpResponse}s.
 */
public class SpdyHttpDecoder extends OneToOneDecoder {

    private final int spdyVersion;
    private final int maxContentLength;
    private final Map<Integer, HttpMessage> messageMap;

    /**
     * Creates a new instance.
     *
     * @param spdyVersion the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public SpdyHttpDecoder(SpdyVersion spdyVersion, int maxContentLength) {
        this(spdyVersion, maxContentLength, new HashMap<Integer, HttpMessage>());
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @param spdyVersion the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     * @param messageMap the {@link Map} used to hold partially received messages.
     */
    protected SpdyHttpDecoder(SpdyVersion spdyVersion, int maxContentLength, Map<Integer, HttpMessage> messageMap) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " + maxContentLength);
        }
        this.spdyVersion = spdyVersion.getVersion();
        this.maxContentLength = maxContentLength;
        this.messageMap = messageMap;
    }

    protected HttpMessage putMessage(int streamId, HttpMessage message) {
        return messageMap.put(streamId, message);
    }

    protected HttpMessage getMessage(int streamId) {
        return messageMap.get(streamId);
    }

    protected HttpMessage removeMessage(int streamId) {
        return messageMap.remove(streamId);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        if (msg instanceof SpdySynStreamFrame) {

            // HTTP requests/responses are mapped one-to-one to SPDY streams.
            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamId = spdySynStreamFrame.getStreamId();

            if (isServerId(streamId)) {
                // SYN_STREAM frames initiated by the server are pushed resources
                int associatedToStreamId = spdySynStreamFrame.getAssociatedToStreamId();

                // If a client receives a SYN_STREAM with an Associated-To-Stream-ID of 0
                // it must reply with a RST_STREAM with error code INVALID_STREAM
                if (associatedToStreamId == 0) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INVALID_STREAM);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

                String URL = SpdyHeaders.getUrl(spdyVersion, spdySynStreamFrame);
                SpdyHeaders.removeUrl(spdyVersion, spdySynStreamFrame);

                // If a client receives a SYN_STREAM without a 'url' header
                // it must reply with a RST_STREAM with error code PROTOCOL_ERROR
                if (URL == null) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

                // If a client receives a response with a truncated header block,
                // reply with a RST_STREAM with error code INTERNAL_ERROR.
                if (spdySynStreamFrame.isTruncated()) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                            new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INTERNAL_ERROR);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

                try {
                    HttpResponse httpResponse = createHttpResponse(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID, Associated-To-Stream-ID, Priority, and URL as headers
                    SpdyHttpHeaders.setStreamId(httpResponse, streamId);
                    SpdyHttpHeaders.setAssociatedToStreamId(httpResponse, associatedToStreamId);
                    SpdyHttpHeaders.setPriority(httpResponse, spdySynStreamFrame.getPriority());
                    SpdyHttpHeaders.setUrl(httpResponse, URL);

                    if (spdySynStreamFrame.isLast()) {
                        HttpHeaders.setContentLength(httpResponse, 0);
                        return httpResponse;
                    } else {
                        // Response body will follow in a series of Data Frames
                        putMessage(streamId, httpResponse);
                    }
                } catch (Exception e) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }
            } else {
                // SYN_STREAM frames initiated by the client are HTTP requests

                // If a client sends a request with a truncated header block, the server must
                // reply with a HTTP 431 REQUEST HEADER FIELDS TOO LARGE reply.
                if (spdySynStreamFrame.isTruncated()) {
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders.setStatus(spdyVersion,
                            spdySynReplyFrame,
                            HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE);
                    SpdyHeaders.setVersion(spdyVersion, spdySynReplyFrame, HttpVersion.HTTP_1_0);
                    Channels.write(ctx, Channels.future(channel), spdySynReplyFrame);
                }

                try {
                    HttpRequest httpRequest = createHttpRequest(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID as a header
                    SpdyHttpHeaders.setStreamId(httpRequest, streamId);

                    if (spdySynStreamFrame.isLast()) {
                        return httpRequest;
                    } else {
                        // Request body will follow in a series of Data Frames
                        putMessage(streamId, httpRequest);
                    }
                } catch (Exception e) {
                    // If a client sends a SYN_STREAM without all of the method, url (host and path),
                    // scheme, and version headers the server must reply with a HTTP 400 BAD REQUEST reply.
                    // Also sends HTTP 400 BAD REQUEST reply if header name/value pairs are invalid
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders.setStatus(spdyVersion, spdySynReplyFrame, HttpResponseStatus.BAD_REQUEST);
                    SpdyHeaders.setVersion(spdyVersion, spdySynReplyFrame, HttpVersion.HTTP_1_0);
                    Channels.write(ctx, Channels.future(channel), spdySynReplyFrame);
                }
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamId = spdySynReplyFrame.getStreamId();

            // If a client receives a SYN_REPLY with a truncated header block,
            // reply with a RST_STREAM frame with error code INTERNAL_ERROR.
            if (spdySynReplyFrame.isTruncated()) {
                SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INTERNAL_ERROR);
                Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
            }

            try {
                HttpResponse httpResponse = createHttpResponse(spdyVersion, spdySynReplyFrame);

                // Set the Stream-ID as a header
                SpdyHttpHeaders.setStreamId(httpResponse, streamId);

                if (spdySynReplyFrame.isLast()) {
                    HttpHeaders.setContentLength(httpResponse, 0);
                    return httpResponse;
                } else {
                    // Response body will follow in a series of Data Frames
                    putMessage(streamId, httpResponse);
                }
            } catch (Exception e) {
                // If a client receives a SYN_REPLY without valid status and version headers
                // the client must reply with a RST_STREAM frame indicating a PROTOCOL_ERROR
                SpdyRstStreamFrame spdyRstStreamFrame =
                    new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
            }

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamId = spdyHeadersFrame.getStreamId();
            HttpMessage httpMessage = getMessage(streamId);

            // If message is not in map discard HEADERS frame.
            if (httpMessage == null) {
                return null;
            }

            // Ignore trailers in a truncated HEADERS frame.
            if (!spdyHeadersFrame.isTruncated()) {
                for (Map.Entry<String, String> e : spdyHeadersFrame.headers()) {
                    httpMessage.headers().add(e.getKey(), e.getValue());
                }
            }

            if (spdyHeadersFrame.isLast()) {
                HttpHeaders.setContentLength(httpMessage, httpMessage.getContent().readableBytes());
                removeMessage(streamId);
                return httpMessage;
            }

        } else if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamId = spdyDataFrame.getStreamId();
            HttpMessage httpMessage = getMessage(streamId);

            // If message is not in map discard Data Frame.
            if (httpMessage == null) {
                return null;
            }

            ChannelBuffer content = httpMessage.getContent();
            if (content.readableBytes() > maxContentLength - spdyDataFrame.getData().readableBytes()) {
                removeMessage(streamId);
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength + " bytes.");
            }

            if (content == ChannelBuffers.EMPTY_BUFFER) {
                content = ChannelBuffers.dynamicBuffer(channel.getConfig().getBufferFactory());
                content.writeBytes(spdyDataFrame.getData());
                httpMessage.setContent(content);
            } else {
                content.writeBytes(spdyDataFrame.getData());
            }

            if (spdyDataFrame.isLast()) {
                HttpHeaders.setContentLength(httpMessage, content.readableBytes());
                removeMessage(streamId);
                return httpMessage;
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            int streamId = spdyRstStreamFrame.getStreamId();
            removeMessage(streamId);
        }

        return null;
    }

    private static HttpRequest createHttpRequest(int spdyVersion, SpdyHeadersFrame requestFrame)
            throws Exception {
        // Create the first line of the request from the name/value pairs
        HttpMethod  method      = SpdyHeaders.getMethod(spdyVersion, requestFrame);
        String      url         = SpdyHeaders.getUrl(spdyVersion, requestFrame);
        HttpVersion httpVersion = SpdyHeaders.getVersion(spdyVersion, requestFrame);
        SpdyHeaders.removeMethod(spdyVersion, requestFrame);
        SpdyHeaders.removeUrl(spdyVersion, requestFrame);
        SpdyHeaders.removeVersion(spdyVersion, requestFrame);

        HttpRequest httpRequest = new DefaultHttpRequest(httpVersion, method, url);

        // Remove the scheme header
        SpdyHeaders.removeScheme(spdyVersion, requestFrame);

        // Replace the SPDY host header with the HTTP host header
        String host = SpdyHeaders.getHost(requestFrame);
        SpdyHeaders.removeHost(requestFrame);
        HttpHeaders.setHost(httpRequest, host);

        for (Map.Entry<String, String> e: requestFrame.headers()) {
            httpRequest.headers().add(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpRequest, true);

        // Transfer-Encoding header is not valid
        httpRequest.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);

        return httpRequest;
    }

    private static HttpResponse createHttpResponse(int spdyVersion, SpdyHeadersFrame responseFrame)
            throws Exception {
        // Create the first line of the response from the name/value pairs
        HttpResponseStatus status = SpdyHeaders.getStatus(spdyVersion, responseFrame);
        HttpVersion version = SpdyHeaders.getVersion(spdyVersion, responseFrame);
        SpdyHeaders.removeStatus(spdyVersion, responseFrame);
        SpdyHeaders.removeVersion(spdyVersion, responseFrame);

        HttpResponse httpResponse = new DefaultHttpResponse(version, status);
        for (Map.Entry<String, String> e: responseFrame.headers()) {
            httpResponse.headers().add(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpResponse, true);

        // Transfer-Encoding header is not valid
        httpResponse.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);
        httpResponse.headers().remove(HttpHeaders.Names.TRAILER);

        return httpResponse;
    }
}
