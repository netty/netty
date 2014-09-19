/*
 * Copyright 2014 The Netty Project
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

package io.netty.resolver;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.SocketAddress;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates and manages {@link NameResolver}s so that each {@link EventExecutor} has its own resolver instance.
 */
public abstract class NameResolverGroup<T extends SocketAddress> implements Closeable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NameResolverGroup.class);

    /**
     * Note that we do not use a {@link ConcurrentMap} here because it is usually expensive to instantiate a resolver.
     */
    private final Map<EventExecutor, NameResolver<T>> resolvers =
            new IdentityHashMap<EventExecutor, NameResolver<T>>();

    protected NameResolverGroup() { }

    /**
     * Returns the {@link NameResolver} associated with the specified {@link EventExecutor}.  If there's no associated
     * resolved found, this method creates and returns a new resolver instance created by
     * {@link #newResolver(EventExecutor)} so that the new resolver is reused on another
     * {@link #getResolver(EventExecutor)} call with the same {@link EventExecutor}.
     */
    public NameResolver<T> getResolver(final EventExecutor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }

        if (executor.isShuttingDown()) {
            throw new IllegalStateException("executor not accepting a task");
        }

        NameResolver<T> r;
        synchronized (resolvers) {
            r = resolvers.get(executor);
            if (r == null) {
                final NameResolver<T> newResolver;
                try {
                    newResolver = newResolver(executor);
                } catch (Exception e) {
                    throw new IllegalStateException("failed to create a new resolver", e);
                }

                resolvers.put(executor, newResolver);
                executor.terminationFuture().addListener(new FutureListener<Object>() {
                    @Override
                    public void operationComplete(Future<Object> future) throws Exception {
                        resolvers.remove(executor);
                        newResolver.close();
                    }
                });

                r = newResolver;
            }
        }

        return r;
    }

    /**
     * Invoked by {@link #getResolver(EventExecutor)} to create a new {@link NameResolver}.
     */
    protected abstract NameResolver<T> newResolver(EventExecutor executor) throws Exception;

    /**
     * Closes all {@link NameResolver}s created by this group.
     */
    @Override
    @SuppressWarnings({ "unchecked", "SuspiciousToArrayCall" })
    public void close() {
        final NameResolver<T>[] rArray;
        synchronized (resolvers) {
            rArray = (NameResolver<T>[]) resolvers.values().toArray(new NameResolver[resolvers.size()]);
            resolvers.clear();
        }

        for (NameResolver<T> r: rArray) {
            try {
                r.close();
            } catch (Throwable t) {
                logger.warn("Failed to close a resolver:", t);
            }
        }
    }
}
