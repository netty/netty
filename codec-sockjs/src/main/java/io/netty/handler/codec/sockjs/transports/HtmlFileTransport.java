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
package io.netty.handler.codec.sockjs.transports;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.handlers.SessionHandler.Events;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A streaming transport for SockJS.
 *
 * This transport is intended to be used in an iframe, where the src of
 * the iframe will have the an url looking something like this:
 *
 * http://server/echo/serverId/sessionId/htmlfile?c=callback
 * The server will respond with a html snipped containing a html header
 * and a script element. When data is available on the server this classes
 * write method will write a script to the connection that will invoke the
 * callback.
 */
public class HtmlFileTransport extends ChannelDuplexHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HtmlFileTransport.class);
    private static final ByteBuf HEADER_PART1 = unreleasableBuffer(copiedBuffer(
            "<!doctype html>\n" +
            "<html><head>\n" +
            "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
            "</head><body><h2>Don't panic!</h2>\n" +
            "  <script>\n" +
            "    document.domain = document.domain;\n" +
            "    var c = parent.", UTF_8));
    private static final ByteBuf HEADER_PART2 = unreleasableBuffer(copiedBuffer(
            ";\n" +
            "    c.start();\n" +
            "    function p(d) {c.message(d);};\n" +
            "    window.onload = function() {c.stop();};\n" +
            "  </script>", UTF_8));
    private static final ByteBuf PREFIX = unreleasableBuffer(copiedBuffer("<script>\np(\"", UTF_8));
    private static final ByteBuf POSTFIX = unreleasableBuffer(copiedBuffer("\");\n</script>\r\n", UTF_8));
    private static final ByteBuf END_HEADER = unreleasableBuffer(copiedBuffer(new byte[] {CR, LF, CR, LF}));

    private final SockJsConfig config;
    private final HttpRequest request;
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicInteger bytesSent = new AtomicInteger(0);
    private String callback;

    public HtmlFileTransport(final SockJsConfig config, final HttpRequest request) {
        this.config = config;
        this.request = request;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            final String c = getCallbackFromRequest((HttpRequest) msg);
            if (c.isEmpty()) {
                respondCallbackRequired(ctx);
                ctx.fireUserEventTriggered(Events.CLOSE_SESSION);
                return;
            } else {
                callback = c;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private static String getCallbackFromRequest(final HttpRequest request) {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        final List<String> c = qsd.parameters().get("c");
        return c == null || c.isEmpty() ? "" : c.get(0);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            if (headerSent.compareAndSet(false, true)) {
                final HttpResponse response = createResponse(Transports.CONTENT_TYPE_HTML);
                final ByteBuf header = ctx.alloc().buffer();
                header.writeBytes(HEADER_PART1.duplicate());
                header.writeBytes(copiedBuffer(callback, UTF_8));
                header.writeBytes(HEADER_PART2.duplicate());
                final int spaces = 1024 * header.readableBytes();
                final ByteBuf paddedBuffer = ctx.alloc().buffer(1024 + 50);
                paddedBuffer.writeBytes(header);
                for (int s = 0; s < spaces + 20; s++) {
                    paddedBuffer.writeByte(' ');
                }
                paddedBuffer.writeBytes(END_HEADER.duplicate());
                ctx.write(response, promise);
                ctx.writeAndFlush(new DefaultHttpContent(paddedBuffer));
            }

            final ByteBuf data = ctx.alloc().buffer();
            data.writeBytes(PREFIX.duplicate());
            data.writeBytes(Transports.escapeJson(frame.content(), data));
            data.writeBytes(POSTFIX.duplicate());
            final int dataSize = data.readableBytes();
            ctx.writeAndFlush(new DefaultHttpContent(data));

            if (maxBytesLimit(dataSize)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("max bytesSize limit reached [{}]", config.maxStreamingBytesSize());
                }
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.write(ReferenceCountUtil.retain(msg), promise);
        }
    }

    private void respondCallbackRequired(final ChannelHandlerContext ctx)
            throws Exception {
        final FullHttpResponse response = Transports.responseWithContent(request.getProtocolVersion(),
                INTERNAL_SERVER_ERROR,
                Transports.CONTENT_TYPE_PLAIN,
                "\"callback\" parameter required");
        Transports.setNoCacheHeaders(response);
        Transports.writeResponse(ctx, response);
    }

    private boolean maxBytesLimit(final int bytesWritten) {
        bytesSent.addAndGet(bytesWritten);
        return bytesSent.get() >= config.maxStreamingBytesSize();
    }

    protected HttpResponse createResponse(final String contentType) {
        final HttpVersion version = request.getProtocolVersion();
        final HttpResponse response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
        if (request.getProtocolVersion().equals(HttpVersion.HTTP_1_1)) {
            response.headers().set(TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        }
        response.headers().set(CONTENT_TYPE, contentType);
        Transports.setDefaultHeaders(response, config);
        return response;
    }

}
