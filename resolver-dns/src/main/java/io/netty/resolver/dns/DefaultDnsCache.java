/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link DnsCache}, backed by a {@link ConcurrentMap}.
 * If any additional {@link DnsRecord} is used, no caching takes place.
 */
@UnstableApi
public class DefaultDnsCache implements DnsCache {
    // Two years are supported by all our EventLoop implementations and so safe to use as maximum.
    // See also: https://github.com/netty/netty/commit/b47fb817991b42ec8808c7d26538f3f2464e1fa6
    private static final int MAX_SUPPORTED_TTL_SECS = (int) TimeUnit.DAYS.toSeconds(365 * 2);

    private static final ScheduledFuture<?> CANCELLED = new ScheduledFuture<Object>() {

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            // We ignore unit and always return the minimum value to ensure the TTL of the cancelled marker is
            // the smallest.
            return Long.MIN_VALUE;
        }

        @Override
        public int compareTo(Delayed o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    };

    private static final AtomicReferenceFieldUpdater<DefaultDnsCache.Entries, ScheduledFuture> FUTURE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    DefaultDnsCache.Entries.class, ScheduledFuture.class, "expirationFuture");

    private final ConcurrentMap<String, Entries> resolveCache = PlatformDependent.newConcurrentHashMap();
    private final int minTtl;
    private final int maxTtl;
    private final int negativeTtl;

    /**
     * Create a cache that respects the TTL returned by the DNS server
     * and doesn't cache negative responses.
     */
    public DefaultDnsCache() {
        this(0, MAX_SUPPORTED_TTL_SECS, 0);
    }

    /**
     * Create a cache.
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @param negativeTtl the TTL for failed queries
     */
    public DefaultDnsCache(int minTtl, int maxTtl, int negativeTtl) {
        this.minTtl = Math.min(MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(minTtl, "minTtl"));
        this.maxTtl = Math.min(MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(maxTtl, "maxTtl"));
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.negativeTtl = checkPositiveOrZero(negativeTtl, "negativeTtl");
    }

    /**
     * Returns the minimum TTL of the cached DNS resource records (in seconds).
     *
     * @see #maxTtl()
     */
    public int minTtl() {
        return minTtl;
    }

    /**
     * Returns the maximum TTL of the cached DNS resource records (in seconds).
     *
     * @see #minTtl()
     */
    public int maxTtl() {
        return maxTtl;
    }

    /**
     * Returns the TTL of the cache for the failed DNS queries (in seconds). The default value is {@code 0}, which
     * disables the cache for negative results.
     */
    public int negativeTtl() {
        return negativeTtl;
    }

    @Override
    public void clear() {
        while (!resolveCache.isEmpty()) {
            for (Iterator<Map.Entry<String, Entries>> i = resolveCache.entrySet().iterator(); i.hasNext();) {
                Map.Entry<String, Entries> e = i.next();
                i.remove();

                e.getValue().clearAndCancel();
            }
        }
    }

    @Override
    public boolean clear(String hostname) {
        checkNotNull(hostname, "hostname");
        Entries entries = resolveCache.remove(appendDot(hostname));
        return entries != null && entries.clearAndCancel();
    }

    private static boolean emptyAdditionals(DnsRecord[] additionals) {
        return additionals == null || additionals.length == 0;
    }

    @Override
    public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
        checkNotNull(hostname, "hostname");
        if (!emptyAdditionals(additionals)) {
            return Collections.<DnsCacheEntry>emptyList();
        }

        Entries entries = resolveCache.get(appendDot(hostname));
        return entries == null ? null : entries.get();
    }

    @Override
    public DnsCacheEntry cache(String hostname, DnsRecord[] additionals,
                               InetAddress address, long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(address, "address");
        checkNotNull(loop, "loop");
        final DefaultDnsCacheEntry e = new DefaultDnsCacheEntry(hostname, address);
        if (maxTtl == 0 || !emptyAdditionals(additionals)) {
            return e;
        }
        cache0(appendDot(hostname), e,
                Math.max(minTtl, (int) Math.min(maxTtl, originalTtl)), loop);
        return e;
    }

    @Override
    public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(cause, "cause");
        checkNotNull(loop, "loop");

        final DefaultDnsCacheEntry e = new DefaultDnsCacheEntry(hostname, cause);
        if (negativeTtl == 0 || !emptyAdditionals(additionals)) {
            return e;
        }

        cache0(appendDot(hostname), e, negativeTtl, loop);
        return e;
    }

    private void cache0(String hostname, DefaultDnsCacheEntry e, int ttl, EventLoop loop) {
        Entries entries = resolveCache.get(hostname);
        if (entries == null) {
            entries = new Entries(hostname);
            Entries oldEntries = resolveCache.putIfAbsent(hostname, entries);
            if (oldEntries != null) {
                entries = oldEntries;
            }
        }
        entries.add(e, ttl, loop);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DefaultDnsCache(minTtl=")
                .append(minTtl).append(", maxTtl=")
                .append(maxTtl).append(", negativeTtl=")
                .append(negativeTtl).append(", cached resolved hostname=")
                .append(resolveCache.size()).append(")")
                .toString();
    }

    private static final class DefaultDnsCacheEntry implements DnsCacheEntry {
        private final String hostname;
        private final InetAddress address;
        private final Throwable cause;

        DefaultDnsCacheEntry(String hostname, InetAddress address) {
            this.hostname = checkNotNull(hostname, "hostname");
            this.address = checkNotNull(address, "address");
            cause = null;
        }

        DefaultDnsCacheEntry(String hostname, Throwable cause) {
            this.hostname = checkNotNull(hostname, "hostname");
            this.cause = checkNotNull(cause, "cause");
            address = null;
        }

        @Override
        public InetAddress address() {
            return address;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        String hostname() {
            return hostname;
        }

        @Override
        public String toString() {
            if (cause != null) {
                return hostname + '/' + cause;
            } else {
                return address.toString();
            }
        }
    }

    // Directly extend AtomicReference for intrinsics and also to keep memory overhead low.
    private final class Entries extends AtomicReference<List<DefaultDnsCacheEntry>> implements Runnable {

        private final String hostname;
        // Needs to be package-private to be able to access it via the AtomicReferenceFieldUpdater
        volatile ScheduledFuture<?> expirationFuture;

        Entries(String hostname) {
            super(Collections.<DefaultDnsCacheEntry>emptyList());
            this.hostname = hostname;
        }

        void add(DefaultDnsCacheEntry e, int ttl, EventLoop loop) {
            if (e.cause() == null) {
                for (;;) {
                    List<DefaultDnsCacheEntry> entries = get();
                    if (!entries.isEmpty()) {
                        final DefaultDnsCacheEntry firstEntry = entries.get(0);
                        if (firstEntry.cause() != null) {
                            assert entries.size() == 1;

                            if (compareAndSet(entries, Collections.singletonList(e))) {
                                scheduleCacheExpirationIfNeeded(ttl, loop);
                                return;
                            } else {
                                // Need to try again as CAS failed
                                continue;
                            }
                        }

                        // Create a new List for COW semantics
                        List<DefaultDnsCacheEntry> newEntries = new ArrayList<DefaultDnsCacheEntry>(entries.size() + 1);
                        int i = 0;
                        do {
                            DefaultDnsCacheEntry entry = entries.get(i);
                            // Only add old entry if the address is not the same as the one we try to add as well.
                            // In this case we will skip it and just add the new entry as this may have
                            // more up-to-date data and cancel the old after we were able to update the cache.
                            if (!e.address().equals(entry.address())) {
                                newEntries.add(entry);
                            }
                        } while (++i < entries.size());
                        newEntries.add(e);
                        if (compareAndSet(entries, Collections.unmodifiableList(newEntries))) {
                            scheduleCacheExpirationIfNeeded(ttl, loop);
                            return;
                        }
                    } else if (compareAndSet(entries, Collections.singletonList(e))) {
                        scheduleCacheExpirationIfNeeded(ttl, loop);
                        return;
                    }
                }
            } else {
                set(Collections.singletonList(e));
                scheduleCacheExpirationIfNeeded(ttl, loop);
            }
        }

        private void scheduleCacheExpirationIfNeeded(int ttl, EventLoop loop) {
            for (;;) {
                // We currently don't calculate a new TTL when we need to retry the CAS as we don't expect this to
                // be invoked very concurrently and also we use SECONDS anyway. If this ever becomes a problem
                // we can reconsider.
                ScheduledFuture<?> oldFuture = FUTURE_UPDATER.get(this);
                if (oldFuture == null || oldFuture.getDelay(TimeUnit.SECONDS) > ttl) {
                    ScheduledFuture<?> newFuture = loop.schedule(this, ttl, TimeUnit.SECONDS);
                    // It is possible that
                    // 1. task will fire in between this line, or
                    // 2. multiple timers may be set if there is concurrency
                    // (1) Shouldn't be a problem because we will fail the CAS and then the next loop will see CANCELLED
                    //     so the ttl will not be less, and we will bail out of the loop.
                    // (2) This is a trade-off to avoid concurrency resulting in contention on a synchronized block.
                    if (FUTURE_UPDATER.compareAndSet(this, oldFuture, newFuture)) {
                         if (oldFuture != null) {
                            oldFuture.cancel(true);
                         }
                         break;
                    } else {
                        // There was something else scheduled in the meantime... Cancel and try again.
                       newFuture.cancel(true);
                    }
                } else {
                    break;
                }
            }
        }

        boolean clearAndCancel() {
            List<DefaultDnsCacheEntry> entries = getAndSet(Collections.<DefaultDnsCacheEntry>emptyList());
            if (entries.isEmpty()) {
                return false;
            }

            ScheduledFuture<?> expirationFuture = FUTURE_UPDATER.getAndSet(this, CANCELLED);
            if (expirationFuture != null) {
                expirationFuture.cancel(false);
            }

            return true;
        }

        @Override
        public void run() {
            // We always remove all entries for a hostname once one entry expire. This is not the
            // most efficient to do but this way we can guarantee that if a DnsResolver
            // be configured to prefer one ip family over the other we will not return unexpected
            // results to the enduser if one of the A or AAAA records has different TTL settings.
            //
            // As a TTL is just a hint of the maximum time a cache is allowed to cache stuff it's
            // completely fine to remove the entry even if the TTL is not reached yet.
            //
            // See https://github.com/netty/netty/issues/7329
            resolveCache.remove(hostname, this);

            clearAndCancel();
        }
    }

    private static String appendDot(String hostname) {
        return StringUtil.endsWith(hostname, '.') ? hostname : hostname + '.';
    }
}
