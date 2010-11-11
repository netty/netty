/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.channel.socket;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2119 $, $Date: 2010-02-01 20:46:09 +0900 (Mon, 01 Feb 2010) $
 *
 */
public class NioServerSocketShutdownTimeTest {

    @Test(timeout = 10000)
    public void testSuccessfulBindAttempt() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setOption("localAddress", new InetSocketAddress(0));
        bootstrap.setOption("child.receiveBufferSize", 9753);
        bootstrap.setOption("child.sendBufferSize", 8642);

        DummyHandler handler = new DummyHandler();
        bootstrap.getPipeline().addLast("dummy", handler);

        Channel channel = bootstrap.bind();

        final long startTime;

        Socket socket = null;
        try {
            socket = new Socket(
                    TestUtil.getLocalHost(),
                    ((InetSocketAddress) channel.getLocalAddress()).getPort());

            while (!handler.connected) {
                Thread.yield();
            }

            socket.close();

            while (!handler.closed) {
                Thread.yield();
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }

            startTime = System.currentTimeMillis();
            channel.close().awaitUninterruptibly();
            bootstrap.getFactory().releaseExternalResources();
        }

        long shutdownTime = System.currentTimeMillis() - startTime;
        assertTrue("Shutdown takes too long: " + shutdownTime + " ms", shutdownTime < 500);
    }

    private static class DummyHandler extends SimpleChannelUpstreamHandler {
        volatile boolean connected;
        volatile boolean closed;

        DummyHandler() {
            super();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            connected = true;
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            closed = true;
        }
    }
}
