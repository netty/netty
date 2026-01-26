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
 * The default {@link DnsDsRecord} implementation.
 */
public final class DefaultDnsDsRecord extends AbstractDnsRecord implements DnsDsRecord {

    private final int keyTag;
    private final int algorithm;
    private final int digestType;
    private final byte[] digest;

    /**
     * Creates a new DS record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param keyTag the key tag
     * @param algorithm the algorithm
     * @param digestType the digest type
     * @param digest the digest bytes
     */
    public DefaultDnsDsRecord(String name, int dnsClass, long timeToLive,
                              int keyTag, int algorithm, int digestType, byte[] digest) {
        super(name, DnsRecordType.DS, dnsClass, timeToLive);
        this.keyTag = keyTag & 0xffff;
        this.algorithm = algorithm & 0xff;
        this.digestType = digestType & 0xff;
        this.digest = checkNotNull(digest, "digest").clone();
    }

    @Override
    public int keyTag() {
        return keyTag;
    }

    @Override
    public int algorithm() {
        return algorithm;
    }

    @Override
    public int digestType() {
        return digestType;
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsDsRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsDsRecord that = (DnsDsRecord) obj;
        return timeToLive() == that.timeToLive() &&
               keyTag == that.keyTag() &&
               algorithm == that.algorithm() &&
               digestType == that.digestType() &&
               Arrays.equals(digest, that.digest());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + keyTag;
        hashCode = 31 * hashCode + algorithm;
        hashCode = 31 * hashCode + digestType;
        hashCode = 31 * hashCode + Arrays.hashCode(digest);
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
                      .append(keyTag)
                      .append(' ')
                      .append(algorithm)
                      .append(' ')
                      .append(digestType)
                      .append(' ')
                      .append(Arrays.toString(digest))
                      .append(')');

        return buf.toString();
    }
}
