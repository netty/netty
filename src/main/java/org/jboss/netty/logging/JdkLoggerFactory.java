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

import java.util.logging.Level;

/**
 * Logger factory which creates a
 * <a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * logger.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class JdkLoggerFactory extends InternalLoggerFactory {

    @Override
    public InternalLogger newInstance(String name) {
        final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(name);
        return new InternalLogger() {
            public void debug(String msg) {
                logger.log(Level.FINE, msg);
            }

            public void debug(String msg, Throwable cause) {
                logger.log(Level.FINE, msg, cause);
            }

            public void error(String msg) {
                logger.log(Level.SEVERE, msg);
            }

            public void error(String msg, Throwable cause) {
                logger.log(Level.SEVERE, msg, cause);
            }

            public void info(String msg) {
                logger.log(Level.INFO, msg);
            }

            public void info(String msg, Throwable cause) {
                logger.log(Level.INFO, msg, cause);
            }

            public boolean isDebugEnabled() {
                return logger.isLoggable(Level.FINE);
            }

            public boolean isErrorEnabled() {
                return logger.isLoggable(Level.SEVERE);
            }

            public boolean isInfoEnabled() {
                return logger.isLoggable(Level.INFO);
            }

            public boolean isWarnEnabled() {
                return logger.isLoggable(Level.WARNING);
            }

            public void warn(String msg) {
                logger.log(Level.WARNING, msg);
            }

            public void warn(String msg, Throwable cause) {
                logger.log(Level.WARNING, msg, cause);
            }

            @Override
            public String toString() {
                return String.valueOf(logger.getName());
            }
        };
    }
}
