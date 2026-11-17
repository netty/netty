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

import io.netty.handler.codec.dns.ProtocolId;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DnsHttpsAddressTest {

    @Test
    public void testAddressNotResolvedThroughHttps() throws Exception {
        DnsHttpsAddress address = DnsHttpsAddress.of(InetAddress.getByName("192.0.2.1"), 443);

        assertThat(address.address()).isEqualTo(InetAddress.getByName("192.0.2.1"));
        assertThat(address.port()).isEqualTo(443);
        assertThat(address.protocols()).containsExactly(ProtocolId.HTTP_1_1);
        assertThat(address.ech()).isNull();
        assertThat(address.priority()).isEqualTo(-1);
        assertThat(address.isHttpsResolved()).isFalse();
        assertThat(address.ttl()).isNull();
    }

    @Test
    public void testAddressNotResolvedThroughHttpsFromSocketAddress() throws Exception {
        DnsHttpsAddress address = DnsHttpsAddress.of(new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 443));

        assertThat(address).isEqualTo(DnsHttpsAddress.of(InetAddress.getByName("192.0.2.1"), 443));
    }

    @Test
    public void testUnresolvedSocketAddressIsRejected() {
        assertThatThrownBy(() -> DnsHttpsAddress.of(InetSocketAddress.createUnresolved("example.com", 443)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPriorityRange() throws Exception {
        // -1 when not resolved through an HTTPS record, otherwise the 16-bit priority of the record.
        assertThatThrownBy(() -> new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.HTTP_1_1), null, -2, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.HTTP_1_1), null, 65536, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPortIsValid() throws Exception {
        // RFC 9460, Section 7.2: the port of a record is between 0 and 65535, and port 0 is reported as is.
        assertThat(DnsHttpsAddress.of(InetAddress.getByName("192.0.2.1"), 0).port()).isEqualTo(0);
        assertThatThrownBy(() -> DnsHttpsAddress.of(InetAddress.getByName("192.0.2.1"), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DnsHttpsAddress.of(InetAddress.getByName("192.0.2.1"), 65536))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEquality() throws Exception {
        DnsHttpsAddress address = new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.H2), new byte[] { 1 }, 1, null);

        assertThat(address).isEqualTo(new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.H2), new byte[] { 1 }, 1, null));
        assertThat(address).isNotEqualTo(new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.H2), new byte[] { 1 }, -1, null));
        assertThat(address.hashCode()).isEqualTo(new DnsHttpsAddress(InetAddress.getByName("192.0.2.1"), 443,
                Collections.singletonList(ProtocolId.H2), new byte[] { 1 }, 1, null).hashCode());
    }
}
