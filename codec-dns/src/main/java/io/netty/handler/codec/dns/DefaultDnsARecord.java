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

import io.netty.util.NetUtil;
import io.netty.util.internal.StringUtil;

import java.util.Arrays;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsARecord} implementation.
 */
public final class DefaultDnsARecord extends AbstractDnsRecord implements DnsARecord {

    private static final int IPV4_LENGTH = 4;

    private final byte[] address;

    /**
     * Creates a new A record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param address the IPv4 address bytes
     */
    public DefaultDnsARecord(String name, int dnsClass, long timeToLive, byte[] address) {
        super(name, DnsRecordType.A, dnsClass, timeToLive);
        this.address = verifyAddress(address, IPV4_LENGTH);
    }

    @Override
    public byte[] address() {
        return address.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsARecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsARecord that = (DnsARecord) obj;
        return timeToLive() == that.timeToLive() &&
               Arrays.equals(address, that.address());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + Arrays.hashCode(address);
        return hashCode;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        buf.append(name().isEmpty() ? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(' ')
                      .append(NetUtil.bytesToIpAddress(address))
                      .append(')');

        return buf.toString();
    }

    private static byte[] verifyAddress(byte[] address, int length) {
        checkNotNull(address, "address");
        if (address.length != length) {
            throw new IllegalArgumentException("address.length: " + address.length + " (expected: " + length + ')');
        }
        return address.clone();
    }
}
