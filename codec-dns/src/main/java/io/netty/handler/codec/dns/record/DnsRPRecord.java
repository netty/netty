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

public class DnsRPRecord extends AbstractDnsRecord {
    // A domain name that specifies the mailbox for the responsible person.
    private final String mbox;

    // A domain name for which TXT RR's exist
    private final String txt;

    public DnsRPRecord(String name, int dnsClass, long timeToLive, String mbox, String txt) {
        super(name, DnsRecordType.RP, dnsClass, timeToLive);
        this.mbox = checkNotNull(mbox, "mbox");
        this.txt = checkNotNull(txt, "txt");
    }

    public String mbox() {
        return mbox;
    }

    public String txt() {
        return txt;
    }
}
