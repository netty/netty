/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.example.http.snoop;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

public class HttpSnoopClientHandler extends SimpleChannelUpstreamHandler {

    private boolean readingChunks;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        if (!readingChunks) {
            HttpResponse response = (HttpResponse) e.getMessage();

            System.err.println("STATUS: " + response.getStatus());
            System.err.println("VERSION: " + response.getProtocolVersion());
            System.err.println();

            if (!response.headers().names().isEmpty()) {
                for (String name: response.headers().names()) {
                    for (String value: response.headers().getAll(name)) {
                        System.err.println("HEADER: " + name + " = " + value);
                    }
                }
                System.err.println();
            }

            if (response.isChunked()) {
                readingChunks = true;
                System.err.println("CHUNKED CONTENT {");
            } else {
                ChannelBuffer content = response.getContent();
                if (content.readable()) {
                    System.err.println("CONTENT {");
                    System.err.println(content.toString(CharsetUtil.UTF_8));
                    System.err.println("} END OF CONTENT");
                }
            }
        } else {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            if (chunk.isLast()) {
                readingChunks = false;
                System.err.println("} END OF CHUNKED CONTENT");
            } else {
                System.err.print(chunk.getContent().toString(CharsetUtil.UTF_8));
                System.err.flush();
            }
        }
    }
}
