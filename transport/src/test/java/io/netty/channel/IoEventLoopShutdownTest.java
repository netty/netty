/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel;

import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandle;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IoEventLoopShutdownTest {
    @Test
    @Timeout(10)
    public void testCloseCallbacksRegisterHandlesWithoutTasks() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        IoEventLoop loop = group.next();
        SocketChannel[] sockets = new SocketChannel[8];
        NioIoHandle[] handles = new NioIoHandle[sockets.length];
        Future<?>[] registrations = new Future<?>[sockets.length];
        boolean[] closed = new boolean[sockets.length];
        try {
            for (int i = 0; i < sockets.length; ++i) {
                final int index = i;
                sockets[i] = SocketChannel.open();
                sockets[i].configureBlocking(false);
                handles[i] = new NioIoHandle() {
                    @Override
                    public SelectableChannel selectableChannel() {
                        return sockets[index];
                    }

                    @Override
                    public void handle(IoRegistration registration, IoEvent event) {
                        // These sockets have no pending I/O.
                    }

                    @Override
                    public void close() throws Exception {
                        closed[index] = true;
                        sockets[index].close();
                        if (index + 1 < handles.length) {
                            // Direct registration on the loop thread queues no Runnable cleanup.
                            registrations[index + 1] = loop.register(handles[index + 1]);
                        }
                    }
                };
            }
            registrations[0] = loop.register(handles[0]).sync();
            loop.submit(() -> group.shutdownGracefully(0, 5, TimeUnit.SECONDS)).sync();
            group.terminationFuture().sync();

            for (int i = 0; i < sockets.length; ++i) {
                assertNotNull(registrations[i], "Handle " + i + " must be registered by the previous close callback");
                assertTrue(registrations[i].isSuccess(), "Handle " + i + " registration must succeed");
                assertTrue(closed[i], "Accepted handle " + i + " must receive close before termination");
                assertFalse(sockets[i].isOpen(), "Accepted socket " + i + " must close before termination");
            }
        } finally {
            for (SocketChannel socket : sockets) {
                if (socket != null) {
                    socket.close();
                }
            }
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 8 })
    @Timeout(10)
    public void testLocalCloseListenerRegistersChannels(int replacements) throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        Channel first = new LocalChannel();
        Channel[] channels = new Channel[replacements];
        ChannelFuture[] registrations = new ChannelFuture[replacements];
        try {
            for (int i = 0; i < replacements; ++i) {
                channels[i] = new LocalChannel();
            }
            group.register(first).sync();
            first.closeFuture().addListener(future -> {
                for (int i = 0; i < replacements; ++i) {
                    registrations[i] = group.register(channels[i]);
                }
            });
            first.eventLoop().submit(() -> group.shutdownGracefully(0, 5, TimeUnit.SECONDS)).sync();
            group.terminationFuture().sync();

            assertFalse(first.isOpen());
            assertTrue(first.closeFuture().isDone());
            assertFalse(first.isRegistered());
            for (int i = 0; i < replacements; ++i) {
                assertNotNull(registrations[i]);
                assertTrue(registrations[i].isSuccess());
                assertFalse(channels[i].isOpen(), "Accepted Local channel " + i + " must close before termination");
                assertTrue(channels[i].closeFuture().isDone());
                assertFalse(channels[i].isRegistered());
            }
        } finally {
            first.close().awaitUninterruptibly();
            for (Channel channel : channels) {
                if (channel != null) {
                    channel.close().awaitUninterruptibly();
                }
            }
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    public void testRegistrationAfterShutdownOnLoopThreadIsRejected() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        IoEventLoop loop = group.next();
        SocketChannel socket = SocketChannel.open();
        AtomicReference<Future<IoRegistration>> registration = new AtomicReference<>();
        try {
            socket.configureBlocking(false);
            NioIoHandle handle = new NioIoHandle() {
                @Override
                public SelectableChannel selectableChannel() {
                    return socket;
                }

                @Override
                public void handle(IoRegistration registration, IoEvent event) {
                    // This socket has no pending I/O.
                }

                @Override
                public void close() throws Exception {
                    socket.close();
                }
            };
            loop.submit(() -> {
                loop.shutdown();
                registration.set(loop.register(handle));
            }).sync();
            group.terminationFuture().sync();

            assertNotNull(registration.get());
            assertFalse(registration.get().isSuccess());
            assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, registration.get().cause());
            assertEquals("event executor terminated", registration.get().cause().getMessage());
            assertTrue(socket.isOpen(), "A rejected handle remains owned by its caller");
        } finally {
            socket.close();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }
}
