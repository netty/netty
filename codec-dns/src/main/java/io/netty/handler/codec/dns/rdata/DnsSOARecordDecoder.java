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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.record.DnsSOARecord;
import io.netty.handler.codec.dns.util.DnsDecodeUtil;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

/**
 * Decoder for {@link DnsSOARecord}.
 */
public class DnsSOARecordDecoder implements DnsRDataRecordDecoder<DnsSOARecord> {
    public static final DnsSOARecordDecoder DEFAULT = new DnsSOARecordDecoder();

    @Override
    public DnsSOARecord decodeRecordWithHeader(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        String ns = decodeDomainName(rData);
        String mailboxNs = decodeDomainName(rData);
        if (!rData.isReadable(Integer.BYTES)) {
            throw new CorruptedFrameException("illegal soa serial length");
        }
        checkIntReadable(rData, "serial");
        int serial = rData.readInt();
        checkIntReadable(rData, "refresh");
        int refresh = rData.readInt();
        checkIntReadable(rData, "retry");
        int retry = rData.readInt();
        checkIntReadable(rData, "expire");
        int expire = rData.readInt();
        checkIntReadable(rData, "minttl");
        int minttl = rData.readInt();
        return new DnsSOARecord(name, dnsClass, timeToLive, ns, mailboxNs,
                                serial, refresh, retry, expire, minttl);
    }

}
