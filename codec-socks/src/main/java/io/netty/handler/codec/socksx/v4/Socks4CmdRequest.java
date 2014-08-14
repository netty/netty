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
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.IDN;

/**
 * An socksv4a cmd request.
 *
 * @see Socks4Response
 * @see Socks4CmdRequestDecoder
 */
public final class Socks4CmdRequest extends Socks4Request {
    private final String userId;
    private final Socks4CmdType cmdType;
    private final String host;
    private final int port;

    private static final byte[] IPv4_DOMAIN_MARKER = {0x00, 0x00, 0x00, 0x01};

    public Socks4CmdRequest(String userId, Socks4CmdType cmdType, String host, int port) {
            if (userId == null) {
                throw new NullPointerException("username");
            }
            if (cmdType == null) {
                throw new NullPointerException("cmdType");
            }
            if (host == null) {
                throw new NullPointerException("host");
            }
            if (port <= 0 || port >= 65536) {
                throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
            }
            this.userId = userId;
            this.cmdType = cmdType;
            this.host = IDN.toASCII(host);
            this.port = port;
        }

    public Socks4CmdRequest(Socks4CmdType cmdType, String host, int port) {
        this("", cmdType, host, port);
    }

    /**
     * Returns the {@link Socks4CmdType} of this {@link Socks4Request}
     *
     * @return The {@link Socks4CmdType} of this {@link Socks4Request}
     */
    public Socks4CmdType cmdType() {
        return cmdType;
    }

    /**
     * Returns host that is used as a parameter in {@link Socks4CmdType}
     *
     * @return host that is used as a parameter in {@link Socks4CmdType}
     */
    public String host() {
        return IDN.toUnicode(host);
    }

    /**
     * Returns userId that is used as a parameter in {@link Socks4CmdType}
     *
     * @return userId that is used as a parameter in {@link Socks4CmdType}
     */
    public String userId() {
        return userId;
    }

    /**
     * Returns port that is used as a parameter in {@link Socks4CmdType}
     *
     * @return port that is used as a parameter in {@link Socks4CmdType}
     */
    public int port() {
        return port;
    }

    @Override
    void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(cmdType.byteValue());
        byteBuf.writeShort(port);
        if (NetUtil.isValidIpV4Address(host)) {
            byteBuf.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
            byteBuf.writeBytes(userId.getBytes());
            byteBuf.writeZero(1);
        } else {
            byteBuf.writeBytes(IPv4_DOMAIN_MARKER);
            byteBuf.writeBytes(userId.getBytes());
            byteBuf.writeZero(1);
            byteBuf.writeBytes(host.getBytes(CharsetUtil.US_ASCII));
            byteBuf.writeZero(1);
        }
    }
}
