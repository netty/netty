/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocketRstTest extends AbstractSocketTest {
    protected void assertRstOnCloseException(IOException cause, Channel clientChannel) {
        if (Locale.getDefault() == Locale.US || Locale.getDefault() == Locale.UK) {
            assertTrue("actual message: " + cause.getMessage(),
                       cause.getMessage().contains("reset") || cause.getMessage().contains("closed"));
        }
    }

    @Test(timeout = 3000)
    public void testSoLingerZeroCausesOnlyRstOnClose() throws Throwable {
        run();
    }

    public void testSoLingerZeroCausesOnlyRstOnClose(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        // SO_LINGER=0 means that we must send ONLY a RST when closing (not a FIN + RST).
        sb.childOption(ChannelOption.SO_LINGER, 0);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverChannelRef.compareAndSet(null, ch);
                latch.countDown();
            }
        });
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        throwableRef.compareAndSet(null, cause);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        latch2.countDown();
                    }
                });
            }
        });
        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        // Wait for the server to get setup.
        latch.await();

        // The server has SO_LINGER=0 and so it must send a RST when close is called.
        serverChannelRef.get().close();

        // Wait for the client to get channelInactive.
        latch2.await();

        // Verify the client received a RST.
        Throwable cause = throwableRef.get();
        assertTrue("actual [type, message]: [" + cause.getClass() + ", " + cause.getMessage() + "]",
                cause instanceof IOException);

        assertRstOnCloseException((IOException) cause, cc);
    }

    @Test(timeout = 3000)
    public void testNoRstIfSoLingerOnClose() throws Throwable {
        run();
    }

    public void testNoRstIfSoLingerOnClose(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverChannelRef.compareAndSet(null, ch);
                latch.countDown();
            }
        });
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        throwableRef.compareAndSet(null, cause);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        latch2.countDown();
                    }
                });
            }
        });
        Channel sc = sb.bind().sync().channel();
        cb.connect(sc.localAddress()).syncUninterruptibly();

        // Wait for the server to get setup.
        latch.await();

        // The server has SO_LINGER=0 and so it must send a RST when close is called.
        serverChannelRef.get().close();

        // Wait for the client to get channelInactive.
        latch2.await();

        // Verify the client did not received a RST.
        assertNull(throwableRef.get());
    }
}
