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
package io.netty.example.http.helloworld;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class HttpHelloWorldServerHandler extends ChannelInboundHandlerAdapter {
    private static final ByteBuf CONTENT =
            Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("Hello World", CharsetUtil.US_ASCII));

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        MessageList<Object> out = MessageList.newInstance();
        int size = msgs.size();
        for (int i = 0; i < size; i++) {
            Object msg = msgs.get(i);
            if (msg instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) msg;

                if (is100ContinueExpected(req)) {
                    out.add(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                }
                boolean keepAlive = isKeepAlive(req);
                FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, CONTENT.duplicate());
                response.headers().set(CONTENT_TYPE, "text/plain");
                response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

                if (!keepAlive) {
                    out.add(response);
                    ctx.write(out).addListener(ChannelFutureListener.CLOSE);
                    out = MessageList.newInstance();
                } else {
                    out.add(response);
                    response.headers().set(CONNECTION, Values.KEEP_ALIVE);
                }
            }
        }
        ctx.write(out);
        msgs.releaseAllAndRecycle();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
