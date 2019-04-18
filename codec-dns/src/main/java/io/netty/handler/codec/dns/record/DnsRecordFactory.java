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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.HashMap;
import java.util.Map;

public final class DnsRecordFactory {
    private static final DnsRecordFactory INSTANCE = new DnsRecordFactory();
    private static final Map<DnsRecordType, DnsRecordSupplier> RECORD_SUPPLIERS =
            new HashMap<DnsRecordType, DnsRecordSupplier>();

    static {
        RECORD_SUPPLIERS.put(DnsRecordType.A, new DnsRecordSupplier() {
            @Override
            public DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData) {
                return new DnsARecord(name, dnsClass, timeToLive, rData);
            }
        });
        RECORD_SUPPLIERS.put(DnsRecordType.NS, new DnsRecordSupplier() {
            @Override
            public DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData) {
                return new DnsNSRecord(name, dnsClass, timeToLive, rData);
            }
        });
        RECORD_SUPPLIERS.put(DnsRecordType.CNAME, new DnsRecordSupplier() {
            @Override
            public DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData) {
                return new DnsCNAMERecord(name, dnsClass, timeToLive, rData);
            }
        });
        RECORD_SUPPLIERS.put(DnsRecordType.AAAA, new DnsRecordSupplier() {
            @Override
            public DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData) {
                return new DnsAAAARecord(name, dnsClass, timeToLive, rData);
            }
        });
        RECORD_SUPPLIERS.put(DnsRecordType.PTR, new DnsRecordSupplier() {
            @Override
            public DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData) {
                return new DnsPTRRecord(name, dnsClass, timeToLive, rData);
            }
        });
    }

    private DnsRecordFactory() {
        // Pirvate constructor for factory class
    }

    public static DnsRecordFactory getInstance() {
        return INSTANCE;
    }

    public DnsRecord dnsRecord(String name, DnsRecordType type, int dnsClass, long timeToLive, ByteBuf rData) {
        DnsRecordSupplier supplier = RECORD_SUPPLIERS.get(type);
        DnsRecord record;
        if (supplier != null) {
            record = supplier.supply(name, dnsClass, timeToLive, rData);
        } else {
            record = new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, rData);
        }
        record.decodeRdata();
        return record;
    }

    interface DnsRecordSupplier {
        DnsRecord supply(String name, int dnsClass, long timeToLive, ByteBuf rData);
    }
}
