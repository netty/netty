/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.server;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2OrHttpChooser;

import javax.net.ssl.SSLEngine;

/**
 * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once decided, the Netty
 * pipeline is setup with the correct handlers for the selected protocol.
 */
public class Http2OrHttpHandler extends Http2OrHttpChooser {
    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    public Http2OrHttpHandler() {
        this(MAX_CONTENT_LENGTH);
    }

    public Http2OrHttpHandler(int maxHttpContentLength) {
        super(maxHttpContentLength);
    }

    @Override
    protected SelectedProtocol getProtocol(SSLEngine engine) {
        String[] protocol = engine.getSession().getProtocol().split(":");
        if (protocol != null && protocol.length > 1) {
            SelectedProtocol selectedProtocol = SelectedProtocol.protocol(protocol[1]);
            System.err.println("Selected Protocol is " + selectedProtocol);
            return selectedProtocol;
        }
        return SelectedProtocol.UNKNOWN;
    }

    @Override
    protected ChannelHandler createHttp1RequestHandler() {
        return new HelloWorldHttp1Handler();
    }

    @Override
    protected Http2ConnectionHandler createHttp2RequestHandler() {
        return new HelloWorldHttp2Handler();
    }
}
