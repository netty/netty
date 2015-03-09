/*
 * Copyright 2015 The Netty Project
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

package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.EncoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

/**
 * Encodes a SOCKS5 address into binary representation.
 *
 * @see Socks5ClientEncoder
 * @see Socks5ServerEncoder
 */
public interface Socks5AddressEncoder {

    Socks5AddressEncoder DEFAULT = new Socks5AddressEncoder() {
        @Override
        public void encodeAddress(Socks5AddressType addrType, String addrValue, ByteBuf out) throws Exception {
            final byte typeVal = addrType.byteValue();
            if (typeVal == Socks5AddressType.IPv4.byteValue()) {
                if (addrValue != null) {
                    out.writeBytes(NetUtil.createByteArrayFromIpAddressString(addrValue));
                } else {
                    out.writeInt(0);
                }
            } else if (typeVal == Socks5AddressType.DOMAIN.byteValue()) {
                if (addrValue != null) {
                    byte[] bndAddr = addrValue.getBytes(CharsetUtil.US_ASCII);
                    out.writeByte(bndAddr.length);
                    out.writeBytes(bndAddr);
                } else {
                    out.writeByte(1);
                    out.writeByte(0);
                }
            } else if (typeVal == Socks5AddressType.IPv6.byteValue()) {
                if (addrValue != null) {
                    out.writeBytes(NetUtil.createByteArrayFromIpAddressString(addrValue));
                } else {
                    out.writeLong(0);
                    out.writeLong(0);
                }
            } else {
                throw new EncoderException("unsupported addrType: " + (addrType.byteValue() & 0xFF));
            }
        }
    };

    /**
     * Encodes a SOCKS5 address.
     *
     * @param addrType the type of the address
     * @param addrValue the string representation of the address
     * @param out the output buffer where the encoded SOCKS5 address field will be written to
     */
    void encodeAddress(Socks5AddressType addrType, String addrValue, ByteBuf out) throws Exception;
}
