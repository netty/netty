/*
 * Copyright 2014 The Netty Project
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
package io.netty.example.spdy.client;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.example.http.snoop.HttpSnoopClientHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This is a modified version of {@link HttpSnoopClientHandler} that uses a {@link BlockingQueue} to wait until an
 * HTTPResponse is received.
 */
public class HttpResponseClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final BlockingQueue<ChannelFuture> queue = new LinkedBlockingQueue<ChannelFuture>();

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            System.out.println("STATUS: " + response.status());
            System.out.println("VERSION: " + response.protocolVersion());
            System.out.println();

            if (!response.headers().isEmpty()) {
                for (CharSequence name : response.headers().names()) {
                    for (CharSequence value : response.headers().getAll(name)) {
                        System.out.println("HEADER: " + name + " = " + value);
                    }
                }
                System.out.println();
            }

            if (HttpUtil.isTransferEncodingChunked(response)) {
                System.out.println("CHUNKED CONTENT {");
            } else {
                System.out.println("CONTENT {");
            }
        }
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;

            System.out.print(content.content().toString(CharsetUtil.UTF_8));
            System.out.flush();

            if (content instanceof LastHttpContent) {
                System.out.println("} END OF CONTENT");
                queue.add(ctx.channel().newSucceededFuture());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        queue.add(ctx.channel().newFailedFuture(cause));
        cause.printStackTrace();
        ctx.close();
    }

    public BlockingQueue<ChannelFuture> queue() {
        return queue;
    }
}
