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
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.DummyHandler;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;


/**
 * An abstract test class to test socket client bootstraps
 */
public abstract class AbstractSocketClientBootstrapTest {

    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test(timeout = 10000)
    public void testFailedConnectionAttempt() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap();
        bootstrap.setFactory(newClientSocketChannelFactory(Executors.newCachedThreadPool()));
        bootstrap.getPipeline().addLast("dummy", new DummyHandler());
        bootstrap.setOption("remoteAddress", new InetSocketAddress("255.255.255.255", 1));
        ChannelFuture future = bootstrap.connect();
        future.awaitUninterruptibly();
        assertFalse(future.isSuccess());
        assertTrue(future.getCause() instanceof IOException);
        bootstrap.releaseExternalResources();
    }

    @Test(timeout = 10000)
    public void testSuccessfulConnectionAttempt() throws Throwable {
        ClientBootstrap bootstrap =
                new ClientBootstrap(newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        try {
            serverSocket.configureBlocking(false);

            bootstrap.getPipeline().addLast("dummy", new DummyHandler());
            bootstrap.setOption(
                    "remoteAddress",
                    new InetSocketAddress(
                            TestUtil.getLocalHost(),
                            serverSocket.socket().getLocalPort()));

            ChannelFuture future = bootstrap.connect();
            serverSocket.accept();
            future.awaitUninterruptibly();

            if (future.getCause() != null) {
                throw future.getCause();
            }
            assertTrue(future.isSuccess());

            future.getChannel().close().awaitUninterruptibly();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore.
            }
            bootstrap.shutdown();
            bootstrap.releaseExternalResources();
        }
    }

    @Test(timeout = 10000)
    public void testSuccessfulConnectionAttemptWithLocalAddress() throws Throwable {
        ClientBootstrap bootstrap =
                new ClientBootstrap(newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        try {
            serverSocket.configureBlocking(false);


            bootstrap.getPipeline().addLast("dummy", new DummyHandler());
            bootstrap.setOption(
                    "remoteAddress",
                    new InetSocketAddress(
                            TestUtil.getLocalHost(),
                            serverSocket.socket().getLocalPort()));
            bootstrap.setOption("localAddress", new InetSocketAddress(0));

            ChannelFuture future = bootstrap.connect();
            serverSocket.accept();
            future.awaitUninterruptibly();

            if (future.getCause() != null) {
                throw future.getCause();
            }
            assertTrue(future.isSuccess());

            future.getChannel().close().awaitUninterruptibly();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore.
            }
            bootstrap.shutdown();
            bootstrap.releaseExternalResources();
        }
    }

    @Test(expected = ChannelPipelineException.class)
    public void testFailedPipelineInitialization() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap(createMock(ChannelFactory.class));
        ChannelPipelineFactory pipelineFactory = createMock(ChannelPipelineFactory.class);
        bootstrap.setPipelineFactory(pipelineFactory);

        expect(pipelineFactory.getPipeline()).andThrow(new ChannelPipelineException());
        replay(pipelineFactory);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(TestUtil.getLocalHost(), 1));
        future.awaitUninterruptibly();
        bootstrap.shutdown();
        bootstrap.releaseExternalResources();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldHaveRemoteAddressOption() {
        new ClientBootstrap(createMock(ChannelFactory.class)).connect();
    }


    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullRemoteAddressParameter1() {
        new ClientBootstrap(createMock(ChannelFactory.class)).connect(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullRemoteAddressParameter2() {
        new ClientBootstrap(createMock(ChannelFactory.class)).connect(null, null);
    }
}
