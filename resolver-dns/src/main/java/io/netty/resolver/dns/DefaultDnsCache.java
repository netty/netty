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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.util.internal.StringUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link DnsCache}, backed by a {@link ConcurrentMap}.
 * If any additional {@link DnsRecord} is used, no caching takes place.
 */
public class DefaultDnsCache implements DnsCache {

    private final Cache<DefaultDnsCacheEntry> resolveCache = new Cache<DefaultDnsCacheEntry>() {

        @Override
        protected boolean shouldReplaceAll(DefaultDnsCacheEntry entry) {
            return entry.cause() != null;
        }

        @Override
        protected boolean equals(DefaultDnsCacheEntry entry, DefaultDnsCacheEntry otherEntry) {
            if (entry.address() != null) {
                return entry.address().equals(otherEntry.address());
            }
            if (otherEntry.address() != null) {
                return false;
            }
            return entry.cause().equals(otherEntry.cause());
        }
    };

    private final int minTtl;
    private final int maxTtl;
    private final int negativeTtl;

    /**
     * Create a cache that respects the TTL returned by the DNS server
     * and doesn't cache negative responses.
     */
    public DefaultDnsCache() {
        this(0, Cache.MAX_SUPPORTED_TTL_SECS, 0);
    }

    /**
     * Create a cache.
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @param negativeTtl the TTL for failed queries
     */
    public DefaultDnsCache(int minTtl, int maxTtl, int negativeTtl) {
        this.minTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(minTtl, "minTtl"));
        this.maxTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(maxTtl, "maxTtl"));
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.negativeTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(negativeTtl, "negativeTtl"));
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
        resolveCache.clear();
    }

    @Override
    public boolean clear(String hostname) {
        checkNotNull(hostname, "hostname");
        return resolveCache.clear(appendDot(hostname));
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

        final List<? extends DnsCacheEntry> entries = resolveCache.get(appendDot(hostname));
        if (entries == null || entries.isEmpty()) {
            return entries;
        }
        return new DnsCacheEntryList(entries);
    }

    @Override
    public DnsCacheEntry cache(String hostname, DnsRecord[] additionals,
                               InetAddress address, long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(address, "address");
        checkNotNull(loop, "loop");
        DefaultDnsCacheEntry e = new DefaultDnsCacheEntry(hostname, address);
        if (maxTtl == 0 || !emptyAdditionals(additionals)) {
            return e;
        }
        resolveCache.cache(appendDot(hostname), e, Math.max(minTtl, (int) Math.min(maxTtl, originalTtl)), loop);
        return e;
    }

    @Override
    public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(cause, "cause");
        checkNotNull(loop, "loop");

        DefaultDnsCacheEntry e = new DefaultDnsCacheEntry(hostname, cause);
        if (negativeTtl == 0 || !emptyAdditionals(additionals)) {
            return e;
        }

        resolveCache.cache(appendDot(hostname), e, negativeTtl, loop);
        return e;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DefaultDnsCache(minTtl=")
                .append(minTtl).append(", maxTtl=")
                .append(maxTtl).append(", negativeTtl=")
                .append(negativeTtl).append(", cached resolved hostname=")
                .append(resolveCache.size()).append(')')
                .toString();
    }

    private static final class DefaultDnsCacheEntry implements DnsCacheEntry {
        private final String hostname;
        private final InetAddress address;
        private final Throwable cause;
        private final int hash;

        DefaultDnsCacheEntry(String hostname, InetAddress address) {
            this.hostname = hostname;
            this.address = address;
            cause = null;
            hash = System.identityHashCode(this);
        }

        DefaultDnsCacheEntry(String hostname, Throwable cause) {
            this.hostname = hostname;
            this.cause = cause;
            address = null;
            hash = System.identityHashCode(this);
        }

        private DefaultDnsCacheEntry(DefaultDnsCacheEntry entry) {
            this.hostname = entry.hostname;
            if (entry.cause == null) {
                this.address = entry.address;
                this.cause = null;
            } else {
                this.address = null;
                this.cause = copyThrowable(entry.cause);
            }
            this.hash = entry.hash;
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

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof DefaultDnsCacheEntry) && ((DefaultDnsCacheEntry) obj).hash == hash;
        }

        DnsCacheEntry copyIfNeeded() {
            if (cause == null) {
                return this;
            }
            return new DefaultDnsCacheEntry(this);
        }
    }

    private static String appendDot(String hostname) {
        return StringUtil.endsWith(hostname, '.') ? hostname : hostname + '.';
    }

    private static Throwable copyThrowable(Throwable error) {
        if (error.getClass() == UnknownHostException.class) {
            // Fast-path as this is the only type of Throwable that our implementation ever add to the cache.
            UnknownHostException copy = new UnknownHostException(error.getMessage()) {
                @Override
                public Throwable fillInStackTrace() {
                    // noop.
                    return this;
                }
            };
            copy.initCause(error.getCause());
            copy.setStackTrace(error.getStackTrace());
            return copy;
        }
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            // Throwable is Serializable so lets just do a deep copy.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(error);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ois = new ObjectInputStream(bais);
            return (Throwable) ois.readObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException ignore) {
                    // noop
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException ignore) {
                    // noop
                }
            }
        }
    }

    private static final class DnsCacheEntryList extends AbstractList<DnsCacheEntry> {
        private final List<? extends DnsCacheEntry> entries;

        DnsCacheEntryList(List<? extends DnsCacheEntry> entries) {
            this.entries = entries;
        }

        @Override
        public DnsCacheEntry get(int index) {
            DefaultDnsCacheEntry entry = (DefaultDnsCacheEntry) entries.get(index);
            // As we dont know what exactly the user is doing with the returned exception (for example
            // using addSuppressed(...) and so hold up a lot of memory until the entry expires) we do
            // create a copy.
            return entry.copyIfNeeded();
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public int hashCode() {
            // Just delegate to super to make checkstyle happy
            return super.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DnsCacheEntryList) {
                // Fast-path.
                return entries.equals(((DnsCacheEntryList) o).entries);
            }
            return super.equals(o);
        }
    };
}
