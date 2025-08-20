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
package io.netty.channel.nio;

import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioIoHandlerTest {

    @Test
    void testNioHandlerCanBeDrivenByMainThread() throws Exception {
        IoHandlerFactory factory = NioIoHandler.newFactory();
        final Thread current = Thread.currentThread();
        final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        // Create our own IoExecutor that is driven by the main thread
        final ThreadAwareExecutor executor = new ThreadAwareExecutor() {
            @Override
            public boolean isExecutorThread(Thread thread) {
                return current == thread;
            }

            @Override
            public void execute(Runnable command) {
                tasks.add(command);
            }
        };
        IoHandlerContext context = new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                // Just busy spin.
                return false;
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                return 0;
            }

            @Override
            public long deadlineNanos() {
                return -1;
            }
        };
        IoHandler handler = factory.newHandler(executor);

        // Open a ServerSocketChannel that we can connect to.
        ServerSocketChannel channel = SelectorProvider.provider().openServerSocketChannel();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(0));
        // Do the connect in another thread so we don't block our io execution loop.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = new Socket();
                try {
                    socket.connect(channel.getLocalAddress());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        AtomicBoolean acceptedConnection = new AtomicBoolean();
        IoRegistration registration = handler.register(new NioSelectableChannelIoHandle<ServerSocketChannel>(channel) {
            @Override
            protected void handle(ServerSocketChannel channel, SelectionKey key) {
                if (key.isAcceptable()) {
                    try {
                        // Just accept the connection and close it.
                        java.nio.channels.SocketChannel accepted = channel.accept();
                        accepted.close();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        acceptedConnection.set(true);
                    }
                }
            }
        });
        registration.submit(NioIoOps.ACCEPT);
        t.start();

        // Let's loop until our registration was cancelled.
        while (registration.isValid()) {
            handler.run(context);
            for (;;) {
                // Execute all tasks that were dispatched.
                Runnable r = tasks.poll();
                if (r == null) {
                    break;
                }
                r.run();
            }
            if (acceptedConnection.get()) {
                // We accepted the connection, let's cancel the registration.
                assertTrue(registration.cancel());
            }
        }
        // Wait until the connect thread is done.
        t.join();

        // Let's now close the ServerSocketChannel and after that destroy the IoHandler.
        channel.close();
        handler.prepareToDestroy();
        handler.destroy();
    }
}
