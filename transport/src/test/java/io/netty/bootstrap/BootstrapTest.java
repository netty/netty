/*
 * Copyright 2013 The Netty Project
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

package io.netty.bootstrap;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BootstrapTest {

    @Test(timeout = 10000)
    public void testBindDeadLock() throws Exception {
        EventLoopGroup groupA = new LocalEventLoopGroup(1);
        EventLoopGroup groupB = new LocalEventLoopGroup(1);

        try {
            ChannelInboundHandler dummyHandler = new DummyHandler();

            final Bootstrap bootstrapA = new Bootstrap();
            bootstrapA.group(groupA);
            bootstrapA.channel(LocalChannel.class);
            bootstrapA.handler(dummyHandler);

            final Bootstrap bootstrapB = new Bootstrap();
            bootstrapB.group(groupB);
            bootstrapB.channel(LocalChannel.class);
            bootstrapB.handler(dummyHandler);

            List<Future<?>> bindFutures = new ArrayList<Future<?>>();

            // Try to bind from each other.
            for (int i = 0; i < 1024; i ++) {
                bindFutures.add(groupA.next().submit(new Runnable() {
                    @Override
                    public void run() {
                        bootstrapB.bind(LocalAddress.ANY);
                    }
                }));

                bindFutures.add(groupB.next().submit(new Runnable() {
                    @Override
                    public void run() {
                        bootstrapA.bind(LocalAddress.ANY);
                    }
                }));
            }

            for (Future<?> f: bindFutures) {
                f.sync();
            }
        } finally {
            groupA.shutdownGracefully();
            groupB.shutdownGracefully();
            groupA.terminationFuture().sync();
            groupB.terminationFuture().sync();
        }
    }

    @Test(timeout = 10000)
    public void testConnectDeadLock() throws Exception {
        EventLoopGroup groupA = new LocalEventLoopGroup(1);
        EventLoopGroup groupB = new LocalEventLoopGroup(1);

        try {
            ChannelInboundHandler dummyHandler = new DummyHandler();

            final Bootstrap bootstrapA = new Bootstrap();
            bootstrapA.group(groupA);
            bootstrapA.channel(LocalChannel.class);
            bootstrapA.handler(dummyHandler);

            final Bootstrap bootstrapB = new Bootstrap();
            bootstrapB.group(groupB);
            bootstrapB.channel(LocalChannel.class);
            bootstrapB.handler(dummyHandler);

            List<Future<?>> bindFutures = new ArrayList<Future<?>>();

            // Try to connect from each other.
            for (int i = 0; i < 1024; i ++) {
                bindFutures.add(groupA.next().submit(new Runnable() {
                    @Override
                    public void run() {
                        bootstrapB.connect(LocalAddress.ANY);
                    }
                }));

                bindFutures.add(groupB.next().submit(new Runnable() {
                    @Override
                    public void run() {
                        bootstrapA.connect(LocalAddress.ANY);
                    }
                }));
            }

            for (Future<?> f: bindFutures) {
                f.sync();
            }
        } finally {
            groupA.shutdownGracefully();
            groupB.shutdownGracefully();
            groupA.terminationFuture().sync();
            groupB.terminationFuture().sync();
        }
    }

    @Sharable
    private static final class DummyHandler extends ChannelInboundHandlerAdapter { }
}
