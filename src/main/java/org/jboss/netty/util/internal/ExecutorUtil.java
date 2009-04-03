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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
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
        Executor[] executorsCopy = new Executor[executors.length];
        for (int i = 0; i < executors.length; i ++) {
            if (executors[i] == null) {
                throw new NullPointerException("executors[" + i + "]");
            }
            executorsCopy[i] = executors[i];
        }

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
                    // Ignore.
                }
            }
        }
    }

    private ExecutorUtil() {
        super();
    }
}
