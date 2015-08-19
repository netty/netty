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

import io.netty.util.internal.ThreadLocalRandom;

import java.net.InetSocketAddress;
import java.util.Random;

final class ShuffledDnsServerAddressStream implements DnsServerAddressStream {

    private final InetSocketAddress[] addresses;
    private int i;

    ShuffledDnsServerAddressStream(InetSocketAddress[] addresses) {
        this.addresses = addresses.clone();

        shuffle();
    }

    private void shuffle() {
        final InetSocketAddress[] addresses = this.addresses;
        final Random r = ThreadLocalRandom.current();

        for (int i = addresses.length - 1; i >= 0; i --) {
            InetSocketAddress tmp = addresses[i];
            int j = r.nextInt(i + 1);
            addresses[i] = addresses[j];
            addresses[j] = tmp;
        }
    }

    @Override
    public InetSocketAddress next() {
        int i = this.i;
        InetSocketAddress next = addresses[i];
        if (++ i < addresses.length) {
            this.i = i;
        } else {
            this.i = 0;
            shuffle();
        }
        return next;
    }

    @Override
    public String toString() {
        return SequentialDnsServerAddressStream.toString("shuffled", i, addresses);
    }
}
