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
package org.jboss.netty.channel.socket;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;

public abstract class AbstractDatagramMulticastTest {




    protected abstract DatagramChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract DatagramChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testMulticast() throws Throwable {
        ConnectionlessBootstrap sb = new ConnectionlessBootstrap(
                newServerSocketChannelFactory(Executors.newCachedThreadPool()));
        ConnectionlessBootstrap cb = new ConnectionlessBootstrap(
                newClientSocketChannelFactory(Executors.newCachedThreadPool()));
        DatagramChannel sc = null;
        DatagramChannel cc = null;
        try {
            MulticastTestHandler mhandler = new MulticastTestHandler();

            cb.getPipeline().addFirst("handler", mhandler);
            sb.getPipeline().addFirst("handler", new SimpleChannelUpstreamHandler());

            int port = TestUtil.getFreePort();

            NetworkInterface loopbackIf;
            try {
                loopbackIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            } catch (SocketException e) {
                loopbackIf = null;
            }

            // check if the NetworkInterface is null, this is the case on my ubuntu dev machine but not on osx and windows.
            // if so fail back the the first interface
            if (loopbackIf == null) {
                for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
                     e.hasMoreElements();) {
                    NetworkInterface nif = e.nextElement();
                    if (nif.isLoopback()) {
                        loopbackIf = nif;
                        break;
                    }
                }
            }

            sb.setOption("networkInterface", loopbackIf);
            sb.setOption("reuseAddress", true);

            sc = (DatagramChannel) sb.bind(new InetSocketAddress(port));


            String group = "230.0.0.1";
            InetSocketAddress groupAddress = new InetSocketAddress(group, port);

            cb.setOption("networkInterface", loopbackIf);
            cb.setOption("reuseAddress", true);

            cc = (DatagramChannel) cb.bind(new InetSocketAddress(port));

            assertTrue(cc.joinGroup(groupAddress, loopbackIf).awaitUninterruptibly().isSuccess());

            assertTrue(sc.write(wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());

            assertTrue(mhandler.await());

            assertTrue(sc.write(wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());

            // leave the group
            assertTrue(cc.leaveGroup(groupAddress, loopbackIf).awaitUninterruptibly().isSuccess());

            // sleep a second to make sure we left the group
            Thread.sleep(1000);

            // we should not receive a message anymore as we left the group before
            assertTrue(sc.write(wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());
        } finally {
            if (sc != null) {
                sc.close().awaitUninterruptibly();
            }
            if (cc != null) {
                cc.close().awaitUninterruptibly();
            }
            sb.releaseExternalResources();
            cb.releaseExternalResources();
        }
    }

    private static ChannelBuffer wrapInt(int value) {
        ChannelBuffer buf = ChannelBuffers.buffer(4);
        buf.writeInt(value);
        return buf;
    }

    private static final class MulticastTestHandler extends SimpleChannelUpstreamHandler {
        private final CountDownLatch latch = new CountDownLatch(1);

        private boolean done;
        private volatile boolean fail;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
            if (done) {
                fail = true;
            }

            assertEquals(1, ((ChannelBuffer) e.getMessage()).readInt());

            latch.countDown();

            // mark the handler as done as we only are supposed to receive one message
            done = true;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            e.getCause().printStackTrace();
        }

        public boolean await() throws Exception {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            if (fail) {
                // fail if we receive an message after we are done
                fail();
            }
            return success;
        }

    }
}
