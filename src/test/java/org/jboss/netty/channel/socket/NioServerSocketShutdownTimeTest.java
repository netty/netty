/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.TimingTestUtil;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class NioServerSocketShutdownTimeTest {

    @Test(timeout = 10000)
    public void testSuccessfulBindAttempt() throws Exception {
        if (!TimingTestUtil.isTimingTestEnabled()) {
            return;
        }

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

        long startTime = System.currentTimeMillis();

        Socket socket = null;
        try {
            socket = new Socket(
                    InetAddress.getLocalHost(),
                    ((InetSocketAddress) channel.getLocalAddress()).getPort());

            while (!handler.connected) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            socket.close();

            while (!handler.closed) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
            channel.close().awaitUninterruptibly();
            bootstrap.getFactory().releaseExternalResources();
        }

        long shutdownTime = System.currentTimeMillis() - startTime;
        assertTrue("Shutdown takes too long: " + shutdownTime + " ms", shutdownTime < 500);
    }

    @ChannelPipelineCoverage("all")
    private static class DummyHandler extends SimpleChannelHandler {
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
