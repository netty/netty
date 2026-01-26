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
 * The default {@link DnsCertRecord} implementation.
 */
public final class DefaultDnsCertRecord extends AbstractDnsRecord implements DnsCertRecord {

    private final int certificateType;
    private final int keyTag;
    private final int algorithm;
    private final byte[] certificate;

    /**
     * Creates a new CERT record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param certificateType the certificate type
     * @param keyTag the key tag
     * @param algorithm the algorithm
     * @param certificate the certificate bytes
     */
    public DefaultDnsCertRecord(String name, int dnsClass, long timeToLive,
                                int certificateType, int keyTag, int algorithm, byte[] certificate) {
        super(name, DnsRecordType.CERT, dnsClass, timeToLive);
        this.certificateType = certificateType & 0xffff;
        this.keyTag = keyTag & 0xffff;
        this.algorithm = algorithm & 0xff;
        this.certificate = checkNotNull(certificate, "certificate").clone();
    }

    @Override
    public int certificateType() {
        return certificateType;
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
    public byte[] certificate() {
        return certificate.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsCertRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsCertRecord that = (DnsCertRecord) obj;
        return timeToLive() == that.timeToLive() &&
               certificateType == that.certificateType() &&
               keyTag == that.keyTag() &&
               algorithm == that.algorithm() &&
               Arrays.equals(certificate, that.certificate());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + certificateType;
        hashCode = 31 * hashCode + keyTag;
        hashCode = 31 * hashCode + algorithm;
        hashCode = 31 * hashCode + Arrays.hashCode(certificate);
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
                      .append(certificateType)
                      .append(' ')
                      .append(keyTag)
                      .append(' ')
                      .append(algorithm)
                      .append(' ')
                      .append(Arrays.toString(certificate))
                      .append(')');

        return buf.toString();
    }
}
