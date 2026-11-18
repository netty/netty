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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the {@link DnsJavaTestServer#holdUntil(int)} the parallel queries of a {@link DnsResolveContext} with several
 * expected types are tested with, see {@link DnsHttpsResolutionTest#testHttpsAndAddressQueriesSentInParallel()}.
 */
public class DnsResolveContextParallelQueriesTest extends AbstractDnsHttpsTest {

    @Test
    public void testHoldDetectsQueriesNotSentInParallel() throws Exception {
        server.add(a("example.com.", "192.0.2.1"));
        // Only one query is sent, so the server never answers.
        server.holdUntil(2);
        DnsNameResolver fastResolver = build(newResolver().queryTimeoutMillis(1000));

        Throwable cause = resolveTypes(fastResolver, "example.com.", DnsRecordType.A).await().cause();

        assertThat(cause).isInstanceOf(UnknownHostException.class);
        assertThat(server.receivedTypes()).containsOnly(Type.A);
    }

    private static Future<List<String>> resolveTypes(final DnsNameResolver resolver, final String name,
                                                     final DnsRecordType... types) {
        final EventLoop loop = resolver.executor();
        final Promise<List<String>> promise = loop.newPromise();
        loop.execute(() -> resolver.nextResolveChannel(promise).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                promise.setFailure(f.cause());
                return;
            }
            new TypesContext(resolver, f.channel(), promise, name, types,
                    resolver.newNameServerAddressStream(name), resolver.maxQueriesPerResolve()).resolve(promise);
        }));
        return promise;
    }

    /**
     * Resolves several record types at once and returns the type of each answer.
     */
    private static final class TypesContext extends DnsResolveContext<String> {

        TypesContext(DnsNameResolver parent, Channel channel, Promise<?> originalPromise, String hostname,
                     DnsRecordType[] expectedTypes, DnsServerAddressStream nameServerAddrs, int allowedQueries) {
            super(parent, channel, originalPromise, hostname, DnsRecord.CLASS_IN, expectedTypes, new DnsRecord[0],
                    nameServerAddrs, allowedQueries);
        }

        @Override
        DnsResolveContext<String> newResolverContext(DnsNameResolver parent, Channel channel,
                                                     Promise<?> originalPromise, String hostname, int dnsClass,
                                                     DnsRecordType[] expectedTypes, DnsRecord[] additionals,
                                                     DnsServerAddressStream nameServerAddrs, int allowedQueries) {
            return new TypesContext(parent, channel, originalPromise, hostname, expectedTypes, nameServerAddrs,
                    allowedQueries);
        }

        @Override
        String convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals, EventLoop eventLoop) {
            return record.type().name();
        }

        @Override
        List<String> filterResults(List<String> unfiltered) {
            return unfiltered;
        }

        @Override
        boolean isCompleteEarly(String resolved) {
            return false;
        }

        @Override
        boolean isDuplicateAllowed() {
            return true;
        }

        @Override
        void cache(String hostname, DnsRecord[] additionals, DnsRecord result, String convertedResult) {
        }

        @Override
        void cache(String hostname, DnsRecord[] additionals, UnknownHostException cause) {
        }
    }
}
