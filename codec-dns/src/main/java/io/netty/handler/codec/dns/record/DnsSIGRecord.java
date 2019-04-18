/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.dns.record;

import io.netty.handler.codec.dns.AbstractDnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Dns {@link DnsRecordType#SIG} record.
 */
public class DnsSIGRecord extends AbstractDnsRecord {
    // The type of the other RRs covered by this SIG.
    private final short typeCovered;

    // Algorithem described in https://tools.ietf.org/html/rfc2535#section-3.2.
    private final byte algorithem;

    //The "labels" octet is an unsigned count of how many labels there are
    // in the original SIG RR owner name not counting the null label for
    // root and not counting any initial "*" for a wildcard.
    private final byte labels;

    private final int originalTTL;
    private final int expiration;
    private final int inception;
    private final short keyTag;
    private final String signerName;
    private final String signature;

    public DnsSIGRecord(String name, int dnsClass, long timeToLive, short typeCovered, byte algorithem, byte labels,
                        int originalTTL, int expiration, int inception, short keyTag, String signerName,
                        String signature) {
        super(name, DnsRecordType.SIG, dnsClass, timeToLive);
        this.typeCovered = typeCovered;
        this.algorithem = algorithem;
        this.labels = labels;
        this.originalTTL = originalTTL;
        this.expiration = expiration;
        this.inception = inception;
        this.keyTag = keyTag;
        this.signerName = checkNotNull(signerName, "singerName");
        this.signature = checkNotNull(signature, "signature");
    }

    public short typeCovered() {
        return typeCovered;
    }

    public byte algorithem() {
        return algorithem;
    }

    public byte labels() {
        return labels;
    }

    public int originalTTL() {
        return originalTTL;
    }

    public int expiration() {
        return expiration;
    }

    public int inception() {
        return inception;
    }

    public short keyTag() {
        return keyTag;
    }

    public String signerName() {
        return signerName;
    }

    public String signature() {
        return signature;
    }
}
