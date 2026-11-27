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
package io.netty.handler.codec.quic;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.fail;

/**
 * Relays UDP between one client and the server, and drops what the server sends while {@link #dropToClient}.
 */
final class DroppingRelay implements AutoCloseable {
    private DatagramSocket socket;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private InetSocketAddress server;
    private InetSocketAddress client;
    volatile boolean dropToClient;
    private volatile boolean closed;
    private Thread thread;

    InetSocketAddress address() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    void start(InetSocketAddress server) throws Exception {
        socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        socket.setSoTimeout(100);
        this.server = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getPort());
        thread = new Thread(this::run, "dropping-relay");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        byte[] buf = new byte[65536];
        while (!closed) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                InetSocketAddress from = (InetSocketAddress) packet.getSocketAddress();
                if (from.equals(server)) {
                    InetSocketAddress to = client;
                    if (!dropToClient && to != null) {
                        socket.send(new DatagramPacket(packet.getData(), packet.getOffset(),
                                packet.getLength(), to));
                    }
                } else {
                    client = from;
                    socket.send(new DatagramPacket(packet.getData(), packet.getOffset(),
                            packet.getLength(), server));
                }
            } catch (SocketTimeoutException ignore) {
                // check closed
            } catch (Exception e) {
                if (!closed) {
                    error.compareAndSet(null, e);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        closed = true;
        if (thread != null) {
            thread.join(1000);
        }
        if (socket != null) {
            socket.close();
        }
        Throwable cause = error.get();
        if (cause != null) {
            fail("The relay failed", cause);
        }
    }
}
