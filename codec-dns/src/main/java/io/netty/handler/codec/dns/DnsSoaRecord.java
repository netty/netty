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
package io.netty.handler.codec.dns;

/**
 * A DNS {@code SOA} (Start of Authority) record as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc1035#section-3.3.13">RFC 1035</a>.
 * <p>
 * SOA records mark the start of a zone of authority and contain parameters
 * for zone transfers and caching.
 */
public interface DnsSoaRecord extends DnsRecord {

    /**
     * Returns the domain name of the primary nameserver for the zone.
     */
    String mname();

    /**
     * Returns the mailbox of the person responsible for the zone.
     * <p>
     * This is encoded as a domain name where the first label is the local part
     * of the email address (with dots escaped or replaced) and the remaining
     * labels form the domain. For example, {@code hostmaster.example.com.}
     * represents {@code hostmaster@example.com}.
     */
    String rname();

    /**
     * Returns the serial number of the zone (unsigned 32-bit).
     * <p>
     * This value is used by secondary nameservers to detect zone changes.
     * It should be incremented each time the zone data is modified.
     */
    long serial();

    /**
     * Returns the refresh interval in seconds (unsigned 32-bit).
     * <p>
     * This specifies how often secondary nameservers should check for zone updates.
     */
    long refresh();

    /**
     * Returns the retry interval in seconds (unsigned 32-bit).
     * <p>
     * This specifies how long a secondary nameserver should wait before retrying
     * a failed zone transfer.
     */
    long retry();

    /**
     * Returns the expire time in seconds (unsigned 32-bit).
     * <p>
     * This specifies the maximum time a secondary nameserver should continue
     * to use zone data if it cannot contact the primary nameserver.
     */
    long expire();

    /**
     * Returns the minimum TTL in seconds (unsigned 32-bit).
     * <p>
     * Per RFC 2308, this value is used as the TTL for negative caching
     * (NXDOMAIN and NODATA responses).
     */
    long minimum();
}
