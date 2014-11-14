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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes {@link SpdySynStreamFrame}s, {@link SpdySynReplyFrame}s,
 * and {@link SpdyDataFrame}s into {@link FullHttpRequest}s and {@link FullHttpResponse}s.
 */
public class SpdyHttpDecoder extends MessageToMessageDecoder<SpdyFrame> {

    private final boolean validateHeaders;
    private final int spdyVersion;
    private final int maxContentLength;
    private final Map<Integer, FullHttpMessage> messageMap;

    /**
     * Creates a new instance.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public SpdyHttpDecoder(SpdyVersion version, int maxContentLength) {
        this(version, maxContentLength, new HashMap<Integer, FullHttpMessage>(), true);
    }

    /**
     * Creates a new instance.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     * @param validateHeaders {@code true} if http headers should be validated
     */
    public SpdyHttpDecoder(SpdyVersion version, int maxContentLength, boolean validateHeaders) {
        this(version, maxContentLength, new HashMap<Integer, FullHttpMessage>(), validateHeaders);
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     * @param messageMap the {@link Map} used to hold partially received messages.
     */
    protected SpdyHttpDecoder(SpdyVersion version, int maxContentLength, Map<Integer, FullHttpMessage> messageMap) {
        this(version, maxContentLength, messageMap, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @param version the protocol version
     * @param maxContentLength the maximum length of the message content.
     *        If the length of the message content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     * @param messageMap the {@link Map} used to hold partially received messages.
     * @param validateHeaders {@code true} if http headers should be validated
     */
    protected SpdyHttpDecoder(SpdyVersion version, int maxContentLength, Map<Integer,
            FullHttpMessage> messageMap, boolean validateHeaders) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " + maxContentLength);
        }
        spdyVersion = version.getVersion();
        this.maxContentLength = maxContentLength;
        this.messageMap = messageMap;
        this.validateHeaders = validateHeaders;
    }

    protected FullHttpMessage putMessage(int streamId, FullHttpMessage message) {
        return messageMap.put(streamId, message);
    }

    protected FullHttpMessage getMessage(int streamId) {
        return messageMap.get(streamId);
    }

    protected FullHttpMessage removeMessage(int streamId) {
        return messageMap.remove(streamId);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, SpdyFrame msg, List<Object> out)
            throws Exception {
        if (msg instanceof SpdySynStreamFrame) {

            // HTTP requests/responses are mapped one-to-one to SPDY streams.
            SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
            int streamId = spdySynStreamFrame.streamId();

            if (SpdyCodecUtil.isServerId(streamId)) {
                // SYN_STREAM frames initiated by the server are pushed resources
                int associatedToStreamId = spdySynStreamFrame.associatedStreamId();

                // If a client receives a SYN_STREAM with an Associated-To-Stream-ID of 0
                // it must reply with a RST_STREAM with error code INVALID_STREAM.
                if (associatedToStreamId == 0) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INVALID_STREAM);
                    ctx.writeAndFlush(spdyRstStreamFrame);
                    return;
                }

                // If a client receives a SYN_STREAM with isLast set,
                // reply with a RST_STREAM with error code PROTOCOL_ERROR
                // (we only support pushed resources divided into two header blocks).
                if (spdySynStreamFrame.isLast()) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                    ctx.writeAndFlush(spdyRstStreamFrame);
                    return;
                }

                // If a client receives a response with a truncated header block,
                // reply with a RST_STREAM with error code INTERNAL_ERROR.
                if (spdySynStreamFrame.isTruncated()) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                    new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INTERNAL_ERROR);
                    ctx.writeAndFlush(spdyRstStreamFrame);
                    return;
                }

                try {
                    FullHttpRequest httpRequestWithEntity = createHttpRequest(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID, Associated-To-Stream-ID, and Priority as headers
                    SpdyHttpHeaders.setStreamId(httpRequestWithEntity, streamId);
                    SpdyHttpHeaders.setAssociatedToStreamId(httpRequestWithEntity, associatedToStreamId);
                    SpdyHttpHeaders.setPriority(httpRequestWithEntity, spdySynStreamFrame.priority());

                    out.add(httpRequestWithEntity);

                } catch (Exception e) {
                    SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                    ctx.writeAndFlush(spdyRstStreamFrame);
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
                    ctx.writeAndFlush(spdySynReplyFrame);
                    return;
                }

                try {
                    FullHttpRequest httpRequestWithEntity = createHttpRequest(spdyVersion, spdySynStreamFrame);

                    // Set the Stream-ID as a header
                    SpdyHttpHeaders.setStreamId(httpRequestWithEntity, streamId);

                    if (spdySynStreamFrame.isLast()) {
                        out.add(httpRequestWithEntity);
                    } else {
                        // Request body will follow in a series of Data Frames
                        putMessage(streamId, httpRequestWithEntity);
                    }
                } catch (Exception e) {
                    // If a client sends a SYN_STREAM without all of the getMethod, url (host and path),
                    // scheme, and version headers the server must reply with a HTTP 400 BAD REQUEST reply.
                    // Also sends HTTP 400 BAD REQUEST reply if header name/value pairs are invalid
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders.setStatus(spdyVersion, spdySynReplyFrame, HttpResponseStatus.BAD_REQUEST);
                    SpdyHeaders.setVersion(spdyVersion, spdySynReplyFrame, HttpVersion.HTTP_1_0);
                    ctx.writeAndFlush(spdySynReplyFrame);
                }
            }

        } else if (msg instanceof SpdySynReplyFrame) {

            SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
            int streamId = spdySynReplyFrame.streamId();

            // If a client receives a SYN_REPLY with a truncated header block,
            // reply with a RST_STREAM frame with error code INTERNAL_ERROR.
            if (spdySynReplyFrame.isTruncated()) {
                SpdyRstStreamFrame spdyRstStreamFrame =
                        new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INTERNAL_ERROR);
                ctx.writeAndFlush(spdyRstStreamFrame);
                return;
            }

            try {
                FullHttpResponse httpResponseWithEntity =
                        createHttpResponse(ctx, spdyVersion, spdySynReplyFrame, validateHeaders);

                // Set the Stream-ID as a header
                SpdyHttpHeaders.setStreamId(httpResponseWithEntity, streamId);

                if (spdySynReplyFrame.isLast()) {
                    HttpHeaders.setContentLength(httpResponseWithEntity, 0);
                    out.add(httpResponseWithEntity);
                } else {
                    // Response body will follow in a series of Data Frames
                    putMessage(streamId, httpResponseWithEntity);
                }
            } catch (Exception e) {
                // If a client receives a SYN_REPLY without valid getStatus and version headers
                // the client must reply with a RST_STREAM frame indicating a PROTOCOL_ERROR
                SpdyRstStreamFrame spdyRstStreamFrame =
                    new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                ctx.writeAndFlush(spdyRstStreamFrame);
            }

        } else if (msg instanceof SpdyHeadersFrame) {

            SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
            int streamId = spdyHeadersFrame.streamId();
            FullHttpMessage fullHttpMessage = getMessage(streamId);

            if (fullHttpMessage == null) {
                // HEADERS frames may initiate a pushed response
                if (SpdyCodecUtil.isServerId(streamId)) {

                    // If a client receives a HEADERS with a truncated header block,
                    // reply with a RST_STREAM frame with error code INTERNAL_ERROR.
                    if (spdyHeadersFrame.isTruncated()) {
                        SpdyRstStreamFrame spdyRstStreamFrame =
                            new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.INTERNAL_ERROR);
                        ctx.writeAndFlush(spdyRstStreamFrame);
                        return;
                    }

                    try {
                        fullHttpMessage = createHttpResponse(ctx, spdyVersion, spdyHeadersFrame, validateHeaders);

                        // Set the Stream-ID as a header
                        SpdyHttpHeaders.setStreamId(fullHttpMessage, streamId);

                        if (spdyHeadersFrame.isLast()) {
                            HttpHeaders.setContentLength(fullHttpMessage, 0);
                            out.add(fullHttpMessage);
                        } else {
                            // Response body will follow in a series of Data Frames
                            putMessage(streamId, fullHttpMessage);
                        }
                    } catch (Exception e) {
                        // If a client receives a SYN_REPLY without valid getStatus and version headers
                        // the client must reply with a RST_STREAM frame indicating a PROTOCOL_ERROR
                        SpdyRstStreamFrame spdyRstStreamFrame =
                            new DefaultSpdyRstStreamFrame(streamId, SpdyStreamStatus.PROTOCOL_ERROR);
                        ctx.writeAndFlush(spdyRstStreamFrame);
                    }
                }
                return;
            }

            // Ignore trailers in a truncated HEADERS frame.
            if (!spdyHeadersFrame.isTruncated()) {
                for (Map.Entry<String, String> e: spdyHeadersFrame.headers()) {
                    fullHttpMessage.headers().add(e.getKey(), e.getValue());
                }
            }

            if (spdyHeadersFrame.isLast()) {
                HttpHeaders.setContentLength(fullHttpMessage, fullHttpMessage.content().readableBytes());
                removeMessage(streamId);
                out.add(fullHttpMessage);
            }

        } else if (msg instanceof SpdyDataFrame) {

            SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
            int streamId = spdyDataFrame.streamId();
            FullHttpMessage fullHttpMessage = getMessage(streamId);

            // If message is not in map discard Data Frame.
            if (fullHttpMessage == null) {
                return;
            }

            ByteBuf content = fullHttpMessage.content();
            if (content.readableBytes() > maxContentLength - spdyDataFrame.content().readableBytes()) {
                removeMessage(streamId);
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength + " bytes.");
            }

            ByteBuf spdyDataFrameData = spdyDataFrame.content();
            int spdyDataFrameDataLen = spdyDataFrameData.readableBytes();
            content.writeBytes(spdyDataFrameData, spdyDataFrameData.readerIndex(), spdyDataFrameDataLen);

            if (spdyDataFrame.isLast()) {
                HttpHeaders.setContentLength(fullHttpMessage, content.readableBytes());
                removeMessage(streamId);
                out.add(fullHttpMessage);
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            int streamId = spdyRstStreamFrame.streamId();
            removeMessage(streamId);
        }
    }

    private static FullHttpRequest createHttpRequest(int spdyVersion, SpdyHeadersFrame requestFrame)
            throws Exception {
        // Create the first line of the request from the name/value pairs
        SpdyHeaders headers     = requestFrame.headers();
        HttpMethod  method      = SpdyHeaders.getMethod(spdyVersion, requestFrame);
        String      url         = SpdyHeaders.getUrl(spdyVersion, requestFrame);
        HttpVersion httpVersion = SpdyHeaders.getVersion(spdyVersion, requestFrame);
        SpdyHeaders.removeMethod(spdyVersion, requestFrame);
        SpdyHeaders.removeUrl(spdyVersion, requestFrame);
        SpdyHeaders.removeVersion(spdyVersion, requestFrame);

        FullHttpRequest req = new DefaultFullHttpRequest(httpVersion, method, url);

        // Remove the scheme header
        SpdyHeaders.removeScheme(spdyVersion, requestFrame);

        // Replace the SPDY host header with the HTTP host header
        String host = headers.get(SpdyHeaders.HttpNames.HOST);
        headers.remove(SpdyHeaders.HttpNames.HOST);
        req.headers().set(HttpHeaders.Names.HOST, host);

        for (Map.Entry<String, String> e: requestFrame.headers()) {
            req.headers().add(e.getKey(), e.getValue());
        }

        // The Connection and Keep-Alive headers are no longer valid
        HttpHeaders.setKeepAlive(req, true);

        // Transfer-Encoding header is not valid
        req.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);

        return req;
    }

    private static FullHttpResponse createHttpResponse(ChannelHandlerContext ctx, int spdyVersion,
                                                       SpdyHeadersFrame responseFrame,
                                                       boolean validateHeaders) throws Exception {

        // Create the first line of the response from the name/value pairs
        HttpResponseStatus status = SpdyHeaders.getStatus(spdyVersion, responseFrame);
        HttpVersion version = SpdyHeaders.getVersion(spdyVersion, responseFrame);
        SpdyHeaders.removeStatus(spdyVersion, responseFrame);
        SpdyHeaders.removeVersion(spdyVersion, responseFrame);

        FullHttpResponse res = new DefaultFullHttpResponse(version, status, ctx.alloc().buffer(), validateHeaders);
        for (Map.Entry<String, String> e: responseFrame.headers()) {
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
