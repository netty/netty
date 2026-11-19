/*
 * Copyright 2026 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;

/**
 * Registers the allocator events with JFR once a Flight Recorder has been initialized.
 * <p>
 * The events are {@link jdk.jfr.Registered @Registered(false)}, because registering the first event class sets up
 * JFR's metadata repository, which is expensive and pointless in processes that never record. Until an event class
 * is registered, JFR does not instrument it, so {@link Event#isEnabled()} is a constant {@code false} and the JIT
 * removes the allocation path guards entirely. Registering it later retransforms the class, which deoptimizes the
 * guards, exactly like starting a recording in a running process.
 * <p>
 * JFR notifies listeners while it holds the {@code PlatformRecorder} class lock. It registers its own JDK events
 * under the same lock, and the event classes are loaded by this class's initializer and have trivial initializers,
 * so registering from the callback does not wait for anything that may in turn wait for that lock.
 * <p>
 * A Flight Recorder is initialized at most once per JVM, so the listener removes itself after registering the events.
 * Otherwise JFR's static listener list would keep this class, and with it the class loader that loaded Netty,
 * reachable for the lifetime of the JVM. JFR iterates over a copy of the list, so this is safe from the callback.
 * <p>
 * If no Flight Recorder is ever initialized, the listener stays in that list, so the class loader that loaded Netty
 * can't be unloaded, for example on a webapp redeploy. Holding the listener weakly doesn't help: before JDK 25,
 * {@code addListener} stores the caller's {@code AccessControlContext} with it, which references Netty's protection
 * domain and so its class loader. Set {@code -Dio.netty.jfr.enabled=false} to avoid this.
 * <p>
 * This must not be initialized from an event class initializer: registration initializes the event classes.
 */
@SuppressWarnings("Since15")
final class JfrEventRegistration implements FlightRecorderListener {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JfrEventRegistration.class);

    private static final Class<?>[] EVENT_CLASSES = {
            AllocateChunkEvent.class,
            FreeChunkEvent.class,
            AllocateBufferEvent.class,
            FreeBufferEvent.class,
            ReallocateBufferEvent.class,
    };

    static {
        try {
            // Notifies the listener immediately if a recorder already exists, e.g. with -XX:StartFlightRecording.
            FlightRecorder.addListener(new JfrEventRegistration());
        } catch (Throwable t) {
            logger.debug("Failed to register the JFR recorder listener, allocator events are disabled", t);
        }
    }

    private JfrEventRegistration() {
    }

    /**
     * Make sure the allocator events are registered once a Flight Recorder exists. Allocators call this, guarded by
     * {@link PlatformDependent#isJfrEnabled()}, before they can emit events. The class initializer does the work.
     */
    static void init() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void recorderInitialized(FlightRecorder recorder) {
        try {
            for (Class<?> eventClass : EVENT_CLASSES) {
                FlightRecorder.register((Class<? extends Event>) eventClass);
            }
        } catch (Throwable t) {
            // Don't break the recording that is being started.
            logger.debug("Failed to register the allocator JFR events", t);
        } finally {
            try {
                FlightRecorder.removeListener(this);
            } catch (Throwable t) {
                logger.debug("Failed to remove the JFR recorder listener", t);
            }
        }
    }
}
