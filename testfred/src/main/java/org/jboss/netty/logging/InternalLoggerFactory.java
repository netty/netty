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

import org.jboss.netty.util.StackTraceSimplifier;

/**
 * Creates an {@link InternalLogger} or changes the default factory
 * implementation.  This factory allows you to choose what logging framework
 * Netty should use.  The default factory is {@link JdkLoggerFactory}.
 * You can change it to your preferred logging framework before other Netty
 * classes are loaded:
 * <pre>
 * InternalLoggerFactory.setDefaultFactory(new {@link Log4JLoggerFactory}());
 * </pre>
 * Please note that the new default factory is effective only for the classes
 * which were loaded after the default factory is changed.  Therefore,
 * {@link #setDefaultFactory(InternalLoggerFactory)} should be called as early
 * as possible and shouldn't be called more than once.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.logging.InternalLogger oneway - - creates
 */
public abstract class InternalLoggerFactory {
    private static volatile InternalLoggerFactory defaultFactory = new JdkLoggerFactory();

    static {
        // Load the dependent classes in advance to avoid the case where
        // the VM fails to load the required classes because of too many open
        // files.
        StackTraceSimplifier.simplify(new Exception());
    }

    /**
     * Returns the default factory.  The initial default factory is
     * {@link JdkLoggerFactory}.
     */
    public static InternalLoggerFactory getDefaultFactory() {
        return defaultFactory;
    }

    /**
     * Changes the default factory.
     */
    public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
        if (defaultFactory == null) {
            throw new NullPointerException("defaultFactory");
        }
        InternalLoggerFactory.defaultFactory = defaultFactory;
    }

    /**
     * Creates a new logger instance with the name of the specified class.
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return getInstance(clazz.getName());
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public static InternalLogger getInstance(String name) {
        final InternalLogger logger = getDefaultFactory().newInstance(name);
        return new InternalLogger() {

            public void debug(String msg) {
                logger.debug(msg);
            }

            public void debug(String msg, Throwable cause) {
                StackTraceSimplifier.simplify(cause);
                logger.debug(msg, cause);
            }

            public void error(String msg) {
                logger.error(msg);
            }

            public void error(String msg, Throwable cause) {
                StackTraceSimplifier.simplify(cause);
                logger.error(msg, cause);
            }

            public void info(String msg) {
                logger.info(msg);
            }

            public void info(String msg, Throwable cause) {
                StackTraceSimplifier.simplify(cause);
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
                StackTraceSimplifier.simplify(cause);
                logger.warn(msg, cause);
            }
        };
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public abstract InternalLogger newInstance(String name);
}
