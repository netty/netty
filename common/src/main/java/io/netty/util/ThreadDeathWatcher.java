/*
 * Copyright 2014 The Netty Project
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

package io.netty.util;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.MpscLinkedQueue;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadFactory;

public final class ThreadDeathWatcher {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ThreadDeathWatcher.class);
    private static final ThreadFactory threadFactory =
            new DefaultThreadFactory(ThreadDeathWatcher.class, true, Thread.MIN_PRIORITY);

    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;

    private static final Queue<Entry> pendingEntries = PlatformDependent.newMpscQueue();
    private static final Watcher watcher = new Watcher();

    private static final Object stateLock = new Object();
    private static int state = ST_NOT_STARTED;

    public static void watch(Thread thread, Runnable task) {
        if (thread == null) {
            throw new NullPointerException("thread");
        }
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (!thread.isAlive()) {
            throw new IllegalArgumentException("thread must be alive.");
        }

        pendingEntries.add(new Entry(thread, task));

        synchronized (stateLock) {
            if (state == ST_NOT_STARTED) {
                state = ST_STARTED;
                Thread watcherThread = threadFactory.newThread(watcher);
                watcherThread.start();
            }
        }
    }

    private ThreadDeathWatcher() { }

    private static final class Watcher implements Runnable {

        private final List<Entry> watchees = new ArrayList<Entry>();

        @Override
        public void run() {
            for (;;) {
                fetchWatchees();
                notifyWatchees();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    // Ignore the interrupt; do not terminate until all tasks are run.
                }

                if (watchees.isEmpty() && pendingEntries.isEmpty()) {
                    synchronized (stateLock) {
                        // Terminate if there is no task in the queue (except the purge task).
                        if (pendingEntries.isEmpty()) {
                            state = ST_NOT_STARTED;
                            break;
                        }
                    }
                }
            }
        }

        private void fetchWatchees() {
            for (;;) {
                Entry e = pendingEntries.poll();
                if (e == null) {
                    break;
                }

                watchees.add(e);
            }
        }

        private void notifyWatchees() {
            for (Iterator<Entry> i = watchees.iterator(); i.hasNext();) {
                Entry e = i.next();
                if (!e.thread.isAlive()) {
                    i.remove();
                    try {
                        e.task.run();
                    } catch (Throwable t) {
                        logger.warn("Thread death watcher task raised an exception:", t);
                    }
                }
            }
        }
    }

    private static final class Entry extends MpscLinkedQueue.Node<Entry> {
        final Thread thread;
        final Runnable task;

        Entry(Thread thread, Runnable task) {
            this.thread = thread;
            this.task = task;
        }

        @Override
        public Entry value() {
            return this;
        }
    }
}
