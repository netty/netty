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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder.State;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksCmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksCmdRequestDecoder extends ReplayingDecoder<State> {

    private SocksProtocolVersion version;
    private int fieldLength;
    private SocksCmdType cmdType;
    private SocksAddressType addressType;
    @SuppressWarnings("UnusedDeclaration")
    private byte reserved;
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
                version = SocksProtocolVersion.valueOf(byteBuf.readByte());
                if (version != SocksProtocolVersion.SOCKS5) {
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = SocksCmdType.valueOf(byteBuf.readByte());
                reserved = byteBuf.readByte();
                addressType = SocksAddressType.valueOf(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        host = SocksCommonUtils.intToIp(byteBuf.readInt());
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case DOMAIN: {
                        fieldLength = byteBuf.readByte();
                        host = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case IPv6: {
                        byte[] bytes = new byte[16];
                        byteBuf.readBytes(bytes);
                        host = SocksCommonUtils.ipv6toStr(bytes);
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdRequest(cmdType, addressType, host, port);
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
