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
package io.netty.testsuite.transport;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;
import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.sctp.SctpChannelConfig;
import io.netty.testsuite.util.DummyHandler;
import io.netty.testsuite.util.SctpTestUtil;
import io.netty.util.internal.ExecutorUtil;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;


/**
 * An abstract test class to test server socket bootstraps
 */
public abstract class AbstractSocketServerBootstrapTest {

    private static final boolean BUFSIZE_MODIFIABLE;

    static {
        boolean bufSizeModifiable = true;

        SctpChannel s = null;
        try {
            s = SctpChannel.open();
            bufSizeModifiable = s.supportedOptions().contains(SctpStandardSocketOptions.SO_RCVBUF);
        } catch (Exception e) {
            bufSizeModifiable = false;
            System.err.println(
                    "SCTP SO_RCVBUF does not work: " + e);
        } finally {
            BUFSIZE_MODIFIABLE = bufSizeModifiable;
            try {
                if (s != null) {
                    s.close();
                }
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    private static ExecutorService executor;

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);

    @Test(timeout = 30000, expected = ChannelException.class)
    public void testFailedBindAttempt() throws Exception {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        SctpServerChannel serverChannel = SctpServerChannel.open();
        serverChannel.bind(new InetSocketAddress(SctpTestUtil.LOOP_BACK, 0));

        try {
            final Iterator<SocketAddress> serverAddresses = serverChannel.getAllLocalAddresses().iterator();
            InetSocketAddress serverAddress = (InetSocketAddress) serverAddresses.next();
            final int boundPort = serverAddress.getPort();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.setFactory(newServerSocketChannelFactory(executor));
            bootstrap.setOption("localAddress", new InetSocketAddress(SctpTestUtil.LOOP_BACK, boundPort));
            bootstrap.bind().close().awaitUninterruptibly();
        } finally {
            serverChannel.close();
        }
    }

    @Test(timeout = 30000)
    public void testSuccessfulBindAttempt() throws Exception {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        ServerBootstrap bootstrap = new ServerBootstrap(
                newServerSocketChannelFactory(executor));

        bootstrap.setParentHandler(new ParentChannelHandler());
        bootstrap.setOption("localAddress", new InetSocketAddress(SctpTestUtil.LOOP_BACK, 0));
        bootstrap.setOption("child.receiveBufferSize", 9753);
        bootstrap.setOption("child.sendBufferSize", 8642);

        bootstrap.getPipeline().addLast("dummy", new DummyHandler());

        Channel channel = bootstrap.bind();
        ParentChannelHandler pch =
            channel.getPipeline().get(ParentChannelHandler.class);

        SctpChannel sctpChannel = SctpChannel.open();
        try {
            sctpChannel.connect(
                    new InetSocketAddress(
                            SctpTestUtil.LOOP_BACK,
                            ((InetSocketAddress) channel.getLocalAddress()).getPort()));

            // Wait until the connection is open in the server side.
            while (pch.child == null) {
                Thread.yield();
            }

            SctpChannelConfig cfg = (SctpChannelConfig) pch.child.getConfig();
            if (BUFSIZE_MODIFIABLE) {
                assertEquals(9753, cfg.getReceiveBufferSize());
                assertEquals(8642, cfg.getSendBufferSize());
            }
        } finally {
            if (sctpChannel != null) {
                try {
                    sctpChannel.close();
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
    }

    @Test(expected = ChannelPipelineException.class)
    public void testFailedPipelineInitialization() throws Exception {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        ClientBootstrap bootstrap = new ClientBootstrap(EasyMock.createMock(ChannelFactory.class));
        ChannelPipelineFactory pipelineFactory = EasyMock.createMock(ChannelPipelineFactory.class);
        bootstrap.setPipelineFactory(pipelineFactory);

        EasyMock.expect(pipelineFactory.getPipeline()).andThrow(new ChannelPipelineException());
        EasyMock.replay(pipelineFactory);

        bootstrap.connect(new InetSocketAddress(SctpTestUtil.LOOP_BACK, 1));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldHaveLocalAddressOption() {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        new ServerBootstrap(EasyMock.createMock(ServerChannelFactory.class)).bind();
    }


    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullLocalAddressParameter() {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        new ServerBootstrap(EasyMock.createMock(ServerChannelFactory.class)).bind(null);
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
