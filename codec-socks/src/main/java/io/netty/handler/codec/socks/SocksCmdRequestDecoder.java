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

import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

/**
 * Decodes {@link ByteBuf}s into {@link SocksCmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksCmdRequestDecoder extends ReplayingDecoder<SocksCmdRequestDecoder.State> {
    private static final String name = "SOCKS_CMD_REQUEST_DECODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    private SocksProtocolVersion version;
    private int fieldLength;
    private SocksCmdType cmdType;
    private SocksAddressType addressType;
    private byte reserved;
    private String userId;
    private String host;
    private int port;
    private SocksRequest msg = SocksCommonUtils.UNKNOWN_SOCKS_REQUEST;

    public SocksCmdRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksProtocolVersion.fromByte(byteBuf.readByte());
                if (version != SocksProtocolVersion.SOCKS5 && version != SocksProtocolVersion.SOCKS4) {
                    msg = new UnknownSocksRequest(version);
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = SocksCmdType.fromByte(byteBuf.readByte());
                if (SocksProtocolVersion.SOCKS5.equals(version)) {
                    reserved = byteBuf.readByte();
                    addressType = SocksAddressType.fromByte(byteBuf.readByte());
                }
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                if (version == SocksProtocolVersion.SOCKS5) {
                    switch (addressType) {
                        case IPv4: {
                            host = SocksCommonUtils.intToIp(byteBuf.readInt());
                            port = byteBuf.readUnsignedShort();
                            msg = new SocksCmdRequest(cmdType, addressType, host, port);
                            break;
                        }
                        case DOMAIN: {
                            fieldLength = byteBuf.readByte();
                            host = byteBuf.readBytes(fieldLength).toString(CharsetUtil.US_ASCII);
                            port = byteBuf.readUnsignedShort();
                            msg = new SocksCmdRequest(cmdType, addressType, host, port);
                            break;
                        }
                        case IPv6: {
                            host = SocksCommonUtils.ipv6toStr(byteBuf.readBytes(16).array());
                            port = byteBuf.readUnsignedShort();
                            msg = new SocksCmdRequest(cmdType, addressType, host, port);
                            break;
                        }
                        case UNKNOWN:
                            break;
                    }
                } else if (version == SocksProtocolVersion.SOCKS4) {
                    port = byteBuf.readUnsignedShort();
                    host = SocksCommonUtils.intToIp(byteBuf.readInt());
                    ByteBuf buf = Unpooled.buffer();
                    byte b = byteBuf.readByte();
                    while (b != SocksCmdResponse.NULL) {
                        buf.writeByte(b);
                        b = byteBuf.readByte();
                    }
                    userId = buf.toString(CharsetUtil.US_ASCII);
                    msg = new SocksCmdRequest(cmdType, port, host);
                }
            }
        }
        ctx.pipeline().remove(this);
        out.add(msg);
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
}
