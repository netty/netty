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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.util.NetUtil;

import java.net.IDN;

/**
 * A socks cmd response.
 *
 * @see Socks4CmdRequest
 * @see Socks4CmdResponseDecoder
 */
public final class Socks4CmdResponse extends Socks4Response {
    private final Socks4CmdStatus cmdStatus;
    private final String host;
    private final int port;

    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] IPv4_HOSTNAME_ZEROED = { 0x00, 0x00, 0x00, 0x00 };

    public Socks4CmdResponse(Socks4CmdStatus cmdStatus) {
        this(cmdStatus, null, 0);
    }

    /**
     * Constructs new response and includes provided host and port as part of it.
     *
     * @param cmdStatus status of the response
     * @param host host (BND.ADDR field) is address that server used when connecting to the target host.
     *             When null a value of 4/8 0x00 octets will be used for IPv4/IPv6 and a single 0x00 byte will be
     *             used for domain addressType. Value is converted to ASCII using {@link IDN#toASCII(String)}.
     * @param port port (BND.PORT field) that the server assigned to connect to the target host
     * @throws NullPointerException in case cmdStatus or addressType are missing
     * @throws IllegalArgumentException in case host or port cannot be validated
     * @see IDN#toASCII(String)
     */
    public Socks4CmdResponse(Socks4CmdStatus cmdStatus, String host, int port) {
        if (cmdStatus == null) {
            throw new NullPointerException("cmdStatus");
        }
        if (host != null) {
            if (!NetUtil.isValidIpV4Address(host)) {
                throw new IllegalArgumentException(host + " is not a valid IPv4 address");
            }
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(port + " is not in bounds 0 <= x <= 65535");
        }
        this.cmdStatus = cmdStatus;
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the {@link Socks4CmdStatus} of this {@link Socks4Response}
     *
     * @return The {@link Socks4CmdStatus} of this {@link Socks4Response}
     */
    public Socks4CmdStatus cmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns host that is used as a parameter in {@link Socks4CmdType}.
     * Host (BND.ADDR field in response) is address that server used when connecting to the target host.
     * This is typically different from address which client uses to connect to the SOCKS server.
     *
     * @return host that is used as a parameter in {@link Socks4CmdType}
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
     * Returns port that is used as a parameter in {@link Socks4CmdType}.
     * Port (BND.PORT field in response) is port that the server assigned to connect to the target host.
     *
     * @return port that is used as a parameter in {@link Socks4CmdType}
     */
    public int port() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeZero(1);
        byteBuf.writeByte(cmdStatus.byteValue());
        byteBuf.writeShort(port);
        byte[] hostContent = host == null ?
                                            IPv4_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
        byteBuf.writeBytes(hostContent);
    }
}
