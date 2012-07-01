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

import org.apache.log4j.Logger;

/**
 * A logger that logs messages to an
 * <a href="http://logging.apache.org/log4j/1.2/index.html">Apache Log4J</a>
 * logger.
 */
class Log4JLogger extends AbstractInternalLogger {

    /**
     * The {@link Logger} to log messages to
     */
    private final Logger logger;

    /**
     * Creates a new {@link Log4JLogger} with the specified external {@link Logger}
     *
     * @param logger The {@link Logger} to log messages to
     */
    Log4JLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return true if messages are allowed, otherwise false
     */
    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return true, since all warnings are allowed
     */
    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return true, since all errors are allowed
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
        logger.debug(message);
    }

    /**
     * Logs a message used for debugging with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void debug(String message, Throwable cause) {
        logger.debug(message, cause);
    }

    /**
     * Logs a message used for information
     *
     * @param message the message being logged
     */
    @Override
    public void info(String message) {
        logger.info(message);
    }

    /**
     * Logs a message used for information with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void info(String message, Throwable cause) {
        logger.info(message, cause);
    }

    /**
     * Logs a message used as a warning
     *
     * @param message the message being logged
     */
    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    /**
     * Logs a message used as a warning with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void warn(String message, Throwable cause) {
        logger.warn(message, cause);
    }

    /**
     * Logs a message used as an error
     *
     * @param message the message being logged
     */
    @Override
    public void error(String message) {
        logger.error(message);
    }

    /**
     * Logs a message used as an error with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void error(String message, Throwable cause) {
        logger.error(message, cause);
    }

    /**
     * Returns a {@link String}-based representation of this {@link Log4JLogger}.
     * In this case, it is the logger's name
     *
     * @return this logger's name
     */
    @Override
    public String toString() {
        return String.valueOf(logger.getName());
    }
}
