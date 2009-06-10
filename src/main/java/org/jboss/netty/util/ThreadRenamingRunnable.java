/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.util;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 * Meta {@link Runnable} that changes the current thread name and reverts it
 * back when its execution ends.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ThreadRenamingRunnable implements Runnable {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ThreadRenamingRunnable.class);

    private static volatile ThreadNameDeterminer threadNameDeterminer =
        new ThreadNameDeterminer() {
            public String determineThreadName(
                    String oldThreadName, String newThreadName) throws Exception {
                return newThreadName;
            }
        };

    /**
     * Returns the {@link ThreadNameDeterminer} which is used to determine the
     * new thread name.
     */
    public static ThreadNameDeterminer getThreadNameDeterminer() {
        return threadNameDeterminer;
    }

    /**
     * Sets the {@link ThreadNameDeterminer} which is used to determine the new
     * thread name.
     */
    public static void setThreadNameDeterminer(ThreadNameDeterminer threadNameDeterminer) {
        if (threadNameDeterminer == null) {
            throw new NullPointerException("threadNameDeterminer");
        }
        ThreadRenamingRunnable.threadNameDeterminer = threadNameDeterminer;
    }

    private final Runnable runnable;
    private final String proposedThreadName;

    /**
     * Creates a new instance which wraps the specified {@code runnable}
     * and changes the thread name to the specified thread name when the
     * specified {@code runnable} is running.
     */
    public ThreadRenamingRunnable(Runnable runnable, String proposedThreadName) {
        if (runnable == null) {
            throw new NullPointerException("runnable");
        }
        if (proposedThreadName == null) {
            throw new NullPointerException("proposedThreadName");
        }
        this.runnable = runnable;
        this.proposedThreadName = proposedThreadName;
    }

    public void run() {
        final Thread currentThread = Thread.currentThread();
        final String oldThreadName = currentThread.getName();
        final String newThreadName = getNewThreadName(oldThreadName);

        // Change the thread name before starting the actual runnable.
        boolean renamed = false;
        if (!oldThreadName.equals(newThreadName)) {
            try {
                currentThread.setName(newThreadName);
                renamed = true;
            } catch (SecurityException e) {
                logger.debug(
                        "Failed to rename a thread " +
                        "due to security restriction.", e);
            }
        }

        // Run the actual runnable and revert the name back when it ends.
        try {
            runnable.run();
        } finally {
            if (renamed) {
                // Revert the name back if the current thread was renamed.
                // We do not check the exception here because we know it works.
                currentThread.setName(oldThreadName);
            }
        }
    }

    private String getNewThreadName(String oldThreadName) {
        String newThreadName = null;

        try {
            newThreadName =
                getThreadNameDeterminer().determineThreadName(
                        oldThreadName, proposedThreadName);
        } catch (Throwable t) {
            logger.warn("Failed to determine the thread name", t);
        }

        return newThreadName == null? oldThreadName : newThreadName;
    }
}
