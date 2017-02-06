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

package io.netty.util.internal.logging;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Creates {@link InternalLogger}s. This factory allows you to choose what logging framework Netty should use. The
 * default factory is {@link Slf4JLoggerFactory}. If SLF4J is not available, {@link Log4JLoggerFactory} is used. If
 * Log4J is not available, {@link JdkLoggerFactory} is used. You can change it to your preferred logging framework
 * before other Netty classes are loaded via {@link #setDefaultFactory(InternalLoggerFactory)}. If you want to change
 * the logger factory, {@link #setDefaultFactory(InternalLoggerFactory)} must be invoked before any other Netty classes
 * are loaded. Note that {@link #setDefaultFactory(InternalLoggerFactory)}} can not be invoked more than once.
 */
public abstract class InternalLoggerFactory {

    private static final InternalLoggerFactoryHolder HOLDER = new InternalLoggerFactoryHolder();

    /**
     * This class holds a reference to the {@link InternalLoggerFactory}. The raison d'être for this class is primarily
     * to aid in testing.
     */
    static final class InternalLoggerFactoryHolder {
        private final AtomicReference<InternalLoggerFactory> reference;

        InternalLoggerFactoryHolder() {
            this(null);
        }

        InternalLoggerFactoryHolder(final InternalLoggerFactory holder) {
            this.reference = new AtomicReference<InternalLoggerFactory>(holder);
        }

        InternalLoggerFactory getFactory() {
            if (reference.get() == null) {
                reference.compareAndSet(null, newDefaultFactory(InternalLoggerFactory.class.getName()));
            }
            return reference.get();
        }

        void setFactory(final InternalLoggerFactory factory) {
            if (factory == null) {
                throw new NullPointerException("factory");
            }
            if (!reference.compareAndSet(null, factory)) {
                throw new IllegalStateException(
                        "factory is already set to [" + reference.get() + "], rejecting [" + factory + "]");
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
    }

    @SuppressWarnings("UnusedCatchParameter")
    private static InternalLoggerFactory newDefaultFactory(String name) {
        InternalLoggerFactory f;
        try {
            f = new Slf4JLoggerFactory(true);
            f.newInstance(name).debug("Using SLF4J as the default logging framework");
        } catch (Throwable t1) {
            try {
                f = Log4JLoggerFactory.INSTANCE;
                f.newInstance(name).debug("Using Log4J as the default logging framework");
            } catch (Throwable t2) {
                f = JdkLoggerFactory.INSTANCE;
                f.newInstance(name).debug("Using java.util.logging as the default logging framework");
            }
        }
        return f;
    }

    /**
     * Get the default factory that was either initialized automatically based on logging implementations on the
     * classpath, or set explicitly via {@link #setDefaultFactory(InternalLoggerFactory)}.
     */
    public static InternalLoggerFactory getDefaultFactory() {
        return HOLDER.getFactory();
    }

    /**
     * Set the default factory. This method must be invoked before the default factory is initialized via
     * {@link #getDefaultFactory()}, and can not be invoked multiple times.
     *
     * @param defaultFactory a non-null implementation of {@link InternalLoggerFactory}
     */
    public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
        HOLDER.setFactory(defaultFactory);
    }

    /**
     * Creates a new logger instance with the name of the specified class.
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return HOLDER.getInstance(clazz);
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    public static InternalLogger getInstance(String name) {
        return HOLDER.getInstance(name);
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    protected abstract InternalLogger newInstance(String name);

}
