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

package io.netty.test.udt.util;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.StatusUDT;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Unit test helper.
 */
public final class UnitHelp {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(UnitHelp.class);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Verify class loading with class initialization.
     */
    public static boolean canLoadAndInitClass(String name) {
        try {
            Class.forName(name, true, UnitHelp.class.getClassLoader());
            log.info("Class load and init success.");
            return true;
        } catch (Throwable e) {
            log.warn("Class load or init failure.", e);
            return false;
        }
    }

    /**
     * Zero out buffer.
     */
    public static void clear(final IntBuffer buffer) {
        for (int index = 0; index < buffer.capacity(); index++) {
            buffer.put(index, 0);
        }
    }

    /**
     * Measure ping time to a host.
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

        process(command);

        final long timeFinish = System.currentTimeMillis();

        return timeFinish - timeStart;
    }

    /**
     * Invoke external process and wait for completion.
     */
    public static void process(final String command) throws Exception {
        final ProcessBuilder builder = new ProcessBuilder(SPACES.split(command));
        final Process process = builder.start();
        process.waitFor();
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
            log.error("Failed to find addess.");
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final Exception e) {
                    log.error("Failed to close socket.");
                }
            }
        }
    }

    /**
     * Find named address on local host.
     */
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
        throw new Exception("Failed to allocate address.");
    }

    /**
     * Allocate available local address / port or throw exception.
     */
    public static InetSocketAddress localSocketAddress() throws Exception {
        return hostedSocketAddress("localhost");
    }

    /**
     * Display contents of a buffer.
     */
    public static void logBuffer(final String title, final IntBuffer buffer) {
        for (int index = 0; index < buffer.capacity(); index++) {
            final int value = buffer.get(index);
            if (value == 0) {
                continue;
            }
            log.info(String.format("%s [id: 0x%08x]", title, value));
        }
    }

    /**
     * Display java.class.path
     */
    public static void logClassPath() {
        final String classPath = System.getProperty("java.class.path");
        final String[] entries = classPath.split(File.pathSeparator);
        final StringBuilder text = new StringBuilder(1024);
        for (final String item : entries) {
            text.append("\n\t");
            text.append(item);
        }
        log.info("\n\t[java.class.path]{}", text);
    }

    /**
     * Display java.library.path
     */
    public static void logLibraryPath() {
        final String classPath = System.getProperty("java.library.path");
        final String[] entries = classPath.split(File.pathSeparator);
        final StringBuilder text = new StringBuilder(1024);
        for (final String item : entries) {
            text.append("\n\t");
            text.append(item);
        }
        log.info("\n\t[java.library.path]{}", text);
    }

    /**
     * Display current OS/ARCH.
     */
    public static void logOsArch() {
        final StringBuilder text = new StringBuilder(1024)
            .append("\n\t")
            .append(System.getProperty("os.name"))
            .append("\n\t")
            .append(System.getProperty("os.arch"));
        log.info("\n\t[os/arch]{}", text);
    }

    /**
     * Display contents of a set.
     */
    public static void logSet(final Set<?> set) {
        @SuppressWarnings("unchecked")
        final TreeSet<?> treeSet = new TreeSet(set);
        for (final Object item : treeSet) {
            log.info("-> {}", item);
        }
    }

    public static String property(final String name) {
        final String value = System.getProperty(name);
        if (value == null) {
            log.error("property '{}' not defined; terminating", name);
            System.exit(1);
        }
        return value;
    }

    public static int[] randomIntArray(final int length, final int range) {
        final int[] array = new int[length];
        final Random generator = ThreadLocalRandom.current();
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.nextInt(range);
        }
        return array;
    }

    public static String randomString() {
        return String.valueOf(System.currentTimeMillis());
    }

    public static String randomSuffix(final String name) {
        return name + '-' + System.currentTimeMillis();
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

    public static Set<Integer> socketIndexSet(final IntBuffer buffer) {
        final Set<Integer> set = new HashSet<Integer>();
        while (buffer.hasRemaining()) {
            set.add(buffer.get());
        }
        return set;
    }

    public static boolean socketPresent(final SocketUDT socket,
            final IntBuffer buffer) {
        for (int index = 0; index < buffer.capacity(); index++) {
            if (buffer.get(index) == socket.id()) {
                return true;
            }
        }
        return false;
    }

    private UnitHelp() {
    }

}
