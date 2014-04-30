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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.NetUtil;
import org.jboss.netty.util.internal.DetectionUtil;

import java.net.IDN;

/**
 * An socks cmd request.
 *
 * @see {@link SocksCmdResponse}
 * @see {@link SocksCmdRequestDecoder}
 */
public final class SocksCmdRequest extends SocksRequest {
    private final CmdType cmdType;
    private final AddressType addressType;
    private final String host;
    private final int port;

    public SocksCmdRequest(CmdType cmdType, AddressType addressType, String host, int port) {
        super(SocksRequestType.CMD);
        if (DetectionUtil.javaVersion() < 6) {
            throw new IllegalStateException("Only supported with Java version 6+");
        }
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
     * Returns the {@link SocksMessage.CmdType} of this {@link SocksCmdRequest}
     */
    public CmdType getCmdType() {
        return cmdType;
    }

    /**
     * Returns the {@link AddressType} of this {@link SocksCmdRequest}
     */
    public AddressType getAddressType() {
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksMessage.CmdType}
     */
    public String getHost() {
        return IDN.toUnicode(host);
    }

    /**
     * Returns port that is used as a parameter in {@link SocksMessage.CmdType}
     */
    public int getPort() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ChannelBuffer channelBuffer) throws Exception {
        channelBuffer.writeByte(getProtocolVersion().getByteValue());
        channelBuffer.writeByte(cmdType.getByteValue());
        channelBuffer.writeByte(0x00);
        channelBuffer.writeByte(addressType.getByteValue());
        switch (addressType) {
            case IPv4: {
                channelBuffer.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
                channelBuffer.writeShort(port);
                break;
            }

            case DOMAIN: {
                channelBuffer.writeByte(host.length());
                channelBuffer.writeBytes(host.getBytes("US-ASCII"));
                channelBuffer.writeShort(port);
                break;
            }

            case IPv6: {
                channelBuffer.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
                channelBuffer.writeShort(port);
                break;
            }
        }
    }
}
