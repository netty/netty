/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

/**
 * Instances the appropriate handshake class to use for clients
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 */
public class WebSocketServerHandshakerFactory {

    private final String webSocketURL;

    private final String subProtocols;

    private boolean allowExtensions = false;

    /**
     * Constructor specifying the destination web socket location
     * 
     * @param webSocketURL
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subProtocols
     *            CSV of supported protocols. Null if sub protocols not
     *            supported.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web
     *            socket frame
     */
    public WebSocketServerHandshakerFactory(String webSocketURL, String subProtocols, boolean allowExtensions) {
        this.webSocketURL = webSocketURL;
        this.subProtocols = subProtocols;
        this.allowExtensions = allowExtensions;
        return;
    }

    /**
     * Instances a new handshaker
     * 
     * @return A new WebSocketServerHandshaker for the requested web socket
     *         version. Null if web socket version is not supported.
     */
    public WebSocketServerHandshaker newHandshaker(ChannelHandlerContext ctx, HttpRequest req) {

        String version = req.getHeader(Names.SEC_WEBSOCKET_VERSION);
        if (version != null) {
            if (version.equals("8")) {
                // Version 8 of the wire protocol - assume version 10 of the
                // specification.
                return new WebSocketServerHandshaker10(webSocketURL, subProtocols, this.allowExtensions);
            } else {
                return null;
            }
        } else {
            // Assume version 00 where version header was not specified
            return new WebSocketServerHandshaker00(webSocketURL, subProtocols);
        }
    }

    /**
     * Return that we need cannot not support the web socket version
     * 
     * @param ctx
     *            Context
     */
    public void sendUnsupportedWebSocketVersionResponse(ChannelHandlerContext ctx) {
        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101, "Switching Protocols"));
        res.setStatus(HttpResponseStatus.UPGRADE_REQUIRED);
        res.setHeader(Names.SEC_WEBSOCKET_VERSION, "8");
        ctx.getChannel().write(res);
        return;
    }

}
