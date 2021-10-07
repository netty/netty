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

import io.netty.buffer.api.LeakInfo;
import io.netty.buffer.api.LoggingLeakCallback;
import io.netty.buffer.api.MemoryManager.LeakCallbackUninstall;
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

import static java.util.Objects.requireNonNull;

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
            CALLBACKS.add(LoggingLeakCallback.getInstance());
        }
        leakDetectionEnabled = enabled;
    }

    private LeakDetection() {
    }

    public static LeakCallbackUninstall onLeakDetected(Consumer<LeakInfo> callback) {
        requireNonNull(callback, "callback");
        LEAK_DETECTION_ENABLED_UPDATER.getAndAddAcquire(1);
        synchronized (CALLBACKS) {
            CALLBACKS.add(callback);
        }
        return new CallbackRemover(callback);
    }

    public static void reportLeak(LifecycleTracer tracer, String leakedObjectDescription) {
        requireNonNull(tracer, "tracer");
        requireNonNull(leakedObjectDescription, "leakedObjectDescription");
        synchronized (CALLBACKS) {
            if (!CALLBACKS.isEmpty()) {
                LeakInfo info = new InternalLeakInfo(tracer, leakedObjectDescription);
                for (Consumer<LeakInfo> callback : CALLBACKS) {
                    callback.accept(info);
                }
            }
        }
    }

    private static final class CallbackRemover implements LeakCallbackUninstall {
        private final Consumer<LeakInfo> callback;

        CallbackRemover(Consumer<LeakInfo> callback) {
            this.callback = callback;
        }

        @Override
        public void close() {
            synchronized (CALLBACKS) {
                CALLBACKS.remove(callback);
            }
            LEAK_DETECTION_ENABLED_UPDATER.getAndAddRelease(-1);
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
