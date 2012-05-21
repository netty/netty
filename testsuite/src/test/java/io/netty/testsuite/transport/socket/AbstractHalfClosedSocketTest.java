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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.socket.SimpleSocketChannelUpstreamHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.SocketAddresses;
import io.netty.util.internal.ExecutorUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractHalfClosedSocketTest {

    private static ExecutorService executor;

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testCloseInputClient() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));
        sb.setOption("reuseAddress", true);
        cb.setOption("reuseAddress", true);

        CloseTestHandler clientHandler = new CloseTestHandler();
        ServerInputCloseTestHandler serverHandler = new ServerInputCloseTestHandler();
        
        sb.getPipeline().addFirst("handler", serverHandler);
        cb.getPipeline().addFirst("handler", clientHandler);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SocketAddresses.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        SocketChannel cc = (SocketChannel) ccf.getChannel();

        assertTrue(cc.isInputOpen());

        ChannelFuture future = cc.closeInput();
        final AtomicBoolean listenerNotified = new AtomicBoolean();
        future.addListener(new ChannelFutureListener() {
            
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        });
        future.awaitUninterruptibly();
        
        assertTrue(listenerNotified.get());
        assertTrue(future.isSuccess());
        assertFalse(cc.isInputOpen());


        // Write something to the server
        cc.write(ChannelBuffers.wrappedBuffer("TEST".getBytes())).awaitUninterruptibly();

        // Check if the input closed event was fired
        assertTrue(clientHandler.isInputClosedFired());
        
        //assertFalse(clientHandler.messageReceived);
        serverHandler.await().close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();

    }
    
    

    @Test(timeout = 10000)
    public void testCloseOutputClient() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));
        sb.setOption("reuseAddress", true);
        cb.setOption("reuseAddress", true);

        CloseTestHandler clientHandler = new CloseTestHandler();
        ServerOutputCloseTestHandler serverHandler = new ServerOutputCloseTestHandler();
        
        sb.getPipeline().addFirst("handler", serverHandler);
        cb.getPipeline().addFirst("handler", clientHandler);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SocketAddresses.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        SocketChannel cc = (SocketChannel) ccf.getChannel();
        assertTrue(cc.isOutputOpen());

        ChannelFuture future = cc.closeOutput();
        final AtomicBoolean listenerNotified = new AtomicBoolean();
        future.addListener(new ChannelFutureListener() {
            
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        });
        future.awaitUninterruptibly();

        assertTrue(listenerNotified.get());
        assertTrue(future.isSuccess());
        assertFalse(cc.isOutputOpen());

        // Write something to the server which should fail as we closed the output
        assertFalse(cc.write(ChannelBuffers.wrappedBuffer("TEST".getBytes())).awaitUninterruptibly().isSuccess());

        assertFalse(serverHandler.isMessageReceivedFired());
        // Check if the output closed event was fired
        assertTrue(clientHandler.isOutputClosedFired());

        serverHandler.await().close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();


    }
    
    
    @Test(timeout = 10000)
    public void testCloseInputServer() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));
        sb.setOption("reuseAddress", true);
        cb.setOption("reuseAddress", true);

        CloseTestHandler clientHandler = new CloseTestHandler();
        ServerOutputCloseTestHandler serverHandler = new ServerOutputCloseTestHandler();
        
        sb.getPipeline().addFirst("handler", serverHandler);
        cb.getPipeline().addFirst("handler", clientHandler);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SocketAddresses.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        SocketChannel cc = (SocketChannel) ccf.getChannel();
        assertTrue(serverHandler.await().isInputOpen());
        ChannelFuture future = serverHandler.channel.closeInput();
        final AtomicBoolean listenerNotified = new AtomicBoolean();
        future.addListener(new ChannelFutureListener() {
            
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        });
        future.awaitUninterruptibly();
        
        assertTrue(listenerNotified.get());
        assertTrue(future.isSuccess());
        assertFalse(serverHandler.await().isInputOpen());

        // Write something to the server
        cc.write(ChannelBuffers.wrappedBuffer("TEST".getBytes())).awaitUninterruptibly();
       
        assertFalse(serverHandler.isMessageReceivedFired());
        
        // Check if the input closed event was fired before
        assertTrue(serverHandler.isInputClosedFired());
        
        sc.close().awaitUninterruptibly();
        serverHandler.await().close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();

    }
    
    @Test(timeout = 10000)
    public void testCloseOutputServer() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));
        sb.setOption("reuseAddress", true);
        cb.setOption("reuseAddress", true);

        CloseTestHandler clientHandler = new CloseTestHandler();
        ServerOutputCloseTestHandler serverHandler = new ServerOutputCloseTestHandler();
        
        sb.getPipeline().addFirst("handler", serverHandler);
        cb.getPipeline().addFirst("handler", clientHandler);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SocketAddresses.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        SocketChannel cc = (SocketChannel) ccf.getChannel();

        assertTrue(serverHandler.await().isOutputOpen());

        ChannelFuture future = serverHandler.await().closeOutput();
        
        final AtomicBoolean listenerNotified = new AtomicBoolean();
        future.addListener(new ChannelFutureListener() {
            
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        });
        future.awaitUninterruptibly();
        
        assertTrue(listenerNotified.get());
        assertTrue(future.isSuccess());
        assertFalse(serverHandler.await().isOutputOpen());
        
        // Write something to the client, this should fail as we closed the output
        assertFalse(serverHandler.await().write(ChannelBuffers.wrappedBuffer("TEST".getBytes())).awaitUninterruptibly().isSuccess());
        
        // Check if the output closed event was fired
        assertTrue(serverHandler.isOutputClosedFired());

        
        assertFalse(clientHandler.isMessageReceivedFired());
        sc.close().awaitUninterruptibly();
        serverHandler.await().close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();

    }
    
    
    
    private static final class ServerOutputCloseTestHandler extends CloseTestHandler {
        private final CountDownLatch latch = new CountDownLatch(1);

        private SocketChannel channel;
        
        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            super.channelOpen(ctx, e);
            channel = (SocketChannel) ctx.getChannel();
            latch.countDown();
        }
        
        public SocketChannel await() throws InterruptedException {
            latch.await();
            return channel;
        }

    }
    
    private static final class ServerInputCloseTestHandler extends SimpleChannelUpstreamHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private Channel channel;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
            ctx.getChannel().write(ChannelBuffers.wrappedBuffer("TEST".getBytes())).addListener(new ChannelFutureListener() {
                
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    latch.countDown();
                    ServerInputCloseTestHandler.this.channel = future.getChannel();
                }
            });
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            latch.countDown();
            channel = ctx.getChannel();
            super.channelClosed(ctx, e);
        }

        public Channel await() throws InterruptedException {
            latch.await();
            return channel;
        }
    }

    private static class CloseTestHandler extends SimpleSocketChannelUpstreamHandler {

        private CountDownLatch inputClosedLatch = new CountDownLatch(1);
        private CountDownLatch outputClosedLatch = new CountDownLatch(1);
        private CountDownLatch messageReceivedLatch = new CountDownLatch(1);
        
        
        @Override
        public void channelOutputClosed(ChannelHandlerContext ctx, ChannelStateEvent evt) throws Exception {
            super.channelOutputClosed(ctx, evt);
            outputClosedLatch.countDown();
        }

        @Override
        public void channelInputClosed(ChannelHandlerContext ctx, ChannelStateEvent evt) throws Exception {
            super.channelInputClosed(ctx, evt);
            inputClosedLatch.countDown();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
            messageReceivedLatch.countDown();
            
        }
        
        public boolean isInputClosedFired() throws InterruptedException {
            return inputClosedLatch.await(3, TimeUnit.SECONDS);
        }
        
        public boolean isOutputClosedFired() throws InterruptedException {
            return outputClosedLatch.await(3, TimeUnit.SECONDS);
        }
        
        public boolean isMessageReceivedFired() throws InterruptedException {
            return messageReceivedLatch.await(3, TimeUnit.SECONDS);
        }
        
    }
}
