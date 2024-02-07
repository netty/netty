/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.netty.resolver.dns.UnixResolverDnsServerAddressStreamProvider.parseEtcResolverOptions;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnixResolverDnsServerAddressStreamProviderTest {
    @Test
    public void defaultLookupShouldReturnResultsIfOnlySingleFileSpecified(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
    }

    @Test
    public void nameServerAddressStreamShouldBeRotationalWhenRotationOptionsIsPresent(
        @TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "options rotate\n" +
            "domain linecorp.local\n" +
            "nameserver 127.0.0.2\n" +
            "nameserver 127.0.0.3\n" +
            "nameserver 127.0.0.4\n");
        UnixResolverDnsServerAddressStreamProvider p =
            new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());

        stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());
        assertHostNameEquals("127.0.0.2", stream.next());

        stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.4", stream.next());
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());

        stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());
    }

    @Test
    public void nameServerAddressStreamShouldAlwaysStartFromTheTopWhenRotationOptionsIsAbsent(
        @TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
            "nameserver 127.0.0.2\n" +
            "nameserver 127.0.0.3\n" +
            "nameserver 127.0.0.4\n");
        UnixResolverDnsServerAddressStreamProvider p =
            new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());

        stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());

        stream = p.nameServerAddressStream("");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
        assertHostNameEquals("127.0.0.4", stream.next());
    }

    @Test
    public void defaultReturnedWhenNoBetterMatch(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        File f2 = buildFile(tempDir, "domain squarecorp.local\n" +
                            "nameserver 127.0.0.4\n" +
                            "nameserver 127.0.0.5\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, f2);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
        assertHostNameEquals("127.0.0.3", stream.next());
    }

    @Test
    public void moreRefinedSelectionReturnedWhenMatch(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        File f2 = buildFile(tempDir, "domain dc1.linecorp.local\n" +
                            "nameserver 127.0.0.4\n" +
                            "nameserver 127.0.0.5\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, f2);

        DnsServerAddressStream stream = p.nameServerAddressStream("myhost.dc1.linecorp.local");
        assertHostNameEquals("127.0.0.4", stream.next());
        assertHostNameEquals("127.0.0.5", stream.next());
    }

    @Test
    public void ndotsOptionIsParsedIfPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options ndots:0\n");
        assertEquals(0, parseEtcResolverOptions(f).ndots());

        f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options ndots:123 foo:goo\n");
        assertEquals(123, parseEtcResolverOptions(f).ndots());
    }

    @Test
    public void defaultValueReturnedIfNdotsOptionsNotPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n");
        assertEquals(1, parseEtcResolverOptions(f).ndots());
    }

    @Test
    public void timeoutOptionIsParsedIfPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options timeout:0\n");
        assertEquals(0, parseEtcResolverOptions(f).timeout());

        f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options foo:bar timeout:124\n");
        assertEquals(124, parseEtcResolverOptions(f).timeout());
    }

    @Test
    public void defaultValueReturnedIfTimeoutOptionsIsNotPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n");
        assertEquals(5, parseEtcResolverOptions(f).timeout());
    }

    @Test
    public void attemptsOptionIsParsedIfPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options attempts:0\n");
        assertEquals(0, parseEtcResolverOptions(f).attempts());

        f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n" +
            "options foo:bar attempts:12\n");
        assertEquals(12, parseEtcResolverOptions(f).attempts());
    }

    @Test
    public void defaultValueReturnedIfAttemptsOptionsIsNotPresent(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search localdomain\n" +
            "nameserver 127.0.0.11\n");
        assertEquals(16, parseEtcResolverOptions(f).attempts());
    }

    @Test
    public void emptyEtcResolverDirectoryDoesNotThrow(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n" +
                           "nameserver 127.0.0.3\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, tempDir.resolve("netty-empty").toFile().listFiles());

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
    }

    @Test
    public void searchDomainsWithOnlyDomain(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("linecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithOnlySearch(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search linecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("linecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearch(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search linecorp.local\n" +
                           "search squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearchSeperatedByWhitespace(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search linecorp.local squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsWithMultipleSearchSeperatedByTab(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "search linecorp.local\tsquarecorp.local\n" +
                "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Arrays.asList("linecorp.local", "squarecorp.local"), domains);
    }

    @Test
    public void searchDomainsPrecedence(@TempDir Path tempDir) throws IOException {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                           "search squarecorp.local\n" +
                           "nameserver 127.0.0.2\n");
        List<String> domains = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains(f);
        assertEquals(Collections.singletonList("squarecorp.local"), domains);
    }

    @Test
    public void ignoreInvalidEntries(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain netty.local\n" +
                "nameserver nil\n" +
                "nameserver 127.0.0.3\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertEquals(1, stream.size());
        assertHostNameEquals("127.0.0.3", stream.next());
    }

    private File buildFile(Path tempDir, String contents) throws IOException {
        Path path = tempDir.resolve("netty-dns-" + UUID.randomUUID().toString().substring(24) + ".txt");
        Files.write(path, contents.getBytes(CharsetUtil.UTF_8));
        return path.toFile();
    }

    @Test
    public void ignoreComments(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "domain linecorp.local\n" +
                "nameserver 127.0.0.2 #somecomment\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("127.0.0.2", stream.next());
    }

    @Test
    public void ipv6Nameserver(@TempDir Path tempDir) throws Exception {
        File f = buildFile(tempDir, "search localdomain\n" +
                "nameserver 10.211.55.1\n" +
                "nameserver fe80::21c:42ff:fe00:18%nonexisting\n");
        UnixResolverDnsServerAddressStreamProvider p =
                new UnixResolverDnsServerAddressStreamProvider(f, null);

        DnsServerAddressStream stream = p.nameServerAddressStream("somehost");
        assertHostNameEquals("10.211.55.1", stream.next());
    }

    private static void assertHostNameEquals(String expectedHostname, InetSocketAddress next) {
        assertEquals(expectedHostname, next.getHostString(), "unexpected hostname: " + next);
    }
}
