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
import io.netty.handler.codec.dns.record.DnsMXRecord;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;
import static io.netty.handler.codec.dns.util.DnsEncodeUtil.*;

/**
 * Codec for {@link DnsMXRecord}.
 */
public class DnsMXRDataCodec implements DnsRDataCodec<DnsMXRecord> {
    public static final DnsMXRDataCodec DEFAULT = new DnsMXRDataCodec();

    @Override
    public DnsMXRecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        checkShortReadable(rData, "preference");
        short preference = rData.readShort();
        String exchange = decodeDomainName(rData);
        return new DnsMXRecord(name, dnsClass, timeToLive, preference, exchange);
    }

    @Override
    public void encodeRData(DnsMXRecord record, ByteBuf out) {
        out.writeShort(record.preference());
        encodeDomainName(record.exchange(), out);
    }
}
