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

import io.netty.util.internal.StringUtil;

import java.util.Arrays;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsDnskeyRecord} implementation.
 */
public final class DefaultDnsDnskeyRecord extends AbstractDnsRecord implements DnsDnskeyRecord {

    private final int flags;
    private final int protocol;
    private final int algorithm;
    private final byte[] publicKey;

    /**
     * Creates a new DNSKEY record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param flags the flags
     * @param protocol the protocol
     * @param algorithm the algorithm
     * @param publicKey the public key bytes
     */
    public DefaultDnsDnskeyRecord(String name, int dnsClass, long timeToLive,
                                  int flags, int protocol, int algorithm, byte[] publicKey) {
        super(name, DnsRecordType.DNSKEY, dnsClass, timeToLive);
        this.flags = flags & 0xffff;
        this.protocol = protocol & 0xff;
        this.algorithm = algorithm & 0xff;
        this.publicKey = checkNotNull(publicKey, "publicKey").clone();
    }

    @Override
    public int flags() {
        return flags;
    }

    @Override
    public int protocol() {
        return protocol;
    }

    @Override
    public int algorithm() {
        return algorithm;
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsDnskeyRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsDnskeyRecord that = (DnsDnskeyRecord) obj;
        return timeToLive() == that.timeToLive() &&
               flags == that.flags() &&
               protocol == that.protocol() &&
               algorithm == that.algorithm() &&
               Arrays.equals(publicKey, that.publicKey());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + flags;
        hashCode = 31 * hashCode + protocol;
        hashCode = 31 * hashCode + algorithm;
        hashCode = 31 * hashCode + Arrays.hashCode(publicKey);
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
                      .append(flags)
                      .append(' ')
                      .append(protocol)
                      .append(' ')
                      .append(algorithm)
                      .append(' ')
                      .append(Arrays.toString(publicKey))
                      .append(')');

        return buf.toString();
    }
}
