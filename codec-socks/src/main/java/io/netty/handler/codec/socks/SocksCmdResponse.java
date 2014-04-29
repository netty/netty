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

import java.net.IDN;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import static io.netty.handler.codec.socks.SocksCmdStatus.FAILURE4;
import static io.netty.handler.codec.socks.SocksCmdStatus.FAILURE4_IDENTD_CONFIRM;
import static io.netty.handler.codec.socks.SocksCmdStatus.FAILURE4_IDENTD_NOT_RUN;
import static io.netty.handler.codec.socks.SocksCmdStatus.SUCCESS4;
import static io.netty.handler.codec.socks.SocksCmdStatus.UNASSIGNED;


/**
 * A socks cmd response.
 *
 * @see SocksCmdRequest
 * @see SocksCmdResponseDecoder
 */
public final class SocksCmdResponse extends SocksResponse {
    static final byte NULL = 0x00;
    private final SocksCmdStatus cmdStatus;

    private final SocksAddressType addressType;
    private final String host;
    private final int port;

    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] DOMAIN_ZEROED = {0x00};
    private static final byte[] IPv4_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] IPv6_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    /**
     * Construct new response for Socks4 protocol.
     *
     * @param cmdStatus status of the response
     * @throws java.lang.NullPointerException in case cmdStatus are missing
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus) {
        super(SocksProtocolVersion.SOCKS4, SocksResponseType.CMD);
        if (cmdStatus == null) {
            throw new NullPointerException("cmdStatus");
        }
        if (!SUCCESS4.equals(cmdStatus) && !FAILURE4.equals(cmdStatus) && !FAILURE4_IDENTD_CONFIRM.equals(cmdStatus)
                && !FAILURE4_IDENTD_NOT_RUN.equals(cmdStatus) && !UNASSIGNED.equals(cmdStatus)) {
            throw new IllegalArgumentException("Incorrect cmdStatus for Socks4: " + cmdStatus);
        }
        this.cmdStatus = cmdStatus;
        this.addressType = null;
        this.host = null;
        this.port = 1;
    }

    /**
     * Socks5
     *
     * @param cmdStatus   status of the response
     * @param addressType type of host parameter
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType) {
        this(cmdStatus, addressType, null, 1);
    }

    /**
     * Constructs new response (Socks5) and includes provided host and port as part of it.
     *
     * @param cmdStatus   status of the response
     * @param addressType type of host parameter
     * @param host        host (BND.ADDR field) is address that server used when connecting to the target host.
     *                    When null a value of 4/8 0x00 octets will be used for IPv4/IPv6 and a single 0x00 byte will be
     *                    used for domain addressType. Value is converted to ASCII using {@link IDN#toASCII(String)}.
     * @param port        port (BND.PORT field) that the server assigned to connect to the target host
     * @throws NullPointerException     in case cmdStatus or addressType are missing
     * @throws IllegalArgumentException in case host or port cannot be validated
     * @see IDN#toASCII(String)
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType, String host, int port) {
        super(SocksProtocolVersion.SOCKS5, SocksResponseType.CMD);
        if (cmdStatus == null) {
            throw new NullPointerException("cmdStatus");
        }
        if (addressType == null) {
            throw new NullPointerException("addressType");
        }
        if (host != null) {
            switch (addressType) {
                case IPv4:
                    if (!NetUtil.isValidIpV4Address(host)) {
                        throw new IllegalArgumentException(host + " is not a valid IPv4 address");
                    }
                    break;
                case DOMAIN:
                    if (IDN.toASCII(host).length() > 255) {
                        throw new IllegalArgumentException(host + " IDN: " +
                                IDN.toASCII(host) + " exceeds 255 char limit");
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
            host = IDN.toASCII(host);
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
        }
        if (SUCCESS4.equals(cmdStatus) || FAILURE4.equals(cmdStatus) || FAILURE4_IDENTD_CONFIRM.equals(cmdStatus)
                || FAILURE4_IDENTD_NOT_RUN.equals(cmdStatus)) {
            throw new IllegalArgumentException("Incorrect cmdStatus for Socks5: " + cmdStatus);
        }
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
        this.host = host;
        this.port = port;
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
     * Returns host that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}.
     * Host (BND.ADDR field in response) is address that server used when connecting to the target host.
     * This is typically different from address which client uses to connect to the SOCKS server.
     *
     * @return host that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}
     *         or null when there was no host specified during response construction
     */
    public String host() {
        if (host != null) {
            return IDN.toUnicode(host);
        } else {
            return null;
        }
    }

    /**
     * Returns port that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}.
     * Port (BND.PORT field in response) is port that the server assigned to connect to the target host.
     *
     * @return port that is used as a parameter in {@link io.netty.handler.codec.socks.SocksCmdType}
     */
    public int port() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        switch (protocolVersion()) {
            case SOCKS4: {
                byteBuf.writeByte(NULL);        // 0x00
                byteBuf.writeByte(cmdStatus.byteValue());
                // 2 and 4 arbitrary bytes, that should be ignored
                byteBuf.writeBytes(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
                break;
            }
            case SOCKS5: {
                byteBuf.writeByte(protocolVersion().byteValue());
                byteBuf.writeByte(cmdStatus.byteValue());
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(addressType().byteValue());
                switch (addressType()) {
                    case IPv4: {
                        byte[] hostContent = host == null ?
                                IPv4_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                        byteBuf.writeBytes(hostContent);
                        byteBuf.writeShort(port);
                        break;
                    }
                    case DOMAIN: {
                        byte[] hostContent = host == null ?
                                DOMAIN_ZEROED : host.getBytes(CharsetUtil.US_ASCII);
                        byteBuf.writeByte(hostContent.length);   // domain length
                        byteBuf.writeBytes(hostContent);   // domain value
                        byteBuf.writeShort(port);  // port value
                        break;
                    }
                    case IPv6: {
                        byte[] hostContent = host == null
                                ? IPv6_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                        byteBuf.writeBytes(hostContent);
                        byteBuf.writeShort(port);
                        break;
                    }
                }
                break;
            }
        }
    }
}
