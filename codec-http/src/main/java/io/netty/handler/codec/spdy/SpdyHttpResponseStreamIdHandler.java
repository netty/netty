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
package io.netty.handler.codec.spdy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * {@link MessageToMessageCodec} that takes care of adding the right {@link SpdyHttpHeaders#Names#STREAM_ID} to the
 * {@link HttpResponse} if one is not present. This makes it possible to just re-use plan handlers current used
 * for HTTP.
 */
public class SpdyHttpResponseStreamIdHandler extends
        MessageToMessageCodec<HttpRequest, HttpRequest, HttpResponse, HttpResponse> {
    private static final Integer NO_ID = -1;
    private final Queue<Integer> ids = new ConcurrentLinkedQueue<Integer>();

    public SpdyHttpResponseStreamIdHandler() {
        super(new Class<?>[] { HttpRequest.class }, new Class<?>[] { HttpResponse.class });
    }

    @Override
    public HttpResponse encode(ChannelHandlerContext ctx, HttpResponse msg) throws Exception {
        boolean contains = msg.containsHeader(SpdyHttpHeaders.Names.STREAM_ID);
        if (!contains) {
            ids.add(NO_ID);
        } else {
            ids.add(SpdyHttpHeaders.getStreamId(msg));
        }
        return msg;
    }

    @Override
    public HttpRequest decode(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
        Integer id = ids.poll();
        if (id != null && id != NO_ID && !msg.containsHeader(SpdyHttpHeaders.Names.STREAM_ID)) {
            SpdyHttpHeaders.setStreamId(msg, id);
        }
        return msg;
    }

}
