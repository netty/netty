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
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.buffer.Unpooled.copiedBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
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
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EventSource transport is an streaming transport in that is maintains a persistent
 * connection from the server to the client over which the server can send messages.
 * This is often refered to a Server Side Events (SSE) and the client side.
 *
 * The response for opening such a unidirection channel is done with a simple
 * plain response with a 'Content-Type' of 'text/event-stream'. Subsequent
 * http chunks will contain data that the server whishes to send to the client.
 *
 */
public class EventSourceTransport extends ChannelOutboundHandlerAdapter {

    public static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream; charset=UTF-8";
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EventSourceTransport.class);

    private static final ByteBuf FRAME_START = unreleasableBuffer(copiedBuffer("data: ", UTF_8));
    private static final ByteBuf CRLF = unreleasableBuffer(copiedBuffer(new byte[] {CR, LF}));
    private static final ByteBuf FRAME_END = unreleasableBuffer(copiedBuffer(new byte[] {CR, LF, CR, LF}));

    private final SockJsConfig config;
    private final HttpRequest request;
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicInteger bytesSent = new AtomicInteger(0);

    public EventSourceTransport(final SockJsConfig config, final HttpRequest request) {
        this.config = config;
        this.request = request;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            if (headerSent.compareAndSet(false, true)) {
                ctx.write(createResponse(CONTENT_TYPE_EVENT_STREAM), promise);
                ctx.writeAndFlush(new DefaultHttpContent(CRLF.duplicate()));
            }

            final ByteBuf data = ctx.alloc().buffer();
            data.writeBytes(FRAME_START.duplicate());
            data.writeBytes(frame.content());
            data.writeBytes(FRAME_END.duplicate());
            final int dataSize = data.readableBytes();
            ctx.writeAndFlush(new DefaultHttpContent(data));
            frame.release();

            if (maxBytesLimit(dataSize)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("max bytesSize limit reached [{}]", config.maxStreamingBytesSize());
                }
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            }
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
