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
package io.netty.channel.socket;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.nio.NioClientSocketChannelFactory;
import io.netty.util.DummyHandler;
import io.netty.util.TestUtil;
import org.junit.Test;


/**
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
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
                    TestUtil.getLocalHost(),
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
