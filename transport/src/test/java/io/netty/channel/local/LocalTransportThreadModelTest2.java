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
package io.netty.channel.local;

import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.MessageBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class LocalTransportThreadModelTest2 {

    private static final String LOCAL_CHANNEL = LocalTransportThreadModelTest2.class.getName();

    static final int messageCountPerRun = 4;

    @Test(timeout = 15000)
    public void testSocketReuse() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        LocalHander serverHandler = new LocalHander("SERVER");
        serverBootstrap
                .group(new LocalEventLoopGroup(), new LocalEventLoopGroup())
                .channel(LocalServerChannel.class)
                .localAddress(new LocalAddress(LOCAL_CHANNEL))
                .childHandler(serverHandler);

        Bootstrap clientBootstrap = new Bootstrap();
        LocalHander clientHandler = new LocalHander("CLIENT");
        clientBootstrap
                .group(new LocalEventLoopGroup())
                .channel(LocalChannel.class)
                .remoteAddress(new LocalAddress(LOCAL_CHANNEL)).handler(clientHandler);

        serverBootstrap.bind().sync();

        int count = 100;
        for (int i = 1; i < count + 1; i ++) {
            Channel ch = clientBootstrap.connect().sync().channel();

            // SPIN until we get what we are looking for.
            int target = i * messageCountPerRun;
            while (serverHandler.count.get() != target || clientHandler.count.get() != target) {
                Thread.sleep(50);
            }
            close(ch, clientHandler);
        }

        assertEquals(count * 2 * messageCountPerRun, serverHandler.count.get() +
                clientHandler.count.get());
    }

    public void close(final Channel localChannel, final LocalHander localRegistrationHandler) {
        // we want to make sure we actually shutdown IN the event loop
        if (localChannel.eventLoop().inEventLoop()) {
            MessageBuf<Object> outboundMessageBuffer =
                    localChannel.pipeline().outboundMessageBuffer();
            if (!outboundMessageBuffer.isEmpty()) {
                System.err.println("NOT EMPTY TO SEND!");
            }

            // Wait until all messages are flushed before closing the channel.
            if (localRegistrationHandler.lastWriteFuture != null) {
                localRegistrationHandler.lastWriteFuture.awaitUninterruptibly();
            }

            MessageBuf<Object> inboundMessageBuffer =
                    localChannel.pipeline().inboundMessageBuffer();
            if (!inboundMessageBuffer.isEmpty()) {
                // sometimes we close the pipeline before everything on it has been notified/received.
                // we want these messages, since they are in our queue.
                Iterator<Object> iterator = inboundMessageBuffer.iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    System.err.println("DEFERRED on close: " + next);
                    iterator.remove();
                }
            }

            localChannel.close();
            return;
        } else {
            localChannel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    close(localChannel, localRegistrationHandler);
                }
            });
        }

        // Wait until the connection is closed or the connection attempt fails.
        localChannel.closeFuture().awaitUninterruptibly();

        MessageBuf<Object> inboundMessageBuffer = localChannel.pipeline().inboundMessageBuffer();
        if (!inboundMessageBuffer.isEmpty()) {
            // sometimes we close the pipeline before everything on it has been notified/received.
            // we want these messages, since they are in our queue.
            Iterator<Object> iterator = inboundMessageBuffer.iterator();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                System.err.println("DEFERRED on close: " + next);
                iterator.remove();
            }
        }
    }

    @Sharable
    static class LocalHander extends ChannelInboundMessageHandlerAdapter<Object> {
        private final String name;

        public volatile ChannelFuture lastWriteFuture;

        public AtomicInteger count = new AtomicInteger(0);

        public LocalHander(String name) {
            this.name = name;

        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            for (int i = 0; i < messageCountPerRun; i ++) {
                lastWriteFuture = ctx.channel().write(name + ' ' + i);
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            count.incrementAndGet();
        }
    }
}
