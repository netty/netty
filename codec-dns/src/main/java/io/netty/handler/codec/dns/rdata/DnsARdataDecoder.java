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

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DnsARdataDecoder implements DnsRdataDecoder<InetAddress> {
    public static final DnsARdataDecoder DEFAULT = new DnsARdataDecoder();
    private static final int IPV4_LEN = 4;

    /**
     * Decode record data to {@link InetAddress}.
     *
     * @param in record data
     *
     * @return ipv4 address
     *
     * @throws CorruptedFrameException if the record data frame is illegal
     */
    @Override
    public InetAddress decodeRdata(ByteBuf in) {
        if (in.readableBytes() < IPV4_LEN) {
            throw new CorruptedFrameException("illegal ipv4 length");
        }
        byte[] address = new byte[IPV4_LEN];
        in.readBytes(address);
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException("unknown ipv4 host", e);
        }
    }
}
