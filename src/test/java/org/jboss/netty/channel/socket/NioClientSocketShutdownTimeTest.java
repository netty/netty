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
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.DummyHandler;
import org.jboss.netty.util.TimeBasedUuidGenerator;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class NioClientSocketShutdownTimeTest {

    static {
        // Initialize the MD5 algorithm before testing - it takes too long
        // on some JDK.
        TimeBasedUuidGenerator.generate();
    }

    @Test
    public void testShutdownTime() throws Throwable {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(0));

        ExecutorService e1 = Executors.newCachedThreadPool();
        ExecutorService e2 = Executors.newCachedThreadPool();
        ClientBootstrap b = new ClientBootstrap(new NioClientSocketChannelFactory(e1, e2));
        b.getPipeline().addLast("handler", new DummyHandler());

        long startTime;

        try {
            serverSocket.configureBlocking(false);

            ChannelFuture f = b.connect(new InetSocketAddress(
                    InetAddress.getLocalHost(),
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
            assertEquals(0, e1.shutdownNow().size());
            assertEquals(0, e2.shutdownNow().size());
            for (;;) {
                try {
                    if (e1.awaitTermination(1, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }
            for (;;) {
                try {
                    if (e2.awaitTermination(1, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }

            try {
                serverSocket.close();
            } catch (IOException ex) {
                // Ignore.
            }
        }

        long shutdownTime = System.currentTimeMillis() - startTime;
        assertTrue("Shutdown takes too long: " + shutdownTime + " ms", shutdownTime < 500);
    }
}
