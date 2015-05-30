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
package io.netty.example.spdy.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.spdy.SpdyFrameCodec;
import io.netty.handler.codec.spdy.SpdyHttpDecoder;
import io.netty.handler.codec.spdy.SpdyHttpEncoder;
import io.netty.handler.codec.spdy.SpdyHttpResponseStreamIdHandler;
import io.netty.handler.codec.spdy.SpdyOrHttpChooser;
import io.netty.handler.codec.spdy.SpdySessionHandler;
import io.netty.handler.codec.spdy.SpdyVersion;

/**
 * Negotiates with the browser if SPDY or HTTP is going to be used. Once decided, the Netty pipeline is setup with
 * the correct handlers for the selected protocol.
 */
public class SpdyOrHttpHandler extends SpdyOrHttpChooser {

    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    @Override
    protected void configureSpdy(ChannelHandlerContext ctx, SpdyVersion version) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        p.addLast(new SpdyFrameCodec(version));
        p.addLast(new SpdySessionHandler(version, true));
        p.addLast(new SpdyHttpEncoder(version));
        p.addLast(new SpdyHttpDecoder(version, MAX_CONTENT_LENGTH));
        p.addLast(new SpdyHttpResponseStreamIdHandler());
        p.addLast(new SpdyServerHandler());
    }

    @Override
    protected void configureHttp1(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
        p.addLast(new SpdyServerHandler());
    }
}
