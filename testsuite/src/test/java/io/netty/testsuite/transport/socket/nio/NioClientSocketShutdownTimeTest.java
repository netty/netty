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
package io.netty.testsuite.transport.socket.nio;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Executors;

import org.junit.Test;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.nio.NioClientSocketChannelFactory;
import io.netty.testsuite.util.DummyHandler;
import io.netty.util.SocketAddresses;

/**
 */
public class NioClientSocketShutdownTimeTest {

    @Test
    public void testShutdownTime() throws Throwable {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        ClientBootstrap b = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        b.getPipeline().addLast("handler", new DummyHandler());

        long startTime;
        long stopTime;

        try {
            serverSocket.configureBlocking(false);

            ChannelFuture f = b.connect(new InetSocketAddress(
                    SocketAddresses.LOCALHOST,
                    serverSocket.socket().getLocalPort()));

            serverSocket.accept();
            f.awaitUninterruptibly();

            if (f.getCause() != null) {
                throw f.getCause();
            }
            assertTrue(f.isSuccess());

            startTime = System.currentTimeMillis();

            f.getChannel().close().awaitUninterruptibly();
        } finally {
            b.getFactory().releaseExternalResources();

            stopTime = System.currentTimeMillis();

            try {
                serverSocket.close();
            } catch (IOException ex) {
                // Ignore.
            }
        }

        long shutdownTime = stopTime - startTime;
        assertTrue("Shutdown takes too long: " + shutdownTime + " ms", shutdownTime < 500);
    }
}
