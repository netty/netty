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

package io.netty.handler.codec.dns.rdata.opt;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.record.opt.EDNS0SubnetOption;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

public class EDNS0SubnetOptionDataCodec implements EDNS0OptionDataCodec<EDNS0SubnetOption> {
    public static final EDNS0SubnetOptionDataCodec DEFAULT = new EDNS0SubnetOptionDataCodec();

    @Override
    public EDNS0SubnetOption decodeOptionData(ByteBuf optionData) {
        checkShortReadable(optionData, "family");
        short family = optionData.readShort();
        checkByteReadable(optionData, "source prefix length");
        byte sourcePrefixLength = optionData.readByte();
        checkByteReadable(optionData, "scope prefix length");
        byte scopePrefixLength = optionData.readByte();

        InetAddress address;
        try {
            switch (family) {
            case 0: // Address family reversed
                if (sourcePrefixLength != 0) {
                    throw new CorruptedFrameException("bad address family");
                }
                address = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0 });
                break;
            case 1: // Address family ipv4
                if (sourcePrefixLength > IPV4_LEN * 8 || scopePrefixLength > IPV4_LEN * 8) {
                    throw new CorruptedFrameException("bad netmask");
                }
                byte[] addressIpv4Bytes = new byte[IPV4_LEN];
                checkReadable(optionData, IPV4_LEN, "ipv4 address");
                optionData.readBytes(addressIpv4Bytes);
                address = InetAddress.getByAddress(addressIpv4Bytes);
                break;
            case 2: // Address family ipv6
                if (sourcePrefixLength > IPV6_LEN * 8 || scopePrefixLength > IPV6_LEN * 8) {
                    throw new CorruptedFrameException("bad netmask");
                }
                byte[] addressIpv6Bytes = new byte[IPV4_LEN];
                checkReadable(optionData, IPV6_LEN, "ipv6 address");
                optionData.readBytes(addressIpv6Bytes);
                address = InetAddress.getByAddress(addressIpv6Bytes);
                break;
            default: // Bad address family
                throw new CorruptedFrameException("bad address family");
            }
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException("unknown host");
        }

        return new EDNS0SubnetOption(family, sourcePrefixLength, scopePrefixLength, address);
    }

    @Override
    public void encodeOptionData(EDNS0SubnetOption option, ByteBuf out) {
        out.writeShort(option.family())
           .writeByte(option.sourcePrefixLength())
           .writeByte(option.scopePrefixLength());

        switch (option.family()) {
        case 0: // Address family reversed
            if (option.sourcePrefixLength() != 0) {
                throw new CorruptedFrameException("bad address familyu");
            }
            break;
        case 1:
            if (option.sourcePrefixLength() > IPV4_LEN * 8) {
                throw new CorruptedFrameException("bad netmask");
            }

            if (option.address().getAddress().length != IPV4_LEN) {
                throw new CorruptedFrameException("bad ipv4 address");
            }
            out.writeBytes(option.address().getAddress());
            break;
        case 2:
            if (option.sourcePrefixLength() > IPV6_LEN * 8) {
                throw new CorruptedFrameException("bad netmask");
            }

            if (option.address().getAddress().length != IPV6_LEN) {
                throw new CorruptedFrameException("bad ipv6 address");
            }
            out.writeBytes(option.address().getAddress());
            break;
        default:
            throw new CorruptedFrameException("bad address family");
        }
    }
}
