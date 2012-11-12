/*
 * Copyright 2012 The Netty Project
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
package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import sun.net.util.IPAddressUtil;

public final class SocksCmdRequest extends SocksRequest {
    private final CmdType cmdType;
    private final AddressType addressType;
    private final String host;
    private final int port;

    public SocksCmdRequest(CmdType cmdType, AddressType addressType, String host, int port) {
        super(SocksRequestType.CMD);
        this.cmdType = cmdType;
        this.addressType = addressType;
        this.host = host;
        this.port = port;
    }

    public CmdType getCmdType() {
        return cmdType;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(cmdType.getByteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.getByteValue());
        switch (addressType) {
            case IPv4: {
                byteBuf.writeBytes(IPAddressUtil.textToNumericFormatV4(host));
                byteBuf.writeShort(port);
                break;
            }

            case DOMAIN: {
                byteBuf.writeByte(host.length());
                byteBuf.writeBytes(host.getBytes(CharsetUtil.US_ASCII));
                byteBuf.writeShort(port);
                break;
            }

            case IPv6: {
                byteBuf.writeBytes(IPAddressUtil.textToNumericFormatV6(host));
                byteBuf.writeShort(port);
                break;
            }
        }
    }
}
