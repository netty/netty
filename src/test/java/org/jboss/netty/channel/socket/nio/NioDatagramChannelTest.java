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
package org.jboss.netty.channel.socket.nio;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class NioDatagramChannelTest {
    private static Channel sc;

    private static InetSocketAddress inetSocketAddress;

    @BeforeClass
    public static void setupChannel() {
        final NioDatagramChannelFactory channelFactory = new NioDatagramChannelFactory(
                Executors.newCachedThreadPool());
        final ConnectionlessBootstrap sb = new ConnectionlessBootstrap(channelFactory);
        inetSocketAddress = new InetSocketAddress("localhost", 9999);
        sc = sb.bind(inetSocketAddress);
        final SimpleHandler handler = new SimpleHandler();
        sc.getPipeline().addFirst("handler", handler);
    }

    @Test
    public void checkBoundPort() throws Throwable {
        final InetSocketAddress socketAddress = (InetSocketAddress) sc
                .getLocalAddress();
        assertEquals(9999, socketAddress.getPort());
    }

    @Test
    public void sendReciveOne() throws Throwable {
        final String expectedPayload = "some payload";
        sendRecive(expectedPayload);
    }

    @Test
    public void sendReciveMultiple() throws Throwable {
        final String expectedPayload = "some payload";
        for (int i = 0; i < 1000; i ++) {
            sendRecive(expectedPayload);
        }
    }

    public void clientBootstrap() {
        final NioDatagramChannelFactory channelFactory = new NioDatagramChannelFactory(
                Executors.newCachedThreadPool());
        final ConnectionlessBootstrap bootstrap = new ConnectionlessBootstrap(channelFactory);
        bootstrap.getPipeline().addLast("test", new SimpleHandler());
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        InetSocketAddress clientAddress = new InetSocketAddress("localhost",
                8888);
        bootstrap.setOption("localAddress", clientAddress);

        ChannelFuture ccf = bootstrap.connect(inetSocketAddress);
        ccf.awaitUninterruptibly();

        Channel cc = ccf.getChannel();
        final String payload = "client payload";
        ChannelFuture write = cc.write(ChannelBuffers.wrappedBuffer(payload
                .getBytes(), 0, payload.length()));
        write.awaitUninterruptibly();
    }

    @AfterClass
    public static void closeChannel() {
        if (sc != null) {
            final ChannelFuture future = sc.close();
            if (future != null) {
                future.awaitUninterruptibly();
            }
        }
    }

    private void sendRecive(final String expectedPayload) throws IOException {
        final UdpClient udpClient = new UdpClient(inetSocketAddress
                .getAddress(), inetSocketAddress.getPort());
        final DatagramPacket dp = udpClient.send(expectedPayload.getBytes());

        dp.setData(new byte[expectedPayload.length()]);
        assertFalse("The payload should have been cleared", expectedPayload
                .equals(new String(dp.getData())));

        udpClient.receive(dp);

        assertEquals(expectedPayload, new String(dp.getData()));
        udpClient.close();
    }

}
