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
package io.netty.testsuite.transport.socket;

import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.ConnectionlessBootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.socket.DatagramChannelFactory;
import io.netty.util.internal.ExecutorUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractDatagramTest {

    private static ExecutorService executor;


    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract DatagramChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract DatagramChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testSimpleSend() throws Throwable {
        ConnectionlessBootstrap sb = new ConnectionlessBootstrap(newServerSocketChannelFactory(executor));
        ConnectionlessBootstrap cb = new ConnectionlessBootstrap(newClientSocketChannelFactory(executor));

        final CountDownLatch latch = new CountDownLatch(1);
        sb.pipeline().addFirst("handler", new SimpleChannelUpstreamHandler() {

            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                super.messageReceived(ctx, e);
                Assert.assertEquals(1,((ChannelBuffer)e.getMessage()).readInt());

                latch.countDown();
            }
            
        });
        cb.pipeline().addFirst("handler", new SimpleChannelUpstreamHandler());

        Channel sc = sb.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        Channel cc = cb.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        assertTrue(cc.write(ChannelBuffers.wrapInt(1), sc.getLocalAddress()).awaitUninterruptibly().isSuccess());

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
        sb.releaseExternalResources();
        cb.releaseExternalResources();
      
    }
}
