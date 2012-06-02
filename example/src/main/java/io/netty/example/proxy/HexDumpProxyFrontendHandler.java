/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class HexDumpProxyFrontendHandler extends ChannelInboundStreamHandlerAdapter {

    private final String remoteHost;
    private final int remotePort;

    private volatile Channel outboundChannel;

    public HexDumpProxyFrontendHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        // TODO: Suspend incoming traffic until connected to the remote host.
        //       Currently, we just keep the inbound traffic in the client channel's outbound buffer.
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.eventLoop(inboundChannel.eventLoop())
         .channel(new NioSocketChannel())
         .remoteAddress(remoteHost, remotePort)
         .initializer(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new HexDumpProxyBackendHandler(inboundChannel));
            }
         });

        ChannelFuture f = b.connect();
        outboundChannel = f.channel();
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    // Connection attempt succeeded:
                    // TODO: Begin to accept incoming traffic.
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer in = ctx.inbound().byteBuffer();
        ChannelBuffer out = outboundChannel.outboundByteBuffer();
        out.discardReadBytes();
        out.writeBytes(in);
        in.clear();
        if (outboundChannel.isActive()) {
            outboundChannel.flush();
        }
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }



    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.flush().addListener(ChannelFutureListener.CLOSE);
        }
    }
}
