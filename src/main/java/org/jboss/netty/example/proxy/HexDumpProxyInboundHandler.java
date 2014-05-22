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
package org.jboss.netty.example.proxy;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

import java.net.InetSocketAddress;

public class HexDumpProxyInboundHandler extends SimpleChannelUpstreamHandler {

    private final ClientSocketChannelFactory cf;

    // This lock guards against the race condition that overrides the
    // OP_READ flag incorrectly.
    // See the related discussion: http://markmail.org/message/x7jc6mqx6ripynqf
    final Object trafficLock = new Object();

    private volatile Channel outboundChannel;

    public HexDumpProxyInboundHandler(ClientSocketChannelFactory cf) {
        this.cf = cf;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Suspend incoming traffic until connected to the remote host.
        final Channel inboundChannel = e.getChannel();
        inboundChannel.setReadable(false);

        // Start the connection attempt.
        ClientBootstrap cb = new ClientBootstrap(cf);
        cb.getPipeline().addLast("handler", new OutboundHandler(e.getChannel()));
        ChannelFuture f = cb.connect(new InetSocketAddress(HexDumpProxy.REMOTE_HOST, HexDumpProxy.REMOTE_PORT));

        outboundChannel = f.getChannel();
        f.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    // Connection attempt succeeded:
                    // Begin to accept incoming traffic.
                    inboundChannel.setReadable(true);
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
        ChannelBuffer msg = (ChannelBuffer) e.getMessage();
        //System.err.println(">>> " + ChannelBuffers.hexDump(msg));
        synchronized (trafficLock) {
            outboundChannel.write(msg);
            // If outboundChannel is saturated, do not read until notified in
            // OutboundHandler.channelInterestChanged().
            if (!outboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // If inboundChannel is not saturated anymore, continue accepting
        // the incoming traffic from the outboundChannel.
        synchronized (trafficLock) {
            if (e.getChannel().isWritable()) {
                if (outboundChannel != null) {
                    outboundChannel.setReadable(true);
                }
            }
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        closeOnFlush(e.getChannel());
    }

    private class OutboundHandler extends SimpleChannelUpstreamHandler {

        private final Channel inboundChannel;

        OutboundHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
            ChannelBuffer msg = (ChannelBuffer) e.getMessage();
            //System.err.println("<<< " + ChannelBuffers.hexDump(msg));
            synchronized (trafficLock) {
                inboundChannel.write(msg);
                // If inboundChannel is saturated, do not read until notified in
                // HexDumpProxyInboundHandler.channelInterestChanged().
                if (!inboundChannel.isWritable()) {
                    e.getChannel().setReadable(false);
                }
            }
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
            // If outboundChannel is not saturated anymore, continue accepting
            // the incoming traffic from the inboundChannel.
            synchronized (trafficLock) {
                if (e.getChannel().isWritable()) {
                    inboundChannel.setReadable(true);
                }
            }
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
            closeOnFlush(inboundChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            e.getCause().printStackTrace();
            closeOnFlush(e.getChannel());
        }
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isConnected()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
