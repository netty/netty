/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.http2.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.draft10.DefaultHttp2Headers;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2StreamFrame;
import io.netty.util.CharsetUtil;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public class HelloWorldHttp2 extends SimpleChannelInboundHandler<Http2StreamFrame> {

    static final byte[] RESPONSE_BYTES = "Hello World".getBytes(CharsetUtil.UTF_8);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Http2StreamFrame frame) throws Exception {
        if (frame.isEndOfStream()) {
            sendResponse(ctx, frame.getStreamId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

    private static void sendResponse(ChannelHandlerContext ctx, int streamId) {
        ByteBuf content = ctx.alloc().buffer();
        content.writeBytes(RESPONSE_BYTES);

        // Send a frame for the response status
        Http2Headers headers = DefaultHttp2Headers.newBuilder().setStatus("200").build();
        Http2HeadersFrame headersFrame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(streamId).setHeaders(headers)
                        .build();
        ctx.write(headersFrame);

        // Send a data frame with the response message.
        Http2DataFrame data =
                new DefaultHttp2DataFrame.Builder().setStreamId(streamId).setEndOfStream(true)
                        .setContent(content).build();
        ctx.writeAndFlush(data);
    }
}
