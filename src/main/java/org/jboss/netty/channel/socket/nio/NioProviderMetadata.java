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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

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
            String value = System.getProperty(CONSTRAINT_LEVEL_PROPERTY);
            constraintLevel = Integer.parseInt(value);
            if (constraintLevel < 0 || constraintLevel > 2) {
                constraintLevel = -1;
            } else {
                logger.debug(
                        "Using the specified NIO constraint level: " +
                        constraintLevel);
            }
        } catch (Exception e) {
            // format error or security issue
        }

        if (constraintLevel < 0) {
            constraintLevel = detectConstraintLevel();
            if (constraintLevel < 0) {
                constraintLevel = 2;
                logger.warn(
                        "Failed to autodetect the NIO constraint level; " +
                        "using the safest level (2)");
            } else if (constraintLevel != 0) {
                logger.warn(
                        "Using the autodected NIO constraint level: " +
                        constraintLevel +
                        " (Use better NIO provider for better performance)");
            } else {
                logger.debug(
                        "Using the autodected NIO constraint level: " +
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

    private static int detectConstraintLevel() {
        // TODO Code cleanup - what a mess.
        final int constraintLevel;
        ExecutorService executor = Executors.newCachedThreadPool();
        boolean success;
        long startTime;
        int interestOps;

        SocketChannel ch = null;
        SelectorLoop loop = null;

        try {
            // Open a channel.
            ch = SocketChannel.open();

            // Configure the channel
            try {
                ch.configureBlocking(false);
            } catch (IOException e) {
                logger.warn("Failed to configure a temporary socket.", e);
                return -1;
            }

            // Prepare the selector loop.
            try {
                loop = new SelectorLoop();
            } catch (IOException e) {
                logger.warn("Failed to open a temporary selector.", e);
                return -1;
            }

            // Register the channel
            try {
                ch.register(loop.selector, 0);
            } catch (ClosedChannelException e) {
                logger.warn("Failed to register a temporary selector.", e);
                return -1;
            }

            SelectionKey key = ch.keyFor(loop.selector);

            // Start the selector loop.
            executor.execute(loop);

            // Level 0
            // TODO Make it run faster
            success = true;
            for (int i = 0; i < 10; i ++) {
                // Increase the probability of calling interestOps
                // while select() is running.
                while (!loop.selecting) {
                    Thread.yield();
                }

                // Wait a little bit more.
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore
                }

                startTime = System.currentTimeMillis();
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

                if (System.currentTimeMillis() - startTime >= 500) {
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
                    while (!loop.selecting) {
                        Thread.yield();
                    }

                    // Wait a little bit more.
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    startTime = System.currentTimeMillis();
                    interestOps = key.interestOps();
                    synchronized (loop) {
                        loop.selector.wakeup();
                        key.interestOps(interestOps | SelectionKey.OP_READ);
                        key.interestOps(interestOps & ~SelectionKey.OP_READ);
                    }

                    if (System.currentTimeMillis() - startTime >= 500) {
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
        } catch (IOException e) {
            return -1;
        } finally {
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a temporary socket.", e);
                }
            }

            if (loop != null) {
                loop.done = true;
                loop.selector.wakeup();
                try {
                    executor.shutdown();
                    for (;;) {
                        try {
                            if (executor.awaitTermination(1, TimeUnit.SECONDS)) {
                                break;
                            }
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                } catch (Exception e) {
                    // Perhaps security exception.
                }

                try {
                    loop.selector.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a temporary selector.", e);
                }
            }
        }

        return constraintLevel;
    }

    private static class SelectorLoop implements Runnable {
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
                        System.out.println(k.readyOps());
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
