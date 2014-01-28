/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collection of method to handle objects that may implement {@link ReferenceCounted}.
 */
public final class ReferenceCountUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ReferenceCountUtil.class);

    /**
     * Try to call {@link ReferenceCounted#retain()} if the specified message implements {@link ReferenceCounted}.
     * If the specified message doesn't implement {@link ReferenceCounted}, this method does nothing.
     */
    @SuppressWarnings("unchecked")
    public static <T> T retain(T msg) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).retain();
        }
        return msg;
    }

    /**
     * Try to call {@link ReferenceCounted#retain()} if the specified message implements {@link ReferenceCounted}.
     * If the specified message doesn't implement {@link ReferenceCounted}, this method does nothing.
     */
    @SuppressWarnings("unchecked")
    public static <T> T retain(T msg, int increment) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).retain(increment);
        }
        return msg;
    }

    /**
     * Tries to call {@link ReferenceCounted#touch()} if the specified message implements {@link ReferenceCounted}.
     * If the specified message doesn't implement {@link ReferenceCounted}, this method does nothing.
     */
    @SuppressWarnings("unchecked")
    public static <T> T touch(T msg) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).touch();
        }
        return msg;
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link ReferenceCounted}.
     * If the specified message doesn't implement {@link ReferenceCounted}, this method does nothing.
     */
    public static boolean release(Object msg) {
        if (msg instanceof ReferenceCounted) {
            return ((ReferenceCounted) msg).release();
        }
        return false;
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link ReferenceCounted}.
     * If the specified message doesn't implement {@link ReferenceCounted}, this method does nothing.
     */
    public static boolean release(Object msg, int decrement) {
        if (msg instanceof ReferenceCounted) {
            return ((ReferenceCounted) msg).release(decrement);
        }
        return false;
    }

    private static final Map<Thread, List<Entry>> pendingReleases = new IdentityHashMap<Thread, List<Entry>>();

    /**
     * Schedules the specified object to be released when the caller thread terminates. Note that this operation is
     * intended to simplify reference counting of ephemeral objects during unit tests. Do not use it beyond the
     * intended use case.
     */
    public static <T> T releaseLater(T msg) {
        return releaseLater(msg, 1);
    }

    /**
     * Schedules the specified object to be released when the caller thread terminates. Note that this operation is
     * intended to simplify reference counting of ephemeral objects during unit tests. Do not use it beyond the
     * intended use case.
     */
    public static <T> T releaseLater(T msg, int decrement) {
        if (msg instanceof ReferenceCounted) {
            synchronized (pendingReleases) {
                Thread thread = Thread.currentThread();
                List<Entry> entries = pendingReleases.get(thread);
                if (entries == null) {
                    // Start the periodic releasing task (if not started yet.)
                    if (pendingReleases.isEmpty()) {
                        ReleasingTask task = new ReleasingTask();
                        task.future = GlobalEventExecutor.INSTANCE.scheduleWithFixedDelay(task, 1, 1, TimeUnit.SECONDS);
                    }

                    // Create a new entry.
                    entries = new ArrayList<Entry>();
                    pendingReleases.put(thread, entries);
                }

                entries.add(new Entry((ReferenceCounted) msg, decrement));
            }
        }
        return msg;
    }

    private static final class Entry {
        final ReferenceCounted obj;
        final int decrement;

        Entry(ReferenceCounted obj, int decrement) {
            this.obj = obj;
            this.decrement = decrement;
        }

        public String toString() {
            return StringUtil.simpleClassName(obj) + ".release(" + decrement + ") refCnt: " + obj.refCnt();
        }
    }

    /**
     * Releases the objects when the thread that called {@link #releaseLater(Object)} has been terminated.
     */
    private static final class ReleasingTask implements Runnable {
        volatile ScheduledFuture<?> future;

        @Override
        public void run() {
            synchronized (pendingReleases) {
                for (Iterator<Map.Entry<Thread, List<Entry>>> i = pendingReleases.entrySet().iterator();
                     i.hasNext();) {

                    Map.Entry<Thread, List<Entry>> e = i.next();
                    if (e.getKey().isAlive()) {
                        continue;
                    }

                    releaseAll(e.getValue());

                    // Remove from the map since the thread is not alive anymore.
                    i.remove();
                }

                if (pendingReleases.isEmpty()) {
                    future.cancel(false);
                }
            }
        }

        private static void releaseAll(Iterable<Entry> entries) {
            for (Entry e: entries) {
                try {
                    if (!e.obj.release(e.decrement)) {
                        logger.warn("Non-zero refCnt: {}", e);
                    } else {
                        logger.warn("Released: {}", e);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to release an object: {}", e.obj, ex);
                }
            }
        }
    }

    private ReferenceCountUtil() { }
}
