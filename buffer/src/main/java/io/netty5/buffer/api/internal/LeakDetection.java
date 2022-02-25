/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.LeakInfo;
import io.netty5.buffer.api.LoggingLeakCallback;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.util.SafeCloseable;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.UnstableApi;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for the leak detection parts that are static and shared system-wide.
 */
@UnstableApi
public final class LeakDetection {
    static volatile int leakDetectionEnabled;

    // Protected by synchronizing on the instance.
    // This field is only accessed when leak are detected, or when callbacks are installed or removed.
    private static final Map<Consumer<LeakInfo>, Integer> CALLBACKS = new IdentityHashMap<>();
    private static final Integer INTEGER_ONE = 1;

    private static final VarHandle LEAK_DETECTION_ENABLED_UPDATER;
    static {
        int enabled = SystemPropertyUtil.getBoolean("io.netty5.buffer.leakDetectionEnabled", false) ? 1 : 0;
        try {
            LEAK_DETECTION_ENABLED_UPDATER = MethodHandles.lookup().findStaticVarHandle(
                    LeakDetection.class, "leakDetectionEnabled", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        if (enabled > 0) {
            CALLBACKS.put(LoggingLeakCallback.getInstance(), 1);
        }
        leakDetectionEnabled = enabled;
    }

    private LeakDetection() {
    }

    /**
     * Internal API for {@link MemoryManager#onLeakDetected(Consumer)}.
     *
     * @see MemoryManager#onLeakDetected(Consumer)
     */
    public static SafeCloseable onLeakDetected(Consumer<LeakInfo> callback) {
        requireNonNull(callback, "callback");
        synchronized (CALLBACKS) {
            Integer newValue = CALLBACKS.compute(callback, (k, v) -> v == null ? INTEGER_ONE : v + 1);
            if (newValue.equals(INTEGER_ONE)) {
                // This callback was not already in the map, so we need to increment the leak-detection-enabled counter.
                LEAK_DETECTION_ENABLED_UPDATER.getAndAddAcquire(1);
            }
        }
        return new CallbackRemover(callback);
    }

    /**
     * Called when a leak is detected. This method will inform all registered
     * {@linkplain MemoryManager#onLeakDetected(Consumer) on-leak-detected} callbacks.
     *
     * @param tracer The life-cycle trace of the leaked object.
     * @param leakedObjectDescription A human-readable description of the leaked object, that can be used for logging.
     */
    public static void reportLeak(LifecycleTracer tracer, String leakedObjectDescription) {
        requireNonNull(tracer, "tracer");
        requireNonNull(leakedObjectDescription, "leakedObjectDescription");
        synchronized (CALLBACKS) {
            if (!CALLBACKS.isEmpty()) {
                LeakInfo info = new InternalLeakInfo(tracer, leakedObjectDescription);
                for (Consumer<LeakInfo> callback : CALLBACKS.keySet()) {
                    callback.accept(info);
                }
            }
        }
    }

    private static final class CallbackRemover extends AtomicBoolean implements SafeCloseable {
        private static final long serialVersionUID = -7883321389305330790L;
        private final Consumer<LeakInfo> callback;

        CallbackRemover(Consumer<LeakInfo> callback) {
            this.callback = callback;
        }

        @Override
        public void close() {
            if (!getAndSet(true)) { // Close can only be called once, per remover-object.
                synchronized (CALLBACKS) {
                    CALLBACKS.compute(callback, (k, v) -> {
                        assert v != null; // This should not be possible with the getAndSet guard above.
                        if (v.equals(INTEGER_ONE)) {
                            // The specific callback was removed, so reduce the leak-detection-enabled counter.
                            LEAK_DETECTION_ENABLED_UPDATER.getAndAddRelease(-1);
                            return null; // And then remove the mapping.
                        }
                        return v - 1;
                    });
                }
            }
        }
    }

    private static final class InternalLeakInfo implements LeakInfo {
        private final LifecycleTracer tracer;
        private final String leakedObjectDescription;
        private Collection<TracePoint> cachedTrace;

        InternalLeakInfo(LifecycleTracer tracer, String leakedObjectDescription) {
            this.tracer = tracer;
            this.leakedObjectDescription = leakedObjectDescription;
        }

        @Override
        public Iterator<TracePoint> iterator() {
            return getTracePoints().iterator();
        }

        @Override
        public Stream<TracePoint> stream() {
            return getTracePoints().stream();
        }

        @Override
        public String objectDescription() {
            return leakedObjectDescription;
        }

        private Collection<TracePoint> getTracePoints() {
            if (cachedTrace == null) {
                cachedTrace = tracer.collectTraces();
            }
            return cachedTrace;
        }
    }
}
