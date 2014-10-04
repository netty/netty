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

package io.netty.resolver.dns;

import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a sequence of DNS server addresses to {@link DnsNameResolver}.
 */
@SuppressWarnings("IteratorNextCanNotThrowNoSuchElementException")
public final class DnsServerAddresses {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsServerAddresses.class);

    private static final List<InetSocketAddress> DEFAULT_NAME_SERVER_LIST;
    private static final InetSocketAddress[] DEFAULT_NAME_SERVER_ARRAY;

    static {
        final int DNS_PORT = 53;
        final List<InetSocketAddress> defaultNameServers = new ArrayList<InetSocketAddress>(2);
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);

            for (int i = 0; i < list.size(); i ++) {
                String dnsAddr = list.get(i);
                if (dnsAddr != null) {
                    defaultNameServers.add(new InetSocketAddress(InetAddress.getByName(dnsAddr), DNS_PORT));
                }
            }
        } catch (Exception ignore) { }

        if (!defaultNameServers.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Default DNS servers: {} (sun.net.dns.ResolverConfiguration)", defaultNameServers);
            }
        } else {
            Collections.addAll(
                    defaultNameServers,
                    new InetSocketAddress("8.8.8.8", DNS_PORT),
                    new InetSocketAddress("8.8.4.4", DNS_PORT));

            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Default DNS servers: {} (Google Public DNS as a fallback)", defaultNameServers);
            }
        }

        DEFAULT_NAME_SERVER_LIST = Collections.unmodifiableList(defaultNameServers);
        DEFAULT_NAME_SERVER_ARRAY = defaultNameServers.toArray(new InetSocketAddress[defaultNameServers.size()]);
    }

    public static List<InetSocketAddress> defaultNameServers() {
        return DEFAULT_NAME_SERVER_LIST;
    }

    public static Iterable<InetSocketAddress> sequential(Iterable<? extends InetSocketAddress> addresses) {
        return sequential0(sanitize(addresses));
    }

    public static Iterable<InetSocketAddress> sequential(InetSocketAddress... addresses) {
        return sequential0(sanitize(addresses));
    }

    private static Iterable<InetSocketAddress> sequential0(final InetSocketAddress[] addresses) {
        return new Iterable<InetSocketAddress>() {
            @Override
            public Iterator<InetSocketAddress> iterator() {
                return new SequentialAddresses(addresses, 0);
            }
        };
    }

    public static Iterable<InetSocketAddress> random(Iterable<? extends InetSocketAddress> addresses) {
        return random0(sanitize(addresses));
    }

    public static Iterable<InetSocketAddress> random(InetSocketAddress... addresses) {
        return random0(sanitize(addresses));
    }

    private static Iterable<InetSocketAddress> random0(InetSocketAddress[] addresses) {
        final Iterator<InetSocketAddress> iterator = new RandomAddresses(addresses);
        return new Iterable<InetSocketAddress>() {
            @Override
            public Iterator<InetSocketAddress> iterator() {
                return iterator;
            }
        };
    }

    public static Iterable<InetSocketAddress> rotational(Iterable<? extends InetSocketAddress> addresses) {
        return rotational0(sanitize(addresses));
    }

    public static Iterable<InetSocketAddress> rotational(InetSocketAddress... addresses) {
        return rotational0(sanitize(addresses));
    }

    private static Iterable<InetSocketAddress> rotational0(final InetSocketAddress[] addresses) {
        return new Iterable<InetSocketAddress>() {
            private final AtomicInteger startIdx = new AtomicInteger();

            @Override
            public Iterator<InetSocketAddress> iterator() {
                for (;;) {
                    int curStartIdx = startIdx.get();
                    int nextStartIdx = curStartIdx + 1;
                    if (nextStartIdx >= addresses.length) {
                        nextStartIdx = 0;
                    }
                    if (startIdx.compareAndSet(curStartIdx, nextStartIdx)) {
                        return new SequentialAddresses(addresses, curStartIdx);
                    }
                }
            }
        };
    }

    public static Iterable<InetSocketAddress> singleton(final InetSocketAddress address) {
        if (address == null) {
            throw new NullPointerException("address");
        }
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + address);
        }

        return new Iterable<InetSocketAddress>() {

            private final Iterator<InetSocketAddress> iterator = new Iterator<InetSocketAddress>() {
                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public InetSocketAddress next() {
                    return address;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

            @Override
            public Iterator<InetSocketAddress> iterator() {
                return iterator;
            }
        };
    }

    private static InetSocketAddress[] sanitize(Iterable<? extends InetSocketAddress> addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        List<InetSocketAddress> list = new ArrayList<InetSocketAddress>(4);
        for (InetSocketAddress a: addresses) {
            if (a == null) {
                break;
            }
            if (a.isUnresolved()) {
                throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + a);
            }
            list.add(a);
        }

        if (list.isEmpty()) {
            return DEFAULT_NAME_SERVER_ARRAY;
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    private static InetSocketAddress[] sanitize(InetSocketAddress[] addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        List<InetSocketAddress> list = new ArrayList<InetSocketAddress>(4);
        for (InetSocketAddress a: addresses) {
            if (a == null) {
                break;
            }
            if (a.isUnresolved()) {
                throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + a);
            }
            list.add(a);
        }

        if (list.isEmpty()) {
            return DEFAULT_NAME_SERVER_ARRAY;
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    private DnsServerAddresses() { }

    private static final class SequentialAddresses implements Iterator<InetSocketAddress> {

        private final InetSocketAddress[] addresses;
        private int i;

        SequentialAddresses(InetSocketAddress[] addresses, int startIdx) {
            this.addresses = addresses;
            i = startIdx;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public InetSocketAddress next() {
            int i = this.i;
            InetSocketAddress next = addresses[i];
            if (++ i < addresses.length) {
                this.i = i;
            } else {
                this.i = 0;
            }
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    private static final class RandomAddresses implements Iterator<InetSocketAddress> {

        private final InetSocketAddress[] addresses;

        RandomAddresses(InetSocketAddress[] addresses) {
            this.addresses = addresses;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public InetSocketAddress next() {
            return addresses[ThreadLocalRandom.current().nextInt(addresses.length)];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
