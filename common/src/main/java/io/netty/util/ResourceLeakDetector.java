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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.simpleClassName;

public class ResourceLeakDetector<T> {

    private static final String PROP_LEVEL_OLD = "io.netty.leakDetectionLevel";
    private static final String PROP_LEVEL = "io.netty.leakDetection.level";
    private static final Level DEFAULT_LEVEL = Level.SIMPLE;

    private static final String PROP_MAX_RECORDS = "io.netty.leakDetection.maxRecords";
    private static final int DEFAULT_MAX_RECORDS = 4;
    private static final String PROP_MAX_SAMPLED_RECORDS = "io.netty.leakDetection.maxSampledRecords";

    private static final int MAX_RECORDS;
    private static final int MAX_SAMPLED_RECORDS;

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
        PARANOID;

        /**
         * Returns level based on string value. Accepts also string that represents ordinal number of enum.
         *
         * @param levelStr - level string : DISABLED, SIMPLE, ADVANCED, PARANOID. Ignores case.
         * @return corresponding level or SIMPLE level in case of no match.
         */
        static Level parseLevel(String levelStr) {
            String trimmedLevelStr = levelStr.trim();
            for (Level l : values()) {
                if (trimmedLevelStr.equalsIgnoreCase(l.name()) || trimmedLevelStr.equals(String.valueOf(l.ordinal()))) {
                    return l;
                }
            }
            return DEFAULT_LEVEL;
        }
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
        String levelStr = SystemPropertyUtil.get(PROP_LEVEL_OLD, defaultLevel.name());

        // If new property name is present, use it
        levelStr = SystemPropertyUtil.get(PROP_LEVEL, levelStr);
        Level level = Level.parseLevel(levelStr);

        MAX_RECORDS = SystemPropertyUtil.getInt(PROP_MAX_RECORDS, DEFAULT_MAX_RECORDS);
        long maxRecordsSampled = SystemPropertyUtil.getLong(PROP_MAX_SAMPLED_RECORDS, MAX_RECORDS * 10L);
        MAX_SAMPLED_RECORDS = Math.max((int) Math.min(Integer.MAX_VALUE, maxRecordsSampled), MAX_RECORDS);

