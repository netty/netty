/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transport;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.sockjs.transport.Transports.wrapWithLN;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.protocol.PreludeFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XMLHttpRequest (XHR) streaming transport is a transport where a persistent
 * connection is maintained between the server and the client, over which the
 * server can send HTTP chunks.
 *
 * This handler is responsible for handling {@link Frame}s and sending the
 * contents of those frames to the client.
 *
 * @see XhrSendTransport
 */
public class XhrStreamingTransport extends ChannelHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(XhrStreamingTransport.class);
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicInteger bytesSent = new AtomicInteger(0);
    private final SockJsConfig config;
    private final HttpRequest request;

    /**
     * Sole constructor.
     *
     * @param config the SockJS {@link SockJsConfig} instance.
     * @param request the {@link HttpRequest} which can be used get information like the HTTP version.
     */
    public XhrStreamingTransport(final SockJsConfig config, final HttpRequest request) {
        this.config = config;
        this.request = request;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            if (headerSent.compareAndSet(false, true)) {
                final HttpResponse response = createResponse(Transports.CONTENT_TYPE_JAVASCRIPT);
                ctx.writeAndFlush(response);

                final ByteBuf content = wrapWithLN(new PreludeFrame().content());
                final DefaultHttpContent preludeChunk = new DefaultHttpContent(content);
                ctx.writeAndFlush(preludeChunk);
            }

            ctx.writeAndFlush(new DefaultHttpContent(wrapWithLN(frame.content())), promise);
            if (frame instanceof CloseFrame) {
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            }

            if (maxBytesLimit(frame.content().readableBytes())) {
                logger.debug("max bytesSize limit reached [{}]", config.maxStreamingBytesSize());
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            }
            frame.release();
        } else {
            ctx.writeAndFlush(ReferenceCountUtil.retain(msg), promise);
        }
    }

    private boolean maxBytesLimit(final int bytesWritten) {
        bytesSent.addAndGet(bytesWritten);
        return bytesSent.get() >= config.maxStreamingBytesSize();
    }

    protected HttpResponse createResponse(String contentType) {
        final HttpVersion version = request.getProtocolVersion();
        HttpResponse response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
        if (request.getProtocolVersion().equals(HttpVersion.HTTP_1_1)) {
            response.headers().set(TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        }
        response.headers().set(CONTENT_TYPE, contentType);
        Transports.setDefaultHeaders(response, config);
        return response;
    }

}
