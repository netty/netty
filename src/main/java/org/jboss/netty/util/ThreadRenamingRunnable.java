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
package org.jboss.netty.util;


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

    private final Runnable runnable;
    private final String threadName;

    /**
     * Creates a new instance which wraps the specified {@code runnable}
     * and changes the thread name to the specified thread name when the
     * specified {@code runnable} is running.
     */
    public ThreadRenamingRunnable(Runnable runnable, String threadName) {
        if (runnable == null) {
            throw new NullPointerException("runnable");
        }
        if (threadName == null) {
            throw new NullPointerException("threadName");
        }
        this.runnable = runnable;
        this.threadName = threadName;
    }

    public void run() {
        final Thread currentThread = Thread.currentThread();
        final String oldThreadName = currentThread.getName();

        // Change the thread name before starting the actual runnable.
        boolean renamed = false;
        try {
            currentThread.setName(threadName);
            renamed = true;
        } catch (SecurityException e) {
            // Probably SecurityException.
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
}
