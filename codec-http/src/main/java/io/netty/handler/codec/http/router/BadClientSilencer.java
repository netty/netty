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
package io.netty.handler.codec.http.router;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * This utility handler should be put at the last position of the inbound pipeline to
 * catch all exceptions caused by bad client (closed connection, malformed request etc.)
 * and server processing.
 *
 * By default exceptions are logged to stderr. You may need to override
 * onUnknownMessage, onBadClient, and onBadServer to log to more suitable places.
 */
@Sharable
public class BadClientSilencer extends SimpleChannelInboundHandler<Object> {
    /** Logs to stderr. Override this method to log to other places if you want. */
    protected void onUnknownMessage(Object msg) {
        System.err.println("Unknown msg: " + msg);
    }

    /** Logs to stderr. Override this method to log to other places if you want. */
    protected void onBadClient(Throwable e) {
        System.err.println("Caught exception (maybe client is bad): " + e);
    }

    /** Logs to stderr. Override this method to log to other places if you want. */
    protected void onBadServer(Throwable e) {
        System.err.println("Caught exception (maybe server is bad): " + e);
    }

    //----------------------------------------------------------------------------

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        // This handler is the last inbound handler.
        // This means msg has not been handled by any previous handler.
        ctx.channel().close();

        if (msg != io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT) { onUnknownMessage(msg); }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        ctx.channel().close();

        if (e instanceof java.io.IOException                            ||  // Connection reset by peer, Broken pipe
            e instanceof java.nio.channels.ClosedChannelException       ||
            e instanceof io.netty.handler.codec.DecoderException        ||
            e instanceof io.netty.handler.codec.CorruptedFrameException ||  // Bad WebSocket frame
            e instanceof java.lang.IllegalArgumentException             ||  // Use https://... to connect to HTTP server
            e instanceof javax.net.ssl.SSLException                     ||  // Use http://... to connect to HTTPS server
            e instanceof io.netty.handler.ssl.NotSslRecordException) {
            onBadClient(e);  // Maybe client is bad
        } else {
            onBadServer(e);  // Maybe server is bad
        }
    }
}
