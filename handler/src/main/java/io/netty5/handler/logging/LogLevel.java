/*
 * Copyright 2023 The Netty Project
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
package io.netty5.handler.logging;

import org.slf4j.event.Level;

import static java.util.Objects.requireNonNull;

/**
 * Logging level for configuring {@link LoggingHandler}.
 */
public enum LogLevel {
    TRACE(Level.TRACE),
    DEBUG(Level.DEBUG),
    INFO(Level.INFO),
    WARN(Level.WARN),
    ERROR(Level.ERROR);

    private final Level internalLevel;

    LogLevel(Level internalLevel) {
        this.internalLevel = internalLevel;
    }

    /**
     * Convert the given object into a {@code LogLevel} object.
     * This supports comparable "Level" objects from many logging frameworks, as well as
     * {@code String} objects for the log-level name.
     *
     * @param level The level to convert into a {@code LogLevel}.
     * @return A comparable log-level.
     * @throws IllegalArgumentException if the given level cannot be converted.
     */
    public static LogLevel from(Object level) {
        requireNonNull(level, "level");
        if (level instanceof Level) {
            switch ((Level) level) {
                case ERROR: return ERROR;
                case WARN: return WARN;
                case INFO: return INFO;
                case DEBUG: return DEBUG;
                case TRACE: return TRACE;
            }
        }
        // Calling toString() works for String values, java.util.logging.Level objects,
        // and similar Level objects in other logging frameworks.
        String name = level.toString();
        switch (name) {
            case "OFF":
            case "ERROR":
            case "SEVERE":
            case "FATAL":
                return ERROR;
            case "WARN":
            case "WARNING":
                return WARN;
            case "INFO":
                return INFO;
            case "CONFIG":
            case "DEBUG":
                return DEBUG;
            case "FINE":
            case "FINER":
            case "FINEST":
            case "TRACE":
            case "ALL":
                return TRACE;
        }
        throw new IllegalArgumentException("Cannot convert object into a level: " + level);
    }

    /**
     * For internal use only.
     *
     * <p/>Converts the specified {@link LogLevel} to its {@link Level} variant.
     *
     * @return the converted level.
     */
    Level toInternalLevel() {
        return internalLevel;
    }
}
