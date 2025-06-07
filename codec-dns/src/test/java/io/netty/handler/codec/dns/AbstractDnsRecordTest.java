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
package io.netty.handler.codec.dns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractDnsRecordTest {

    @Test
    public void testValidDomainName() {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        AbstractDnsRecord record = new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
        assertEquals(name + '.', record.name());
    }

    @Test
    public void testValidDomainNameUmlaut() {
        String name = "ä";
        AbstractDnsRecord record = new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
        assertEquals("xn--4ca.", record.name());
    }

    @Test
    public void testValidDomainNameTrailingDot() {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.";
        AbstractDnsRecord record = new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
        assertEquals(name, record.name());
    }

    @Test
    public void testValidDomainNameUmlautTrailingDot() {
        String name = "ä.";
        AbstractDnsRecord record = new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
        assertEquals("xn--4ca.", record.name());
    }

    @Test
    public void testValidDomainNameLength() {
        final String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
            }
        });
    }

    @Test
    public void testValidDomainNameUmlautLength() {
        final String name = "äaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new AbstractDnsRecord(name, DnsRecordType.A, 0) { };
            }
        });
    }

    /*
     * RFC-1034 Section 3.1 (page 7 & 8)
     * RFC-1035 Section 2.3.3 (page 9 & 10)
     */
    @Test
    public void testEqualsAndHashCodeIgnoreCase() {
        AbstractDnsRecord lowerCase = new AbstractDnsRecord("example.com.", DnsRecordType.A, 0) { };
        AbstractDnsRecord upperCase = new AbstractDnsRecord("EXAMPLE.COM.", DnsRecordType.A, 0) { };
        assertEquals(lowerCase.hashCode(), upperCase.hashCode());
        assertEquals(lowerCase, upperCase);
    }
}
