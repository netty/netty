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

package io.netty.resolver.dns;

import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class ShuffledDnsServerAddressStream implements DnsServerAddressStream {

    private final List<InetSocketAddress> addresses;
    private int i;

    /**
     * Create a new instance.
     * @param addresses The addresses are not cloned. It is assumed the caller has cloned this array or otherwise will
     *                  not modify the contents.
     */
    ShuffledDnsServerAddressStream(List<InetSocketAddress> addresses) {
        this.addresses = addresses;

        shuffle();
    }

    private ShuffledDnsServerAddressStream(List<InetSocketAddress> addresses, int startIdx) {
        this.addresses = addresses;
        i = startIdx;
    }

    private void shuffle() {
        Collections.shuffle(addresses, PlatformDependent.threadLocalRandom());
    }

    @Override
    public InetSocketAddress next() {
        int i = this.i;
        InetSocketAddress next = addresses.get(i);
        if (++ i < addresses.size()) {
            this.i = i;
        } else {
            this.i = 0;
            shuffle();
        }
        return next;
    }

    @Override
    public int size() {
        return addresses.size();
    }

    @Override
    public ShuffledDnsServerAddressStream duplicate() {
        return new ShuffledDnsServerAddressStream(addresses, i);
    }

    @Override
    public String toString() {
        return SequentialDnsServerAddressStream.toString("shuffled", i, addresses);
    }
}
