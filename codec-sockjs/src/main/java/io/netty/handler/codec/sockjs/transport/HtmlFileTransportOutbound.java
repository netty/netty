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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.util.JsonConverter;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_HTML;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.NO_CACHE_HEADER;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.responseFor;
import static io.netty.handler.codec.sockjs.util.Arguments.checkNotNull;
import static io.netty.util.CharsetUtil.UTF_8;

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
public class HtmlFileTransportOutbound extends ChannelOutboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HtmlFileTransportOutbound.class);
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
    private static final ByteBuf END_HEADER = unreleasableBuffer(copiedBuffer(new byte[] {CR, LF, CR, LF}));
    private static final ByteBuf SCRIPT_PREFIX = unreleasableBuffer(copiedBuffer("<script>\np(\"", UTF_8));
    private static final ByteBuf SCRIPT_POSTFIX = unreleasableBuffer(copiedBuffer("\");\n</script>\r\n", UTF_8));

    private final SockJsConfig config;
    private final HttpRequest request;
    private boolean headerSent;
    private int bytesSent;

    public HtmlFileTransportOutbound(final SockJsConfig config, final HttpRequest request) {
        checkNotNull(config, "config");
        checkNotNull(request, "request");
        this.config = config;
        this.request = request;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            if (!headerSent) {
                final HttpResponse response = responseFor(request)
                        .ok()
                        .chunked()
                        .contentType(CONTENT_TYPE_HTML)
                        .setCookie(config)
                        .header(CONNECTION, CLOSE)
                        .header(CACHE_CONTROL, NO_CACHE_HEADER)
                        .buildResponse();

                final ByteBuf header = ctx.alloc().buffer();
                addHeaderPart1(header).addCallback(header, ctx).addHeaderPart2(header);
                final ByteBuf paddedHeader = paddedHeader(header, ctx);
                ctx.write(response, promise);
                ctx.write(new DefaultHttpContent(paddedHeader)).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            headerSent = true;
                        }
                    }
                });
            }

            final ByteBuf data = ctx.alloc().buffer();
            addScriptPrefix(data).addContent(frame, data).addScriptPostfix(data);
            final int dataSize = data.readableBytes();
            ctx.write(new DefaultHttpContent(data));

            if (maxBytesLimit(dataSize)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("max bytesSize limit reached [{}]", config.maxStreamingBytesSize());
                }
                ctx.write(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.write(msg, promise);
        }
    }

    private HtmlFileTransportOutbound addHeaderPart1(final ByteBuf buffer) {
        buffer.writeBytes(HEADER_PART1.duplicate());
        return this;
    }

    private HtmlFileTransportOutbound addHeaderPart2(final ByteBuf buffer) {
        buffer.writeBytes(HEADER_PART2.duplicate());
        return this;
    }

    private HtmlFileTransportOutbound addCallback(final ByteBuf buffer, final ChannelHandlerContext ctx) {
        final String callback = ctx.channel().attr(HtmlFileTransportInbound.CALLBACK).get();
        final ByteBuf content = copiedBuffer(callback, UTF_8);
        buffer.writeBytes(content);
        content.release();
        return this;
    }

    private HtmlFileTransportOutbound addScriptPrefix(final ByteBuf buffer) {
        buffer.writeBytes(SCRIPT_PREFIX.duplicate());
        return this;
    }

    private HtmlFileTransportOutbound addContent(final Frame frame, final ByteBuf buffer) {
        buffer.writeBytes(JsonConverter.escapeJson(frame.content(), buffer));
        frame.content().release();
        return this;
    }

    private HtmlFileTransportOutbound addScriptPostfix(final ByteBuf buffer) {
        buffer.writeBytes(SCRIPT_POSTFIX.duplicate());
        return this;
    }

    private static ByteBuf paddedHeader(final ByteBuf header, final ChannelHandlerContext ctx) {
        final int spaces = 1024 * header.readableBytes();
        final ByteBuf paddedBuffer = ctx.alloc().buffer(1024 + 50);
        paddedBuffer.writeBytes(header);
        header.release();
        for (int s = 0; s < spaces + 20; s++) {
            paddedBuffer.writeByte(' ');
        }
        paddedBuffer.writeBytes(END_HEADER.duplicate());
        return paddedBuffer;
    }

    private boolean maxBytesLimit(final int bytesWritten) {
        bytesSent += bytesWritten;
        return bytesSent >= config.maxStreamingBytesSize();
    }

}
