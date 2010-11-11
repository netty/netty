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

import org.jboss.netty.util.internal.StackTraceSimplifier;

/**
 * Creates an {@link InternalLogger} or changes the default factory
 * implementation.  This factory allows you to choose what logging framework
 * Netty should use.  The default factory is {@link JdkLoggerFactory}.
 * You can change it to your preferred logging framework before other Netty
 * classes are loaded:
 * <pre>
 * {@link InternalLoggerFactory}.setDefaultFactory(new {@link Log4JLoggerFactory}());
 * </pre>
 * Please note that the new default factory is effective only for the classes
 * which were loaded after the default factory is changed.  Therefore,
 * {@link #setDefaultFactory(InternalLoggerFactory)} should be called as early
 * as possible and shouldn't be called more than once.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
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

            public boolean isEnabled(InternalLogLevel level) {
                return logger.isEnabled(level);
            }

            public void log(InternalLogLevel level, String msg) {
                logger.log(level, msg);
            }

            public void log(InternalLogLevel level, String msg, Throwable cause) {
                StackTraceSimplifier.simplify(cause);
                logger.log(level, msg, cause);
            }
        };
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public abstract InternalLogger newInstance(String name);
}
