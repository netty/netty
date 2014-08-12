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
package io.netty.handler.codec.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.IDN;

/**
 * An socks cmd request.
 *
 * @see SocksV5CmdResponse
 * @see SocksV5CmdRequestDecoder
 */
public final class SocksV5CmdRequest extends SocksV5Request {
    private final SocksV5CmdType cmdType;
    private final SocksV5AddressType addressType;
    private final String host;
    private final int port;

    public SocksV5CmdRequest(SocksV5CmdType cmdType, SocksV5AddressType addressType, String host, int port) {
        super(SocksV5RequestType.CMD);
        if (cmdType == null) {
            throw new NullPointerException("cmdType");
        }
        if (addressType == null) {
            throw new NullPointerException("addressType");
        }
        if (host == null) {
            throw new NullPointerException("host");
        }
        switch (addressType) {
            case IPv4:
                if (!NetUtil.isValidIpV4Address(host)) {
                    throw new IllegalArgumentException(host + " is not a valid IPv4 address");
                }
                break;
            case DOMAIN:
                if (IDN.toASCII(host).length() > 255) {
                    throw new IllegalArgumentException(host + " IDN: " + IDN.toASCII(host) + " exceeds 255 char limit");
                }
                break;
            case IPv6:
                if (!NetUtil.isValidIpV6Address(host)) {
                    throw new IllegalArgumentException(host + " is not a valid IPv6 address");
                }
                break;
            case UNKNOWN:
                break;
        }
        if (port <= 0 || port >= 65536) {
            throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
        }
        this.cmdType = cmdType;
        this.addressType = addressType;
        this.host = IDN.toASCII(host);
        this.port = port;
    }

    /**
     * Returns the {@link SocksV5CmdType} of this {@link SocksV5CmdRequest}
     *
     * @return The {@link SocksV5CmdType} of this {@link SocksV5CmdRequest}
     */
    public SocksV5CmdType cmdType() {
        return cmdType;
    }

    /**
     * Returns the {@link SocksV5AddressType} of this {@link SocksV5CmdRequest}
     *
     * @return The {@link SocksV5AddressType} of this {@link SocksV5CmdRequest}
     */
    public SocksV5AddressType addressType() {
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksV5CmdType}
     *
     * @return host that is used as a parameter in {@link SocksV5CmdType}
     */
    public String host() {
        return IDN.toUnicode(host);
    }

    /**
     * Returns port that is used as a parameter in {@link SocksV5CmdType}
     *
     * @return port that is used as a parameter in {@link SocksV5CmdType}
     */
    public int port() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(cmdType.byteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.byteValue());
        switch (addressType) {
            case IPv4: {
                byteBuf.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
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
                byteBuf.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
                byteBuf.writeShort(port);
                break;
            }
        }
    }
}
