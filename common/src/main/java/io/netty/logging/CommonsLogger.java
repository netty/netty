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

import org.apache.commons.logging.Log;

/**
 * A logger that logs to an
 * <a href="http://commons.apache.org/logging/">Apache Commons Logging</a>
 * logger.
 */
class CommonsLogger extends AbstractInternalLogger {

    /**
     * The Commons logger being logged to
     */
    private final Log logger;

    /**
     * The name of the logger
     */
    private final String loggerName;

    /**
     * Creates a new {@link CommonsLogger}
     *
     * @param logger The Commons logger to log to
     * @param loggerName The name of the logger
     */
    CommonsLogger(Log logger, String loggerName) {
        this.logger = logger;
        this.loggerName = loggerName;
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    /**
     * Logs a message used for debugging
     *
     * @param message The message being logged
     */
    @Override
    public void debug(String message) {
        logger.debug(message);
    }

    /**
     * Logs a message used for debugging with an attached cause
     *
     * @param message The message being logged
     * @param cause The cause of this message
     */
    @Override
    public void debug(String message, Throwable cause) {
        logger.debug(message, cause);
    }

    /**
     * Logs a message used for information
     *
     * @param message The message being logged
     */
    @Override
    public void info(String message) {
        logger.info(message);
    }

    /**
     * Logs a message used for information with an attached cause
     *
     * @param message The message being logged
     * @param cause The cause of the message
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
     * @param message The message being logged
     */
    @Override
    public void error(String message) {
        logger.error(message);
    }

    /**
     * Logs a message used as an error with an attached cause
     *
     * @param message The message being logged
     * @param cause The cause of the message
     */
    @Override
    public void error(String message, Throwable cause) {
        logger.error(message, cause);
    }

    /**
     * Gets a {@link String}-based representation of this logger.
     * In this case, it is simply the logger's name
     *
     * @return The logger's name
     */
    @Override
    public String toString() {
        return loggerName;
    }
}
