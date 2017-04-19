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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.spdy.SpdyHttpHeaders.Names;
import io.netty.util.ReferenceCountUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.spdy.SpdyHeaders.HttpNames.*;

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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Release any outstanding messages from the map
        for (Map.Entry<Integer, FullHttpMessage> entry : messageMap.entrySet()) {
            ReferenceCountUtil.safeRelease(entry.getValue());
        }
        messageMap.clear();
        super.channelInactive(ctx);
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
                    FullHttpRequest httpRequestWithEntity = createHttpRequest(spdySynStreamFrame, ctx.alloc());

                    // Set the Stream-ID, Associated-To-Stream-ID, and Priority as headers
                    httpRequestWithEntity.headers().setInt(Names.STREAM_ID, streamId);
                    httpRequestWithEntity.headers().setInt(Names.ASSOCIATED_TO_STREAM_ID, associatedToStreamId);
                    httpRequestWithEntity.headers().setInt(Names.PRIORITY, spdySynStreamFrame.priority());

                    out.add(httpRequestWithEntity);

                } catch (Throwable ignored) {
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
                    SpdyHeaders frameHeaders = spdySynReplyFrame.headers();
                    frameHeaders.setInt(STATUS, HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE.code());
                    frameHeaders.setObject(VERSION, HttpVersion.HTTP_1_0);
                    ctx.writeAndFlush(spdySynReplyFrame);
                    return;
                }

                try {
                    FullHttpRequest httpRequestWithEntity = createHttpRequest(spdySynStreamFrame, ctx.alloc());

                    // Set the Stream-ID as a header
                    httpRequestWithEntity.headers().setInt(Names.STREAM_ID, streamId);

                    if (spdySynStreamFrame.isLast()) {
                        out.add(httpRequestWithEntity);
                    } else {
                        // Request body will follow in a series of Data Frames
                        putMessage(streamId, httpRequestWithEntity);
                    }
                } catch (Throwable t) {
                    // If a client sends a SYN_STREAM without all of the getMethod, url (host and path),
                    // scheme, and version headers the server must reply with a HTTP 400 BAD REQUEST reply.
                    // Also sends HTTP 400 BAD REQUEST reply if header name/value pairs are invalid
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
                    spdySynReplyFrame.setLast(true);
                    SpdyHeaders frameHeaders = spdySynReplyFrame.headers();
                    frameHeaders.setInt(STATUS, HttpResponseStatus.BAD_REQUEST.code());
                    frameHeaders.setObject(VERSION, HttpVersion.HTTP_1_0);
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
                   createHttpResponse(spdySynReplyFrame, ctx.alloc(), validateHeaders);

                // Set the Stream-ID as a header
                httpResponseWithEntity.headers().setInt(Names.STREAM_ID, streamId);

                if (spdySynReplyFrame.isLast()) {
                    HttpUtil.setContentLength(httpResponseWithEntity, 0);
                    out.add(httpResponseWithEntity);
                } else {
                    // Response body will follow in a series of Data Frames
                    putMessage(streamId, httpResponseWithEntity);
                }
            } catch (Throwable t) {
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
                        fullHttpMessage = createHttpResponse(spdyHeadersFrame, ctx.alloc(), validateHeaders);

                        // Set the Stream-ID as a header
                        fullHttpMessage.headers().setInt(Names.STREAM_ID, streamId);

                        if (spdyHeadersFrame.isLast()) {
                            HttpUtil.setContentLength(fullHttpMessage, 0);
                            out.add(fullHttpMessage);
                        } else {
                            // Response body will follow in a series of Data Frames
                            putMessage(streamId, fullHttpMessage);
                        }
                    } catch (Throwable t) {
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
                for (Map.Entry<CharSequence, CharSequence> e: spdyHeadersFrame.headers()) {
                    fullHttpMessage.headers().add(e.getKey(), e.getValue());
                }
            }

            if (spdyHeadersFrame.isLast()) {
                HttpUtil.setContentLength(fullHttpMessage, fullHttpMessage.content().readableBytes());
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
                HttpUtil.setContentLength(fullHttpMessage, content.readableBytes());
                removeMessage(streamId);
                out.add(fullHttpMessage);
            }

        } else if (msg instanceof SpdyRstStreamFrame) {

            SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
            int streamId = spdyRstStreamFrame.streamId();
            removeMessage(streamId);
        }
    }

    private static FullHttpRequest createHttpRequest(SpdyHeadersFrame requestFrame, ByteBufAllocator alloc)
       throws Exception {
        // Create the first line of the request from the name/value pairs
        SpdyHeaders headers     = requestFrame.headers();
        HttpMethod  method      = HttpMethod.valueOf(headers.getAsString(METHOD));
        String      url         = headers.getAsString(PATH);
        HttpVersion httpVersion = HttpVersion.valueOf(headers.getAsString(VERSION));
        headers.remove(METHOD);
        headers.remove(PATH);
        headers.remove(VERSION);

        boolean release = true;
        ByteBuf buffer = alloc.buffer();
        try {
            FullHttpRequest req = new DefaultFullHttpRequest(httpVersion, method, url, buffer);

            // Remove the scheme header
            headers.remove(SCHEME);

            // Replace the SPDY host header with the HTTP host header
            CharSequence host = headers.get(HOST);
            headers.remove(HOST);
            req.headers().set(HttpHeaderNames.HOST, host);

            for (Map.Entry<CharSequence, CharSequence> e : requestFrame.headers()) {
                req.headers().add(e.getKey(), e.getValue());
            }

            // The Connection and Keep-Alive headers are no longer valid
            HttpUtil.setKeepAlive(req, true);

            // Transfer-Encoding header is not valid
            req.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            release = false;
            return req;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }

    private static FullHttpResponse createHttpResponse(SpdyHeadersFrame responseFrame, ByteBufAllocator alloc,
                                                       boolean validateHeaders) throws Exception {

        // Create the first line of the response from the name/value pairs
        SpdyHeaders headers = responseFrame.headers();
        HttpResponseStatus status = HttpResponseStatus.parseLine(headers.get(STATUS));
        HttpVersion version = HttpVersion.valueOf(headers.getAsString(VERSION));
        headers.remove(STATUS);
        headers.remove(VERSION);

        boolean release = true;
        ByteBuf buffer = alloc.buffer();
        try {
            FullHttpResponse res = new DefaultFullHttpResponse(version, status, buffer, validateHeaders);
            for (Map.Entry<CharSequence, CharSequence> e: responseFrame.headers()) {
                res.headers().add(e.getKey(), e.getValue());
            }

            // The Connection and Keep-Alive headers are no longer valid
            HttpUtil.setKeepAlive(res, true);

            // Transfer-Encoding header is not valid
            res.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            res.headers().remove(HttpHeaderNames.TRAILER);

            release = false;
            return res;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }
}
