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
package org.jboss.netty.bootstrap;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.util.DummyHandler;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;


/**
 * An abstract test class to test server socket bootstraps
 */
public abstract class AbstractSocketServerBootstrapTest {

    private static final boolean BUFSIZE_MODIFIABLE;

    static {
        boolean bufSizeModifiable = true;

        Socket s = new Socket();
        try {
            s.setReceiveBufferSize(1234);
            try {
                if (s.getReceiveBufferSize() != 1234) {
                    throw new IllegalStateException();
                }
            } catch (Exception e) {
                bufSizeModifiable = false;
                System.err.println(
                        "Socket.getReceiveBufferSize() does not work: " + e);
            }
        } catch (Exception e) {
            bufSizeModifiable = false;
            System.err.println(
                    "Socket.setReceiveBufferSize() does not work: " + e);
        } finally {
            BUFSIZE_MODIFIABLE = bufSizeModifiable;
            try {
                s.close();
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);

    @Test(timeout = 30000, expected = ChannelException.class)
    public void testFailedBindAttempt() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        final ServerSocket ss = new ServerSocket(0);
        final int boundPort = ss.getLocalPort();
        try {
            bootstrap.setFactory(newServerSocketChannelFactory(Executors.newCachedThreadPool()));
            bootstrap.setOption("localAddress", new InetSocketAddress(boundPort));
            bootstrap.bind().close().awaitUninterruptibly();
        } finally {
            ss.close();
            bootstrap.releaseExternalResources();
        }
    }

    @Test(timeout = 30000)
    public void testSuccessfulBindAttempt() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap(
                newServerSocketChannelFactory(Executors.newCachedThreadPool()));

        bootstrap.setParentHandler(new ParentChannelHandler());
        bootstrap.setOption("localAddress", new InetSocketAddress(0));
        bootstrap.setOption("child.receiveBufferSize", 9753);
        bootstrap.setOption("child.sendBufferSize", 8642);

        bootstrap.getPipeline().addLast("dummy", new DummyHandler());

        Channel channel = bootstrap.bind();
        ParentChannelHandler pch =
            channel.getPipeline().get(ParentChannelHandler.class);

        Socket socket = null;
        try {
            socket = new Socket(
                    TestUtil.getLocalHost(),
                    ((InetSocketAddress) channel.getLocalAddress()).getPort());

            // Wait until the connection is open in the server side.
            while (pch.child == null) {
                Thread.yield();
            }

            SocketChannelConfig cfg = (SocketChannelConfig) pch.child.getConfig();
            if (BUFSIZE_MODIFIABLE) {
                assertEquals(9753, cfg.getReceiveBufferSize());
                assertEquals(8642, cfg.getSendBufferSize());
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
        }

        // Wait until the child connection is closed in the client side.
        // We do not use Channel.close() to make sure it is closed automatically.
        while (pch.child.isOpen()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Wait until all child events are fired.
        while (pch.result.length() < 2) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Confirm the received child events.
        assertEquals("12", pch.result.toString());
        bootstrap.releaseExternalResources();
    }

    @Test(expected = ChannelPipelineException.class)
    public void testFailedPipelineInitialization() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap(createMock(ChannelFactory.class));
        ChannelPipelineFactory pipelineFactory = createMock(ChannelPipelineFactory.class);
        bootstrap.setPipelineFactory(pipelineFactory);

        expect(pipelineFactory.getPipeline()).andThrow(new ChannelPipelineException());
        replay(pipelineFactory);

        bootstrap.connect(new InetSocketAddress(TestUtil.getLocalHost(), 1));
        bootstrap.releaseExternalResources();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldHaveLocalAddressOption() {
        new ServerBootstrap(createMock(ServerChannelFactory.class)).bind();
    }


    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullLocalAddressParameter() {
        new ServerBootstrap(createMock(ServerChannelFactory.class)).bind(null);
    }

    private static class ParentChannelHandler extends SimpleChannelUpstreamHandler {

        volatile Channel child;
        final StringBuffer result = new StringBuffer();

        ParentChannelHandler() {
        }

        @Override
        public void childChannelClosed(ChannelHandlerContext ctx,
                ChildChannelStateEvent e) throws Exception {
            result.append('2');
        }

        @Override
        public void childChannelOpen(ChannelHandlerContext ctx,
                ChildChannelStateEvent e) throws Exception {
            child = e.getChildChannel();
            result.append('1');
        }
    }
}
