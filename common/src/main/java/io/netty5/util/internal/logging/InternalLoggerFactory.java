/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.util.internal.logging;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Creates {@link InternalLogger}s. This factory allows you to choose what logging framework Netty should use. The
 * default factory is {@link Slf4JLoggerFactory}. If SLF4J is not available, {@link Log4J2LoggerFactory} is used. If
 * Log4J is not available, {@link JdkLoggerFactory} is used. You can change it to your preferred logging framework
 * before other Netty classes are loaded via {@link #setDefaultFactory(InternalLoggerFactory)}. If you want to change
 * the logger factory, {@link #setDefaultFactory(InternalLoggerFactory)} must be invoked before any other Netty classes
 * are loaded.
 * <strong>Note that {@link #setDefaultFactory(InternalLoggerFactory)}} can not be invoked more than once.</strong>
 */
public abstract class InternalLoggerFactory {
    /**
     * This class holds a reference to the {@link InternalLoggerFactory}. The raison d'être for this class is primarily
     * to aid in testing.
     */
    static final class InternalLoggerFactoryHolder {
        static final InternalLoggerFactoryHolder HOLDER = new InternalLoggerFactoryHolder();

        private final AtomicReference<InternalLoggerFactory> reference;

        InternalLoggerFactoryHolder() {
            reference = new AtomicReference<>();
        }

        InternalLoggerFactoryHolder(final InternalLoggerFactory delegate) {
            reference = new AtomicReference<>(delegate);
        }

        InternalLoggerFactory getFactory() {
            InternalLoggerFactory factory = reference.get();
            if (factory == null) {
                factory = newDefaultFactory(InternalLoggerFactory.class.getName());
                if (!reference.compareAndSet(null, factory)) {
                    factory = reference.get();
                }
            }
            return factory;
        }

        void setFactory(final InternalLoggerFactory factory) {
            requireNonNull(factory, "factory");
            if (!reference.compareAndSet(null, factory)) {
                throw new IllegalStateException(
                        "factory is already set to [" + reference + "], rejecting [" + factory + ']');
            }
        }

        InternalLogger getInstance(final Class<?> clazz) {
            return getInstance(clazz.getName());
        }

        InternalLogger getInstance(final String name) {
            return newInstance(name);
        }

        InternalLogger newInstance(String name) {
            return getFactory().newInstance(name);
        }

        private static InternalLoggerFactory newDefaultFactory(String name) {
            InternalLoggerFactory f = useSlf4JLoggerFactory(name);
            if (f != null) {
                return f;
            }

            f = useLog4J2LoggerFactory(name);
            if (f != null) {
                return f;
            }

            return useJdkLoggerFactory(name);
        }

        private static InternalLoggerFactory useSlf4JLoggerFactory(String name) {
            try {
                InternalLoggerFactory f = Slf4JLoggerFactory.getInstanceWithNopCheck();
                f.newInstance(name).debug("Using SLF4J as the default logging framework");
                return f;
            } catch (LinkageError | Exception ignore) {
                return null;
            }
        }

        private static InternalLoggerFactory useLog4J2LoggerFactory(String name) {
            try {
                InternalLoggerFactory f = Log4J2LoggerFactory.INSTANCE;
                f.newInstance(name).debug("Using Log4J2 as the default logging framework");
                return f;
            } catch (LinkageError | Exception ignore) {
                return null;
            }
        }

        private static InternalLoggerFactory useJdkLoggerFactory(String name) {
            InternalLoggerFactory f = JdkLoggerFactory.INSTANCE;
            f.newInstance(name).debug("Using java.util.logging as the default logging framework");
            return f;
        }
    }

    /**
     * Get the default factory that was either initialized automatically based on logging implementations on the
     * classpath, or set explicitly via {@link #setDefaultFactory(InternalLoggerFactory)}.
     */
    public static InternalLoggerFactory getDefaultFactory() {
        return InternalLoggerFactoryHolder.HOLDER.getFactory();
    }

    /**
     * Set the default factory. This method must be invoked before the default factory is initialized via
     * {@link #getDefaultFactory()}, and can not be invoked multiple times.
     *
     * @param defaultFactory a non-null implementation of {@link InternalLoggerFactory}
     */
    public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
        requireNonNull(defaultFactory, "defaultFactory");
        InternalLoggerFactoryHolder.HOLDER.setFactory(defaultFactory);
    }

    /**
     * Creates a new logger instance with the name of the specified class.
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return InternalLoggerFactoryHolder.HOLDER.getInstance(clazz);
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public static InternalLogger getInstance(String name) {
        return InternalLoggerFactoryHolder.HOLDER.getInstance(name);
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    protected abstract InternalLogger newInstance(String name);
}
