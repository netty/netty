/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util;

import io.netty.util.internal.SystemPropertyUtil;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Alternative leak detector implementation for reliable and performant detection in tests.
 *
 * <h3>Background</h3>
 * <p>
 * The standard {@link ResourceLeakDetector} produces no "false positives", but this comes with tradeoffs. You either
 * get many false negatives because only a small sample of buffers is instrumented, or you turn on paranoid detection
 * which carries a somewhat heavy performance cost with each allocation. Additionally, paranoid detection enables
 * detailed recording of buffer access operations with heavy performance impact. Avoiding false negatives is necessary
 * for (unit, fuzz...) testing if bugs should lead to reliable test failures, but the performance impact can be
 * prohibitive for some tests.
 *
 * <h3>The presence detector</h3>
 * <p>
 * The <i>leak presence detector</i> takes a different approach. It foregoes detailed tracking of allocation and
 * modification stack traces. In return every resource is counted, so there are no false negatives where a leak would
 * not be detected.
 * <p>
 * The presence detector also does not wait for an unclosed resource to be garbage collected before it's reported as
 * leaked. This ensures that leaks are detected promptly and can be directly associated with a particular test, but it
 * can lead to false positives. Tests that use the presence detector must shut down completely <i>before</i> checking
 * for resource leaks. There are also complications with static fields, described below.
 *
 * <h3>Resource Scopes</h3>
 * <p>
 * A resource scope manages all resources of a set of threads over time. On allocation, a resource is assigned to a
 * scope through the {@link #currentScope()} method. When {@link #check()} is called, or the scope is
 * {@link ResourceScope#close() closed}, all resources in that scope must have been released.
 * <p>
 * By default, there is only a single "global" scope, and when {@link #check()} is called, all resources in the entire
 * JVM must have been released. To enable parallel test execution, it may be necessary to use separate scopes for
 * separate tests instead, so that one test can check for its own leaks while another test is still in progress. You
 * can override {@link #currentScope()} to implement this for your test framework.
 *
 * <h3>Static Fields</h3>
 * <p>
 * While the presence detector requires that <i>all</i> resources be closed after a test, some resources kept in static
 * fields cannot be released, or there would be false positives. To avoid this, resources created inside static
 * initializers, specifically when the allocation stack trace contains a {@code <clinit>} method, <i>are not
 * tracked</i>.
 * <p>
 * Because the presence detector does not normally capture or introspect allocation stack traces, additional
 * cooperation is required. Any static initializer must be wrapped in a {@link #staticInitializer(Supplier)} call,
 * which will temporarily enable stack trace introspection. For example:
 * <pre>{@code
 * private static final ByteBuf CRLF_BUF = LeakPresenceDetector.staticInitializer(() -> unreleasableBuffer(
 *             directBuffer(2).writeByte(CR).writeByte(LF)).asReadOnly());
 * }</pre>
 * <p>
 * Since stack traces are not captured by default, it can be difficult to tell apart a real leak from a missed static
 * initializer. You can temporarily turn on allocation stack trace capture using the
 * {@code -Dio.netty.util.LeakPresenceDetector.trackCreationStack=true} system property.
 *
 * @param <T> The resource type to detect
 */
public class LeakPresenceDetector<T> extends ResourceLeakDetector<T> {
    private static final String TRACK_CREATION_STACK_PROPERTY = "io.netty.util.LeakPresenceDetector.trackCreationStack";
    private static final boolean TRACK_CREATION_STACK =
            SystemPropertyUtil.getBoolean(TRACK_CREATION_STACK_PROPERTY, false);
    private static final ResourceScope GLOBAL = new ResourceScope("global");

    private static int staticInitializerCount;

    private static boolean inStaticInitializerSlow(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("<clinit>")) {
                return true;
            }
        }
        return false;
    }

    private static boolean inStaticInitializerFast() {
        // This plain field access is safe. The worst that can happen is that we see non-zero where we shouldn't.
        return staticInitializerCount != 0 && inStaticInitializerSlow(Thread.currentThread().getStackTrace());
    }

    /**
     * Wrap a static initializer so that any resources created inside the block will not be tracked. Example:
     * <pre>{@code
     * private static final ByteBuf CRLF_BUF = LeakPresenceDetector.staticInitializer(() -> unreleasableBuffer(
     *             directBuffer(2).writeByte(CR).writeByte(LF)).asReadOnly());
     * }</pre>
     * <p>
     * Note that technically, this method does not actually care what happens inside the block. Instead, it turns on
     * stack trace introspection at the start of the block, and turns it back off at the end. Any allocation in that
     * interval will be checked to see whether it is part of a static initializer, and if it is, it will not be
     * tracked.
     *
     * @param supplier A code block to run
     * @return The value returned by the {@code supplier}
     * @param <R> The supplier return type
     */
    public static <R> R staticInitializer(Supplier<R> supplier) {
        if (!inStaticInitializerSlow(Thread.currentThread().getStackTrace())) {
            throw new IllegalStateException("Not in static initializer.");
        }
        synchronized (LeakPresenceDetector.class) {
            staticInitializerCount++;
        }
        try {
            return supplier.get();
        } finally {
            synchronized (LeakPresenceDetector.class) {
                staticInitializerCount--;
            }
        }
    }

    /**
     * Create a new detector for the given resource type.
     *
     * @param resourceType The resource type
     */
    public LeakPresenceDetector(Class<?> resourceType) {
        super(resourceType, 0);
    }

    /**
     * This constructor should not be used directly, it is called reflectively by {@link ResourceLeakDetectorFactory}.
     *
     * @param resourceType The resource type
     * @param samplingInterval Ignored
     */
    @Deprecated
    @SuppressWarnings("unused")
    public LeakPresenceDetector(Class<?> resourceType, int samplingInterval) {
        this(resourceType);
    }

    /**
     * This constructor should not be used directly, it is called reflectively by {@link ResourceLeakDetectorFactory}.
     *
     * @param resourceType The resource type
     * @param samplingInterval Ignored
     * @param maxActive Ignored
     */
    @SuppressWarnings("unused")
    public LeakPresenceDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(resourceType);
    }

    /**
     * Get the resource scope for the current thread. This is used to assign resources to scopes, and it is used by
     * {@link #check()} to tell which scope to check for open resources. By default, the global scope is returned.
     *
     * @return The resource scope to use
     */
    protected ResourceScope currentScope() throws AllocationProhibitedException {
        return GLOBAL;
    }

    @Override
    public final ResourceLeakTracker<T> track(T obj) {
        if (inStaticInitializerFast()) {
            return null;
        }
        return trackForcibly(obj);
    }

    @Override
    public final ResourceLeakTracker<T> trackForcibly(T obj) {
        return new PresenceTracker<>(currentScope());
    }

    @Override
    public final boolean isRecordEnabled() {
        return false;
    }

    /**
     * Check the current leak presence detector scope for open resources. If any resources remain unclosed, an
     * exception is thrown.
     *
     * @throws IllegalStateException If there is a leak, or if the leak detector is not a {@link LeakPresenceDetector}.
     */
    public static void check() {
        // for LeakPresenceDetector, this is cheap.
        ResourceLeakDetector<Object> detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(Object.class);

        if (!(detector instanceof LeakPresenceDetector)) {
            throw new IllegalStateException(
                    "LeakPresenceDetector not in use. Please register it using " +
                            "-Dio.netty.customResourceLeakDetector=" + LeakPresenceDetector.class.getName());
        }

        //noinspection resource
        ((LeakPresenceDetector<Object>) detector).currentScope().check();
    }

    private static final class PresenceTracker<T> extends AtomicBoolean implements ResourceLeakTracker<T> {
        private final ResourceScope scope;

        PresenceTracker(ResourceScope scope) {
            super(false);
            this.scope = scope;

            scope.checkOpen();

            scope.openResourceCounter.increment();
            if (TRACK_CREATION_STACK) {
                scope.creationStacks.put(this, new LeakCreation());
            }
        }

        @Override
        public void record() {
        }

        @Override
        public void record(Object hint) {
        }

        @Override
        public boolean close(Object trackedObject) {
            if (compareAndSet(false, true)) {
                scope.openResourceCounter.decrement();
                if (TRACK_CREATION_STACK) {
                    scope.creationStacks.remove(this);
                }
                scope.checkOpen();
                return true;
            }
            return false;
        }
    }

    /**
     * A resource scope keeps track of the resources for a particular set of threads. Different scopes can be checked
     * for leaks separately, to enable parallel test execution.
     */
    public static final class ResourceScope implements Closeable {
        final String name;
        final LongAdder openResourceCounter = new LongAdder();
        final Map<PresenceTracker<?>, Throwable> creationStacks =
                TRACK_CREATION_STACK ? new ConcurrentHashMap<>() : null;
        boolean closed;

        /**
         * Create a new scope.
         *
         * @param name The scope name, used for error reporting
         */
        public ResourceScope(String name) {
            this.name = name;
        }

        void checkOpen() {
            if (closed) {
                throw new AllocationProhibitedException("Resource scope '" + name + "' already closed");
            }
        }

        void check() {
            long n = openResourceCounter.sumThenReset();
            if (n != 0) {
                StringBuilder msg = new StringBuilder("Possible memory leak detected for resource scope '")
                        .append(name).append("'. ");
                if (n < 0) {
                    msg.append("Resource count was negative: A resource previously reported as a leak was released " +
                            "after all. Please ensure that that resource is released before its test finishes.");
                    throw new IllegalStateException(msg.toString());
                }
                if (TRACK_CREATION_STACK) {
                    msg.append("Creation stack traces:");
                    IllegalStateException ise = new IllegalStateException(msg.toString());
                    int i = 0;
                    for (Throwable t : creationStacks.values()) {
                        ise.addSuppressed(t);
                        if (i++ > 5) {
                            break;
                        }
                    }
                    creationStacks.clear();
                    throw ise;
                }
                msg.append("Please use paranoid leak detection to get more information, or set " +
                        "-D" + TRACK_CREATION_STACK_PROPERTY + "=true");
                throw new IllegalStateException(msg.toString());
            }
        }

        /**
         * Check whether there are any open resources left, and {@link #close()} would throw.
         *
         * @return {@code true} if there are open resources
         */
        public boolean hasOpenResources() {
            return openResourceCounter.sum() > 0;
        }

        /**
         * Close this scope. Closing a scope will prevent new resources from being allocated (or released) in this
         * scope. The call also throws an exception if there are any resources left open.
         */
        @Override
        public void close() {
            closed = true;
            check();
        }
    }

    private static final class LeakCreation extends Throwable {
        final Thread thread = Thread.currentThread();
        String message;

        @Override
        public synchronized String getMessage() {
            if (message == null) {
                if (inStaticInitializerSlow(getStackTrace())) {
                    message = "Resource created in static initializer. Please wrap the static initializer in " +
                            "LeakPresenceDetector.staticInitializer so that this resource is excluded.";
                } else {
                    message = "Resource created outside static initializer on thread '" + thread.getName() + "' (" +
                            thread.getState() + "), likely leak.";
                }
            }
            return message;
        }
    }

    /**
     * Special exception type to show that an allocation is prohibited at the moment, for example because the
     * {@link ResourceScope} is closed, or because the current thread cannot be associated with a particular scope.
     * <p>
     * Some code in Netty will treat this exception specially to avoid allocation loops.
     */
    public static final class AllocationProhibitedException extends IllegalStateException {
        public AllocationProhibitedException(String s) {
            super(s);
        }
    }
}
