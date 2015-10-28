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

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.util.internal.StringUtil.*;

public final class ResourceLeakDetector<T> {

    private static final String PROP_LEVEL_OLD = "io.netty.leakDetectionLevel";
    private static final String PROP_LEVEL = "io.netty.leakDetection.level";
    private static final Level DEFAULT_LEVEL = Level.SIMPLE;

    private static final String PROP_MAX_RECORDS = "io.netty.leakDetection.maxRecords";
    private static final int DEFAULT_MAX_RECORDS = 4;
    private static final int MAX_RECORDS;

    /**
     * Represents the level of resource leak detection.
     */
    public enum Level {
        /**
         * Disables resource leak detection.
         */
        DISABLED,
        /**
         * Enables simplistic sampling resource leak detection which reports there is a leak or not,
         * at the cost of small overhead (default).
         */
        SIMPLE,
        /**
         * Enables advanced sampling resource leak detection which reports where the leaked object was accessed
         * recently at the cost of high overhead.
         */
        ADVANCED,
        /**
         * Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
         * at the cost of the highest possible overhead (for testing purposes only).
         */
        PARANOID
    }

    private static Level level;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ResourceLeakDetector.class);

    static {
        final boolean disabled;
        if (SystemPropertyUtil.get("io.netty.noResourceLeakDetection") != null) {
            disabled = SystemPropertyUtil.getBoolean("io.netty.noResourceLeakDetection", false);
            logger.debug("-Dio.netty.noResourceLeakDetection: {}", disabled);
            logger.warn(
                    "-Dio.netty.noResourceLeakDetection is deprecated. Use '-D{}={}' instead.",
                    PROP_LEVEL, DEFAULT_LEVEL.name().toLowerCase());
        } else {
            disabled = false;
        }

        Level defaultLevel = disabled? Level.DISABLED : DEFAULT_LEVEL;

        // First read old property name
        String levelStr = SystemPropertyUtil.get(PROP_LEVEL_OLD, defaultLevel.name()).trim().toUpperCase();

        // If new property name is present, use it
        levelStr = SystemPropertyUtil.get(PROP_LEVEL, levelStr).trim().toUpperCase();
        Level level = DEFAULT_LEVEL;
        for (Level l: EnumSet.allOf(Level.class)) {
            if (levelStr.equals(l.name()) || levelStr.equals(String.valueOf(l.ordinal()))) {
                level = l;
            }
        }

        MAX_RECORDS = SystemPropertyUtil.getInt(PROP_MAX_RECORDS, DEFAULT_MAX_RECORDS);

        ResourceLeakDetector.level = level;
        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_LEVEL, level.name().toLowerCase());
            logger.debug("-D{}: {}", PROP_MAX_RECORDS, MAX_RECORDS);
        }
    }

    // Should be power of two.
    private static final int DEFAULT_SAMPLING_INTERVAL = 128;

    /**
     * @deprecated Use {@link #setLevel(Level)} instead.
     */
    @Deprecated
    public static void setEnabled(boolean enabled) {
        setLevel(enabled? Level.SIMPLE : Level.DISABLED);
    }

    /**
     * Returns {@code true} if resource leak detection is enabled.
     */
    public static boolean isEnabled() {
        return getLevel().ordinal() > Level.DISABLED.ordinal();
    }

    /**
     * Sets the resource leak detection level.
     */
    public static void setLevel(Level level) {
        if (level == null) {
            throw new NullPointerException("level");
        }
        ResourceLeakDetector.level = level;
    }

    /**
     * Returns the current resource leak detection level.
     */
    public static Level getLevel() {
        return level;
    }

    /** the linked list of active resources */
    private final DefaultResourceLeak head = new DefaultResourceLeak(null);
    private final DefaultResourceLeak tail = new DefaultResourceLeak(null);

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
    private final ConcurrentMap<String, Boolean> reportedLeaks = PlatformDependent.newConcurrentHashMap();

    private final String resourceType;
    private final int samplingInterval;
    private final int mask;
    private final long maxActive;
    private long active;
    private final AtomicBoolean loggedTooManyActive = new AtomicBoolean();

    private long leakCheckCnt;

    public ResourceLeakDetector(Class<?> resourceType) {
        this(simpleClassName(resourceType));
    }

    public ResourceLeakDetector(String resourceType) {
        this(resourceType, DEFAULT_SAMPLING_INTERVAL, Long.MAX_VALUE);
    }

    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(simpleClassName(resourceType), samplingInterval, maxActive);
    }

    public ResourceLeakDetector(String resourceType, int samplingInterval, long maxActive) {
        if (resourceType == null) {
            throw new NullPointerException("resourceType");
        }
        if (samplingInterval <= 0) {
            throw new IllegalArgumentException("samplingInterval: " + samplingInterval + " (expected: 1+)");
        }
        if (maxActive <= 0) {
            throw new IllegalArgumentException("maxActive: " + maxActive + " (expected: 1+)");
        }

        this.resourceType = resourceType;
        this.samplingInterval = MathUtil.findNextPositivePowerOfTwo(samplingInterval);
        // samplingInterval is a power of two so we calculate a mask that we can use to
        // check if we need to do any leak detection or not.
        mask = this.samplingInterval - 1;
        this.maxActive = maxActive;

        head.next = tail;
        tail.prev = head;
    }

    /**
     * Creates a new {@link ResourceLeak} which is expected to be closed via {@link ResourceLeak#close()} when the
     * related resource is deallocated.
     *
     * @return the {@link ResourceLeak} or {@code null}
     */
    public ResourceLeak open(T obj) {
        Level level = ResourceLeakDetector.level;
        if (level == Level.DISABLED) {
            return null;
        }

        if (level.ordinal() < Level.PARANOID.ordinal()) {
            if ((leakCheckCnt ++ & mask) == 0) {
                reportLeak(level);
                return new DefaultResourceLeak(obj);
            } else {
                return null;
            }
        } else {
            reportLeak(level);
            return new DefaultResourceLeak(obj);
        }
    }

    private void reportLeak(Level level) {
        if (!logger.isErrorEnabled()) {
            for (;;) {
                @SuppressWarnings("unchecked")
                DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
                if (ref == null) {
                    break;
                }
                ref.close();
            }
            return;
        }

        // Report too many instances.
        int samplingInterval = level == Level.PARANOID? 1 : this.samplingInterval;
        if (active * samplingInterval > maxActive && loggedTooManyActive.compareAndSet(false, true)) {
            logger.error("LEAK: You are creating too many " + resourceType + " instances.  " +
                    resourceType + " is a shared resource that must be reused across the JVM," +
                    "so that only a few instances are created.");
        }

        // Detect and report previous leaks.
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }

            ref.clear();

            if (!ref.close()) {
                continue;
            }

            String records = ref.toString();
            if (reportedLeaks.putIfAbsent(records, Boolean.TRUE) == null) {
                if (records.isEmpty()) {
                    logger.error("LEAK: {}.release() was not called before it's garbage-collected. " +
                            "Enable advanced leak reporting to find out where the leak occurred. " +
                            "To enable advanced leak reporting, " +
                            "specify the JVM option '-D{}={}' or call {}.setLevel() " +
                            "See http://netty.io/wiki/reference-counted-objects.html for more information.",
                            resourceType, PROP_LEVEL, Level.ADVANCED.name().toLowerCase(), simpleClassName(this));
                } else {
                    logger.error(
                            "LEAK: {}.release() was not called before it's garbage-collected. " +
                            "See http://netty.io/wiki/reference-counted-objects.html for more information.{}",
                            resourceType, records);
                }
            }
        }
    }

    private final class DefaultResourceLeak extends PhantomReference<Object> implements ResourceLeak {
        private final String creationRecord;
        private final Deque<String> lastRecords = new ArrayDeque<String>();
        private final AtomicBoolean freed;
        private DefaultResourceLeak prev;
        private DefaultResourceLeak next;

        DefaultResourceLeak(Object referent) {
            super(referent, referent != null? refQueue : null);

            if (referent != null) {
                Level level = getLevel();
                if (level.ordinal() >= Level.ADVANCED.ordinal()) {
                    creationRecord = newRecord(3);
                } else {
                    creationRecord = null;
                }

                // TODO: Use CAS to update the list.
                synchronized (head) {
                    prev = head;
                    next = head.next;
                    head.next.prev = this;
                    head.next = this;
                    active ++;
                }
                freed = new AtomicBoolean();
            } else {
                creationRecord = null;
                freed = new AtomicBoolean(true);
            }
        }

        @Override
        public void record() {
            if (creationRecord != null) {
                String value = newRecord(2);

                synchronized (lastRecords) {
                    int size = lastRecords.size();
                    if (size == 0 || !lastRecords.getLast().equals(value)) {
                        lastRecords.add(value);
                    }
                    if (size > MAX_RECORDS) {
                        lastRecords.removeFirst();
                    }
                }
            }
        }

        @Override
        public boolean close() {
            if (freed.compareAndSet(false, true)) {
                synchronized (head) {
                    active --;
                    prev.next = next;
                    next.prev = prev;
                    prev = null;
                    next = null;
                }
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            if (creationRecord == null) {
                return "";
            }

            Object[] array;
            synchronized (lastRecords) {
                array = lastRecords.toArray();
            }

            StringBuilder buf = new StringBuilder(16384)
                .append(NEWLINE)
                .append("Recent access records: ")
                .append(array.length)
                .append(NEWLINE);

            if (array.length > 0) {
                for (int i = array.length - 1; i >= 0; i --) {
                    buf.append('#')
                       .append(i + 1)
                       .append(':')
                       .append(NEWLINE)
                       .append(array[i]);
                }
            }

            buf.append("Created at:")
               .append(NEWLINE)
               .append(creationRecord);

            buf.setLength(buf.length() - NEWLINE.length());
            return buf.toString();
        }
    }

    private static final String[] STACK_TRACE_ELEMENT_EXCLUSIONS = {
            "io.netty.buffer.AbstractByteBufAllocator.toLeakAwareBuffer(",
            "io.netty.buffer.AdvancedLeakAwareByteBuf.recordLeakNonRefCountingOperation("
    };

    static String newRecord(int recordsToSkip) {
        StringBuilder buf = new StringBuilder(4096);
        StackTraceElement[] array = new Throwable().getStackTrace();
        for (StackTraceElement e: array) {
            if (recordsToSkip > 0) {
                recordsToSkip --;
            } else {
                String estr = e.toString();

                // Strip the noisy stack trace elements.
                boolean excluded = false;
                for (String exclusion: STACK_TRACE_ELEMENT_EXCLUSIONS) {
                    if (estr.startsWith(exclusion)) {
                        excluded = true;
                        break;
                    }
                }

                if (!excluded) {
                    buf.append('\t');
                    buf.append(estr);
                    buf.append(NEWLINE);
                }
            }
        }

        return buf.toString();
    }
}
