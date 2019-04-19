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
import io.netty.handler.codec.dns.record.DnsAAAARecord;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

/**
 * Codec for {@link DnsAAAARecord}.
 */
public class DnsAAAARDataCodec implements DnsRDataCodec<DnsAAAARecord> {
    public static final DnsAAAARDataCodec DEFAULT = new DnsAAAARDataCodec();

    @Override
    public DnsAAAARecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        checkReadable(rData, IPV6_LEN, "ipv6");
        byte[] addressBytes = new byte[IPV6_LEN];
        rData.readBytes(addressBytes);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException("unknown ipv6 host", e);
        }
        return new DnsAAAARecord(name, dnsClass, timeToLive, address);
    }

    @Override
    public void encodeRData(DnsAAAARecord record, ByteBuf out) {
        out.writeBytes(record.address().getAddress());
    }
}
