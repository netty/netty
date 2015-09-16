/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * Translates HTTP/1.x object reads into HTTP/2 frames.
 */
public class InboundHttpToHttp2Adapter extends ChannelHandlerAdapter {
    private final Http2Connection connection;
    private final Http2FrameListener listener;

    public InboundHttpToHttp2Adapter(Http2Connection connection, Http2FrameListener listener) {
        this.connection = connection;
        this.listener = listener;
    }

    private int getStreamId(HttpHeaders httpHeaders) {
        return httpHeaders.getInt(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(),
            connection.remote().nextStreamId());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpMessage) {
            FullHttpMessage message = (FullHttpMessage) msg;
            try {
                int streamId = getStreamId(message.headers());
                Http2Stream stream = connection.stream(streamId);
                if (stream == null) {
                    stream = connection.remote().createStream(streamId, false);
                }
                Http2Headers messageHeaders = HttpUtil.toHttp2Headers(message);
                boolean hasContent = message.content().isReadable();
                boolean hasTrailers = !message.trailingHeaders().isEmpty();
                listener.onHeadersRead(
                    ctx, streamId, messageHeaders, 0, !(hasContent || hasTrailers));
                if (hasContent) {
                    listener.onDataRead(ctx, streamId, message.content(), 0, !hasTrailers);
                }
                if (hasTrailers) {
                    Http2Headers headers = HttpUtil.toHttp2Headers(message.trailingHeaders());
                    listener.onHeadersRead(ctx, streamId, headers, 0, true);
                }
                stream.closeRemoteSide();
            } finally {
                message.release();
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
