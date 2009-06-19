/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.logging;

/**
 * <em>Internal-use-only</em> logger used by Netty.  <strong>DO NOT</strong>
 * access this class outside of Netty.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
