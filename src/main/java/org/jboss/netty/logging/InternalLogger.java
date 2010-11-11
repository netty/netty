/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.logging;

/**
 * <em>Internal-use-only</em> logger used by Netty.  <strong>DO NOT</strong>
 * access this class outside of Netty.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface InternalLogger {
    /**
     * Returns {@code true} if a DEBUG level message is logged.
     */
    boolean isDebugEnabled();

    /**
     * Returns {@code true} if an INFO level message is logged.
     */
    boolean isInfoEnabled();

    /**
     * Returns {@code true} if a WARN level message is logged.
     */
    boolean isWarnEnabled();

    /**
     * Returns {@code true} if an ERROR level message is logged.
     */
    boolean isErrorEnabled();

    /**
     * Returns {@code true} if the specified log level message is logged.
     */
    boolean isEnabled(InternalLogLevel level);

    /**
     * Logs a DEBUG level message.
     */
    void debug(String msg);

    /**
     * Logs a DEBUG level message.
     */
    void debug(String msg, Throwable cause);

    /**
     * Logs an INFO level message.
     */
    void info(String msg);

    /**
     * Logs an INFO level message.
     */
    void info(String msg, Throwable cause);

    /**
     * Logs a WARN level message.
     */
    void warn(String msg);

    /**
     * Logs a WARN level message.
     */
    void warn(String msg, Throwable cause);

    /**
     * Logs an ERROR level message.
     */
    void error(String msg);

    /**
     * Logs an ERROR level message.
     */
    void error(String msg, Throwable cause);

    /**
     * Logs a message.
     */
    void log(InternalLogLevel level, String msg);

    /**
     * Logs a message.
     */
    void log(InternalLogLevel level, String msg, Throwable cause);
}
