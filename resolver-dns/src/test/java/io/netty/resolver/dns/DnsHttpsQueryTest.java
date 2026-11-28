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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DnsHttpsQueryTest {

    @Test
    public void testSchemeIsCaseInsensitive() {
        DnsHttpsQuery query = new DnsHttpsQuery("HTTPS", "example.com", 443);

        assertThat(query.scheme()).isEqualTo("https");
        assertThat(query).isEqualTo(new DnsHttpsQuery("https", "EXAMPLE.com", 443));
        assertThat(query.toString()).isEqualTo("https://example.com:443");
    }

    @Test
    public void testOnlyHttpSchemes() {
        assertThatThrownBy(() -> new DnsHttpsQuery("ftp", "example.com", 21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPortRange() {
        assertThatThrownBy(() -> new DnsHttpsQuery("https", "example.com", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DnsHttpsQuery("https", "example.com", 65536))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testHostRequired() {
        assertThatThrownBy(() -> new DnsHttpsQuery("https", null, 443)).isInstanceOf(NullPointerException.class);
    }
}
