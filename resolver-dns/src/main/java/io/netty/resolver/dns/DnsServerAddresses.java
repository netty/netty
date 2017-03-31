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

import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.defaultAddressArray;

/**
 * Provides an infinite sequence of DNS server addresses to {@link DnsNameResolver}.
 */
@UnstableApi
@SuppressWarnings("IteratorNextCanNotThrowNoSuchElementException")
public abstract class DnsServerAddresses {
    /**
     * @deprecated Use {@link DefaultDnsServerAddressStreamProvider#defaultAddressList()}.
     * <p>
     * Returns the list of the system DNS server addresses. If it failed to retrieve the list of the system DNS server
     * addresses from the environment, it will return {@code "8.8.8.8"} and {@code "8.8.4.4"}, the addresses of the
     * Google public DNS servers.
     */
    @Deprecated
    public static List<InetSocketAddress> defaultAddressList() {
        return DefaultDnsServerAddressStreamProvider.defaultAddressList();
    }

    /**
     * @deprecated Use {@link DefaultDnsServerAddressStreamProvider#defaultAddresses()}.
     * <p>
     * Returns the {@link DnsServerAddresses} that yields the system DNS server addresses sequentially. If it failed to
     * retrieve the list of the system DNS server addresses from the environment, it will use {@code "8.8.8.8"} and
     * {@code "8.8.4.4"}, the addresses of the Google public DNS servers.
     * <p>
     * This method has the same effect with the following code:
     * <pre>
     * DnsServerAddresses.sequential(DnsServerAddresses.defaultAddressList());
     * </pre>
     * </p>
     */
    @Deprecated
    public static DnsServerAddresses defaultAddresses() {
        return DefaultDnsServerAddressStreamProvider.defaultAddresses();
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code addresses} sequentially. Once the
     * last address is yielded, it will start again from the first address.
     */
    public static DnsServerAddresses sequential(Iterable<? extends InetSocketAddress> addresses) {
        return sequential0(sanitize(addresses));
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code addresses} sequentially. Once the
     * last address is yielded, it will start again from the first address.
     */
    public static DnsServerAddresses sequential(InetSocketAddress... addresses) {
        return sequential0(sanitize(addresses));
    }

    private static DnsServerAddresses sequential0(final InetSocketAddress... addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new DefaultDnsServerAddresses("sequential", addresses) {
            @Override
            public DnsServerAddressStream stream() {
                return new SequentialDnsServerAddressStream(addresses, 0);
            }
        };
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code address} in a shuffled order. Once all
     * addresses are yielded, the addresses are shuffled again.
     */
    public static DnsServerAddresses shuffled(Iterable<? extends InetSocketAddress> addresses) {
        return shuffled0(sanitize(addresses));
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code addresses} in a shuffled order. Once all
     * addresses are yielded, the addresses are shuffled again.
     */
    public static DnsServerAddresses shuffled(InetSocketAddress... addresses) {
        return shuffled0(sanitize(addresses));
    }

    private static DnsServerAddresses shuffled0(final InetSocketAddress[] addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new DefaultDnsServerAddresses("shuffled", addresses) {
            @Override
            public DnsServerAddressStream stream() {
                return new ShuffledDnsServerAddressStream(addresses);
            }
        };
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code addresses} in a rotational sequential
     * order. It is similar to {@link #sequential(Iterable)}, but each {@link DnsServerAddressStream} starts from
     * a different starting point.  For example, the first {@link #stream()} will start from the first address, the
     * second one will start from the second address, and so on.
     */
    public static DnsServerAddresses rotational(Iterable<? extends InetSocketAddress> addresses) {
        return rotational0(sanitize(addresses));
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the specified {@code addresses} in a rotational sequential
     * order. It is similar to {@link #sequential(Iterable)}, but each {@link DnsServerAddressStream} starts from
     * a different starting point.  For example, the first {@link #stream()} will start from the first address, the
     * second one will start from the second address, and so on.
     */
    public static DnsServerAddresses rotational(InetSocketAddress... addresses) {
        return rotational0(sanitize(addresses));
    }

    private static DnsServerAddresses rotational0(final InetSocketAddress[] addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new RotationalDnsServerAddresses(addresses);
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields only a single {@code address}.
     */
    public static DnsServerAddresses singleton(final InetSocketAddress address) {
        if (address == null) {
            throw new NullPointerException("address");
        }
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + address);
        }

        return new SingletonDnsServerAddresses(address);
    }

    private static InetSocketAddress[] sanitize(Iterable<? extends InetSocketAddress> addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        final List<InetSocketAddress> list;
        if (addresses instanceof Collection) {
            list = new ArrayList<InetSocketAddress>(((Collection<?>) addresses).size());
        } else {
            list = new ArrayList<InetSocketAddress>(4);
        }

        for (InetSocketAddress a : addresses) {
            if (a == null) {
                break;
            }
            if (a.isUnresolved()) {
                throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + a);
            }
            list.add(a);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("empty addresses");
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    private static InetSocketAddress[] sanitize(InetSocketAddress[] addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        List<InetSocketAddress> list = new ArrayList<InetSocketAddress>(addresses.length);
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
            return defaultAddressArray();
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    /**
     * Starts a new infinite stream of DNS server addresses. This method is invoked by {@link DnsNameResolver} on every
     * uncached {@link DnsNameResolver#resolve(String)}or {@link DnsNameResolver#resolveAll(String)}.
     */
    public abstract DnsServerAddressStream stream();
}
