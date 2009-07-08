/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.SystemPropertyUtil;

/**
 * Provides information which is specific to a NIO service provider
 * implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class NioProviderMetadata {
    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioProviderMetadata.class);

    private static final String CONSTRAINT_LEVEL_PROPERTY =
        "java.nio.channels.spi.constraintLevel";

    private static final long AUTODETECTION_TIMEOUT = 7000L;

    /**
     * 0 - no need to wake up to get / set interestOps (most cases)
     * 1 - no need to wake up to get interestOps, but need to wake up to set.
     * 2 - need to wake up to get / set interestOps    (old providers)
     */
    static final int CONSTRAINT_LEVEL;

    static {
        int constraintLevel = -1;

        // Use the system property if possible.
        try {
            String value = SystemPropertyUtil.get(CONSTRAINT_LEVEL_PROPERTY, "-1");
            constraintLevel = Integer.parseInt(value);
            if (constraintLevel < 0 || constraintLevel > 2) {
                constraintLevel = -1;
            } else {
                logger.debug(
                        "Using the specified NIO constraint level: " +
                        constraintLevel);
            }
        } catch (Exception e) {
            // format error
        }

        if (constraintLevel < 0) {
            constraintLevel = detectConstraintLevelFromSystemProperties();

            if (constraintLevel < 0) {
                logger.debug(
                        "Couldn't get the NIO constraint level from the system properties.");
                ConstraintLevelAutodetector autodetector =
                    new ConstraintLevelAutodetector();

                try {
                    constraintLevel = autodetector.autodetectWithTimeout();
                } catch (Exception e) {
                    // Probably because of security manager - try again without
                    // creating a new thread directly.
                    constraintLevel = autodetector.autodetectWithoutTimeout();
                }
            }

            if (constraintLevel < 0) {
                constraintLevel = 2;
                logger.warn(
                        "Failed to autodetect the NIO constraint level; " +
                        "using the safest level (2)");
            } else if (constraintLevel != 0) {
                logger.info(
                        "Using the autodetected NIO constraint level: " +
                        constraintLevel +
                        " (Use better NIO provider for better performance)");
            } else {
                logger.debug(
                        "Using the autodetected NIO constraint level: " +
                        constraintLevel);
            }
        }

        CONSTRAINT_LEVEL = constraintLevel;

        if (CONSTRAINT_LEVEL < 0 || CONSTRAINT_LEVEL > 2) {
            throw new Error(
                    "Unexpected NIO constraint level: " +
                    CONSTRAINT_LEVEL + ", please report this error.");
        }
    }

    private static int detectConstraintLevelFromSystemProperties() {
        String version = SystemPropertyUtil.get("java.specification.version");
        String os = SystemPropertyUtil.get("os.name");
        String vendor = SystemPropertyUtil.get("java.vm.vendor");
        String provider;
        try {
            provider = SelectorProvider.provider().getClass().getName();
        } catch (Exception e) {
            // Perhaps security exception.
            provider = null;
        }

        if (version == null || os == null || vendor == null || provider == null) {
            return -1;
        }

        os = os.toLowerCase();
        vendor = vendor.toLowerCase();

        // Sun JVM
        if (vendor.indexOf("sun") >= 0) {
            // Linux
            if (os.indexOf("linux") >= 0) {
                if (provider.equals("sun.nio.ch.EPollSelectorProvider") ||
                    provider.equals("sun.nio.ch.PollSelectorProvider")) {
                    return 0;
                }

            // Windows
            } else if (os.indexOf("windows") >= 0) {
                if (provider.equals("sun.nio.ch.WindowsSelectorProvider")) {
                    return 0;
                }

            // Solaris
            } else if (os.indexOf("sun") >= 0 || os.indexOf("solaris") >= 0) {
                if (provider.equals("sun.nio.ch.DevPollSelectorProvider")) {
                    return 0;
                }
            }
        // Apple JVM
        } else if (vendor.indexOf("apple") >= 0) {
            // Mac OS
            if (os.indexOf("mac") >= 0 && os.indexOf("os") >= 0) {
                if (provider.equals("sun.nio.ch.KQueueSelectorProvider")) {
                    return 0;
                }
            }
        // IBM
        } else if (vendor.indexOf("ibm") >= 0) {
            // Linux
            if (os.indexOf("linux") >= 0) {
                if (version.equals("1.5") || version.matches("^1\\.5\\D.*$")) {
                    if (provider.equals("sun.nio.ch.PollSelectorProvider")) {
                        return 1;
                    }
                }

                // Commented out - the constraint level of IBM JDK 1.6 is
                //                 different between versions.
                //
                //else if (version.equals("1.6") || version.matches("^1\\.6\\D.*$")) {
                //    if (provider.equals("sun.nio.ch.EPollSelectorProvider") ||
                //        provider.equals("sun.nio.ch.PollSelectorProvider")) {
                //        return 2;
                //    }
                //}

            // AIX
            } if (os.indexOf("aix") >= 0) {
                if (version.equals("1.5") || version.matches("^1\\.5\\D.*$")) {
                    if (provider.equals("sun.nio.ch.PollSelectorProvider")) {
                        return 1;
                    }
                }

                // Commented out - the constraint level of IBM JDK 1.6 is
                //                 different between versions.
                //
                //else if (version.equals("1.6") || version.matches("^1\\.6\\D.*$")) {
                //    if (provider.equals("sun.nio.ch.EPollSelectorProvider") ||
                //        provider.equals("sun.nio.ch.PollSelectorProvider")) {
                //        return 2;
                //    }
                //}
            }
        // BEA
        } else if (vendor.indexOf("bea") >= 0 || vendor.indexOf("oracle") >= 0) {
            // Linux
            if (os.indexOf("linux") >= 0) {
                if (provider.equals("sun.nio.ch.EPollSelectorProvider") ||
                    provider.equals("sun.nio.ch.PollSelectorProvider")) {
                    return 0;
                }

            // Windows
            } else if (os.indexOf("windows") >= 0) {
                if (provider.equals("sun.nio.ch.WindowsSelectorProvider")) {
                    return 0;
                }
            }
        }

        // Others (untested)
        return -1;
    }


    private static final class ConstraintLevelAutodetector {

        ConstraintLevelAutodetector() {
            super();
        }

        int autodetectWithTimeout() {
            final BlockingQueue<Integer> resultQueue = new LinkedBlockingQueue<Integer>();
            Runnable detector = new ThreadRenamingRunnable(new Runnable() {
                public void run() {
                    int level = -1;
                    try {
                        level = autodetectWithoutTimeout();
                    } finally {
                        boolean finished = resultQueue.offer(Integer.valueOf(level));
                        assert finished;
                    }
                }
            }, "NIO constraint level detector");

            Thread detectorThread = new Thread(detector);
            try {
                detectorThread.setDaemon(true);
            } catch (Exception e) {
                logger.warn(
                        "Failed to set the daemon flag of the autodetector thread.", e);
            }
            detectorThread.start();

            for (;;) {
                try {
                    Integer result = resultQueue.poll(AUTODETECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (result == null) {
                        logger.warn("NIO constraint level autodetection timed out.");
                        return -1;
                    } else {
                        return result.intValue();
                    }
                } catch (InterruptedException e) {
                    // Ignored
                }
            }
        }

        int autodetectWithoutTimeout() {
            final int constraintLevel;
            ExecutorService executor = Executors.newCachedThreadPool();
            boolean success;
            long startTime;
            int interestOps;

            ServerSocketChannel ch = null;
            SelectorLoop loop = null;

            try {
                // Open a channel.
                ch = ServerSocketChannel.open();

                // Configure the channel
                try {
                    ch.socket().bind(new InetSocketAddress(0));
                    ch.configureBlocking(false);
                } catch (Throwable e) {
                    logger.warn("Failed to configure a temporary socket.", e);
                    return -1;
                }

                // Prepare the selector loop.
                try {
                    loop = new SelectorLoop();
                } catch (Throwable e) {
                    logger.warn("Failed to open a temporary selector.", e);
                    return -1;
                }

                // Register the channel
                try {
                    ch.register(loop.selector, 0);
                } catch (Throwable e) {
                    logger.warn("Failed to register a temporary selector.", e);
                    return -1;
                }

                SelectionKey key = ch.keyFor(loop.selector);

                // Start the selector loop.
                executor.execute(loop);

                // Level 0
                success = true;
                for (int i = 0; i < 10; i ++) {

                    // Increase the probability of calling interestOps
                    // while select() is running.
                    do {
                        while (!loop.selecting) {
                            Thread.yield();
                        }

                        // Wait a little bit more.
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    } while (!loop.selecting);

                    startTime = System.nanoTime();
                    key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT);
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);

                    if (System.nanoTime() - startTime >= 500000000L) {
                        success = false;
                        break;
                    }
                }

                if (success) {
                    constraintLevel = 0;
                } else {
                    // Level 1
                    success = true;
                    for (int i = 0; i < 10; i ++) {

                        // Increase the probability of calling interestOps
                        // while select() is running.
                        do {
                            while (!loop.selecting) {
                                Thread.yield();
                            }

                            // Wait a little bit more.
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        } while (!loop.selecting);

                        startTime = System.nanoTime();
                        interestOps = key.interestOps();
                        synchronized (loop) {
                            loop.selector.wakeup();
                            key.interestOps(interestOps | SelectionKey.OP_ACCEPT);
                            key.interestOps(interestOps & ~SelectionKey.OP_ACCEPT);
                        }

                        if (System.nanoTime() - startTime >= 500000000L) {
                            success = false;
                            break;
                        }
                    }
                    if (success) {
                        constraintLevel = 1;
                    } else {
                        constraintLevel = 2;
                    }
                }
            } catch (Throwable e) {
                return -1;
            } finally {
                if (ch != null) {
                    try {
                        ch.close();
                    } catch (Throwable e) {
                        logger.warn("Failed to close a temporary socket.", e);
                    }
                }

                if (loop != null) {
                    loop.done = true;
                    try {
                        executor.shutdownNow();
                    } catch (NullPointerException ex) {
                        // Some JDK throws NPE here, but shouldn't.
                    }

                    try {
                        for (;;) {
                            loop.selector.wakeup();
                            try {
                                if (executor.awaitTermination(1, TimeUnit.SECONDS)) {
                                    break;
                                }
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        }
                    } catch (Throwable e) {
                        // Perhaps security exception.
                    }

                    try {
                        loop.selector.close();
                    } catch (Throwable e) {
                        logger.warn("Failed to close a temporary selector.", e);
                    }
                }
            }

            return constraintLevel;
        }
    }

    private static final class SelectorLoop implements Runnable {
        final Selector selector;
        volatile boolean done;
        volatile boolean selecting; // Just an approximation

        SelectorLoop() throws IOException {
            selector = Selector.open();
        }

        public void run() {
            while (!done) {
                synchronized (this) {
                    // Guard
                }
                try {
                    selecting = true;
                    try {
                        selector.select(1000);
                    } finally {
                        selecting = false;
                    }

                    Set<SelectionKey> keys = selector.selectedKeys();
                    for (SelectionKey k: keys) {
                        k.interestOps(0);
                    }
                    keys.clear();
                } catch (IOException e) {
                    logger.warn("Failed to wait for a temporary selector.", e);
                }
            }
        }
    }
}
