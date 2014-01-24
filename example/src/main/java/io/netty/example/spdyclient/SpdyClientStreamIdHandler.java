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
package io.netty.example.spdyclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.spdy.SpdyHttpHeaders;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds a unique client stream ID to the SPDY header. Client stream IDs MUST be odd.
 */
public class SpdyClientStreamIdHandler extends MessageToMessageEncoder<HttpMessage> {

    private final AtomicInteger currentStreamId = new AtomicInteger(1);

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpMessage msg, List<Object> out) throws Exception {

        boolean contains = msg.headers().contains(SpdyHttpHeaders.Names.STREAM_ID);
        if (!contains) {
            // Client stream IDs are always odd
            SpdyHttpHeaders.setStreamId(msg, currentStreamId.getAndAdd(2));
        }

        out.add(ReferenceCountUtil.retain(msg));
    }

}
