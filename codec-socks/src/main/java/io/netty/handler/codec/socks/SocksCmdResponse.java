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

import java.net.IDN;

/**
 * An socks cmd response.
 *
 * @see SocksCmdRequest
 * @see SocksCmdResponseDecoder
 */
public final class SocksCmdResponse extends SocksResponse {
    private final SocksCmdStatus cmdStatus;

    private final SocksAddressType addressType;
    private final String boundAddress;
    private final int boundPort;

    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] DOMAIN_ZEROED = {0x00};
    private static final byte[] IPv4_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] IPv6_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType) {
        this(cmdStatus, addressType, null, 0);
    }

    /**
     * Constructs new response and provides bound address and bound host as part of it.
     *
     * @param cmdStatus status of the response
     * @param addressType type of returned bound address
     * @param boundAddress bound address, when null a value of 4/8 0x00 octets will be used for IPv4/IPv8 and a single
     *                     0x00 byte will be used for domain addressType
     * @param boundPort bound port
     * @throws NullPointerException in case cmdStatus or addressType are missing
     * @throws IllegalArgumentException in case bound address or bound port cannot be validated
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType,
                            String boundAddress, int boundPort) {
        super(SocksResponseType.CMD);
        if (cmdStatus == null) {
            throw new NullPointerException("cmdStatus");
        }
        if (addressType == null) {
            throw new NullPointerException("addressType");
        }
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
        if (boundAddress != null) {
            this.boundAddress = IDN.toASCII(boundAddress);
            switch (addressType) {
                case IPv4:
                    if (!NetUtil.isValidIpV4Address(boundAddress)) {
                        throw new IllegalArgumentException(boundAddress + " is not a valid IPv4 address");
                    }
                    break;
                case DOMAIN:
                    if (boundAddress.length() > 255) {
                        throw new IllegalArgumentException(boundAddress + " IDN: " +
                                boundAddress + " exceeds 255 char limit");
                    }
                    break;
                case IPv6:
                    if (!NetUtil.isValidIpV6Address(boundAddress)) {
                        throw new IllegalArgumentException(boundAddress + " is not a valid IPv6 address");
                    }
                    break;
                case UNKNOWN:
                    break;
            }
        } else {
            this.boundAddress = null;
        }
        if (boundPort < 0 && boundPort >= 65535) {
            throw new IllegalArgumentException(boundPort + " is not in bounds 0 < x < 65536");
        }
        this.boundPort = boundPort;
    }

    /**
     * Returns the {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     */
    public SocksCmdStatus cmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns the {@link SocksAddressType} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksAddressType} of this {@link SocksCmdResponse}
     */
    public SocksAddressType addressType() {
        return addressType;
    }

    /**
     * Returns bound host that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}.
     *
     * @return bound host that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}
     */
    public String boundAddress() {
        return boundAddress;
    }

    /**
     * Returns bound port that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}
     *
     * @return bound port that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}
     */
    public int boundPort() {
        return boundPort;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(cmdStatus.byteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.byteValue());
        switch (addressType) {
            case IPv4: {
                byte[] boundAddressContent = boundAddress == null ?
                        IPv4_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(boundAddress);
                byteBuf.writeBytes(boundAddressContent);
                byteBuf.writeShort(boundPort);
                break;
            }
            case DOMAIN: {
                byte[] boundAddressContent = boundAddress == null ?
                        DOMAIN_ZEROED : boundAddress.getBytes(CharsetUtil.US_ASCII);
                byteBuf.writeByte(boundAddressContent.length);   // domain length
                byteBuf.writeBytes(boundAddressContent);   // domain value
                byteBuf.writeShort(boundPort);  // port value
                break;
            }
            case IPv6: {
                byte[] boundAddressContent = boundAddress == null
                        ? IPv6_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(boundAddress);
                byteBuf.writeBytes(boundAddressContent);
                byteBuf.writeShort(boundPort);
                break;
            }
        }
    }
}
