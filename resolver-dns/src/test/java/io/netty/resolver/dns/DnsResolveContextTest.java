/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.fail;

public class DnsResolveContextTest {

    private static final String HOSTNAME = "netty.io.";

    @Test
    public void testCnameLoop() {
        for (int i = 1; i < 128; i++) {
            try {
                DnsResolveContext.cnameResolveFromCache(buildCache(i), HOSTNAME);
                fail();
            } catch (UnknownHostException expected) {
                // expected
            }
        }
    }

    private static DnsCnameCache buildCache(int chainLength) {
        EmbeddedChannel channel = new EmbeddedChannel();
        DnsCnameCache cache = new DefaultDnsCnameCache();
        if (chainLength == 1) {
            cache.cache(HOSTNAME, HOSTNAME, Long.MAX_VALUE, channel.eventLoop());
        } else {
            String lastName = HOSTNAME;
            for (int i = 1; i < chainLength; i++) {
                String nextName = i + "." + lastName;
                cache.cache(lastName, nextName, Long.MAX_VALUE, channel.eventLoop());
                lastName = nextName;
            }
            cache.cache(lastName, HOSTNAME, Long.MAX_VALUE, channel.eventLoop());
        }
        return cache;
    }
}
