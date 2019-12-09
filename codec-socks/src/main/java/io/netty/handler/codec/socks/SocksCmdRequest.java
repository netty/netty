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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import java.net.IDN;

/**
 * An socks cmd request.
 *
 * @see SocksCmdResponse
 * @see SocksCmdRequestDecoder
 */
public final class SocksCmdRequest extends SocksRequest {
    private final SocksCmdType cmdType;
    private final SocksAddressType addressType;
    private final String host;
    private final int port;

    public SocksCmdRequest(SocksCmdType cmdType, SocksAddressType addressType, String host, int port) {
        super(SocksRequestType.CMD);
        ObjectUtil.checkNotNull(cmdType, "cmdType");
        ObjectUtil.checkNotNull(addressType, "addressType");
        ObjectUtil.checkNotNull(host, "host");

        switch (addressType) {
            case IPv4:
                if (!NetUtil.isValidIpV4Address(host)) {
                    throw new IllegalArgumentException(host + " is not a valid IPv4 address");
                }
                break;
            case DOMAIN:
                String asciiHost = IDN.toASCII(host);
                if (asciiHost.length() > 255) {
                    throw new IllegalArgumentException(host + " IDN: " + asciiHost + " exceeds 255 char limit");
                }
                host = asciiHost;
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
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the {@link SocksCmdType} of this {@link SocksCmdRequest}
     *
     * @return The {@link SocksCmdType} of this {@link SocksCmdRequest}
     */
    public SocksCmdType cmdType() {
        return cmdType;
    }

    /**
     * Returns the {@link SocksAddressType} of this {@link SocksCmdRequest}
     *
     * @return The {@link SocksAddressType} of this {@link SocksCmdRequest}
     */
    public SocksAddressType addressType() {
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksCmdType}
     *
     * @return host that is used as a parameter in {@link SocksCmdType}
     */
    public String host() {
        return addressType == SocksAddressType.DOMAIN ? IDN.toUnicode(host) : host;
    }

    /**
     * Returns port that is used as a parameter in {@link SocksCmdType}
     *
     * @return port that is used as a parameter in {@link SocksCmdType}
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
                byteBuf.writeCharSequence(host, CharsetUtil.US_ASCII);
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
