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

/**
 * Bench Utils.
 */
package io.netty.bench.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.StatusUDT;

public final class NetworkUtil {

    private NetworkUtil() {
    }

    public static List<String> list(final String values) {
        return Arrays.asList(values.split(","));
    }

    /**
     * measure ping time to host
     */
    public static long ping(final String host) throws Exception {
        final String name = System.getProperty("os.name").toLowerCase();

        final String command;
        if (name.contains("linux")) {
            command = "ping -c 1 " + host;
        } else if (name.contains("mac os x")) {
            command = "ping -c 1 " + host;
        } else if (name.contains("windows")) {
            command = "ping -n 1 " + host;
        } else {
            throw new Exception("unknown platform");
        }

        final long timeStart = System.currentTimeMillis();

        NetworkUtil.process(command);

        final long timeFinish = System.currentTimeMillis();

        final long timeDiff = timeFinish - timeStart;

        NetworkUtil.log("ping time = " + timeDiff);

        return timeDiff;
    }

    public static void process(final String command) throws Exception {
        final ProcessBuilder builder = new ProcessBuilder(command.split("\\s"));
        final Process process = builder.start();
        process.waitFor();
    }

    public static void socketAwait(final SocketChannel socket) throws Exception {
        while (true) {
            if (socket.isConnected()) {
                return;
            } else {
                Thread.sleep(50);
            }
        }
    }

    /**
     * Block till socket reaches given state.
     */
    public static void socketAwait(final SocketUDT socket,
            final StatusUDT... statusArray) throws Exception {
        while (true) {
            for (final StatusUDT status : statusArray) {
                if (socket.status() == status) {
                    return;
                } else {
                    Thread.sleep(50);
                }
            }
        }
    }

    /**
     * @return newly allocated address or null for failure
     */
    public static synchronized InetSocketAddress findLocalAddress(
            final String host) {
        ServerSocket socket = null;
        try {
            final InetAddress address = InetAddress.getByName(host);
            socket = new ServerSocket(0, 3, address);
            return (InetSocketAddress) socket.getLocalSocketAddress();
        } catch (final Exception e) {
            log("failed to find addess");
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final Exception e) {
                    log("failed to close socket");
                }
            }
        }
    }

    public static InetSocketAddress hostedSocketAddress(final String host)
            throws Exception {
        for (int k = 0; k < 10; k++) {
            final InetSocketAddress address = findLocalAddress(host);
            if (address == null) {
                Thread.sleep(500);
                continue;
            }
            return address;
        }
        throw new Exception("failed to allocate address");
    }

    /**
     * allocate available local address / port or throw exception
     */
    public static InetSocketAddress localSocketAddress() throws Exception {
        return hostedSocketAddress("localhost");
    }

    public static void fail(final String message) throws RuntimeException {
        throw new RuntimeException(message);
    }

    public static void log(final String message) {
        System.out.println(message);
    }

    public static void err(final String message) {
        System.err.println(message);
    }

    public static class ClassTrace extends SecurityManager {

        public Class<?> getClassAt(final int index) {
            return getClassContext()[index];
        }

        @Override
        public Class<?>[] getClassContext() {
            return super.getClassContext();
        }

        public String getNameAt(final int index) {
            return getClassContext()[index].getName();
        }

        public void log(final int depth) {
            final Class<?>[] array = getClassContext();
            final int size = Math.min(array.length, depth);
            for (int k = 0; k < size; k++) {
                NetworkUtil.log("klaz " + k + " " + array[k].getName());
            }
        }
    }

}
