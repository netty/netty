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

import org.jboss.logging.Logger;

/**
 * A logger that logs to a
 * <a href="http://anonsvn.jboss.org/repos/common/common-logging-spi/">JBoss Logging</a>
 * logger.
 */
class JBossLogger extends AbstractInternalLogger {

    /**
     * The JBoss logger to log messages to
     */
    private final Logger logger;

    /**
     * Creates a new {@link JBossLogger}
     *
     * @param logger the JBoss logger to log messages to
     */
    JBossLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return true if messages can be logged, otherwise false
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return true, since warning messages are always able to be logged
     */
    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return true, because error messages are always able to be logged
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
     * @param message The message being logged
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
     * Logs a message as an error
     *
     * @param message the message being logged
     */
    @Override
    public void error(String message) {
        logger.error(message);
    }

    /**
     * Logs a message as an error with an attached cause
     *
     * @param message the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void error(String message, Throwable cause) {
        logger.error(message, cause);
    }

    /**
     * Gets a {@link String}-based representation of this {@link JBossLogger}.
     * In this case, it is the logger's name
     *
     * @return The logger's name
     */
    @Override
    public String toString() {
        return String.valueOf(logger.getName());
    }
}
