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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.LeakInfo;
import io.netty.buffer.api.LoggingLeakCallback;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utility class for the leak detection parts that are static and shared system-wide.
 */
@UnstableApi
public final class LeakDetection {
    static volatile int leakDetectionEnabled;

    // Protected by synchronizing on the instance.
    // This field is only accessed when leak are detected, or when callbacks are installed or removed.
    private static final Set<Consumer<LeakInfo>> CALLBACKS = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final VarHandle LEAK_DETECTION_ENABLED_UPDATER;
    static {
        int enabled = SystemPropertyUtil.getBoolean("io.netty.buffer.leakDetectionEnabled", false) ? 1 : 0;
        try {
            LEAK_DETECTION_ENABLED_UPDATER = MethodHandles.lookup().findStaticVarHandle(
                    LeakDetection.class, "leakDetectionEnabled", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        if (enabled > 0) {
            CALLBACKS.add(new LoggingLeakCallback());
        }
        leakDetectionEnabled = enabled;
    }

    private LeakDetection() {
    }

    public static AutoCloseable onLeakDetected(Consumer<LeakInfo> callback) {
        LEAK_DETECTION_ENABLED_UPDATER.getAndAddAcquire(1);
        Set<Consumer<LeakInfo>> callbacks = CALLBACKS;
        synchronized (callbacks) {
            callbacks.add(callback);
        }
        return new CallbackRemover(callback);
    }

    public static void reportLeak(LifecycleTracer tracer, Buffer recoveredBuffer) {
        Set<Consumer<LeakInfo>> callbacks = CALLBACKS;
        synchronized (callbacks) {
            if (!callbacks.isEmpty()) {
                LeakInfo info = new InternalLeakInfo(tracer, recoveredBuffer);
                System.err.println("LEAK: "); // todo remove
                info.forEach(trace -> { // todo remove
                    trace.getTraceback().printStackTrace();
                });
                for (Consumer<LeakInfo> callback : callbacks) {
                    callback.accept(info);
                }
            }
        }
    }

    private static final class CallbackRemover implements AutoCloseable {
        private final Consumer<LeakInfo> callback;

        CallbackRemover(Consumer<LeakInfo> callback) {
            this.callback = callback;
        }

        @Override
        public void close() {
            Set<Consumer<LeakInfo>> callbacks = CALLBACKS;
            synchronized (callbacks) {
                callbacks.remove(callback);
            }
            LEAK_DETECTION_ENABLED_UPDATER.getAndAddRelease(-1);
        }
    }

    private static final class InternalLeakInfo implements LeakInfo {
        private final LifecycleTracer tracer;
        private final int bytesLeaked;
        private Collection<TracePoint> cachedTrace;

        InternalLeakInfo(LifecycleTracer tracer, Buffer recoveredBuffer) {
            this.tracer = tracer;
            bytesLeaked = recoveredBuffer.capacity();
        }

        @Override
        public int bytesLeaked() {
            return bytesLeaked;
        }

        @Override
        public Iterator<TracePoint> iterator() {
            return getTracePoints().iterator();
        }

        @Override
        public Stream<TracePoint> stream() {
            return getTracePoints().stream();
        }

        private Collection<TracePoint> getTracePoints() {
            if (cachedTrace == null) {
                cachedTrace = tracer.collectTraces();
            }
            return cachedTrace;
        }
    }
}
