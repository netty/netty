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
 * The default {@link DnsSshfpRecord} implementation.
 */
public final class DefaultDnsSshfpRecord extends AbstractDnsRecord implements DnsSshfpRecord {

    private final int algorithm;
    private final int fingerprintType;
    private final byte[] fingerprint;

    /**
     * Creates a new SSHFP record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param algorithm the algorithm
     * @param fingerprintType the fingerprint type
     * @param fingerprint the fingerprint bytes
     */
    public DefaultDnsSshfpRecord(String name, int dnsClass, long timeToLive,
                                 int algorithm, int fingerprintType, byte[] fingerprint) {
        super(name, DnsRecordType.SSHFP, dnsClass, timeToLive);
        this.algorithm = algorithm & 0xff;
        this.fingerprintType = fingerprintType & 0xff;
        this.fingerprint = checkNotNull(fingerprint, "fingerprint").clone();
    }

    @Override
    public int algorithm() {
        return algorithm;
    }

    @Override
    public int fingerprintType() {
        return fingerprintType;
    }

    @Override
    public byte[] fingerprint() {
        return fingerprint.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsSshfpRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsSshfpRecord that = (DnsSshfpRecord) obj;
        return timeToLive() == that.timeToLive() &&
               algorithm == that.algorithm() &&
               fingerprintType == that.fingerprintType() &&
               Arrays.equals(fingerprint, that.fingerprint());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + algorithm;
        hashCode = 31 * hashCode + fingerprintType;
        hashCode = 31 * hashCode + Arrays.hashCode(fingerprint);
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
                      .append(algorithm)
                      .append(' ')
                      .append(fingerprintType)
                      .append(' ')
                      .append(Arrays.toString(fingerprint))
                      .append(')');

        return buf.toString();
    }
}
