/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.FullHttpMessage;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.util.internal.UnstableApi;

/**
 * Translates HTTP/1.x object reads into HTTP/2 frames.
 */
@UnstableApi
public class InboundHttpToHttp2Adapter implements ChannelHandler {
    private final Http2Connection connection;
    private final Http2FrameListener listener;

    public InboundHttpToHttp2Adapter(Http2Connection connection, Http2FrameListener listener) {
        this.connection = connection;
        this.listener = listener;
    }

    private static int getStreamId(Http2Connection connection, HttpHeaders httpHeaders) {
        return httpHeaders.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(),
                                  connection.remote().incrementAndGetNextStreamId());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpMessage) {
            final FullHttpMessage<?> fullHttpMessage = (FullHttpMessage<?>) msg;
            try (fullHttpMessage) {
                handle(ctx, connection, listener, fullHttpMessage);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    // note that this may behave strangely when used for the initial upgrade
    // message when using h2c, since that message is ineligible for flow
    // control, but there is not yet an API for signaling that.
    static void handle(ChannelHandlerContext ctx, Http2Connection connection,
                       Http2FrameListener listener, FullHttpMessage<?> message) throws Http2Exception {
        int streamId = getStreamId(connection, message.headers());
        Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            stream = connection.remote().createStream(streamId, false);
        }
        message.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.name());
        Http2Headers messageHeaders = HttpConversionUtil.toHttp2Headers(message, true);
        boolean hasContent = message.payload().readableBytes() > 0;
        boolean hasTrailers = !message.trailingHeaders().isEmpty();
        listener.onHeadersRead(
                ctx, streamId, messageHeaders, 0, !(hasContent || hasTrailers));
        if (hasContent) {
            final Buffer payload = message.payload();
            listener.onDataRead(ctx, streamId, ByteBufAdaptor.intoByteBuf(payload), 0, !hasTrailers);
        }
        if (hasTrailers) {
            Http2Headers headers = HttpConversionUtil.toHttp2Headers(message.trailingHeaders(), true);
            listener.onHeadersRead(ctx, streamId, headers, 0, true);
        }
        stream.closeRemoteSide();
    }
}
