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
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.rdata.DnsAAAARdataDecoder;

import java.net.InetAddress;

public class DnsAAAARecord extends DefaultDnsRawRecord {
    private InetAddress address;

    public DnsAAAARecord(String name, int dnsClass, long timeToLive, ByteBuf content) {
        super(name, DnsRecordType.AAAA, dnsClass, timeToLive, content);
    }

    public InetAddress address() {
        return address;
    }

    @Override
    public void decodeRdata() {
        address = DnsAAAARdataDecoder.DEFAULT.decodeRdata(content());
    }
}
