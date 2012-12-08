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
package io.netty.monitor;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.monitor.spi.MonitorProvider;
import io.netty.monitor.spi.MonitorRegistryFactory;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * <p>
 * Represents all {@link MonitorRegistry MonitorRegistries} that can be
 * constructed by {@link MonitorRegistryFactory MonitorRegistryFactories} loaded
 * using Java 6's {@link ServiceLoader}.
 * </p>
 * <p>
 * A {@code MonitorRegistryFactory} that wishes to contribute a
 * {@code MonitorRegistry} via {@code MonitorRegistries} needs to
 * <ol>
 * <li>have a no-args default constructor, and</li>
 * <li>register itself - its fully qualified class name - in
 * {@code META-INF/services/io.netty.monitor.spi.MonitorRegistryFactory}.</li>
 * </ol>
 * </p>
 */
public final class MonitorRegistries implements Iterable<MonitorRegistry> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MonitorRegistries.class);
    //set of initialization states
    private static final int UNINITIALIZED = 0;
    private static final int ONGOING_INITIALIZATION = 1;
    private static final int SUCCESSFUL_INITIALIZATION = 2;
    private static final int NOP_FALLBACK_INITIALIZATION = 3;

    private static int INITIALIZATION_STATE = UNINITIALIZED;
    private static MonitorRegistry selectedRegistry;

    /**
     * Return <em>the</em> singleton {@code MonitorRegistries} instance.
     *
     * @return <em>The</em> singleton {@code MonitorRegistries} instance
     */
    public static MonitorRegistries instance() {
        return Holder.INSTANCE;
    }

    private interface Holder {
        MonitorRegistries INSTANCE = new MonitorRegistries();
    }

    private static final ServiceLoader<MonitorRegistryFactory> FACTORIES = ServiceLoader
            .load(MonitorRegistryFactory.class);

    /**
     * Create a new {@link MonitorRegistry} that supports the supplied
     * {@link MonitorProvider provider}.
     *
     * @param provider The {@link MonitorProvider provider} we are interested in
     * @return A {@link MonitorRegistry} implemented by the supplied
     *         {@link MonitorProvider provider}
     * @throws NullPointerException     If {@code provider} is {@code null}
     * @throws IllegalArgumentException If no {@code MonitorRegistry} matching
     *                                  the given {@link MonitorProvider provider} could be found
     */
    public static MonitorRegistry forProvider(final MonitorProvider provider) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        for (final MonitorRegistryFactory candidate : FACTORIES) {
            if (candidate.provider().equals(provider)) {
                return candidate.newMonitorRegistry();
            }
        }
        throw new IllegalArgumentException("Could not find MonitorRegistryFactory by provider " + provider
                + " among the set of registered MonitorRegistryFactories");
    }

    /**
     * <p>
     * Look up and return <em>the</em> a uniquely determined
     * {@link MonitorRegistry} implementation. This method will select
     * exactly one {@link MonitorRegistryFactory} from those registered in
     * {@code META-INF/services/io.netty.monitor.spi.MonitorRegistryFactory}.
     * if no implementation is found then a NOOP registry is returned.
     * If multiple implementations are found then the first one returned by
     * {@link #iterator()} is used and a message is logged to say which one is
     * selected.
     * </p>
     *
     * @return <em>the</em> uniquely determined {@link MonitorRegistry}
     *         implementation
     */
    public MonitorRegistry unique() {
        //Implementation based on SLF4J's
        if (INITIALIZATION_STATE == UNINITIALIZED) {
            INITIALIZATION_STATE = ONGOING_INITIALIZATION;
            performInitialization();
        }
        switch (INITIALIZATION_STATE) {
            case SUCCESSFUL_INITIALIZATION:
                return selectedRegistry;
            case NOP_FALLBACK_INITIALIZATION:
            case ONGOING_INITIALIZATION:
            default:
                return MonitorRegistry.NOOP;
        }
    }

    private void performInitialization() {
        final Iterator<MonitorRegistry> registries = iterator();
        if (registries.hasNext()) {
            selectedRegistry = registries.next();
            INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
        }
        if (selectedRegistry != null && registries.hasNext()) {
            logger.warn(String.format("Multiple metrics implementations found. " +
                    "Selected %s, ignoring other implementations", selectedRegistry.getClass().getName()));
        }
        if (selectedRegistry == null) {
            INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION;
            logger.debug("No metrics implementation found on the classpath.");
        }
    }

    /**
     * @see Iterable#iterator()
     */
    @Override
    public Iterator<MonitorRegistry> iterator() {
        return new MonitorRegistryIterator(FACTORIES.iterator());
    }

    private static final class MonitorRegistryIterator implements Iterator<MonitorRegistry> {

        private final Iterator<MonitorRegistryFactory> factories;

        private MonitorRegistryIterator(final Iterator<MonitorRegistryFactory> factories) {
            this.factories = factories;
        }

        @Override
        public boolean hasNext() {
            return factories.hasNext();
        }

        @Override
        public MonitorRegistry next() {
            return factories.next().newMonitorRegistry();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Removing a MonitorRegistry is not supported");
        }
    }

    private MonitorRegistries() {
        // Singleton
    }
}
