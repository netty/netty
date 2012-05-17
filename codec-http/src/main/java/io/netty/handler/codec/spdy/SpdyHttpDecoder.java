/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channels;
import io.netty.handler.codec.frame.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * Decodes {@link SpdySynStreamFrame}s, {@link SpdySynReplyFrame}s,
 * and {@link SpdyDataFrame}s into {@link HttpRequest}s and {@link HttpResponse}s.
 */
public class SpdyHttpDecoder extends OneToOneDecoder {

    private final int maxContentLength;
    private final Map<Integer, HttpMessage> messageMap = new HashMap<Integer, HttpMessage>();

    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public SpdyHttpDecoder(int maxContentLength) {
        super();
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " + maxContentLength);
        }
        this.maxContentLength = maxContentLength;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        if (msg instanceof SpdySynStreamFrame) {

            // HTTP requests/responses are mapped one-to-one to SPDY streams.
            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamID = spdySynStreamFrame.getStreamID();

            if (SpdyCodecUtil.isServerID(streamID)) {
                // SYN_STREAM frames inititated by the server are pushed resources
                int associatedToStreamID = spdySynStreamFrame.getAssociatedToStreamID();

                // If a client receives a SYN_STREAM with an Associated-To-Stream-ID of 0
                // it must reply with a RST_STREAM with error code INVALID_STREAM
                if (associatedToStreamID == 0) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.INVALID_STREAM);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

                String URL = SpdyHeaders.getUrl(spdySynStreamFrame);

                // If a client receives a SYN_STREAM without a 'url' header
                // it must reply with a RST_STREAM with error code PROTOCOL_ERROR
                if (URL == null) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

                try {
                    HttpResponse httpResponse = createHttpResponse(spdySynStreamFrame);

                    // Set the Stream-ID, Associated-To-Stream-ID, Priority, and URL as headers
                    SpdyHttpHeaders.setStreamID(httpResponse, streamID);
                    SpdyHttpHeaders.setAssociatedToStreamID(httpResponse, associatedToStreamID);
                    SpdyHttpHeaders.setPriority(httpResponse, spdySynStreamFrame.getPriority());
                    SpdyHttpHeaders.setUrl(httpResponse, URL);

                    if (spdySynStreamFrame.isLast()) {
                        HttpHeaders.setContentLength(httpResponse, 0);
                        return httpResponse;
                    } else {
                        // Response body will follow in a series of Data Frames
                        messageMap.put(new Integer(streamID), httpResponse);
                    }
                } catch (Exception e) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                    Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
                }

            } else {
                // SYN_STREAM frames initiated by the client are HTTP requests
                try {
                    HttpRequest httpRequest = createHttpRequest(spdySynStreamFrame);

                    // Set the Stream-ID as a header
                    SpdyHttpHeaders.setStreamID(httpRequest, streamID);

                    if (spdySynStreamFrame.isLast()) {
                        return httpRequest;
                    } else {
                        // Request body will follow in a series of Data Frames
                        messageMap.put(new Integer(streamID), httpRequest);
                    }
                } catch (Exception e) {
                    // If a client sends a SYN_STREAM without method, url, and version headers
                    // the server must reply with a HTTP 400 BAD REQUEST reply
                    // Also sends HTTP 400 BAD REQUEST reply if header name/value pairs are invalid
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamID);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders.setStatus(spdySynReplyFrame, HttpResponseStatus.BAD_REQUEST);
                    SpdyHeaders.setVersion(spdySynReplyFrame, HttpVersion.HTTP_1_0);
                    Channels.write(ctx, Channels.future(channel), spdySynReplyFrame);
                }
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamID = spdySynReplyFrame.getStreamID();

            try {
                HttpResponse httpResponse = createHttpResponse(spdySynReplyFrame);

                // Set the Stream-ID as a header
                SpdyHttpHeaders.setStreamID(httpResponse, streamID);

                if (spdySynReplyFrame.isLast()) {
                    HttpHeaders.setContentLength(httpResponse, 0);
                    return httpResponse;
                } else {
                    // Response body will follow in a series of Data Frames
                    messageMap.put(new Integer(streamID), httpResponse);
                }
            } catch (Exception e) {
                // If a client receives a SYN_REPLY without valid status and version headers
                // the client must reply with a RST_STREAM frame indicating a PROTOCOL_ERROR
                SpdyRstStreamFrame spdyRstStreamFrame =
                    new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                Channels.write(ctx, Channels.future(channel), spdyRstStreamFrame);
            }

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            Integer streamID = new Integer(spdyHeadersFrame.getStreamID());
            HttpMessage httpMessage = messageMap.get(streamID);

            // If message is not in map discard HEADERS frame.
            // SpdySessionHandler should prevent this from happening.
            if (httpMessage == null) {
                return null;
            }

            for (Map.Entry<String, String> e: spdyHeadersFrame.getHeaders()) {
                httpMessage.addHeader(e.getKey(), e.getValue());
            }

        } else if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            Integer streamID = new Integer(spdyDataFrame.getStreamID());
            HttpMessage httpMessage = messageMap.get(streamID);

            // If message is not in map discard Data Frame.
            // SpdySessionHandler should prevent this from happening.
            if (httpMessage == null) {
                return null;
            }

            ChannelBuffer content = httpMessage.getContent();
            if (content.readableBytes() > maxContentLength - spdyDataFrame.getData().readableBytes()) {
                messageMap.remove(streamID);
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
                messageMap.remove(streamID);
                return httpMessage;
            }
        }

        return null;
    }

    private HttpRequest createHttpRequest(SpdyHeaderBlock requestFrame)
            throws Exception {
        // Create the first line of the request from the name/value pairs
        HttpMethod  method  = SpdyHeaders.getMethod(requestFrame);
        String      url     = SpdyHeaders.getUrl(requestFrame);
        HttpVersion version = SpdyHeaders.getVersion(requestFrame);
        SpdyHeaders.removeMethod(requestFrame);
        SpdyHeaders.removeUrl(requestFrame);
        SpdyHeaders.removeVersion(requestFrame);

        HttpRequest httpRequest = new DefaultHttpRequest(version, method, url);
        for (Map.Entry<String, String> e: requestFrame.getHeaders()) {
            httpRequest.addHeader(e.getKey(), e.getValue());
        }

        // Chunked encoding is no longer valid
        List<String> encodings = httpRequest.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty()) {
            httpRequest.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        } else {
            httpRequest.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, encodings);
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpRequest, true);

        return httpRequest;
    }

    private HttpResponse createHttpResponse(SpdyHeaderBlock responseFrame)
            throws Exception {
        // Create the first line of the response from the name/value pairs
        HttpResponseStatus status = SpdyHeaders.getStatus(responseFrame);
        HttpVersion version = SpdyHeaders.getVersion(responseFrame);
        SpdyHeaders.removeStatus(responseFrame);
        SpdyHeaders.removeVersion(responseFrame);

        HttpResponse httpResponse = new DefaultHttpResponse(version, status);
        for (Map.Entry<String, String> e: responseFrame.getHeaders()) {
            httpResponse.addHeader(e.getKey(), e.getValue());
        }

        // Chunked encoding is no longer valid
        List<String> encodings = httpResponse.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty()) {
            httpResponse.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        } else {
            httpResponse.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, encodings);
        }
        httpResponse.removeHeader(HttpHeaders.Names.TRAILER);

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpResponse, true);

        return httpResponse;
    }
}
