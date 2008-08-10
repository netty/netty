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
 * Logger factory which creates a <a href="http://www.slf4j.org/">SLF4J</a>
 * logger.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class Slf4JLoggerFactory extends InternalLoggerFactory {

    @Override
    public InternalLogger newInstance(String name) {
        final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(name);
        return new InternalLogger() {
            public void debug(String msg) {
                logger.debug(msg);
            }

            public void debug(String msg, Throwable cause) {
                logger.debug(msg, cause);
            }

            public void error(String msg) {
                logger.error(msg);
            }

            public void error(String msg, Throwable cause) {
                logger.error(msg, cause);
            }

            public void info(String msg) {
                logger.info(msg);
            }

            public void info(String msg, Throwable cause) {
                logger.info(msg, cause);
            }

            public boolean isDebugEnabled() {
                return logger.isDebugEnabled();
            }

            public boolean isErrorEnabled() {
                return logger.isErrorEnabled();
            }

            public boolean isInfoEnabled() {
                return logger.isInfoEnabled();
            }

            public boolean isWarnEnabled() {
                return logger.isWarnEnabled();
            }

            public void warn(String msg) {
                logger.warn(msg);
            }

            public void warn(String msg, Throwable cause) {
                logger.warn(msg, cause);
            }

            @Override
            public String toString() {
                return String.valueOf(logger.getName());
            }
        };
    }
}
