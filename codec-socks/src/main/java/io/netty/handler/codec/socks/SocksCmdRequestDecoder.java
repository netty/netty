/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder.State;
import io.netty.util.NetUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksCmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksCmdRequestDecoder extends ReplayingDecoder<State> {

    private SocksCmdType cmdType;
    private SocksAddressType addressType;

    public SocksCmdRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readByte() != SocksProtocolVersion.SOCKS5.byteValue()) {
                    out.add(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = SocksCmdType.valueOf(byteBuf.readByte());
                byteBuf.skipBytes(1); // reserved
                addressType = SocksAddressType.valueOf(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        String host = NetUtil.intToIpAddress(ByteBufUtil.readIntBE(byteBuf));
                        int port = ByteBufUtil.readUnsignedShortBE(byteBuf);
                        out.add(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case DOMAIN: {
                        int fieldLength = byteBuf.readByte();
                        String host = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                        int port = ByteBufUtil.readUnsignedShortBE(byteBuf);
                        out.add(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case IPv6: {
                        byte[] bytes = new byte[16];
                        byteBuf.readBytes(bytes);
                        String host = SocksCommonUtils.ipv6toStr(bytes);
                        int port = ByteBufUtil.readUnsignedShortBE(byteBuf);
                        out.add(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case UNKNOWN: {
                        out.add(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                        break;
                    }
                    default: {
                        throw new Error();
                    }
                }
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }

    @UnstableApi
    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
}
