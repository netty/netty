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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DatagramMulticastTest extends AbstractDatagramTest {

    @Test
    public void testMulticast() throws Throwable {
        run();
    }

    public void testMulticast(Bootstrap sb, Bootstrap cb) throws Throwable {
        MulticastTestHandler mhandler = new MulticastTestHandler();

        sb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                // Nothing will be sent.
            }
        });

        cb.handler(mhandler);

        sb.option(ChannelOption.IP_MULTICAST_IF, NetUtil.LOOPBACK_IF);
        sb.option(ChannelOption.SO_REUSEADDR, true);
        cb.option(ChannelOption.IP_MULTICAST_IF, NetUtil.LOOPBACK_IF);
        cb.option(ChannelOption.SO_REUSEADDR, true);
        cb.localAddress(addr.getPort());

        Channel sc = sb.bind().sync().channel();
        if (sc instanceof OioDatagramChannel) {
            // skip the test for OIO, as it fails because of
            // No route to host which makes no sense.
            // Maybe a JDK bug ?
            sc.close().awaitUninterruptibly();
            return;
        }
        DatagramChannel cc = (DatagramChannel) cb.bind().sync().channel();

        String group = "230.0.0.1";
        InetSocketAddress groupAddress = new InetSocketAddress(group, addr.getPort());

        cc.joinGroup(groupAddress, NetUtil.LOOPBACK_IF).sync();

        sc.writeAndFlush(new DatagramPacket(Unpooled.copyInt(1), groupAddress)).sync();
        assertTrue(mhandler.await());

        // leave the group
        cc.leaveGroup(groupAddress, NetUtil.LOOPBACK_IF).sync();

        // sleep a second to make sure we left the group
        Thread.sleep(1000);

        // we should not receive a message anymore as we left the group before
        sc.writeAndFlush(new DatagramPacket(Unpooled.copyInt(1), groupAddress)).sync();
        mhandler.await();

        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
    }

    private static final class MulticastTestHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final CountDownLatch latch = new CountDownLatch(1);

        private boolean done;
        private volatile boolean fail;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            if (done) {
                fail = true;
            }

            assertEquals(1, msg.content().readInt());

            latch.countDown();

            // mark the handler as done as we only are supposed to receive one message
            done = true;
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
