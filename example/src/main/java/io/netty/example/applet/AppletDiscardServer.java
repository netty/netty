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
package io.netty.example.applet;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

import javax.swing.JApplet;

/**
 * {@link JApplet} which starts up a Server that receive data and discard it.
 */
public class AppletDiscardServer extends JApplet {
    private ServerBootstrap bootstrap;
    @Override
    public void init() {
        try {
            bootstrap = new ServerBootstrap();
            bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup()).channel(NioServerSocketChannel.class)
                    .localAddress(9999).childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new DiscardServerHandler());
                }
            });
            ChannelFuture f = bootstrap.bind().sync();
            f.channel().closeFuture().sync();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    private static final class DiscardServerHandler extends ChannelInboundByteHandlerAdapter {

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            System.out.println("Received: " + in.toString(CharsetUtil.UTF_8));
            in.clear();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
