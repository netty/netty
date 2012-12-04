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

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractDatagramTest {


    protected abstract DatagramChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract DatagramChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testSimpleSend() throws Throwable {
        ConnectionlessBootstrap sb = new ConnectionlessBootstrap(
                newServerSocketChannelFactory(Executors.newCachedThreadPool()));
        ConnectionlessBootstrap cb = new ConnectionlessBootstrap(
                newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        final CountDownLatch latch = new CountDownLatch(1);
        sb.getPipeline().addFirst("handler", new SimpleChannelUpstreamHandler() {

            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                super.messageReceived(ctx, e);
                assertEquals(1, ((ChannelBuffer) e.getMessage()).readInt());

                latch.countDown();
            }

        });
        cb.getPipeline().addFirst("handler", new SimpleChannelUpstreamHandler());

        Channel sc = sb.bind(new InetSocketAddress("127.0.0.1",0));

        Channel cc = cb.bind(new InetSocketAddress("127.0.0.1", 0));
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        buf.writeInt(1);
        cc.write(buf, sc.getLocalAddress());

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
        cb.shutdown();
        sb.shutdown();
        cb.releaseExternalResources();
        sb.releaseExternalResources();
    }
}
