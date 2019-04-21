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

/**
 * DnsRDataCodecs is a factory class that provides the ability to get RData codecs by dns record type.
 */
public final class DnsRDataCodecs {
    private static final Map<DnsRecordType, DnsRDataCodec<? extends DnsRecord>> CODECS =
            new HashMap<DnsRecordType, DnsRDataCodec<? extends DnsRecord>>();

    static {
        CODECS.put(DnsRecordType.A, DnsARDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.NS, DnsNSRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.CNAME, DnsCNAMERDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.SOA, DnsSOARDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.PTR, DnsPTRRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.MX, DnsMXRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.TXT, DnsTXTRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.RP, DnsRPRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.AFSDB, DnsAFSDBRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.SIG, DnsSIGRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.OPT, DnsOPTRDataCodec.DEFAULT);
        CODECS.put(DnsRecordType.AAAA, DnsAAAARDataCodec.DEFAULT);
    }

    private DnsRDataCodecs() {
        // Private constructor for factory class
    }

    public static DnsRDataDecoder<? extends DnsRecord> rDataDecoder(DnsRecordType type) {
        return CODECS.get(type);
    }

    public static DnsRDataEncoder<? extends DnsRecord> rDataEncoder(DnsRecordType type) {
        return CODECS.get(type);
    }

    public static DnsRDataCodec<? extends DnsRecord> rDataCodec(DnsRecordType type) {
        return CODECS.get(type);
    }
}
