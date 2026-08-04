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
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

// FIXME(trustin): Find a better name and move it to the 'resolver' module.
final class InflightNameResolver<T> implements NameResolver<T> {

    private final EventExecutor executor;
    private final NameResolver<T> delegate;
    private final ConcurrentMap<String, InflightEntry<T>> resolvesInProgress;
    private final ConcurrentMap<String, InflightEntry<List<T>>> resolveAllsInProgress;

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

    @SuppressWarnings("unchecked")
    private <U> Promise<U> resolve(
            final ConcurrentMap<String, InflightEntry<U>> resolveMap,
            final String inetHost, final Promise<U> promise, final boolean resolveAll) {

        // Loop to recover from a "zombie" entry: a stale entry whose delegate promise never
        // completed (e.g. the original caller's underlying channel was closed without
        // cancelling the promise) and that has already been released but not yet swept from
        // the map. See https://github.com/netty/netty/issues/17039.
        for (;;) {
            final InflightEntry<U> newEntry = new InflightEntry<U>(promise, inetHost);
            final InflightEntry<U> existing = resolveMap.putIfAbsent(inetHost, newEntry);

            if (existing == null) {
                // We are the first caller - drive the actual resolution through the delegate.
                try {
                    if (resolveAll) {
                        final Promise<List<T>> castPromise = (Promise<List<T>>) promise; // U is List<T>
                        delegate.resolveAll(inetHost, castPromise);
                    } else {
                        final Promise<T> castPromise = (Promise<T>) promise; // U is T
                        delegate.resolve(inetHost, castPromise);
                    }
                } catch (Throwable cause) {
                    // case 7: delegate threw synchronously - fail the promise so that our
                    // completion listener below can release the entry and clean the map.
                    promise.tryFailure(cause);
                }

                if (promise.isDone()) {
                    // Synchronous completion - release our initial refCnt and, if no other
                    // caller attached in the meantime, remove the entry from the map.
                    if (newEntry.tryRelease()) {
                        resolveMap.remove(inetHost, newEntry);
                    }
                } else {
                    // Asynchronous in flight. The first caller's refCnt is released when the
                    // delegate's promise completes; if it drops to zero we know no subsequent
                    // caller ever attached and we must remove the map entry.
                    //
                    // Safety net (case 2 in the design): if the executor terminates while the
                    // promise is still in flight, the delegate will not be able to complete it
                    // and any listeners on the in-flight promise (e.g. the
                    // FirstCallerCleanupListener below) will never fire because they are
                    // scheduled on the now-terminated executor. Release the entry here so that
                    // the map slot is dropped even when the executor is gone. We do this
                    // unconditionally: if FirstCallerCleanupListener has already run,
                    // tryRelease() is a no-op and remove(...) is a no-op. If the
                    // delegate promise was completed but the listener never fired (the common
                    // case on shutdown), this is the path that actually cleans up the slot.
                    executor.terminationFuture().addListener(f -> {
                        if (f.isSuccess()) {
                            if (newEntry.tryRelease()) {
                                resolveMap.remove(inetHost, newEntry);
                            }
                            if (!newEntry.promise.isDone()) {
                                // Delegate can no longer complete the promise - abort it so
                                // any waiters on the first caller's promise are unblocked.
                                newEntry.promise.tryFailure(
                                        new AbortedInflightResolveException(newEntry.hostname));
                            }
                        }
                    });
                    promise.addListener(new FirstCallerCleanupListener<U>(newEntry, resolveMap));
                }
                return promise;
            }

            // Another resolution is already in progress for this hostname.
            if (existing.promise.isDone()) {
                // Already completed - transfer the result directly to the caller's promise.
                transferResult(existing.promise, promise);
                return promise;
            }

            if (existing.tryAcquire()) {
                // Re-check isDone after acquire: the delegate may have completed in the window
                // between our putIfAbsent and tryAcquire.
                if (existing.promise.isDone()) {
                    transferResult(existing.promise, promise);
                    existing.tryRelease();
                    return promise;
                }
                // Attach a transfer listener so this caller's promise completes with the same
                // outcome as the in-flight delegate promise.
                existing.promise.addListener(new TransferListener<U>(existing, promise));
                // Release our slot if/when the caller's promise completes (success, failure, or
                // explicit cancel). If we are the last one out, the entry is removed from the
                // map and the delegate promise is aborted if it has not yet completed.
                promise.addListener(new ReleaseListener<U>(existing, resolveMap));
                return promise;
            }

            // The inflight entry is released / aborted but not yet swept from the map.
            // Treat it as a zombie: remove it and retry. The next iteration will either find
            // a clean map (and we become the new first caller) or attach to a freshly-inserted
            // entry.
            resolveMap.remove(inetHost, existing);
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

    /**
     * Holds an in-flight {@link Promise} together with the set of callers that have attached
     * to it. Each caller (including the original first caller) implicitly owns one reference on
     * the entry and must release it when its own promise completes.
     * <p>
     * The reference count is independent of the DNS query-consolidation bookkeeping in
     * {@link DnsNameResolver} (the {@code inflightLookups} map keyed by {@code DnsQuestion}),
     * which only deduplicates questions that share the same outgoing UDP datagram.
     * <p>
     * Package-private so that {@link DnsAddressResolverGroup} can declare the in-flight maps
     * with the correct value type. Not exposed outside {@code io.netty.resolver.dns}.
     */
    static final class InflightEntry<T> extends AbstractReferenceCounted {
        final Promise<T> promise; // visible for testing
        final String hostname; // visible for testing

        InflightEntry(Promise<T> promise, String hostname) {
            this.promise = promise;
            this.hostname = hostname;
        }

        /**
         * Attempt to reserve a slot for a subsequent caller.
         *
         * @return {@code true} if the caller has been registered; {@code false} if the entry has
         *         already been released (or is at zero references) and the caller must either
         *         retry the resolve or start a new resolution.
         */
        boolean tryAcquire() {
            if (refCnt() == 0) {
                return false;
            }
            try {
                retain();
                return true;
            } catch (IllegalReferenceCountException ignore) {
                return false;
            }
        }

        /**
         * Release one slot. Returns {@code true} if this call dropped the count to zero, in
         * which case the caller is responsible for cleaning the map and (if necessary)
         * aborting the {@link #promise}.
         */
        boolean tryRelease() {
            if (refCnt() == 0) {
                return false;
            }
            try {
                return release();
            } catch (IllegalReferenceCountException ignore) {
                return false;
            }
        }

        @Override
        protected void deallocate() {
            // No resources to release; refCnt == 0 marks the entry as no longer attachable.
        }

        @Override
        public InflightEntry<T> touch(Object hint) {
            return this;
        }

    }

    /**
     * Cleans up the map when the first caller's underlying delegate promise completes and no
     * other caller is still attached. The first caller's reference is released here.
     */
    private static final class FirstCallerCleanupListener<U> implements FutureListener<U> {
        private final InflightEntry<U> entry;
        private final ConcurrentMap<String, InflightEntry<U>> map;

        FirstCallerCleanupListener(InflightEntry<U> entry, ConcurrentMap<String, InflightEntry<U>> map) {
            this.entry = entry;
            this.map = map;
        }

        @Override
        public void operationComplete(Future<U> f) {
            // Release the first caller's refCnt. If no subsequent caller attached, this
            // drops the count to zero and we must remove the map entry. The delegate promise
            // is already done at this point, so no abort is needed.
            if (entry.tryRelease()) {
                map.remove(entry.hostname, entry);
            }
        }
    }

    /**
     * Transfers the outcome of the in-flight delegate promise to a subsequent caller's promise
     * once the delegate completes. Successful, failed, and cancelled outcomes are all
     * forwarded transparently.
     */
    private static final class TransferListener<U> implements FutureListener<U> {
        private final InflightEntry<U> entry; // visible for testing
        private final Promise<U> callerPromise;

        TransferListener(InflightEntry<U> entry, Promise<U> callerPromise) {
            this.entry = entry;
            this.callerPromise = callerPromise;
        }

        @Override
        public void operationComplete(Future<U> f) {
            transferResult(f, callerPromise);
        }
    }

    /**
     * Releases a subsequent caller's slot in the inflight entry when the caller's own promise
     * completes. If the release drops the count to zero (no other caller is still attached),
     * the entry is removed from the map and the underlying delegate promise is aborted (if it
     * has not yet completed) so that the inflight resolve does not leak.
     */
    private static final class ReleaseListener<U> implements FutureListener<U> {
        private final InflightEntry<U> entry; // visible for testing
        private final ConcurrentMap<String, InflightEntry<U>> map;

        ReleaseListener(InflightEntry<U> entry, ConcurrentMap<String, InflightEntry<U>> map) {
            this.entry = entry;
            this.map = map;
        }

        @Override
        public void operationComplete(Future<U> f) {
            if (entry.tryRelease()) {
                map.remove(entry.hostname, entry);
                if (!entry.promise.isDone()) {
                    // Last caller out and the delegate is still pending - abort it so that
                    // future callers for the same hostname can start a fresh resolution
                    // instead of attaching to a stuck entry. See issue #17039.
                    entry.promise.tryFailure(new AbortedInflightResolveException(entry.hostname));
                }
            }
        }
    }

    /**
     * Signals that an inflight resolve was abandoned by all of its callers before the
     * underlying delegate could complete. Thrown only inside this package to drive the
     * cleanup path; never escapes to user code (it is delivered as the cause of the
     * delegate's promise, which is also internal to this class).
     */
    private static final class AbortedInflightResolveException extends RuntimeException {
        private static final long serialVersionUID = -1840684398074488192L;

        AbortedInflightResolveException(String hostname) {
            super("Inflight resolve of '" + hostname + "' was cancelled by the last listener before completion");
        }
    }
}
