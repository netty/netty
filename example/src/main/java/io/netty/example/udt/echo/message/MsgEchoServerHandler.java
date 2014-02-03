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
package io.netty.example.udt.echo.message;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.udt.nio.NioUdtProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler implementation for the echo server.
 */
@Sharable
public class MsgEchoServerHandler extends ChannelHandlerAdapter {

    private static final Logger log = Logger.getLogger(MsgEchoServerHandler.class.getName());

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
            final Throwable cause) {
        log.log(Level.WARNING, "close the connection when an exception is raised", cause);
        ctx.close();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        log.info("ECHO active " + NioUdtProvider.socketUDT(ctx.channel()).toStringOptions());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
