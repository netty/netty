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

import android.util.Log;

/**
 * An <a href="http://android.com">Android</a> logger
 */
public class AndroidLogger extends AbstractInternalLogger {

    /**
     * The tag of this {@link AndroidLogger}
     */
    private final String tag;

    /**
     * Creates a new {@link AndroidLogger}
     *
     * @param tag the tag of this {@link AndroidLogger}
     */
    public AndroidLogger(String tag) {
        this.tag = tag;
    }

    /**
     * Checks to see if trace messages can be logged
     *
     * @return {@code true}
     */
    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    /**
     * Checks to see if debugging messages can be logged
     *
     * @return {@code true}
     */
    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    /**
     * Checks to see if informational messages can be logged
     *
     * @return {@code true}
     */
    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    /**
     * Checks to see if warning messages can be logged
     *
     * @return {@code true}
     */
    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    /**
     * Checks to see if error messages can be logged
     *
     * @return {@code true}
     */
    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    /**
     * Logs a trace message
     *
     * @param msg the message being logged
     */
    @Override
    public void trace(String msg) {
        Log.v(tag, msg);
    }

    /**
     * Logs a trace message with an attached cause
     *
     * @param msg the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void trace(String msg, Throwable cause) {
        Log.v(tag, msg, cause);
    }

    /**
     * Logs a debug message
     *
     * @param msg the message being logged
     */
    @Override
    public void debug(String msg) {
        Log.d(tag, msg);
    }

    /**
     * Logs a debug message with an attached cause
     *
     * @param msg the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void debug(String msg, Throwable cause) {
        Log.d(tag, msg, cause);
    }

    /**
     * Logs an informational message
     *
     * @param msg the message being logged
     */
    @Override
    public void info(String msg) {
        Log.i(tag, msg);
    }

    /**
     * Logs an informational message with an attached cause
     *
     * @param msg the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void info(String msg, Throwable cause) {
        Log.i(tag, msg, cause);
    }

    /**
     * Logs a warning message
     *
     * @param msg the message being logged
     */
    @Override
    public void warn(String msg) {
        Log.w(tag, msg);
    }

    /**
     * Logs a warning message with an attached cause
     *
     * @param msg the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void warn(String msg, Throwable cause) {
        Log.w(tag, msg, cause);
    }

    /**
     * Logs an error message
     *
     * @param msg the message being logged
     */
    @Override
    public void error(String msg) {
        Log.e(tag, msg);
    }

    /**
     * Logs an error message with an attached cause
     *
     * @param msg the message being logged
     * @param cause the cause of this message
     */
    @Override
    public void error(String msg, Throwable cause) {
        Log.e(tag, msg, cause);
    }

    /**
     * Returns a {@link String}-based representation of this {@link AndroidLogger}
     * In this case, it is simply the logger's name.
     *
     * @return the logger's name
     */
    @Override
    public String toString() {
        return tag;
    }

}
