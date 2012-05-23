/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http.snoop;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;

public class HttpSnoopClientHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private boolean readingChunks;


    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelInboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }


    @Override
    public void messageReceived(ChannelInboundHandlerContext<Object> ctx, Object msg) throws Exception {
        if (!readingChunks) {
            HttpResponse response = (HttpResponse) msg;

            System.out.println("STATUS: " + response.getStatus());
            System.out.println("VERSION: " + response.getProtocolVersion());
            System.out.println();

            if (!response.getHeaderNames().isEmpty()) {
                for (String name: response.getHeaderNames()) {
                    for (String value: response.getHeaders(name)) {
                        System.out.println("HEADER: " + name + " = " + value);
                    }
                }
                System.out.println();
            }

            if (response.isChunked()) {
                readingChunks = true;
                System.out.println("CHUNKED CONTENT {");
            } else {
                ChannelBuffer content = response.getContent();
                if (content.readable()) {
                    System.out.println("CONTENT {");
                    System.out.println(content.toString(CharsetUtil.UTF_8));
                    System.out.println("} END OF CONTENT");
                }
            }
        } else {
            HttpChunk chunk = (HttpChunk) msg;
            if (chunk.isLast()) {
                readingChunks = false;
                System.out.println("} END OF CHUNKED CONTENT");
            } else {
                System.out.print(chunk.getContent().toString(CharsetUtil.UTF_8));
                System.out.flush();
            }
        }
    }

    @Override
    public void exceptionCaught(
            ChannelInboundHandlerContext<Object> ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
