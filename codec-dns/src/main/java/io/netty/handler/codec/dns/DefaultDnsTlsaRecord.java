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
 * The default {@link DnsTlsaRecord} implementation.
 */
public final class DefaultDnsTlsaRecord extends AbstractDnsRecord implements DnsTlsaRecord {

    private final int usage;
    private final int selector;
    private final int matchingType;
    private final byte[] associationData;

    /**
     * Creates a new TLSA record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param usage the usage
     * @param selector the selector
     * @param matchingType the matching type
     * @param associationData the association data
     */
    public DefaultDnsTlsaRecord(String name, int dnsClass, long timeToLive,
                                int usage, int selector, int matchingType, byte[] associationData) {
        super(name, DnsRecordType.TLSA, dnsClass, timeToLive);
        this.usage = usage & 0xff;
        this.selector = selector & 0xff;
        this.matchingType = matchingType & 0xff;
        this.associationData = checkNotNull(associationData, "associationData").clone();
    }

    @Override
    public int usage() {
        return usage;
    }

    @Override
    public int selector() {
        return selector;
    }

    @Override
    public int matchingType() {
        return matchingType;
    }

    @Override
    public byte[] associationData() {
        return associationData.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsTlsaRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsTlsaRecord that = (DnsTlsaRecord) obj;
        return timeToLive() == that.timeToLive() &&
               usage == that.usage() &&
               selector == that.selector() &&
               matchingType == that.matchingType() &&
               Arrays.equals(associationData, that.associationData());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + usage;
        hashCode = 31 * hashCode + selector;
        hashCode = 31 * hashCode + matchingType;
        hashCode = 31 * hashCode + Arrays.hashCode(associationData);
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
                      .append(usage)
                      .append(' ')
                      .append(selector)
                      .append(' ')
                      .append(matchingType)
                      .append(' ')
                      .append(Arrays.toString(associationData))
                      .append(')');

        return buf.toString();
    }
}
