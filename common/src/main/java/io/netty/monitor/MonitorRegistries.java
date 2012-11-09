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

    /**
     * Return <em>the</em> singleton {@code MonitorRegistries} instance.
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
     * @param provider The {@link MonitorProvider provider} we are interested in
     * @return A {@link MonitorRegistry} implemented by the supplied
     *         {@link MonitorProvider provider}
     * @throws NullPointerException If {@code provider} is {@code null}
     * @throws IllegalArgumentException If no {@code MonitorRegistry} matching
     *             the given {@link MonitorProvider provider} could be found
     */
    public MonitorRegistry forProvider(final MonitorProvider provider) {
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
     * Look up and return <em>the</em> uniquely determined
     * {@link MonitorRegistry} implementation. This method will work in the
     * standard situation where exactly one {@link MonitorRegistryFactory} is
     * registered in
     * {@code META-INF/services/io.netty.monitor.spi.MonitorRegistryFactory}.
     * Otherwise, if either none or more than one such provider is found on the
     * classpath, it will throw an {@code IllegalStateException}.
     * </p>
     * @return <em>The</em> uniquely determined {@link MonitorRegistry}
     *         implementation
     * @throws IllegalStateException If either none or more that one
     *             {@link MonitorRegistries} provider was found on the
     *             classpath
     */
    public MonitorRegistry unique() {
        final Iterator<MonitorRegistry> registries = iterator();
        if (!registries.hasNext()) {
            throw new IllegalStateException("Could not find any MonitorRegistries the classpath - "
                    + "implementations need to be registered in META-INF/services/"
                    + MonitorRegistryFactory.class.getName());
        }
        final MonitorRegistry candidate = registries.next();
        if (registries.hasNext()) {
            throw new IllegalStateException("Found more than one MonitorRegistryFactory on the classpath - "
                    + "check if there is more than one implementation registered in META-INF/services/"
                    + MonitorRegistryFactory.class.getName());
        }
        return candidate;
    }

    /**
     * @see java.lang.Iterable#iterator()
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
