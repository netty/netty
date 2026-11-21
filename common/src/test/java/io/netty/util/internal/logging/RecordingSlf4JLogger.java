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
package io.netty.util.internal.logging;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.spi.LocationAwareLogger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A SLF4J {@link org.slf4j.Logger} for tests, which records every logging call it receives.
 */
class RecordingSlf4JLogger extends LegacyAbstractLogger {
    private static final long serialVersionUID = 3442616934520584417L;

    private final Set<Level> enabledLevels;
    private final transient List<LoggedEvent> events = new ArrayList<>();

    /**
     * Creates a logger with all levels enabled.
     */
    RecordingSlf4JLogger(String name) {
        this(name, EnumSet.allOf(Level.class));
    }

    RecordingSlf4JLogger(String name, Set<Level> enabledLevels) {
        this.name = name;
        this.enabledLevels = enabledLevels;
    }

    List<LoggedEvent> events() {
        return events;
    }

    @Override
    public boolean isTraceEnabled() {
        return enabledLevels.contains(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return enabledLevels.contains(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return enabledLevels.contains(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return enabledLevels.contains(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return enabledLevels.contains(Level.ERROR);
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        events.add(new LoggedEvent(level, marker, null, messagePattern, arguments, throwable));
    }

    /**
     * A {@link RecordingSlf4JLogger} which is also a {@link LocationAwareLogger}.
     */
    static final class LocationAware extends RecordingSlf4JLogger implements LocationAwareLogger {
        private static final long serialVersionUID = -2189446155224318123L;

        LocationAware(String name) {
            super(name);
        }

        @Override
        public void log(Marker marker, String fqcn, int level, String message, Object[] argArray, Throwable t) {
            events().add(new LoggedEvent(Level.intToLevel(level), marker, fqcn, message, argArray, t));
        }
    }

    static final class LoggedEvent {
        final Level level;
        final Marker marker;
        final String fqcn;
        final String message;
        final Object[] arguments;
        final Throwable throwable;

        LoggedEvent(Level level, Marker marker, String fqcn, String message, Object[] arguments,
                    Throwable throwable) {
            this.level = level;
            this.marker = marker;
            this.fqcn = fqcn;
            this.message = message;
            this.arguments = arguments;
            this.throwable = throwable;
        }
    }
}
