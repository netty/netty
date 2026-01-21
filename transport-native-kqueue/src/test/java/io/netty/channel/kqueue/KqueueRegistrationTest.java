/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.handler.logging.LoggingHandler;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KqueueRegistrationTest {

    @BeforeEach
    public void setUp() {
        KQueue.ensureAvailability();
    }

    @Test
    void testFdReuse() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory());
        Promise<Throwable> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        try {
            bootstrap.group(group)
                    .channel(KQueueSocketChannel.class)
                    .handler(new LoggingHandler());
            bootstrap.connect(new InetSocketAddress(NetUtil.LOCALHOST, SocketTestPermutation.UNASSIGNED_PORT))
                    .addListener((FutureListener<Channel>) f1 -> {
                        if (f1.cause() instanceof ConnectException) {
                            bootstrap.connect(new InetSocketAddress(NetUtil.LOCALHOST,
                                            SocketTestPermutation.UNASSIGNED_PORT))
                                    .addListener((FutureListener<Channel>) f2 -> {
                                        if (f2.isSuccess()) {
                                            f2.getNow().close();
                                        }
                                        promise.setSuccess(f2.cause());
                                    });
                        } else {
                            if (f1.isSuccess()) {
                                f1.getNow().close();
                            }
                            promise.setFailure(new IllegalStateException());
                        }
                    });
            Throwable cause = promise.get();
            assertInstanceOf(ConnectException.class, cause);
        } finally {
            group.shutdownGracefully();
        }
    }
}
