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
 * @apiviz.landmark
 * @apiviz.has io.netty.logging.InternalLogger oneway - - creates
 */
public abstract class InternalLoggerFactory {
    private static volatile InternalLoggerFactory defaultFactory = new JdkLoggerFactory();

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

            @Override
            public void debug(String msg) {
                logger.debug(msg);
            }

            @Override
            public void debug(String msg, Throwable cause) {
                logger.debug(msg, cause);
            }

            @Override
            public void error(String msg) {
                logger.error(msg);
            }

            @Override
            public void error(String msg, Throwable cause) {
                logger.error(msg, cause);
            }

            @Override
            public void info(String msg) {
                logger.info(msg);
            }

            @Override
            public void info(String msg, Throwable cause) {
                logger.info(msg, cause);
            }

            @Override
            public boolean isDebugEnabled() {
                return logger.isDebugEnabled();
            }

            @Override
            public boolean isErrorEnabled() {
                return logger.isErrorEnabled();
            }

            @Override
            public boolean isInfoEnabled() {
                return logger.isInfoEnabled();
            }

            @Override
            public boolean isWarnEnabled() {
                return logger.isWarnEnabled();
            }

            @Override
            public void warn(String msg) {
                logger.warn(msg);
            }

            @Override
            public void warn(String msg, Throwable cause) {
                logger.warn(msg, cause);
            }

            @Override
            public boolean isEnabled(InternalLogLevel level) {
                return logger.isEnabled(level);
            }

            @Override
            public void log(InternalLogLevel level, String msg) {
                logger.log(level, msg);
            }

            @Override
            public void log(InternalLogLevel level, String msg, Throwable cause) {
                logger.log(level, msg, cause);
            }
        };
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public abstract InternalLogger newInstance(String name);
}