        ResourceLeakDetector.level = level;
        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_LEVEL, level.name().toLowerCase());
            logger.debug("-D{}: {}", PROP_MAX_RECORDS, MAX_RECORDS);
            logger.debug("-D{}: {}", PROP_MAX_SAMPLED_RECORDS, MAX_SAMPLED_RECORDS);
        }
    }

    static final int DEFAULT_SAMPLING_INTERVAL = 128;

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

    /** the collection of active resources */
    private final ConcurrentMap<DefaultResourceLeak, LeakEntry> allLeaks = PlatformDependent.newConcurrentHashMap();

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
    private final ConcurrentMap<String, Boolean> reportedLeaks = PlatformDependent.newConcurrentHashMap();

    private final String resourceType;
    private final int samplingInterval;

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType) {
        this(simpleClassName(resourceType));
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType) {
        this(resourceType, DEFAULT_SAMPLING_INTERVAL, Long.MAX_VALUE);
    }

    /**
     * @deprecated Use {@link ResourceLeakDetector#ResourceLeakDetector(Class, int)}.
     * <p>
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     *
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(resourceType, samplingInterval);
    }

    /**
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     */
    @SuppressWarnings("deprecation")
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
        this(simpleClassName(resourceType), samplingInterval, Long.MAX_VALUE);
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     * <p>
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType, int samplingInterval, long maxActive) {
        if (resourceType == null) {
            throw new NullPointerException("resourceType");
        }

        this.resourceType = resourceType;
        this.samplingInterval = samplingInterval;
    }

    /**
     * Creates a new {@link ResourceLeak} which is expected to be closed via {@link ResourceLeak#close()} when the
     * related resource is deallocated.
     *
     * @return the {@link ResourceLeak} or {@code null}
     * @deprecated use {@link #track(Object)}
     */
    @Deprecated
    public final ResourceLeak open(T obj) {
        return track0(obj);
    }

    /**
     * Creates a new {@link ResourceLeakTracker} which is expected to be closed via
     * {@link ResourceLeakTracker#close(Object)} when the related resource is deallocated.
     *
     * @return the {@link ResourceLeakTracker} or {@code null}
     */
    public final ResourceLeakTracker<T> track(T obj) {
        return track0(obj);
    }

    private DefaultResourceLeak track0(T obj) {
        Level level = ResourceLeakDetector.level;
        if (level == Level.DISABLED) {
            return null;
        }

        if (level.ordinal() < Level.PARANOID.ordinal()) {
            if ((PlatformDependent.threadLocalRandom().nextInt(samplingInterval)) == 0) {
                reportLeak();
                return new DefaultResourceLeak(obj);
            }
            return null;
        }
        reportLeak();
        return new DefaultResourceLeak(obj);
    }

    private void clearRefQueue() {
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }
            ref.dispose();
        }
    }

    private void reportLeak() {
        if (!logger.isErrorEnabled()) {
            clearRefQueue();
            return;
        }

        // Detect and report previous leaks.
        for (;;) {
            @SuppressWarnings("unchecked")
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }

            if (!ref.dispose()) {
                continue;
            }

            String records = ref.toString();
            if (reportedLeaks.putIfAbsent(records, Boolean.TRUE) == null) {
                if (records.isEmpty()) {
                    reportUntracedLeak(resourceType);
                } else {
                    reportTracedLeak(resourceType, records);
                }
            }
        }
    }

    /**
     * This method is called when a traced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected void reportTracedLeak(String resourceType, String records) {
        logger.error(
                "LEAK: {}.release() was not called before it's garbage-collected. " +
                "See http://netty.io/wiki/reference-counted-objects.html for more information.{}",
                resourceType, records);
    }

    /**
     * This method is called when an untraced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected void reportUntracedLeak(String resourceType) {
        logger.error("LEAK: {}.release() was not called before it's garbage-collected. " +
                "Enable advanced leak reporting to find out where the leak occurred. " +
                "To enable advanced leak reporting, " +
                "specify the JVM option '-D{}={}' or call {}.setLevel() " +
                "See http://netty.io/wiki/reference-counted-objects.html for more information.",
                resourceType, PROP_LEVEL, Level.ADVANCED.name().toLowerCase(), simpleClassName(this));
    }

    /**
     * @deprecated This method will no longer be invoked by {@link ResourceLeakDetector}.
     */
    @Deprecated
    protected void reportInstancesLeak(String resourceType) {
    }

    @SuppressWarnings("deprecation")
    private final class DefaultResourceLeak extends PhantomReference<Object> implements ResourceLeakTracker<T>,
            ResourceLeak {
        private final Record head;

        // This will be updated once a new Record will be created and added
        private Record tail;

        private final int trackedHash;
        private int numRecords;
        private int droppedRecords;

        DefaultResourceLeak(Object referent) {
            super(referent, refQueue);

            assert referent != null;

            // Store the hash of the tracked object to later assert it in the close(...) method.
            // It's important that we not store a reference to the referent as this would disallow it from
            // be collected via the PhantomReference.
            trackedHash = System.identityHashCode(referent);
            head = tail = getLevel().ordinal() >= Level.ADVANCED.ordinal() ? new Record() : null;
            allLeaks.put(this, LeakEntry.INSTANCE);
        }

        @Override
        public void record() {
            record0(null);
        }

        @Override
        public void record(Object hint) {
            record0(hint);
        }

        private void record0(Object hint) {
            // Check MAX_RECORDS > 0 here to avoid similar check before remove from and add to lastRecords
            if (head != null && MAX_RECORDS > 0) {
                synchronized (head) {
                    if (tail == null) {
                        // already closed
                        return;
                    }

                    Record record = hint == null ? new Record() : new Record(hint);
                    tail.next = record;
                    tail = record;

                    // Enforce a limit so our linked-list not grows too large and cause a GC storm later on when we
                    // unlink it. The reason why we choose a different limit to MAX_RECORDS is that we will not handle
                    // duplications here as its very expensive. We will filter these out when we actually
                    // detected a leak.
                    if (numRecords == MAX_SAMPLED_RECORDS) {
                        head.next = head.next.next;
                        droppedRecords++;
                    } else {
                        numRecords++;
                    }
                }
            }
        }

        boolean dispose() {
            clear();
            return allLeaks.remove(this, LeakEntry.INSTANCE);
        }

        @Override
        public boolean close() {
            // Use the ConcurrentMap remove method, which avoids allocating an iterator.
            if (allLeaks.remove(this, LeakEntry.INSTANCE)) {
                // Call clear so the reference is not even enqueued.
                clear();

                if (head != null) {
                    synchronized (head) {
                        // Allow to GC all the records.
                        head.next = null;
                        numRecords = 0;
                        tail = null;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean close(T trackedObject) {
            // Ensure that the object that was tracked is the same as the one that was passed to close(...).
            assert trackedHash == System.identityHashCode(trackedObject);

            // We need to actually do the null check of the trackedObject after we close the leak because otherwise
            // we may get false-positives reported by the ResourceLeakDetector. This can happen as the JIT / GC may
            // be able to figure out that we do not need the trackedObject anymore and so already enqueue it for
            // collection before we actually get a chance to close the enclosing ResourceLeak.
            return close() && trackedObject != null;
        }

        @Override
        public String toString() {
            if (head == null) {
                return EMPTY_STRING;
            }

            final String creationRecord;
            final String[] array;
            int idx = 0;
            String last = null;
            final int dropped;

            synchronized (head) {
                if (tail == null) {
                    // Already closed
                    return EMPTY_STRING;
                }
                dropped = droppedRecords;
                creationRecord = head.toString();
                array = new String[numRecords];
                Record record = head.next;
                while (record != null) {
                    String recordStr = record.toString();
                    if (last == null || !last.equals(recordStr)) {
                        array[idx ++] = recordStr;
                        last = recordStr;
                    }
                    record = record.next;
                }
            }

            int removed = idx > MAX_RECORDS ? idx - MAX_RECORDS : 0;

            StringBuilder buf = new StringBuilder(16384).append(NEWLINE);
            if (removed > 0) {
                buf.append("WARNING: ")
                   .append(removed)
                   .append(" leak records were discarded because the leak record count is limited to ")
                   .append(MAX_RECORDS)
                   .append(". Use system property ")
                   .append(PROP_MAX_RECORDS)
                   .append(" to increase the limit.")
                   .append(NEWLINE);
            }
            if (dropped > 0) {
                buf.append(dropped)
                   .append(" leak records were not sampled because the leak record sample count is limited to ")
                   .append(MAX_SAMPLED_RECORDS)
                   .append(". Use system property ")
                   .append(PROP_MAX_SAMPLED_RECORDS)
                   .append(" to increase the limit.")
                   .append(NEWLINE);
            }

            int records = idx - removed;
            buf.append("Recent access records: ").append(records).append(NEWLINE);

            if (records > 0) {
                // The array may not be completely filled so we need to take this into account.
                for (int i = records - 1; i >= 0; i --) {
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

    private static final class Record extends Throwable {

        private static final Set<String> STACK_TRACE_ELEMENT_EXCLUSIONS = new HashSet<String>();
        static {
            STACK_TRACE_ELEMENT_EXCLUSIONS.add("io.netty.util.ReferenceCountUtil.touch");
            STACK_TRACE_ELEMENT_EXCLUSIONS.add("io.netty.buffer.AdvancedLeakAwareByteBuf.touch");
            STACK_TRACE_ELEMENT_EXCLUSIONS.add("io.netty.buffer.AbstractByteBufAllocator.toLeakAwareBuffer");
            STACK_TRACE_ELEMENT_EXCLUSIONS.add(
                    "io.netty.buffer.AdvancedLeakAwareByteBuf.recordLeakNonRefCountingOperation");
        }

        private final String hintString;
        Record next;

        Record(Object hint) {
            // This needs to be generated even if toString() is never called as it may change later on.
            hintString = hint instanceof ResourceLeakHint ? ((ResourceLeakHint) hint).toHintString() : hint.toString();
        }

        Record() {
           hintString = null;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(4096);
            if (hintString != null) {
                buf.append("\tHint: ").append(hintString).append(NEWLINE);
            }

            // Append the stack trace.
            StackTraceElement[] array = getStackTrace();
            // Skip the first three elements.
            for (int i = 3; i < array.length; i++) {
                StackTraceElement element = array[i];

                // Strip the noisy stack trace elements.
                if (STACK_TRACE_ELEMENT_EXCLUSIONS.contains(element.getClassName() + '.' + element.getMethodName())) {
                    continue;
                }
                buf.append('\t');
                buf.append(element.toString());
                buf.append(NEWLINE);
            }
            return buf.toString();
        }
    }

    private static final class LeakEntry {
        static final LeakEntry INSTANCE = new LeakEntry();
        private static final int HASH = System.identityHashCode(INSTANCE);

        private LeakEntry() {
        }

        @Override
        public int hashCode() {
            return HASH;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    }
}
