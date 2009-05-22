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
package org.jboss.netty.bootstrap;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.DummyHandler;
import org.jboss.netty.util.TestUtil;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
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
        bootstrap.getPipeline().addLast("dummy", new DummyHandler());
        bootstrap.setOption("remoteAddress", new InetSocketAddress("255.255.255.255", 1));
        ChannelFuture future = bootstrap.connect();
        future.awaitUninterruptibly();
        assertFalse(future.isSuccess());
        assertTrue(future.getCause() instanceof IOException);
    }

    @Test(timeout = 10000)
    public void testSuccessfulConnectionAttempt() throws Throwable {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        try {
            serverSocket.configureBlocking(false);

            ClientBootstrap bootstrap =
                new ClientBootstrap(newClientSocketChannelFactory(executor));

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
        }
    }

    @Test(expected = ChannelPipelineException.class)
    public void testFailedPipelineInitialization() throws Exception {
        ClientBootstrap bootstrap = new ClientBootstrap(createMock(ChannelFactory.class));
        ChannelPipelineFactory pipelineFactory = createMock(ChannelPipelineFactory.class);
        bootstrap.setPipelineFactory(pipelineFactory);

        expect(pipelineFactory.getPipeline()).andThrow(new ChannelPipelineException());
        replay(pipelineFactory);

        bootstrap.connect(new InetSocketAddress(TestUtil.getLocalHost(), 1));
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
