/*
 * Copyright 2014 The Netty Project
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
package io.netty5.example.http2.helloworld.server;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpUtil;

import java.nio.charset.StandardCharsets;

import static io.netty5.example.http2.helloworld.server.HelloWorldHttp2Handler.RESPONSE_BYTES;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty5.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty5.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty5.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * HTTP handler that responds with a "Hello World"
 */
public class HelloWorldHttp1Handler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final String establishApproach;

    public HelloWorldHttp1Handler(String establishApproach) {
        this.establishApproach = requireNonNull(establishApproach, "establishApproach");
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (HttpUtil.is100ContinueExpected(req)) {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, ctx.bufferAllocator().allocate(0)));
        }

        final byte[] sourceBytes = (" - via " + req.protocolVersion() + " (" + establishApproach + ")")
                .getBytes(US_ASCII);
        final Buffer content = ctx.bufferAllocator().allocate(RESPONSE_BYTES.length + sourceBytes.length)
                .writeBytes(RESPONSE_BYTES).writeBytes(sourceBytes);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(CONTENT_LENGTH, response.payload().readableBytes());

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (keepAlive) {
            if (req.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(CONNECTION, KEEP_ALIVE);
            }
            ctx.write(response);
        } else {
            // Tell the client we're going to close the connection.
            response.headers().set(CONNECTION, CLOSE);
            ctx.write(response).addListener(ctx, ChannelFutureListeners.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
