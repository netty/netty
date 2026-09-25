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
package io.netty.util.internal;

import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;

/**
 * Records whether a Flight Recorder has been initialized, for {@link PlatformDependent#isJfrEnabled()}.
 * <p>
 * JFR registers an event class when the class is initialized, and the first registration sets up JFR's metadata
 * repository, which is expensive. Netty therefore only initializes its event classes once a recorder exists.
 * No event is lost: every recording, including one started with {@code -XX:StartFlightRecording} or
 * {@code jcmd JFR.start}, obtains the recorder through {@code FlightRecorder.getFlightRecorder()}, which notifies
 * the listeners before it returns.
 * <p>
 * This is a separate class so that {@link PlatformDependent} can be loaded and verified without {@code jdk.jfr}.
 * The listener only writes a field of its own class, which is already initialized, so it never waits for another
 * class to be initialized while JFR holds its lock.
 */
@SuppressWarnings("Since15")
final class JfrRecorderListener implements FlightRecorderListener {

    /**
     * Set once a Flight Recorder has been initialized; it never changes back. This is a plain field so that
     * allocation paths can check it with a plain read.
     */
    static boolean recorderInitialized;

    private JfrRecorderListener() {
    }

    /**
     * Registers the listener. It is notified immediately if a recorder already exists, and never if the JVM does
     * not support JFR. Throws a {@link LinkageError} if {@code jdk.jfr} is not available.
     */
    static void register() {
        FlightRecorder.addListener(new JfrRecorderListener());
    }

    @Override
    public void recorderInitialized(FlightRecorder recorder) {
        recorderInitialized = true;
    }
}
