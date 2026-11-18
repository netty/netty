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
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.SvcParam;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsResolveResult.Endpoint;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.SVCBBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the supported keys of {@link DnsHttpsResolution}, which {@link DnsNameResolver#resolveAll(DnsHttpsQuery)}
 * does not expose: the resolution itself is tested through the resolver by {@link DnsHttpsResolutionTest}.
 */
public class DnsHttpsResolutionSupportedKeysTest extends AbstractDnsHttpsTest {

    private final DnsHttpsCache cache = new DefaultDnsHttpsCache();

    @Test
    public void testCompatibilityAppliedToCachedRecords() throws Exception {
        server.add(https("example.com.", 1, "ech.example.net.",
                new SVCBBase.ParameterMandatory(Collections.singletonList(SvcParam.ECH)),
                new SVCBBase.ParameterEch(new byte[] { 1, 2, 3 })));
        server.add(https("example.com.", 2, "plain.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        // The records are cached by a resolution of the default keys, which ignores the ech one (see
        // DnsHttpsResolutionTest.testUnsupportedMandatoryKey).
        resolve("example.com.").get(10, TimeUnit.SECONDS);
        server.clear();
        Set<Integer> withEch = new HashSet<Integer>(DnsHttpsResolveContext.DEFAULT_SUPPORTED_KEYS);
        withEch.add(SvcParam.ECH);

        List<Endpoint> withEchKey = endpoints(resolve("example.com.", withEch).get(10, TimeUnit.SECONDS));

        assertThat(targets(withEchKey)).containsExactly("ech.example.net.", "plain.example.net.");
    }

    @Test
    public void testAutomaticallyMandatoryPort() throws Exception {
        server.add(https("example.com.", 1, "port.example.net.", new SVCBBase.ParameterPort(8443)));
        server.add(https("example.com.", 2, "plain.example.net."));
        Set<Integer> withoutPort = new HashSet<Integer>(Arrays.asList(SvcParam.MANDATORY, SvcParam.ALPN));

        List<Endpoint> records = endpoints(resolve("example.com.", withoutPort).get(10, TimeUnit.SECONDS));

        assertThat(targets(records)).containsExactly("plain.example.net.");
    }

    private Future<List<DnsResolveResult>> resolve(String name) {
        return resolve(name, DnsHttpsResolveContext.DEFAULT_SUPPORTED_KEYS);
    }

    private Future<List<DnsResolveResult>> resolve(final String name, final Set<Integer> supportedKeys) {
        final EventLoop loop = resolver.executor();
        final Promise<List<DnsResolveResult>> promise = loop.newPromise();
        loop.execute(() -> new DnsHttpsResolution(resolver, resolver.resolveCache(), cache, supportedKeys,
                DnsHttpsResolveContext.DEFAULT_MAX_ALIASES, ResolvedAddressTypes.IPV4_PREFERRED, false)
                .resolve(name, 443, new DnsRecord[0], true, promise));
        return promise;
    }

    private static List<String> targets(List<Endpoint> endpoints) {
        List<String> targets = new ArrayList<String>(endpoints.size());
        for (Endpoint endpoint : endpoints) {
            targets.add(endpoint.targetName());
        }
        return targets;
    }

    private static List<Endpoint> endpoints(List<DnsResolveResult> results) {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (DnsResolveResult result : results) {
            if (result instanceof Endpoint) {
                endpoints.add((Endpoint) result);
            }
        }
        return endpoints;
    }
}
