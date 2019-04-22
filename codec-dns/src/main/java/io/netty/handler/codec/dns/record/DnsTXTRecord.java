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

import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Dns {@link DnsRecordType#TXT} record.
 */
public class DnsTXTRecord extends AbstractDnsRecord {
    // One or more <character-string>s.
    private final List<String> txt;

    public DnsTXTRecord(String name, int dnsClass, long timeToLive, List<String> txt) {
        super(name, DnsRecordType.TXT, dnsClass, timeToLive);
        this.txt = Collections.unmodifiableList(checkNotNull(txt, "txt"));
    }

    public List<String> txt() {
        return txt;
    }

    @Override
    protected String readableRDataStr() {
        StringBuilder builder = new StringBuilder(32);
        for (String s : txt) {
            builder.append(s);
        }
        return builder.toString();
    }
}
