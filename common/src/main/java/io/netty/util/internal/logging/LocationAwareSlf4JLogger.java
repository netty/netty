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

import org.slf4j.spi.LocationAwareLogger;

import static org.slf4j.spi.LocationAwareLogger.*;

/**
 * <a href="http://www.slf4j.org/">SLF4J</a> logger which is location aware and so will log the correct origin of the
 * logging event by filter out the wrapper itself.
 */
final class LocationAwareSlf4JLogger extends AbstractInternalLogger {

    // IMPORTANT: All our log methods first check if the log level is enabled before call the wrapped
    // LocationAwareLogger.log(...) method. This is done to reduce GC creation that is caused by varargs.

    static final String FQCN = LocationAwareSlf4JLogger.class.getName();
    private static final long serialVersionUID = -8292030083201538180L;

    private final transient LocationAwareLogger logger;

    LocationAwareSlf4JLogger(LocationAwareLogger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    private void log(final int level, final String message) {
        logger.log(null, FQCN, level, message, null, null);
    }

    private void log(final int level, final String message, Throwable cause) {
        logger.log(null, FQCN, level, message, null, cause);
    }

    private void log(final int level, final org.slf4j.helpers.FormattingTuple tuple) {
        logger.log(null, FQCN, level, tuple.getMessage(), tuple.getArgArray(), tuple.getThrowable());
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            log(TRACE_INT, msg);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            log(TRACE_INT, org.slf4j.helpers.MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        if (isTraceEnabled()) {
            log(TRACE_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB));
        }
    }

    @Override
    public void trace(String format, Object... argArray) {
        if (isTraceEnabled()) {
            log(TRACE_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, argArray));
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            log(TRACE_INT, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, msg);
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB));
        }
    }

    @Override
    public void debug(String format, Object... argArray) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, argArray));
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        if (isInfoEnabled()) {
            log(INFO_INT, msg);
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            log(INFO_INT, org.slf4j.helpers.MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        if (isInfoEnabled()) {
            log(INFO_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB));
        }
    }

    @Override
    public void info(String format, Object... argArray) {
        if (isInfoEnabled()) {
            log(INFO_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, argArray));
        }
    }

    @Override
    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            log(INFO_INT, msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        if (isWarnEnabled()) {
            log(WARN_INT, msg);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            log(WARN_INT, org.slf4j.helpers.MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void warn(String format, Object... argArray) {
        if (isWarnEnabled()) {
            log(WARN_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, argArray));
        }
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        if (isWarnEnabled()) {
            log(WARN_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB));
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            log(WARN_INT, msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        if (isErrorEnabled()) {
            log(ERROR_INT, msg);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            log(ERROR_INT, org.slf4j.helpers.MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        if (isErrorEnabled()) {
            log(ERROR_INT, org.slf4j.helpers.MessageFormatter.format(format, argA, argB));
        }
    }

    @Override
    public void error(String format, Object... argArray) {
        if (isErrorEnabled()) {
            log(ERROR_INT, org.slf4j.helpers.MessageFormatter.arrayFormat(format, argArray));
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            log(ERROR_INT, msg, t);
        }
    }
}
