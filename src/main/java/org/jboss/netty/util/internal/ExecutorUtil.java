/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shuts down a list of {@link Executor}s.  {@link #terminate(Executor...)} will
 * shut down all specified {@link ExecutorService}s immediately and wait for
 * their termination.  An {@link Executor} which is not an {@link ExecutorService}
 * will be ignored silently.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class ExecutorUtil {

    /**
     * Returns {@code true} if and only if the specified {@code executor}
     * is an {@link ExecutorService} and is shut down.  Please note that this
     * method returns {@code false} if the specified {@code executor} is not an
     * {@link ExecutorService}.
     */
    public static boolean isShutdown(Executor executor) {
        if (executor instanceof ExecutorService) {
            if (((ExecutorService) executor).isShutdown()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shuts down the specified executors.
     */
    public static void terminate(Executor... executors) {
        // Check nulls.
        if (executors == null) {
            throw new NullPointerException("executors");
        }

        Executor[] executorsCopy = new Executor[executors.length];
        for (int i = 0; i < executors.length; i ++) {
            if (executors[i] == null) {
                throw new NullPointerException("executors[" + i + "]");
            }
            executorsCopy[i] = executors[i];
        }

        // Check dead lock.
        final Executor currentParent = DeadLockProofWorker.PARENT.get();
        if (currentParent != null) {
            for (Executor e: executorsCopy) {
                if (e == currentParent) {
                    throw new IllegalStateException(
                            "An Executor cannot be shut down from the thread " +
                            "acquired from itself.  Please make sure you are " +
                            "not calling releaseExternalResources() from an " +
                            "I/O worker thread.");
                }
            }
        }

        // Shut down all executors.
        boolean interrupted = false;
        for (Executor e: executorsCopy) {
            if (!(e instanceof ExecutorService)) {
                continue;
            }

            ExecutorService es = (ExecutorService) e;
            for (;;) {
                try {
                    es.shutdownNow();
                } catch (SecurityException ex) {
                    // Running in a restricted environment - fall back.
                    try {
                        es.shutdown();
                    } catch (SecurityException ex2) {
                        // Running in a more restricted environment.
                        // Can't shut down this executor - skip to the next.
                        break;
                    } catch (NullPointerException ex2) {
                        // Some JDK throws NPE here, but shouldn't.
                    }
                } catch (NullPointerException ex) {
                    // Some JDK throws NPE here, but shouldn't.
                }

                try {
                    if (es.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorUtil() {
        super();
    }
}
