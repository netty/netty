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
package net.gleamynode.netty.logging;

import java.util.logging.Level;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class JdkLoggerFactory extends LoggerFactory {

    @Override
    public Logger getLogger(String name) {
        final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(name);
        return new Logger() {
            @Override
            public void debug(String msg) {
                logger.log(Level.FINE, msg);
            }

            @Override
            public void debug(String msg, Throwable cause) {
                logger.log(Level.FINE, msg, cause);
            }

            @Override
            public void error(String msg) {
                logger.log(Level.SEVERE, msg);
            }

            @Override
            public void error(String msg, Throwable cause) {
                logger.log(Level.SEVERE, msg, cause);
            }

            @Override
            public void info(String msg) {
                logger.log(Level.INFO, msg);
            }

            @Override
            public void info(String msg, Throwable cause) {
                logger.log(Level.INFO, msg, cause);
            }

            @Override
            public boolean isDebugEnabled() {
                return logger.isLoggable(Level.FINE);
            }

            @Override
            public boolean isErrorEnabled() {
                return logger.isLoggable(Level.SEVERE);
            }

            @Override
            public boolean isInfoEnabled() {
                return logger.isLoggable(Level.INFO);
            }

            @Override
            public boolean isWarnEnabled() {
                return logger.isLoggable(Level.WARNING);
            }

            @Override
            public void warn(String msg) {
                logger.log(Level.WARNING, msg);
            }

            @Override
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
