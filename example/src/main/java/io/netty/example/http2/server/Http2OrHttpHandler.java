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
package io.netty.example.http2.server;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.draft10.Http2OrHttpChooser;
import org.eclipse.jetty.npn.NextProtoNego;

import javax.net.ssl.SSLEngine;
import java.util.logging.Logger;

/**
 * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once decided, the Netty pipeline is setup with
 * the correct handlers for the selected protocol.
 */
public class Http2OrHttpHandler extends Http2OrHttpChooser {
    private static final Logger logger = Logger.getLogger(
            Http2OrHttpHandler.class.getName());
    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    public Http2OrHttpHandler() {
        this(MAX_CONTENT_LENGTH);
    }

    public Http2OrHttpHandler(int maxHttpContentLength) {
        super(maxHttpContentLength);
    }

    @Override
    protected SelectedProtocol getProtocol(SSLEngine engine) {
        Http2ServerProvider provider = (Http2ServerProvider) NextProtoNego.get(engine);
        SelectedProtocol selectedProtocol = provider.getSelectedProtocol();

        logger.info("Selected Protocol is " + selectedProtocol);
        return selectedProtocol;
    }

    @Override
    protected ChannelHandler createHttp1RequestHandler() {
        return new HelloWorldHttp1Handler();
    }

    @Override
    protected ChannelHandler createHttp2RequestHandler() {
        return new HelloWorldHttp2Handler();
    }
}
