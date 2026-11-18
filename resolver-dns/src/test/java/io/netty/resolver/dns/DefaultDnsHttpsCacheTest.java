/*
 * Copyright 2026 The Netty Project
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
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Ticker;
import io.netty.util.concurrent.MockTicker;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.codec.dns.DefaultDnsSvcbRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSvcbRecord;
import io.netty.handler.codec.dns.SvcParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DefaultDnsHttpsCacheTest {

    private EventLoopGroup group;
    private EventLoop loop;

    @BeforeEach
    public void setUp() {
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        loop = group.next();
    }

    @AfterEach
    public void tearDown() {
        group.shutdownGracefully();
    }

    private static final DnsRecord[] NO_ADDITIONALS = new DnsRecord[0];

    @Test
    public void testCacheRecords() {
        DefaultDnsHttpsCache cache = new DefaultDnsHttpsCache();
        List<DnsSvcbRecord> records = new ArrayList<DnsSvcbRecord>(Collections.singletonList(record()));

        DnsHttpsCacheEntry cached = cache.cache("example.com", NO_ADDITIONALS, records, 300, loop);
        records.clear();

        DnsHttpsCacheEntry entry = cache.get("example.com.", NO_ADDITIONALS);
        assertThat(entry).isSameAs(cached);
        assertThat(entry.cause()).isNull();
        assertThat(entry.records()).containsExactly(record());
        assertThatThrownBy(() -> entry.records().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testReplace() {
        DefaultDnsHttpsCache cache = new DefaultDnsHttpsCache(0, 300, 300);

        cache.cache("example.com.", NO_ADDITIONALS, new UnknownHostException(), loop);
        cache.cache("example.com.", NO_ADDITIONALS, Collections.singletonList(record()), 300, loop);

        assertThat(cache.get("example.com.", NO_ADDITIONALS).records()).containsExactly(record());
    }

    @Test
    public void testClear() {
        DefaultDnsHttpsCache cache = new DefaultDnsHttpsCache();
        cache.cache("a.example.com.", NO_ADDITIONALS, Collections.singletonList(record()), 300, loop);
        cache.cache("b.example.com.", NO_ADDITIONALS, Collections.singletonList(record()), 300, loop);

        assertThat(cache.clear("a.example.com")).isTrue();
        assertThat(cache.clear("a.example.com")).isFalse();
        assertThat(cache.get("a.example.com.", NO_ADDITIONALS)).isNull();
        assertThat(cache.get("b.example.com.", NO_ADDITIONALS)).isNotNull();

        cache.clear();

        assertThat(cache.get("b.example.com.", NO_ADDITIONALS)).isNull();
    }

    @Test
    public void testInvalidTtls() {
        assertThatThrownBy(() -> new DefaultDnsHttpsCache(10, 5, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultDnsHttpsCache(-1, 5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DnsSvcbRecord record() {
        return new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1, ".",
                Collections.<SvcParam>singletonList(new SvcParam.Port(443)));
    }

    @Test
    public void testEntryReportsItsDeadline() {
        MockTicker ticker = Ticker.newMockTicker();
        ticker.advance(5, TimeUnit.SECONDS);
        EmbeddedChannel channel = EmbeddedChannel.builder().ticker(ticker).build();
        try {
            DefaultDnsHttpsCache cache = new DefaultDnsHttpsCache(10, 30, 5);
            DnsHttpsCacheEntry records = cache.cache("example.com", null,
                    Collections.<DnsSvcbRecord>emptyList(), 60, channel.eventLoop());
            DnsHttpsCacheEntry failure = cache.cache("example.net", null, new UnknownHostException(),
                    channel.eventLoop());

            assertThat(records.deadlineNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(35));
            assertThat(failure.deadlineNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(10));
            assertThat(cache.get("example.com", null).deadlineNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(35));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
