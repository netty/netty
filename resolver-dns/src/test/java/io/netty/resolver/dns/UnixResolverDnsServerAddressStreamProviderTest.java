/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.CharsetUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.resolver.dns.UnixResolverDnsServerAddressStreamProvider.DEFAULT_NDOTS;
import static io.netty.resolver.dns.UnixResolverDnsServerAddressStreamProvider.parseEtcResolverFirstNdots;
import static org.junit.Assert.assertEquals;

public class UnixResolverDnsServerAddressStreamProviderTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void defaultLookupShouldReturnResultsIfOnlySingleFileSpecified() throws Exception {
        File f = buildFile("domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
    }

    @Test
    public void defaultReturnedWhenNoBetterMatch() throws Exception {
        File f = buildFile("domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        File f2 = buildFile("domain squarecorp.local\n" +
                            "nameserver 127.0.0.4\n" +
                            "nameserver 127.0.0.5\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, f2);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
    }

    @Test
    public void moreRefinedSelectionReturnedWhenMatch() throws Exception {
        File f = buildFile("domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        File f2 = buildFile("domain dc1.linecorp.local\n" +
                            "nameserver 127.0.0.4\n" +
                            "nameserver 127.0.0.5\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, f2);

        DnsServerAddressStream stream = p.nameServerAddressStream("myhost.dc1.linecorp.local");
        assertHostNameEquals("127.0.0.4", stream.next());
        assertHostNameEquals("127.0.0.5", stream.next());
    }

    @Test
    public void ndotsIsParsedIfPresent() throws IOException {
        File f = buildFile("search localdomain\n" +
                           "nameserver 127.0.0.11\n" +
                           "options ndots:0\n");
        assertEquals(0, parseEtcResolverFirstNdots(f));

        f = buildFile("search localdomain\n" +
                      "nameserver 127.0.0.11\n" +
                      "options ndots:123 foo:goo\n");
        assertEquals(123, parseEtcResolverFirstNdots(f));
    }

    @Test
    public void defaultValueReturnedIfNdotsNotPresent() throws IOException {
        File f = buildFile("search localdomain\n" +
                           "nameserver 127.0.0.11\n");
        assertEquals(DEFAULT_NDOTS, parseEtcResolverFirstNdots(f));
    }

    @Test
    public void emptyEtcResolverDirectoryDoesNotThrow() throws IOException {
        File f = buildFile("domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, folder.newFolder().listFiles());

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
    }

    @Test
    public void searchDomainsWithOnlyDomain() throws IOException {
        File f = buildFile("domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("linecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithOnlySearch() throws IOException {
        File f = buildFile("search linecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("linecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearch() throws IOException {
        File f = buildFile("search linecorp.local\n" +
                           "search squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearchSeperatedByWhitespace() throws IOException {
        File f = buildFile("search linecorp.local squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearchSeperatedByTab() throws IOException {
        File f = buildFile("search linecorp.local\tsquarecorp.local\n" +
                "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsPrecedence() throws IOException {
        File f = buildFile("domain linecorp.local\n" +
                           "search squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("squarecorp.local"), domains);
    }

    private File buildFile(String contents) throws IOException {
        File f = folder.newFile();
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(contents.getBytes(CharsetUtil.UTF_8));
        } finally {
            out.close();
        }
        return f;
    }

    private static void assertHostNameEquals(String expectedHostname, InetSocketAddress next) {
        assertEquals("unexpected hostname: " + next, expectedHostname, next.getHostString());
    }
}
