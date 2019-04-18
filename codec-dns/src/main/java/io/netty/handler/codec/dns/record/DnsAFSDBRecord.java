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
 * Dns {@link DnsRecordType#AFSDB} record.
 */
public class DnsAFSDBRecord extends AbstractDnsRecord {
    // The subtype field is a 16 bit integer
    private final short subtype;

    //The hostname field is a domain name of a host that has a server for the cell named by the owner name of the RR.
    private final String hostname;

    public DnsAFSDBRecord(String name, int dnsClass, long timeToLive, short subtype,
                          String hostname) {
        super(name, DnsRecordType.AFSDB, dnsClass, timeToLive);
        this.subtype = subtype;
        this.hostname = checkNotNull(hostname, "hostname");
    }

    public short subtype() {
        return subtype;
    }

    public String hostname() {
        return hostname;
    }
}
