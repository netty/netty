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
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.Map;

/**
 * Decodes {@link SpdySynStreamFrame}s, {@link SpdySynReplyFrame}s,
 * and {@link SpdyDataFrame}s into {@link FullHttpRequest}s and {@link FullHttpResponse}s.
 */
public class SpdyHttpDecoder extends MessageToMessageDecoder<SpdyDataOrControlFrame> {

    private final int spdyVersion;
    private final int maxContentLength;
    private final Map<Integer, FullHttpMessage> messageMap = new HashMap<Integer, FullHttpMessage>();

    /**
     * Creates a new instance.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public SpdyHttpDecoder(int version, int maxContentLength) {
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
    protected void decode(ChannelHandlerContext ctx, SpdyDataOrControlFrame msg, MessageBuf<Object> out)
            throws Exception {
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
                    FullHttpResponse httpResponseWithEntity =
                            createHttpResponse(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID, Associated-To-Stream-ID, Priority, and URL as headers
                    SpdyHttpHeaders.setStreamId(httpResponseWithEntity, streamID);
                    SpdyHttpHeaders.setAssociatedToStreamId(httpResponseWithEntity, associatedToStreamId);
                    SpdyHttpHeaders.setPriority(httpResponseWithEntity, spdySynStreamFrame.getPriority());
                    SpdyHttpHeaders.setUrl(httpResponseWithEntity, URL);

                    if (spdySynStreamFrame.isLast()) {
                        HttpHeaders.setContentLength(httpResponseWithEntity, 0);
                        out.add(httpResponseWithEntity);
                        return;
                    } else {
                        // Response body will follow in a series of Data Frames
                        messageMap.put(Integer.valueOf(streamID), httpResponseWithEntity);
                    }
                } catch (Exception e) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamID, SpdyStreamStatus.PROTOCOL_ERROR);
                    ctx.write(spdyRstStreamFrame);
                }
            } else {
                // SYN_STREAM frames initiated by the client are HTTP requests
                try {
                    FullHttpRequest httpRequestWithEntity = createHttpRequest(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID as a header
                    SpdyHttpHeaders.setStreamId(httpRequestWithEntity, streamID);

                    if (spdySynStreamFrame.isLast()) {
                        out.add(httpRequestWithEntity);
                        return;
                    } else {
                        // Request body will follow in a series of Data Frames
                        messageMap.put(Integer.valueOf(streamID), httpRequestWithEntity);
                    }
                } catch (Exception e) {
                    // If a client sends a SYN_STREAM without all of the getMethod, url (host and path),
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
                FullHttpResponse httpResponseWithEntity = createHttpResponse(spdyVersion, spdySynReplyFrame);

                // Set the Stream-ID as a header
                SpdyHttpHeaders.setStreamId(httpResponseWithEntity, streamID);

                if (spdySynReplyFrame.isLast()) {
                    HttpHeaders.setContentLength(httpResponseWithEntity, 0);
                    out.add(httpResponseWithEntity);
                    return;
                } else {
                    // Response body will follow in a series of Data Frames
                    messageMap.put(Integer.valueOf(streamID), httpResponseWithEntity);
                }
            } catch (Exception e) {
                // If a client receives a SYN_REPLY without valid getStatus and version headers
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
                return;
            }

            for (Map.Entry<String, String> e: spdyHeadersFrame.headers().entries()) {
                httpMessage.headers().add(e.getKey(), e.getValue());
            }

        } else if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            Integer streamID = Integer.valueOf(spdyDataFrame.getStreamId());
            FullHttpMessage fullHttpMessage = messageMap.get(streamID);

            // If message is not in map discard Data Frame.
            // SpdySessionHandler should prevent this from happening.
            if (fullHttpMessage == null) {
                return;
            }

            ByteBuf content = fullHttpMessage.content();
            if (content.readableBytes() > maxContentLength - spdyDataFrame.content().readableBytes()) {
                messageMap.remove(streamID);
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength + " bytes.");
            }

            ByteBuf spdyDataFrameData = spdyDataFrame.content();
            int spdyDataFrameDataLen = spdyDataFrameData.readableBytes();
            content.writeBytes(spdyDataFrameData, spdyDataFrameData.readerIndex(), spdyDataFrameDataLen);

            if (spdyDataFrame.isLast()) {
                HttpHeaders.setContentLength(fullHttpMessage, content.readableBytes());
                messageMap.remove(streamID);
                out.add(fullHttpMessage);
                return;
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            Integer streamID = Integer.valueOf(spdyRstStreamFrame.getStreamId());
            messageMap.remove(streamID);
        }
    }

    private static FullHttpRequest createHttpRequest(int spdyVersion, SpdyHeaderBlock requestFrame)
            throws Exception {
        // Create the first line of the request from the name/value pairs
        HttpMethod  method      = SpdyHeaders.getMethod(spdyVersion, requestFrame);
        String      url         = SpdyHeaders.getUrl(spdyVersion, requestFrame);
        HttpVersion httpVersion = SpdyHeaders.getVersion(spdyVersion, requestFrame);
        SpdyHeaders.removeMethod(spdyVersion, requestFrame);
        SpdyHeaders.removeUrl(spdyVersion, requestFrame);
        SpdyHeaders.removeVersion(spdyVersion, requestFrame);

        FullHttpRequest req = new DefaultFullHttpRequest(httpVersion, method, url);

        // Remove the scheme header
        SpdyHeaders.removeScheme(spdyVersion, requestFrame);

        if (spdyVersion >= 3) {
            // Replace the SPDY host header with the HTTP host header
            String host = SpdyHeaders.getHost(requestFrame);
            SpdyHeaders.removeHost(requestFrame);
            HttpHeaders.setHost(req, host);
        }

        for (Map.Entry<String, String> e: requestFrame.headers().entries()) {
            req.headers().add(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(req, true);

        // Transfer-Encoding header is not valid
        req.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);

        return req;
    }

    private static FullHttpResponse createHttpResponse(int spdyVersion, SpdyHeaderBlock responseFrame)
            throws Exception {
        // Create the first line of the response from the name/value pairs
        HttpResponseStatus status = SpdyHeaders.getStatus(spdyVersion, responseFrame);
        HttpVersion version = SpdyHeaders.getVersion(spdyVersion, responseFrame);
        SpdyHeaders.removeStatus(spdyVersion, responseFrame);
        SpdyHeaders.removeVersion(spdyVersion, responseFrame);

        FullHttpResponse res = new DefaultFullHttpResponse(version, status);
        for (Map.Entry<String, String> e: responseFrame.headers().entries()) {
            res.headers().add(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(res, true);

        // Transfer-Encoding header is not valid
        res.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);
        res.headers().remove(HttpHeaders.Names.TRAILER);

        return res;
    }
}
