/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.dns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractDnsRecordTest {

    @Test
    public void testValidDomainName() {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        AbstractDnsRecord record = new TestDnsRecord(name, DnsRecordType.A, 0);
        assertEquals(name + '.', record.name());
    }

    @Test
    public void testValidDomainNameUmlaut() {
        String name = "ä";
        AbstractDnsRecord record = new TestDnsRecord(name, DnsRecordType.A, 0);
        assertEquals("xn--4ca.", record.name());
    }

    @Test
    public void testValidDomainNameTrailingDot() {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.";
        AbstractDnsRecord record = new TestDnsRecord(name, DnsRecordType.A, 0);
        assertEquals(name, record.name());
    }

    @Test
    public void testValidDomainNameUmlautTrailingDot() {
        String name = "ä.";
        AbstractDnsRecord record = new TestDnsRecord(name, DnsRecordType.A, 0);
        assertEquals("xn--4ca.", record.name());
    }

    @Test
    public void testValidDomainNameLength() {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThrows(IllegalArgumentException.class, () -> new TestDnsRecord(name, DnsRecordType.A, 0));
    }

    @Test
    public void testValidDomainNameUmlautLength() {
        String name = "äaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThrows(IllegalArgumentException.class, () -> new TestDnsRecord(name, DnsRecordType.A, 0));
    }

    private static final class TestDnsRecord extends AbstractDnsRecord {
        TestDnsRecord(String name, DnsRecordType type, long timeToLive) {
            super(name, type, timeToLive);
        }

        @Override
        public DnsRecord copy() {
            return this; // This class is immutable.
        }
    }
}
