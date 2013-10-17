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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link SimpleChannelHandler} that takes care of adding the right streamId to the
 * {@link HttpResponse} if one is not present. This makes it possible to just re-use plan handlers current used
 * for HTTP.
 */
public class SpdyHttpResponseStreamIdHandler extends SimpleChannelHandler {
    private static final Integer NO_ID = -1;
    private final Queue<Integer> ids = new ConcurrentLinkedQueue<Integer>();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpMessage) {
            boolean contains = ((HttpMessage) e.getMessage()).headers().contains(SpdyHttpHeaders.Names.STREAM_ID);
            if (!contains) {
                ids.add(NO_ID);
            } else {
                ids.add(SpdyHttpHeaders.getStreamId((HttpMessage) e.getMessage()));
            }
        } else if (e.getMessage() instanceof SpdyRstStreamFrame) {
            // remove id from the queue
            ids.remove(((SpdyRstStreamFrame) e.getMessage()).getStreamId());
        }
        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            Integer id = ids.poll();
            if (id != null && id.intValue() != NO_ID && !response.headers().contains(SpdyHttpHeaders.Names.STREAM_ID)) {
                SpdyHttpHeaders.setStreamId(response, id);
            }
        }
        super.writeRequested(ctx, e);
    }

}
