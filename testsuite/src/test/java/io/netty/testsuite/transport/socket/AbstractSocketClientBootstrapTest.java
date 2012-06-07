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
package io.netty.testsuite.transport.socket;

import static org.junit.Assert.*;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipelineException;
import io.netty.testsuite.util.DummyHandler;
import io.netty.util.SocketAddresses;
import io.netty.util.internal.ExecutorUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * An abstract test class to test socket client bootstraps
 */
public abstract class AbstractSocketClientBootstrapTest {

    private static ExecutorService executor;

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test(timeout = 10000)
    public void testFailedConnectionAttempt() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap();
        bootstrap.setFactory(newClientSocketChannelFactory(executor));
        bootstrap.pipeline().addLast("dummy", new DummyHandler());
        bootstrap.setOption("remoteAddress", new InetSocketAddress("255.255.255.255", 1));
        ChannelFuture future = bootstrap.connect();
        future.awaitUninterruptibly();
        assertFalse(future.isSuccess());
        assertTrue(future.cause() instanceof IOException);
    }

    @Test(timeout = 10000)
    public void testSuccessfulConnectionAttempt() throws Throwable {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        try {
            serverSocket.configureBlocking(false);

            ClientBootstrap bootstrap =
                new ClientBootstrap(newClientSocketChannelFactory(executor));

            bootstrap.pipeline().addLast("dummy", new DummyHandler());
            bootstrap.setOption(
                    "remoteAddress",
                    new InetSocketAddress(
                            SocketAddresses.LOCALHOST,
                            serverSocket.socket().getLocalPort()));

            ChannelFuture future = bootstrap.connect();
            serverSocket.accept();
            future.awaitUninterruptibly();

            if (future.cause() != null) {
                throw future.cause();
            }
            assertTrue(future.isSuccess());

            future.channel().close().awaitUninterruptibly();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    @Test(timeout = 10000)
    public void testSuccessfulConnectionAttemptWithLocalAddress() throws Throwable {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        try {
            serverSocket.configureBlocking(false);

            ClientBootstrap bootstrap =
                new ClientBootstrap(newClientSocketChannelFactory(executor));

            bootstrap.pipeline().addLast("dummy", new DummyHandler());
            bootstrap.setOption(
                    "remoteAddress",
                    new InetSocketAddress(
                            SocketAddresses.LOCALHOST,
                            serverSocket.socket().getLocalPort()));
            bootstrap.setOption("localAddress", new InetSocketAddress(0));

            ChannelFuture future = bootstrap.connect();
            serverSocket.accept();
            future.awaitUninterruptibly();

            if (future.cause() != null) {
                throw future.cause();
            }
            assertTrue(future.isSuccess());

            future.channel().close().awaitUninterruptibly();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    @Test(expected = ChannelPipelineException.class)
    public void testFailedPipelineInitialization() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap(EasyMock.createMock(ChannelFactory.class));
        ChannelPipelineFactory pipelineFactory = EasyMock.createMock(ChannelPipelineFactory.class);
        bootstrap.setPipelineFactory(pipelineFactory);

        EasyMock.expect(pipelineFactory.pipeline()).andThrow(new ChannelPipelineException());
        EasyMock.replay(pipelineFactory);

        bootstrap.connect(new InetSocketAddress(SocketAddresses.LOCALHOST, 1));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldHaveRemoteAddressOption() {
        new ClientBootstrap(EasyMock.createMock(ChannelFactory.class)).connect();
    }


    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullRemoteAddressParameter1() {
        new ClientBootstrap(EasyMock.createMock(ChannelFactory.class)).connect(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullRemoteAddressParameter2() {
        new ClientBootstrap(EasyMock.createMock(ChannelFactory.class)).connect(null, null);
    }
}
