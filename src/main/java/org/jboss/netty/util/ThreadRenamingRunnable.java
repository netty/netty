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
package org.jboss.netty.util;

import java.util.regex.Pattern;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 * A {@link Runnable} that changes the current thread name and reverts it back
 * when its execution ends.  To change the default thread names set by Netty,
 * use {@link #setThreadNameDeterminer(ThreadNameDeterminer)}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.util.ThreadNameDeterminer oneway - -
 *
 */
public class ThreadRenamingRunnable implements Runnable {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ThreadRenamingRunnable.class);

    private static final Pattern SERVICE_PATTERN = Pattern.compile("[a-zA-Z0-9]*");
    private static final Pattern CATEGORY_PATTERN = SERVICE_PATTERN;
    private static final Pattern ID_PATTERN = Pattern.compile("^[-_a-zA-Z0-9]*$");


    private static volatile ThreadNameDeterminer threadNameDeterminer =
        ThreadNameDeterminer.PROPOSED;

    /**
     * Returns the {@link ThreadNameDeterminer} which overrides the proposed
     * new thread name.
     */
    public static ThreadNameDeterminer getThreadNameDeterminer() {
        return threadNameDeterminer;
    }

    /**
     * Sets the {@link ThreadNameDeterminer} which overrides the proposed new
     * thread name.  Please note that the specified {@link ThreadNameDeterminer}
     * affects only new {@link ThreadRenamingRunnable}s; the existing instances
     * are not affected at all.  Therefore, you should make sure to call this
     * method at the earliest possible point (i.e. before any Netty worker
     * thread starts) for consistent thread naming.  Otherwise, you might see
     * the default thread names and the new names appear at the same time in
     * the full thread dump.
     */
    public static void setThreadNameDeterminer(ThreadNameDeterminer threadNameDeterminer) {
        if (threadNameDeterminer == null) {
            throw new NullPointerException("threadNameDeterminer");
        }
        ThreadRenamingRunnable.threadNameDeterminer = threadNameDeterminer;
    }

    /**
     * Renames the specified thread.
     *
     * @return {@code true} if and only if the thread was renamed
     */
    public static boolean renameThread(Thread thread, String service, String category, String id, String comment) {
        if (thread == null) {
            throw new NullPointerException("thread");
        }

        validateNameComponents(service, category, id);

        // Normalize the parameters.
        service = service != null? service : "";
        category = category != null? category : "";
        id = id != null? id : "";
        comment = comment != null? comment : "";

        // Get the old & new thread names.
        String oldThreadName = thread.getName();
        String newThreadName = null;
        try {
            newThreadName = getThreadNameDeterminer().determineThreadName(
                    oldThreadName, service, category, id, comment);
        } catch (Throwable t) {
            logger.warn("Failed to determine the thread name", t);
        }
        if (newThreadName == null || newThreadName.length() == 0) {
            newThreadName = oldThreadName;
        }

        // Change the thread name.
        boolean renamed = false;
        if (!oldThreadName.equals(newThreadName)) {
            try {
                //System.out.println(newThreadName);
                thread.setName(newThreadName);
                renamed = true;
            } catch (SecurityException e) {
                logger.debug(
                        "Failed to rename a thread " +
                        "due to security restriction.", e);
            }
        }

        return renamed;
    }

    private static void validateNameComponents(String service, String category, String id) {
        if (service != null && !SERVICE_PATTERN.matcher(service).matches()) {
            throw new IllegalArgumentException(
                    "service: " + service +
                    " (expected: " + SERVICE_PATTERN.pattern() + ')');
        }

        if (category != null && !CATEGORY_PATTERN.matcher(category).matches()) {
            throw new IllegalArgumentException(
                    "category: " + category +
                    " (expected: " + CATEGORY_PATTERN.pattern() + ')');
        }

        if (id != null && !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "id: " + id +
                    " (expected: " + ID_PATTERN.pattern() + ')');
        }
    }

    private final Runnable runnable;
    private final String service;
    private final String category;
    private final String id;
    private final String comment;

    /**
     * Creates a new instance which wraps the specified {@code runnable}
     * and changes the thread name to the specified thread name when the
     * specified {@code runnable} is running.
     */
    public ThreadRenamingRunnable(
            Runnable runnable,
            String service, String category, String id, String comment) {
        if (runnable == null) {
            throw new NullPointerException("runnable");
        }

        validateNameComponents(service, category, id);
        this.runnable = runnable;
        this.service = service;
        this.category = category;
        this.id = id;
        this.comment = comment;
    }

    public void run() {
        final Thread currentThread = Thread.currentThread();
        final String oldThreadName = currentThread.getName();

        // Change the thread name before starting the actual runnable.
        final boolean renamed = renameThread(
                Thread.currentThread(), service, category, id, comment);

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
}
