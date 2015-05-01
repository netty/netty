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
package io.netty.testsuite.util;

import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.rules.TestName;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.Channel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TestUtils {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TestUtils.class);

    private static final int START_PORT = 32768;
    private static final int END_PORT = 65536;
    private static final int NUM_CANDIDATES = END_PORT - START_PORT;

    private static final List<Integer> PORTS = new ArrayList<Integer>();
    private static Iterator<Integer> portIterator;

    private static final Method hotspotMXBeanDumpHeap;
    private static final Object hotspotMXBean;

    private static final long DUMP_PROGRESS_LOGGING_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    static {
        // Populate the list of random ports.
        for (int i = START_PORT; i < END_PORT; i ++) {
            PORTS.add(i);
        }
        Collections.shuffle(PORTS);

        // Retrieve the hotspot MXBean and its class if available.
        Object mxBean;
        Method mxBeanDumpHeap;
        try {
            Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            mxBean = ManagementFactory.newPlatformMXBeanProxy(
                    server, "com.sun.management:type=HotSpotDiagnostic", clazz);
            mxBeanDumpHeap = clazz.getMethod("dumpHeap", String.class, boolean.class);
        } catch (Exception ignored) {
            mxBean = null;
            mxBeanDumpHeap = null;
        }

        hotspotMXBean = mxBean;
        hotspotMXBeanDumpHeap = mxBeanDumpHeap;
    }

    /**
     * Return a free port which can be used to bind to
     *
     * @return port
     */
    public static int getFreePort() {
        for (int i = 0; i < NUM_CANDIDATES; i ++) {
            final int port = nextCandidatePort();
            final InetSocketAddress wildcardAddr = new InetSocketAddress(port);
            final InetSocketAddress loopbackAddr = new InetSocketAddress(NetUtil.LOCALHOST4, port);

            // Ensure it is possible to bind on wildcard/loopback and tcp/udp.
            if (isTcpPortAvailable(wildcardAddr) &&
                isTcpPortAvailable(loopbackAddr) &&
                isUdpPortAvailable(wildcardAddr) &&
                isUdpPortAvailable(loopbackAddr)) {
                return port;
            }
        }

        throw new RuntimeException("unable to find a free port");
    }

    private static int nextCandidatePort() {
        if (portIterator == null || !portIterator.hasNext()) {
            portIterator = PORTS.iterator();
        }
        return portIterator.next();
    }

    private static boolean isTcpPortAvailable(InetSocketAddress localAddress) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(false);
            ss.bind(localAddress);
            ss.close();
            ss = null;
            return true;
        } catch (Exception ignore) {
            // Unavailable
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ignore) {
                    // Ignore
                }
            }
        }

        return false;
    }

    private static boolean isUdpPortAvailable(InetSocketAddress localAddress) {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(false);
            ds.bind(localAddress);
            ds.close();
            ds = null;
            return true;
        } catch (Exception ignore) {
            // Unavailable
        } finally {
            if (ds != null) {
                ds.close();
            }
        }

        return false;
    }

    /**
     * Return {@code true} if SCTP is supported by the running os.
     *
     */
    public static boolean isSctpSupported() {
        String os = System.getProperty("os.name").toLowerCase(Locale.UK);
        if ("unix".equals(os) || "linux".equals(os) || "sun".equals(os) || "solaris".equals(os)) {
            try {
                // Try to open a SCTP Channel, by using reflection to make it compile also on
                // operation systems that not support SCTP like OSX and Windows
                Class<?> sctpChannelClass = Class.forName("com.sun.nio.sctp.SctpChannel");
                Channel channel = (Channel) sctpChannelClass.getMethod("open").invoke(null);
                try {
                    channel.close();
                } catch (IOException e) {
                    // ignore
                }
            } catch (UnsupportedOperationException e) {
                // This exception may get thrown if the OS does not have
                // the shared libs installed.
                System.out.print("Not supported: " + e.getMessage());
                return false;
            } catch (Throwable t) {
                if (!(t instanceof IOException)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the method name of the current test.
     */
    public static String testMethodName(TestName testName) {
        String testMethodName = testName.getMethodName();
        if (testMethodName.contains("[")) {
            testMethodName = testMethodName.substring(0, testMethodName.indexOf('['));
        }
        return testMethodName;
    }

    public static void dump(String filenamePrefix) throws IOException {

        if (filenamePrefix == null) {
            throw new NullPointerException("filenamePrefix");
        }

        final String timestamp = timestamp();
        final File heapDumpFile = new File(filenamePrefix + '.' + timestamp + ".hprof");
        if (heapDumpFile.exists()) {
            if (!heapDumpFile.delete()) {
                throw new IOException("Failed to remove the old heap dump: " + heapDumpFile);
            }
        }

        final File threadDumpFile = new File(filenamePrefix + '.' + timestamp + ".threads");
        if (threadDumpFile.exists()) {
            if (!threadDumpFile.delete()) {
                throw new IOException("Failed to remove the old thread dump: " + threadDumpFile);
            }
        }

        dumpHeap(heapDumpFile);
        dumpThreads(threadDumpFile);
    }

    public static void compressHeapDumps() throws IOException {
        final File[] files = new File(System.getProperty("user.dir")).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".hprof");
            }
        });

        final byte[] buf = new byte[65536];
        final LZMA2Options options = new LZMA2Options(LZMA2Options.PRESET_DEFAULT);

        for (File file: files) {
            final String filename = file.toString();
            final String xzFilename = filename + ".xz";
            final long fileLength = file.length();

            logger.info("Compressing the heap dump: {}", xzFilename);

            long lastLogTime = System.nanoTime();
            long counter = 0;

            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(filename);
                out = new XZOutputStream(new FileOutputStream(xzFilename), options);
                for (;;) {
                    int readBytes = in.read(buf);
                    if (readBytes < 0) {
                        break;
                    }
                    if (readBytes == 0) {
                        continue;
                    }

                    out.write(buf, 0, readBytes);
                    counter += readBytes;

                    long currentTime = System.nanoTime();
                    if (currentTime - lastLogTime > DUMP_PROGRESS_LOGGING_INTERVAL) {
                        logger.info("Compressing the heap dump: {} ({}%)",
                                    xzFilename, counter * 100 / fileLength);
                        lastLogTime = currentTime;
                    }
                }
                out.close();
                in.close();
            } catch (Throwable t) {
                logger.warn("Failed to compress the heap dump: {}", xzFilename, t);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // Ignore.
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                        // Ignore.
                    }
                }
            }

            // Delete the uncompressed dump in favor of the compressed one.
            if (!file.delete()) {
                logger.warn("Failed to delete the uncompressed heap dump: {}", filename);
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("HHmmss.SSS").format(new Date());
    }

    private static void dumpHeap(File file) {
        if (hotspotMXBean == null) {
            logger.warn("Can't dump heap: HotSpotDiagnosticMXBean unavailable");
            return;
        }

        final String filename = file.toString();
        logger.info("Dumping heap: {}", filename);
        try {
            hotspotMXBeanDumpHeap.invoke(hotspotMXBean, filename, true);
        } catch (Exception e) {
            logger.warn("Failed to dump heap: {}", filename, e);
        }
    }

    private static void dumpThreads(File file) {
        final String filename = file.toString();
        OutputStream out = null;
        try {
            logger.info("Dumping threads: {}", filename);
            final StringBuilder buf = new StringBuilder(8192);
            try {
                for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                    buf.append(info);
                }
                buf.append('\n');
            } catch (UnsupportedOperationException ignored) {
                logger.warn("Can't dump threads: ThreadMXBean.dumpAllThreads() unsupported");
                return;
            }

            out = new FileOutputStream(file);
            out.write(buf.toString().getBytes(CharsetUtil.UTF_8));
        } catch (Exception e) {
            logger.warn("Failed to dump threads: {}", filename, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Ignore.
                }
            }
        }
    }

    private TestUtils() { }
}
