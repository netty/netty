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
import io.netty.handler.codec.dns.record.DnsARecord;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

/**
 * Codec for {@link DnsARecord}.
 */
public class DnsARDataCodec implements DnsRDataCodec<DnsARecord> {
    public static final DnsARDataCodec DEFAULT = new DnsARDataCodec();
    private static final int IPV4_LEN = 4;

    @Override
    public DnsARecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        checkReadable(rData, IPV4_LEN, "ipv4");
        byte[] addressBytes = new byte[IPV4_LEN];
        rData.readBytes(addressBytes);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException("unknown ipv4 host", e);
        }
        return new DnsARecord(name, dnsClass, timeToLive, address);
    }

    @Override
    public void encodeRData(DnsARecord record, ByteBuf out) {
        out.writeBytes(record.address().getAddress());
    }
}
