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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.CharsetUtil;

public class HttpSnoopClientHandler extends SimpleChannelUpstreamHandler {
    
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(HttpSnoopClientHandler.class);

    private boolean readingChunks;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!readingChunks) {
            HttpResponse response = (HttpResponse) e.getMessage();

            logger.info("STATUS: " + response.getStatus());
            logger.info("VERSION: " + response.getProtocolVersion());
            
            if (!response.getHeaderNames().isEmpty()) {
                for (String name: response.getHeaderNames()) {
                    for (String value: response.getHeaders(name)) {
                        logger.info("HEADER: " + name + " = " + value);
                    }
                }
            }

            if (response.isChunked()) {
                readingChunks = true;
                logger.info("CHUNKED CONTENT {");
            } else {
                ChannelBuffer content = response.getContent();
                if (content.readable()) {
                    logger.info("CONTENT {");
                    logger.info(content.toString(CharsetUtil.UTF_8));
                    logger.info("} END OF CONTENT");
                }
            }
        } else {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            if (chunk.isLast()) {
                readingChunks = false;
                logger.info("} END OF CHUNKED CONTENT");
            } else {
                logger.info(chunk.getContent().toString(CharsetUtil.UTF_8));
            }
        }
    }
}
