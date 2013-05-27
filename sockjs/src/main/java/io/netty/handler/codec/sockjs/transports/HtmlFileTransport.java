/*
 * Copyright 2012 The Netty Project
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

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.handlers.SessionHandler.Events;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.CharsetUtil;
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
public class HtmlFileTransport extends ChannelOutboundHandlerAdapter implements ChannelInboundHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HtmlFileTransport.class);
    private final Config config;
    private final HttpRequest request;
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicInteger bytesSent = new AtomicInteger(0);
    private String callback;

    private static final ByteBuf HEADER_PART1 = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            "<!doctype html>\n" +
            "<html><head>\n" +
            "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
            "</head><body><h2>Don't panic!</h2>\n" +
            "  <script>\n" +
            "    document.domain = document.domain;\n" +
            "    var c = parent.", CharsetUtil.UTF_8));
    private static final ByteBuf HEADER_PART2 = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            ";\n" +
            "    c.start();\n" +
            "    function p(d) {c.message(d);};\n" +
            "    window.onload = function() {c.stop();};\n" +
            "  </script>", CharsetUtil.UTF_8));
    private static final ByteBuf PREFIX = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            "<script>\np(\"", CharsetUtil.UTF_8));
    private static final ByteBuf POSTFIX = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            "\");\n</script>\r\n", CharsetUtil.UTF_8));
    private static final ByteBuf END_HEADER = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            new byte[] {HttpConstants.CR, HttpConstants.LF, HttpConstants.CR, HttpConstants.LF}));

    public HtmlFileTransport(final Config config, final HttpRequest request) {
        this.config = config;
        this.request = request;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        final int size = msgs.size();
        for (int i = 0; i < size; i ++) {
            final Object obj = msgs.get(i);
            if (obj instanceof HttpRequest) {
                String c = getCallbackFromRequest((HttpRequest) obj);
                if (c.length() == 0) {
                    respondCallbackRequired(ctx);
                    ctx.fireUserEventTriggered(Events.CLOSE_SESSION);
                    return;
                } else {
                    callback = c;
                }
            }
        }
        ctx.fireMessageReceived(msgs);
    }

    public String getCallbackFromRequest(final HttpRequest request) {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        final List<String> c = qsd.parameters().get("c");
        return c == null || c.isEmpty() ? "" : c.get(0);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final MessageList<Object> msgs, final ChannelPromise promise)
            throws Exception {
        final int size = msgs.size();
        for (int i = 0; i < size; i++) {
            final Object obj = msgs.get(i);
            if (obj instanceof Frame) {
                final Frame frame = (Frame) obj;
                if (headerSent.compareAndSet(false, true)) {
                    final HttpResponse response = createResponse(Transports.CONTENT_TYPE_HTML);
                    final ByteBuf header = Unpooled.buffer();
                    header.writeBytes(HEADER_PART1.duplicate());
                    header.writeBytes(Unpooled.copiedBuffer(callback, UTF_8));
                    header.writeBytes(HEADER_PART2.duplicate());
                    final int spaces = 1024 * header.readableBytes();
                    final ByteBuf paddedBuffer = Unpooled.buffer(1024 + 50);
                    paddedBuffer.writeBytes(header);
                    for (int s = 0; s < spaces + 20; s++) {
                        paddedBuffer.writeByte(' ');
                    }
                    paddedBuffer.writeBytes(END_HEADER.duplicate());
                    ctx.write(response, promise);
                    ctx.write(new DefaultHttpContent(paddedBuffer));
                }

                final ByteBuf data = Unpooled.buffer();
                data.writeBytes(PREFIX.duplicate());
                data.writeBytes(Transports.escapeJson(frame.content(), data));
                data.writeBytes(POSTFIX.duplicate());
                final int dataSize = data.readableBytes();
                ctx.write(new DefaultHttpContent(data));

                if (maxBytesLimit(dataSize)) {
                    logger.debug("max bytesSize limit reached [" + config.maxStreamingBytesSize() + "]. Closing");
                    ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
                    ctx.close();
                }
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Added [" + ctx + "]");
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

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelReadSuspended(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadSuspended();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

}
