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
 * Dns {@link DnsRecordType#MX} record.
 */
public class DnsMXRecord extends AbstractDnsRecord {
    // A 16 bit integer which specifies the preference given to this RR among
    // others at the same owner.  Lower values are preferred.
    private final short preference;

    //A <domain-name> which specifies a host willing to act asa mail exchange for the owner name.
    private final String exchange;

    public DnsMXRecord(String name, int dnsClass, long timeToLive, short preference,
                       String exchange) {
        super(name, DnsRecordType.MX, dnsClass, timeToLive);
        this.preference = preference;
        this.exchange = checkNotNull(exchange, "exchange");
    }

    public short preference() {
        return preference;
    }

    public String exchange() {
        return exchange;
    }
}
