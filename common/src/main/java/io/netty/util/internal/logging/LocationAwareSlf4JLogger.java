/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal.logging;

import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

import static org.slf4j.spi.LocationAwareLogger.*;

/**
 * <a href="http://www.slf4j.org/">SLF4J</a> logger.
 * similar to <a href="https://github.com/qos-ch/slf4j/blob/v_1.7.25/jcl-over-slf4j/src/main/java/org/apache/commons/logging/impl/SLF4JLocationAwareLog.java">SLF4JLocationAwareLog</a>
 */
class LocationAwareSlf4JLogger extends AbstractInternalLogger {
    public static final String FQCN = LocationAwareSlf4JLogger.class.getName();

    private final transient LocationAwareLogger logger;

    LocationAwareSlf4JLogger(LocationAwareLogger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    public void log(final int level, final String message, final Object... params) {
        logger.log(null, FQCN, level, message, params, null);
    }

    public void log(final int level, final String message, Throwable throwable, final Object... params) {
        logger.log(null, FQCN, level, message, params, throwable);
    }

    public void log(final Marker marker, final int level, final String message, Throwable throwable, final Object... params) {
        logger.log(marker, FQCN, level, message, params, throwable);
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        log(TRACE_INT, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        log(TRACE_INT, format, arg);
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        log(TRACE_INT, format, argA, argB);
    }

    @Override
    public void trace(String format, Object... argArray) {
        log(TRACE_INT, format, argArray);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(TRACE_INT, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        log(DEBUG_INT, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        log(DEBUG_INT, format, arg);
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        log(DEBUG_INT, format, argA, argB);
    }

    @Override
    public void debug(String format, Object... argArray) {
        log(DEBUG_INT, format, argArray);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(DEBUG_INT, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        log(INFO_INT, msg);
    }

    @Override
    public void info(String format, Object arg) {
        log(INFO_INT, format, arg);
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        log(INFO_INT, format, argA, argB);
    }

    @Override
    public void info(String format, Object... argArray) {
        log(INFO_INT, format, argArray);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(INFO_INT, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        log(WARN_INT, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        log(WARN_INT, format, arg);
    }

    @Override
    public void warn(String format, Object... argArray) {
        log(WARN_INT, format, argArray);
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        log(WARN_INT, format, argA, argB);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(WARN_INT, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        log(ERROR_INT, msg);
    }

    @Override
    public void error(String format, Object arg) {
        log(ERROR_INT, format, arg);
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        log(ERROR_INT, format, argA, argB);
    }

    @Override
    public void error(String format, Object... argArray) {
        log(ERROR_INT, format, argArray);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(ERROR_INT, msg, t);
    }
}
