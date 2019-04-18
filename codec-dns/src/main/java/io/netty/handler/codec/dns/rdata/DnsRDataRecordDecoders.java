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

package io.netty.handler.codec.dns.rdata;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.HashMap;
import java.util.Map;

public final class DnsRDataRecordDecoders {
    private static final Map<DnsRecordType, DnsRDataRecordDecoder<? extends DnsRecord>> DECODERS =
            new HashMap<DnsRecordType, DnsRDataRecordDecoder<? extends DnsRecord>>();

    static {
        DECODERS.put(DnsRecordType.A, DnsARecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.NS, DnsNSRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.CNAME, DnsCNAMERecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.SOA, DnsSOARecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.PTR, DnsPTRRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.MX, DnsMXRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.TXT, DnsTXTRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.RP, DnsRPRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.AFSDB, DnsAFSDBRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.SIG, DnsSIGRecordDecoder.DEFAULT);
        DECODERS.put(DnsRecordType.AAAA, DnsAAAARecordDecoder.DEFAULT);
    }

    private DnsRDataRecordDecoders() {
        // Private constructor for factory class
    }

    public static DnsRDataRecordDecoder<? extends DnsRecord> rDataDecoder(DnsRecordType type) {
        return DECODERS.get(type);
    }
}
