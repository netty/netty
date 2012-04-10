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
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelFactory;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.internal.ExecutorUtil;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractDatagramMulticastTest {


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
    public void testMulticast() throws Throwable {
                
        ConnectionlessBootstrap sb = new ConnectionlessBootstrap(newServerSocketChannelFactory(executor));
        ConnectionlessBootstrap cb = new ConnectionlessBootstrap(newClientSocketChannelFactory(executor));
        MulticastTestHandler mhandler = new MulticastTestHandler();
        
        cb.getPipeline().addFirst("handler", mhandler);
        sb.getPipeline().addFirst("handler", new SimpleChannelUpstreamHandler());

        int port = TestUtils.getFreePort();
        
        NetworkInterface iface = NetworkInterface.getNetworkInterfaces().nextElement();
        sb.setOption("networkInterface", iface);
        sb.setOption("reuseAddress", true);
        
        Channel sc = sb.bind(new InetSocketAddress(port));

        
        String group = "230.0.0.1";
        InetSocketAddress groupAddress = new InetSocketAddress(group, port);

        cb.setOption("networkInterface", iface);
        cb.setOption("reuseAddress", true);

        DatagramChannel cc = (DatagramChannel) cb.bind(new InetSocketAddress(port));
        

        assertTrue(cc.joinGroup(groupAddress, iface).awaitUninterruptibly().isSuccess());
 
        assertTrue(sc.write(ChannelBuffers.wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());
        
        
        assertTrue(mhandler.await());
      
        assertTrue(sc.write(ChannelBuffers.wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());


        // leave the group
        assertTrue(cc.leaveGroup(groupAddress, iface).awaitUninterruptibly().isSuccess());

        // sleep a second to make sure we left the group
        Thread.sleep(1000);
        
        // we should not receive a message anymore as we left the group before
        assertTrue(sc.write(ChannelBuffers.wrapInt(1), groupAddress).awaitUninterruptibly().isSuccess());

        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
        
    }
    
    private final class MulticastTestHandler extends SimpleChannelUpstreamHandler {
        private final CountDownLatch latch = new CountDownLatch(1);

        private boolean done = false;
        private volatile boolean fail = false;
        
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
            if (done) {
                fail = true;
            }
            
            Assert.assertEquals(1,((ChannelBuffer)e.getMessage()).readInt());
            
            latch.countDown();
            
            // mark the handler as done as we only are supposed to receive one message
            done = true;
        }
        
        public boolean await() throws Exception {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            if (fail) {
                // fail if we receive an message after we are done
                Assert.fail();
            }
            return success;
        }
        
    }
}
