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
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class EventSourceTransport extends ChannelOutboundHandlerAdapter {
    public static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream; charset=UTF-8";
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EventSourceTransport.class);
    private static final ByteBuf FRAME_START = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("data: ", UTF_8));
    private static final ByteBuf CRLF = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            new byte[] {HttpConstants.CR, HttpConstants.LF}));
    private static final ByteBuf FRAME_END = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
            new byte[] {HttpConstants.CR, HttpConstants.LF, HttpConstants.CR, HttpConstants.LF}));
    private final Config config;
    private final HttpRequest request;
    private final AtomicBoolean headerSent = new AtomicBoolean(false);
    private final AtomicInteger bytesSent = new AtomicInteger(0);

    public EventSourceTransport(final Config config, final HttpRequest request) {
        this.config = config;
        this.request = request;
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
                    ctx.write(createResponse(CONTENT_TYPE_EVENT_STREAM), promise);
                    ctx.write(new DefaultHttpContent(CRLF.duplicate()));
                }

                final ByteBuf data = Unpooled.buffer();
                data.writeBytes(FRAME_START.duplicate());
                data.writeBytes(frame.content());
                data.writeBytes(FRAME_END.duplicate());
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
