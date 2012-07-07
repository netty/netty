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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A logger that logs messages to a
 * <a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * logger.
 */
class JdkLogger extends AbstractInternalLogger {

    /**
     * The JDK {@link Logger} to log messages to
     */
    private final Logger logger;

    /**
     * The name of this {@link JdkLogger}
     */
    private final String loggerName;

    /**
     * Creates a new {@link JdkLogger}
     *
     * @param logger the {@link Logger} to log messages to
     * @param loggerName The name of this {@link JdkLogger}
     */
    JdkLogger(Logger logger, String loggerName) {
        this.logger = logger;
        this.loggerName = loggerName;
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    /**
     * Logs a message used for debugging
     *
     * @param message the message being logged
     */
    @Override
    public void debug(String message) {
        logger.logp(Level.FINE, loggerName, null, message);
    }

    /**
     * Logs a message used for debugging with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void debug(String message, Throwable cause) {
        logger.logp(Level.FINE, loggerName, null, message, cause);
    }

    /**
     * Logs a message used for information
     *
     * @param message the message being logged
     */
    @Override
    public void info(String message) {
        logger.logp(Level.INFO, loggerName, null, message);
    }

    /**
     * Logs a message used for information with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void info(String message, Throwable cause) {
        logger.logp(Level.INFO, loggerName, null, message, cause);
    }

    /**
     * Logs a message used as a warning
     *
     * @param message the message being logged
     */
    @Override
    public void warn(String message) {
        logger.logp(Level.WARNING, loggerName, null, message);
    }

    /**
     * Logs a message used as a warning with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void warn(String message, Throwable cause) {
        logger.logp(Level.WARNING, loggerName, null, message, cause);
    }

    /**
     * Logs a message used as an error
     *
     * @param message the message being logged
     */
    @Override
    public void error(String message) {
        logger.logp(Level.SEVERE, loggerName, null, message);
    }

    /**
     * Logs a message used as an error with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void error(String message, Throwable cause) {
        logger.logp(Level.SEVERE, loggerName, null, message, cause);
    }

    /**
     * Returns a {@link String}-based representation of this {@link JdkLogger}.
     * In this case, it is the name of the logger
     *
     * @return The name of this logger
     */
    @Override
    public String toString() {
        return loggerName;
    }
}
