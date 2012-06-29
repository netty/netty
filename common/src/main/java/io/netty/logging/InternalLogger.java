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

/**
 * An <em>internal use only</em> logger used by Netty.
 * <strong>DO NOT</strong> use this class outside of Netty!
 */
public interface InternalLogger {

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return True if able to be logged, otherwise false
     */
    boolean isDebugEnabled();

    /**
     * Checks to see if informational messages can be logged
     *
     * @return True if able to be logged, otherwise false
     */
    boolean isInfoEnabled();

    /**
     * Checks to see if warnings can be logged
     *
     * @return True if able to be logged, otherwise false
     */
    boolean isWarnEnabled();

    /**
     * Checks to see if error messages can be logged
     *
     * @return True if able to be logged, otherwise false
     */
    boolean isErrorEnabled();

    /**
     * Checks to see if a specified {@link InternalLogLevel} can be logged
     *
     * @return True if able to be logged, otherwise false
     */
    boolean isEnabled(InternalLogLevel level);

    /**
     * Logs a message used for debugging
     *
     * @param message The message to log
     */
    void debug(String message);

    /**
     * Logs a message used for debugging with an attached cause
     *
     * @param message The message to log
     * @param cause The cause of the message
     */
    void debug(String message, Throwable cause);

    /**
     * Logs a message used for information
     *
     * @param message The message to log
     */
    void info(String message);

    /**
     * Logs a message used for information with an attached cause
     *
     * @param message The message to log
     * @param cause The cause of the message
     */
    void info(String message, Throwable cause);

    /**
     * Logs a message used as a warning
     *
     * @param message The message to log
     */
    void warn(String message);

    /**
     * Logs a message used as a warning with an attached cause
     *
     * @param message The message to log
     * @param cause The cause of the message
     */
    void warn(String message, Throwable cause);

    /**
     * Logs a message used as an error
     *
     * @param message The message to log
     */
    void error(String message);

    /**
     * Logs a message used as an error with an attached cause
     *
     * @param message The message to log
     * @param cause The cause of the message
     */
    void error(String message, Throwable cause);

    /**
     * Logs a message
     *
     * @param level The {@link InternalLogLevel} to use
     * @param message The message to log
     */
    void log(InternalLogLevel level, String message);

    /**
     * Logs a message with an attached cause
     *
     * @param level The {@link InternalLogLevel} to use
     * @param message The message to log
     * @param cause The cause of the message
     */
    void log(InternalLogLevel level, String message, Throwable cause);
}
