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

import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.spdy.SpdyOrHttpChooser;

/**
 * Negotiates with the browser if SPDY or HTTP is going to be used. Once decided, the Netty pipeline is setup with
 * the correct handlers for the selected protocol.
 */
public class SpdyOrHttpHandler extends SpdyOrHttpChooser {

    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    public SpdyOrHttpHandler() {
        this(MAX_CONTENT_LENGTH, MAX_CONTENT_LENGTH);
    }

    public SpdyOrHttpHandler(int maxSpdyContentLength, int maxHttpContentLength) {
        super(maxSpdyContentLength, maxHttpContentLength);
    }

    @Override
    protected ChannelInboundHandler createHttpRequestHandlerForHttp() {
        return new SpdyServerHandler();
    }

}
