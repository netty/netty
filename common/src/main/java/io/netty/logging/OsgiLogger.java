/*
 * Copyright 2012 The Netty Project
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
package io.netty.logging;

import org.osgi.service.log.LogService;

/**
 * A logger that logs messages to an
 * <a href="http://www.osgi.org/">OSGi</a> {@link LogService} logger.
 */
class OsgiLogger extends AbstractInternalLogger {

    /**
     * The parent {@link OsgiLoggerFactory} that generated this instance
     */
    private final OsgiLoggerFactory parent;

    /**
     * The {@link InternalLogger} to fall back to if logging to the {@link LogService} fails
     */
    private final InternalLogger fallback;

    /**
     * This logger's name
     */
    private final String name;

    /**
     * This logger's prefix
     */
    private final String prefix;

    /**
     * Initializes a new {@link OsgiLogger}
     *
     * @param parent the parent {@link OsgiLoggerFactory} of this instance
     * @param name the name of the logger
     * @param fallback the fallback {@link InternalLogger}
     */
    OsgiLogger(OsgiLoggerFactory parent, String name, InternalLogger fallback) {
        this.parent = parent;
        this.name = name;
        this.fallback = fallback;
        prefix = "[" + name + "] ";
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return true, since all messages can be logged
     */
    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return true, since all messages can be logged
     */
    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return true, since all messages can be logged
     */
    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return true, since all messages can be logged
     */
    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    /**
     * Logs a message used for debugging
     *
     * @param message the message being logged
     */
    @Override
    public void debug(String message) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_DEBUG, prefix + message);
        } else {
            fallback.debug(message);
        }
    }

    /**
     * Logs a message used for debugging with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void debug(String message, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_DEBUG, prefix + message, cause);
        } else {
            fallback.debug(message, cause);
        }
    }

    /**
     * Logs a message used for information
     *
     * @param message the message being logged
     */
    @Override
    public void info(String message) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_INFO, prefix + message);
        } else {
            fallback.info(message);
        }
    }

    /**
     * Logs a messaged used for information with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void info(String message, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_INFO, prefix + message, cause);
        } else {
            fallback.info(message, cause);
        }
    }

    /**
     * Logs a message used as a warning
     *
     * @param message the message being logged
     */
    @Override
    public void warn(String message) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_WARNING, prefix + message);
        } else {
            fallback.warn(message);
        }
    }

    /**
     * Logs a message used as a warning with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void warn(String message, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_WARNING, prefix + message, cause);
        } else {
            fallback.warn(message, cause);
        }
    }

    /**
     * Logs a message used as an error
     *
     * @param message the message being logged
     */
    @Override
    public void error(String message) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_ERROR, prefix + message);
        } else {
            fallback.error(message);
        }
    }

    /**
     * Logs a message used as an error with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void error(String message, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_ERROR, prefix + message, cause);
        } else {
            fallback.error(message, cause);
        }
    }

    /**
     * Returns a {@link String}-based representation of this logger.
     * In this case, it is the logger's name
     *
     * @return The name of this logger
     */
    @Override
    public String toString() {
        return name;
    }
}
