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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.v5.Socks5CmdRequestDecoder.State;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link Socks5CmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class Socks5CmdRequestDecoder extends ReplayingDecoder<State> {
    private SocksProtocolVersion version;
    private int fieldLength;
    private Socks5CmdType cmdType;
    private Socks5AddressType addressType;
    @SuppressWarnings("UnusedDeclaration")
    private byte reserved;
    private String host;
    private int port;
    private Socks5Request msg = UnknownSocks5Request.INSTANCE;

    public Socks5CmdRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksProtocolVersion.valueOf(byteBuf.readByte());
                if (version != SocksProtocolVersion.SOCKS5) {
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = Socks5CmdType.valueOf(byteBuf.readByte());
                reserved = byteBuf.readByte();
                addressType = Socks5AddressType.valueOf(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        host = Socks5CommonUtils.intToIp(byteBuf.readInt());
                        port = byteBuf.readUnsignedShort();
                        msg = new Socks5CmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case DOMAIN: {
                        fieldLength = byteBuf.readByte();
                        host = byteBuf.readBytes(fieldLength).toString(CharsetUtil.US_ASCII);
                        port = byteBuf.readUnsignedShort();
                        msg = new Socks5CmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case IPv6: {
                        if (actualReadableBytes() < 16) {
                            // Let it replay.
                            byteBuf.readBytes(16);

                            // Should never reach here.
                            throw new Error();
                        }

                        byte[] byteArray = new byte[16];
                        byteBuf.readBytes(byteArray);

                        host = Socks5CommonUtils.ipv6toStr(byteArray);
                        port = byteBuf.readUnsignedShort();
                        msg = new Socks5CmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case UNKNOWN:
                        break;
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
