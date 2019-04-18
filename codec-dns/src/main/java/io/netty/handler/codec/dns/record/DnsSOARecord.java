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
 * Dns {@link DnsRecordType#SOA} record.
 */
public class DnsSOARecord extends AbstractDnsRecord {
    // The <domain-name> of the name server that was the original or primary source of data for this zone.
    private final String ns;

    // A <domain-name> which specifies the mailbox of the person responsible for this zone.
    private final String mailboxNs;

    //The unsigned 32 bit version number of the original copy of the zone.  Zone transfers preserve this value.
    // This value wraps and should be compared using sequence space arithmetic.
    private final int serial;

    // A 32 bit time interval before the zone should be refreshed.
    private final int refresh;

    // A 32 bit time interval that should elapse before a failed refresh should be retried.
    private final int retry;

    //A 32 bit time value that specifies the upper limit on the time interval that can
    // elapse before the zone is no longer authoritative.
    private final int expire;

    // The unsigned 32 bit minimum TTL field that should be exported with any RR from this zone.
    private final int minttl;

    public DnsSOARecord(String name, int dnsClass, long timeToLive, String ns, String mailboxNs, int serial,
                        int refresh, int retry, int expire, int minttl) {
        super(name, DnsRecordType.SOA, dnsClass, timeToLive);
        this.ns = checkNotNull(ns, "ns");
        this.mailboxNs = checkNotNull(mailboxNs, "mailboxNs");
        this.serial = serial;
        this.refresh = refresh;
        this.retry = retry;
        this.expire = expire;
        this.minttl = minttl;
    }

    public String ns() {
        return ns;
    }

    public String mailboxNs() {
        return mailboxNs;
    }

    public int serial() {
        return serial;
    }

    public int refresh() {
        return refresh;
    }

    public int retry() {
        return retry;
    }

    public int expire() {
        return expire;
    }

    public int minttl() {
        return minttl;
    }
}
