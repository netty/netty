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
package org.jboss.netty.util;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 * Logger factory which suppresses all log messages.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class SilentLoggerFactory extends InternalLoggerFactory {

    @Override
    public InternalLogger newInstance(final String name) {
        return new InternalLogger() {
            public void debug(String msg) {
                // NOOP
            }

            public void debug(String msg, Throwable cause) {
                // NOOP
            }

            public void error(String msg) {
                // NOOP
            }

            public void error(String msg, Throwable cause) {
                // NOOP
            }

            public void info(String msg) {
                // NOOP
            }

            public void info(String msg, Throwable cause) {
                // NOOP
            }

            public boolean isDebugEnabled() {
                return true;
            }

            public boolean isErrorEnabled() {
                return true;
            }

            public boolean isInfoEnabled() {
                return true;
            }

            public boolean isWarnEnabled() {
                return true;
            }

            public void warn(String msg) {
                // NOOP
            }

            public void warn(String msg, Throwable cause) {
                // NOOP
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
