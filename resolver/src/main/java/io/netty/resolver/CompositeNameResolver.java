/*
 * Copyright 2015 The Netty Project
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
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;

import java.util.Arrays;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A composite {@link SimpleNameResolver} that resolves a host name against a sequence of {@link NameResolver}s.
 *
 * In case of a failure, only the last one will be reported.
 */
public final class CompositeNameResolver<T> extends SimpleNameResolver<T> {

    private final NameResolver<T>[] resolvers;

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned
     *                 by {@link #resolve(String)}
     * @param resolvers the {@link NameResolver}s to be tried sequentially
     */
    public CompositeNameResolver(EventExecutor executor, NameResolver<T>... resolvers) {
        super(executor);
        checkNotNull(resolvers, "resolvers");
        for (int i = 0; i < resolvers.length; i++) {
            ObjectUtil.checkNotNull(resolvers[i], "resolvers[" + i + ']');
        }
        if (resolvers.length < 2) {
            throw new IllegalArgumentException("resolvers: " + Arrays.asList(resolvers) +
                    " (expected: at least 2 resolvers)");
        }
        this.resolvers = resolvers.clone();
    }

    @Override
    protected void doResolve(String inetHost, Promise<T> promise) throws Exception {
        doResolveRec(inetHost, promise, 0, null);
    }

    private void doResolveRec(final String inetHost,
                              final Promise<T> promise,
                              final int resolverIndex,
                              Throwable lastFailure) throws Exception {
        if (resolverIndex >= resolvers.length) {
            promise.setFailure(lastFailure);
        } else {
            NameResolver<T> resolver = resolvers[resolverIndex];
            resolver.resolve(inetHost).addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    if (future.isSuccess()) {
                        promise.setSuccess(future.getNow());
                    } else {
                        doResolveRec(inetHost, promise, resolverIndex + 1, future.cause());
                    }
                }
            });
        }
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<T>> promise) throws Exception {
        doResolveAllRec(inetHost, promise, 0, null);
    }

    private void doResolveAllRec(final String inetHost,
                              final Promise<List<T>> promise,
                              final int resolverIndex,
                              Throwable lastFailure) throws Exception {
        if (resolverIndex >= resolvers.length) {
            promise.setFailure(lastFailure);
        } else {
            NameResolver<T> resolver = resolvers[resolverIndex];
            resolver.resolveAll(inetHost).addListener(new FutureListener<List<T>>() {
                @Override
                public void operationComplete(Future<List<T>> future) throws Exception {
                    if (future.isSuccess()) {
                        promise.setSuccess(future.getNow());
                    } else {
                        doResolveAllRec(inetHost, promise, resolverIndex + 1, future.cause());
                    }
                }
            });
        }
    }
}
