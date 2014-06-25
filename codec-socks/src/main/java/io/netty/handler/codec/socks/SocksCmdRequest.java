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


/**
 * An socks cmd request.
 * For backward compatibility was created another constructor for Socks4 and left old for Socks5
 *
 * @see SocksCmdResponse
 * @see SocksCmdRequestDecoder
 */
public abstract class SocksCmdRequest extends SocksRequest {
    protected SocksCmdType cmdType;
    protected String host;
    protected int port;
    protected SocksAddressType addressType;

    public SocksCmdRequest(SocksProtocolVersion protocolVersion, SocksRequestType requestType,
                           SocksCmdType cmdType, int port) {
        super(protocolVersion, requestType);
        if (cmdType == null) {
            throw new NullPointerException("cmdType");
        }
        if (port < 0 || port >= 65536) {
            throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
        }
        this.cmdType = cmdType;
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
     * Returns the {@link SocksAddressType} of this {@link SocksCmdRequest} for Socks5
     *
     * @return The {@link SocksAddressType} of this {@link SocksCmdRequest} for Socks5
     */
    public SocksAddressType addressType() {
        if (SocksProtocolVersion.SOCKS4.equals(protocolVersion())) {
            throw new IllegalStateException("SocksAddressType doesn't support in Socks4 protocol.");
        }
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksCmdType}
     *
     * @return host that is used as a parameter in {@link SocksCmdType}
     */
    public String host() {
        return IDN.toUnicode(host);
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
        switch (protocolVersion()) {
            case SOCKS4: {
                byteBuf.writeShort(port);
                byteBuf.writeBytes(NetUtil.createByteArrayFromIpAddressString(host));
                byteBuf.writeByte(' ');       // userId for Ident (skipped)
                byteBuf.writeByte(SocksCmdResponse.NULL);
                break;
            }
            case SOCKS5: {
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
                break;
            }
        }
    }
}
