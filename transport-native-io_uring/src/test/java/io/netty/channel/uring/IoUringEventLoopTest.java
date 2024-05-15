/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.testsuite.transport.AbstractSingleThreadEventLoopTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringEventLoopTest extends AbstractSingleThreadEventLoopTest {

    public static final Runnable EMPTY_RUNNABLE = () -> {
    };

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    protected IoHandlerFactory newIoHandlerFactory() {
        return IoUringIoHandler.newFactory();
    }

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
    }

    @Override
    protected Channel newChannel() {
        return new IoUringServerSocketChannel();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return IoUringServerSocketChannel.class;
    }

    @Test
    public void testSubmitMultipleTasksAndEnsureTheseAreExecuted() throws Exception {
        EventLoopGroup group =  new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        try {
            EventLoop loop = group.next();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    @RepeatedTest(100)
    public void shutdownNotSoGracefully() throws Exception {
        EventLoopGroup group =  new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        CountDownLatch latch = new CountDownLatch(1);
        group.submit(() -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
        assertTrue(group.shutdownGracefully(0L, 0L, TimeUnit.NANOSECONDS)
                .await(1500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shutsDownGracefully() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        assertTrue(group.shutdownGracefully(1L, 1L, TimeUnit.MILLISECONDS)
                .await(1500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSchedule() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            EventLoop loop = group.next();
            loop.schedule(EMPTY_RUNNABLE, 1, TimeUnit.SECONDS).sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
