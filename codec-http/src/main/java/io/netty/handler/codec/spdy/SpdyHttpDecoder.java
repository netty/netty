/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.Map;

/**
 * Decodes {@link SpdySynStreamFrame}s, {@link SpdySynReplyFrame}s,
 * and {@link SpdyDataFrame}s into {@link HttpRequest}s and {@link HttpResponse}s.
 */
public class SpdyHttpDecoder extends MessageToMessageDecoder<Object, HttpMessage> {

    private final int spdyVersion;
    private final int maxContentLength;
    private final Map<Integer, HttpMessage> messageMap = new HashMap<Integer, HttpMessage>();

    /**
     * Creates a new instance.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public SpdyHttpDecoder(int version, int maxContentLength) {
        super(SpdyDataFrame.class, SpdyControlFrame.class);

        if (version < SpdyConstants.SPDY_MIN_VERSION || version > SpdyConstants.SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported version: " + version);
        }
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " + maxContentLength);
        }
        spdyVersion = version;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public HttpMessage decode(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SpdySynStreamFrame) {

            // HTTP requests/responses are mapped one-to-one to SPDY streams.
            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamID = spdySynStreamFrame.getStreamId();

            if (SpdyCodecUtil.isServerId(streamID)) {
                // SYN_STREAM frames initiated by the server are pushed resources
                int associatedToStreamId = spdySynStreamFrame.getAssociatedToStreamId();

                // If a client receives a SYN_STREAM with an Associated-To-Stream-ID of 0
                // it must reply with a RST_STREAM with error code INVALID_STREAM
                if (associatedToStreamId == 0) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.INVALID_STREAM);
                    ctx.write(spdyRstStreamFrame);
                }

                String URL = SpdyHeaders.getUrl(spdyVersion, spdySynStreamFrame);

                // If a client receives a SYN_STREAM without a 'url' header
                // it must reply with a RST_STREAM with error code PROTOCOL_ERROR
                if (URL == null) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                    ctx.write(spdyRstStreamFrame);
                }

                try {
                    HttpResponse httpResponse = createHttpResponse(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID, Associated-To-Stream-ID, Priority, and URL as headers
                    SpdyHttpHeaders.setStreamId(httpResponse, streamID);
                    SpdyHttpHeaders.setAssociatedToStreamId(httpResponse, associatedToStreamId);
                    SpdyHttpHeaders.setPriority(httpResponse, spdySynStreamFrame.getPriority());
                    SpdyHttpHeaders.setUrl(httpResponse, URL);

                    if (spdySynStreamFrame.isLast()) {
                        HttpHeaders.setContentLength(httpResponse, 0);
                        return httpResponse;
                    } else {
                        // Response body will follow in a series of Data Frames
                        messageMap.put(Integer.valueOf(streamID), httpResponse);
                    }
                } catch (Exception e) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                    ctx.write(spdyRstStreamFrame);
                }
            } else {
                // SYN_STREAM frames initiated by the client are HTTP requests
                try {
                    HttpRequest httpRequest = createHttpRequest(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID as a header
                    SpdyHttpHeaders.setStreamId(httpRequest, streamID);

                    if (spdySynStreamFrame.isLast()) {
                        return httpRequest;
                    } else {
                        // Request body will follow in a series of Data Frames
                        messageMap.put(Integer.valueOf(streamID), httpRequest);
                    }
                } catch (Exception e) {
                    // If a client sends a SYN_STREAM without all of the method, url (host and path),
                    // scheme, and version headers the server must reply with a HTTP 400 BAD REQUEST reply.
                    // Also sends HTTP 400 BAD REQUEST reply if header name/value pairs are invalid
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamID);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders.setStatus(spdyVersion, spdySynReplyFrame, HttpResponseStatus.BAD_REQUEST);
                    SpdyHeaders.setVersion(spdyVersion, spdySynReplyFrame, HttpVersion.HTTP_1_0);
                    ctx.write(spdySynReplyFrame);
                }
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamID = spdySynReplyFrame.getStreamId();

            try {
                HttpResponse httpResponse = createHttpResponse(spdyVersion, spdySynReplyFrame);

                // Set the Stream-ID as a header
                SpdyHttpHeaders.setStreamId(httpResponse, streamID);

                if (spdySynReplyFrame.isLast()) {
                    HttpHeaders.setContentLength(httpResponse, 0);
                    return httpResponse;
                } else {
                    // Response body will follow in a series of Data Frames
                    messageMap.put(Integer.valueOf(streamID), httpResponse);
                }
            } catch (Exception e) {
                // If a client receives a SYN_REPLY without valid status and version headers
                // the client must reply with a RST_STREAM frame indicating a PROTOCOL_ERROR
                SpdyRstStreamFrame spdyRstStreamFrame =
                    new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                ctx.write(spdyRstStreamFrame);
            }

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            Integer streamID = Integer.valueOf(spdyHeadersFrame.getStreamId());
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
            Integer streamID = Integer.valueOf(spdyDataFrame.getStreamId());
            HttpMessage httpMessage = messageMap.get(streamID);

            // If message is not in map discard Data Frame.
            // SpdySessionHandler should prevent this from happening.
            if (httpMessage == null) {
                return null;
            }

            ByteBuf content = httpMessage.getContent();
            if (content.readableBytes() > maxContentLength - spdyDataFrame.getData().readableBytes()) {
                messageMap.remove(streamID);
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength + " bytes.");
            }

            ByteBuf spdyDataFrameData = spdyDataFrame.getData();
            int spdyDataFrameDataLen = spdyDataFrameData.readableBytes();
            if (content == Unpooled.EMPTY_BUFFER) {
                content = Unpooled.buffer(spdyDataFrameDataLen);
                content.writeBytes(spdyDataFrameData, spdyDataFrameData.readerIndex(), spdyDataFrameDataLen);
                httpMessage.setContent(content);
            } else {
                content.writeBytes(spdyDataFrameData, spdyDataFrameData.readerIndex(), spdyDataFrameDataLen);
            }

            if (spdyDataFrame.isLast()) {
                HttpHeaders.setContentLength(httpMessage, content.readableBytes());
                messageMap.remove(streamID);
                return httpMessage;
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            Integer streamID = Integer.valueOf(spdyRstStreamFrame.getStreamId());
            messageMap.remove(streamID);
        }

        return null;
    }

    private static HttpRequest createHttpRequest(int spdyVersion, SpdyHeaderBlock requestFrame)
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

        if (spdyVersion >= 3) {
            // Replace the SPDY host header with the HTTP host header
            String host = SpdyHeaders.getHost(requestFrame);
            SpdyHeaders.removeHost(requestFrame);
            HttpHeaders.setHost(httpRequest, host);
        }

        for (Map.Entry<String, String> e: requestFrame.getHeaders()) {
            httpRequest.addHeader(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpRequest, true);

        // Transfer-Encoding header is not valid
        httpRequest.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);

        return httpRequest;
    }

    private static HttpResponse createHttpResponse(int spdyVersion, SpdyHeaderBlock responseFrame)
            throws Exception {
        // Create the first line of the response from the name/value pairs
        HttpResponseStatus status = SpdyHeaders.getStatus(spdyVersion, responseFrame);
        HttpVersion version = SpdyHeaders.getVersion(spdyVersion, responseFrame);
        SpdyHeaders.removeStatus(spdyVersion, responseFrame);
        SpdyHeaders.removeVersion(spdyVersion, responseFrame);

        HttpResponse httpResponse = new DefaultHttpResponse(version, status);
        for (Map.Entry<String, String> e: responseFrame.getHeaders()) {
            httpResponse.addHeader(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(httpResponse, true);

        // Transfer-Encoding header is not valid
        httpResponse.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        httpResponse.removeHeader(HttpHeaders.Names.TRAILER);

        return httpResponse;
    }
}
