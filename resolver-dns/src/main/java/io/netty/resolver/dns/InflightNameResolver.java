/*
 * Copyright 2016 The Netty Project
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

package io.netty.resolver.dns;

import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

// FIXME(trustin): Find a better name and move it to the 'resolver' module.
final class InflightNameResolver<T> implements NameResolver<T> {

    private final EventExecutor executor;
    private final NameResolver<T> delegate;
    private final ConcurrentMap<String, InflightEntry<T>> resolvesInProgress;
    private final ConcurrentMap<String, InflightEntry<List<T>>> resolveAllsInProgress;

    /**
     * Set to {@code true} by {@link #close()}. No new resolution is started once this is set, and
     * any in-flight entry that was started by this resolver is aborted.
     */
    private volatile boolean closed;

    InflightNameResolver(EventExecutor executor, NameResolver<T> delegate,
                         ConcurrentMap<String, InflightEntry<T>> resolvesInProgress,
                         ConcurrentMap<String, InflightEntry<List<T>>> resolveAllsInProgress) {

        this.executor = checkNotNull(executor, "executor");
        this.delegate = checkNotNull(delegate, "delegate");
        this.resolvesInProgress = checkNotNull(resolvesInProgress, "resolvesInProgress");
        this.resolveAllsInProgress = checkNotNull(resolveAllsInProgress, "resolveAllsInProgress");
    }

    @Override
    public Future<T> resolve(String inetHost) {
        return resolve(inetHost, executor.<T>newPromise());
    }

    @Override
    public Future<List<T>> resolveAll(String inetHost) {
        return resolveAll(inetHost, executor.<List<T>>newPromise());
    }

    @Override
    public void close() {
        closed = true;
        sweep(resolvesInProgress);
        sweep(resolveAllsInProgress);
        delegate.close();
    }

    @Override
    public Promise<T> resolve(String inetHost, Promise<T> promise) {
        return resolve(resolvesInProgress, inetHost, promise, false);
    }

    @Override
    public Promise<List<T>> resolveAll(String inetHost, Promise<List<T>> promise) {
        return resolve(resolveAllsInProgress, inetHost, promise, true);
    }

    /**
     * Removes and aborts all the entries of {@code map} that were started by this resolver.
     * <p>
     * Every {@link InflightNameResolver} is bound to exactly one {@link EventExecutor}, and
     * {@link io.netty.resolver.AddressResolverGroup} closes it when that executor terminates.
     * Aborting the entries
     * here releases the map slots of resolves that can no longer complete because the executor
     * which was supposed to complete them is gone. See
     * <a href="https://github.com/netty/netty/issues/17039">#17039</a>.
     * <p>
     * The map is shared by all the resolvers of an {@link io.netty.resolver.AddressResolverGroup},
     * hence the
     * {@link InflightEntry#owner} check: entries started by another resolver stay untouched.
     */
    private <U> void sweep(ConcurrentMap<String, InflightEntry<U>> map) {
        for (Map.Entry<String, InflightEntry<U>> e : map.entrySet()) {
            final InflightEntry<U> entry = e.getValue();
            if (entry.owner == this && map.remove(e.getKey(), entry)) {
                entry.abort();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <U> Promise<U> resolve(
            final ConcurrentMap<String, InflightEntry<U>> resolveMap,
            final String inetHost, final Promise<U> promise, final boolean resolveAll) {

        for (;;) {
            if (closed) {
                promise.cancel(false);
                return promise;
            }

            final InflightEntry<U> newEntry = new InflightEntry<U>(this, promise);
            final InflightEntry<U> existing = resolveMap.putIfAbsent(inetHost, newEntry);

            if (existing != null) {
                // A resolution for this hostname is already in progress, attach to it.
                switch (existing.attach(promise)) {
                    case ATTACHED:
                        return promise;
                    case COMPLETED:
                        transferResult(existing.promise, promise);
                        return promise;
                    default:
                        // The entry was aborted (its owner was closed) in the window between our
                        // putIfAbsent() and attach(). Drop the stale slot and retry as the new
                        // first caller so that a fresh resolution is started.
                        resolveMap.remove(inetHost, existing);
                        continue;
                }
            }

            // We are the first caller for this hostname, so we drive the actual resolution.
            if (closed) {
                // close() completed after our putIfAbsent() and therefore did not observe this
                // entry yet. Clean it up ourselves instead of starting a resolution that can no
                // longer be completed.
                resolveMap.remove(inetHost, newEntry);
                promise.cancel(false);
                return promise;
            }

            try {
                if (resolveAll) {
                    delegate.resolveAll(inetHost, (Promise<List<T>>) promise); // U is List<T>
                } else {
                    delegate.resolve(inetHost, (Promise<T>) promise); // U is T
                }
            } catch (Throwable cause) {
                // The delegate threw synchronously. Fail the promise so that the outcome is
                // propagated below like any other completion.
                promise.tryFailure(cause);
            }

            if (promise.isDone()) {
                // Completed synchronously, e.g. because the answer was served from the cache.
                // Publish the outcome to the callers that attached in the meantime and release
                // the slot.
                newEntry.complete();
                resolveMap.remove(inetHost, newEntry);
            } else {
                // The listener is notified on this resolver's executor, which is also the
                // executor of the in-flight promise. If that executor terminates before the
                // promise completes, the listener is never notified; close() takes care of the
                // entry in that case.
                promise.addListener(f -> {
                    newEntry.complete();
                    resolveMap.remove(inetHost, newEntry);
                });
            }
            return promise;
        }
    }

    private static <T> void transferResult(Future<T> src, Promise<T> dst) {
        if (src.isSuccess()) {
            dst.trySuccess(src.getNow());
        } else {
            dst.tryFailure(src.cause());
        }
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + delegate + ')';
    }

    private enum AttachResult {
        ATTACHED,
        COMPLETED,
        ABORTED
    }

    /**
     * A single in-flight resolution together with the callers that want its result.
     * <p>
     * The first caller's {@link Promise} is handed over to the delegate and owns the map slot; the
     * other callers ("waiters") are completed directly by {@link #complete()} or {@link #abort()},
     * never through a listener registered on {@link #promise}. That matters because
     * {@link #promise} is bound to the first caller's executor: once that executor is terminated
     * its listeners are dropped (see {@code DefaultPromise#safeExecute}), so a waiter relying on
     * such a listener would hang forever.
     * <p>
     * Package-private so that {@link DnsAddressResolverGroup} can declare the in-flight maps with
     * the correct value type. Not exposed outside {@code io.netty.resolver.dns}.
     */
    static final class InflightEntry<T> {
        final InflightNameResolver<?> owner;
        final Promise<T> promise;
        private final List<Promise<T>> waiters = new ArrayList<Promise<T>>(2);
        private boolean done;
        private boolean aborted;

        InflightEntry(InflightNameResolver<?> owner, Promise<T> promise) {
            this.owner = owner;
            this.promise = promise;
        }

        /**
         * Attaches {@code waiter} to this entry. If the entry is already done the waiter is not
         * registered; the outcome is reported back so that the caller can either forward the
         * result ({@link AttachResult#COMPLETED}) or retry with a new resolution
         * ({@link AttachResult#ABORTED}).
         */
        AttachResult attach(Promise<T> waiter) {
            synchronized (this) {
                if (done) {
                    return aborted ? AttachResult.ABORTED : AttachResult.COMPLETED;
                }
                waiters.add(waiter);
                return AttachResult.ATTACHED;
            }
        }

        /**
         * Completes all the waiters with the outcome of the in-flight promise. Only the first
         * call has an effect.
         */
        void complete() {
            final boolean success = promise.isSuccess();
            complete0(success ? promise.getNow() : null, success ? null : promise.cause());
        }

        /**
         * Aborts this entry because the resolver that started it was closed. The waiters are
         * cancelled: the resolution they were waiting for can no longer complete, and leaving
         * them pending would hang the callers.
         */
        void abort() {
            final List<Promise<T>> pending;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                aborted = true;
                pending = new ArrayList<Promise<T>>(waiters);
                waiters.clear();
            }
            for (Promise<T> waiter : pending) {
                waiter.cancel(false);
            }
            // Only complete the in-flight promise if the executor of this resolver is still
            // running. When the entry is aborted because that executor is shutting down, the
            // notification cannot be dispatched anyway (DefaultPromise logs an error in that
            // case), so leave the promise alone just like any other promise that is pending when
            // its executor terminates.
            if (!owner.executor.isShuttingDown()) {
                promise.cancel(false);
            }
        }

        private void complete0(T value, Throwable cause) {
            final List<Promise<T>> pending;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                pending = new ArrayList<Promise<T>>(waiters);
                waiters.clear();
            }
            if (cause == null) {
                for (Promise<T> waiter : pending) {
                    waiter.trySuccess(value);
                }
            } else {
                for (Promise<T> waiter : pending) {
                    waiter.tryFailure(cause);
                }
            }
        }
    }
}
