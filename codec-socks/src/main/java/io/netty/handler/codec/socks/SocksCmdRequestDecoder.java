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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.NetUtil;

/**
 * Decodes {@link ByteBuf}s into {@link SocksCmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksCmdRequestDecoder extends ByteToMessageDecoder {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }

    private State state = State.CHECK_PROTOCOL_VERSION;
    private SocksCmdType cmdType;
    private SocksAddressType addressType;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                if (byteBuf.readByte() != SocksProtocolVersion.SOCKS5.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                    break;
                }
                state = State.READ_CMD_HEADER;
            }
            case READ_CMD_HEADER: {
                if (byteBuf.readableBytes() < 3) {
                    return;
                }
                cmdType = SocksCmdType.valueOf(byteBuf.readByte());
                byteBuf.skipBytes(1); // reserved
                addressType = SocksAddressType.valueOf(byteBuf.readByte());
                state = State.READ_CMD_ADDRESS;
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        if (byteBuf.readableBytes() < 6) {
                            return;
                        }
                        String host = NetUtil.intToIpAddress(byteBuf.readInt());
                        int port = byteBuf.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case DOMAIN: {
                        if (byteBuf.readableBytes() < 1) {
                            return;
                        }
                        int fieldLength = byteBuf.getByte(byteBuf.readerIndex());
                        if (byteBuf.readableBytes() < 3 + fieldLength) {
                            return;
                        }
                        byteBuf.skipBytes(1);
                        String host = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                        int port = byteBuf.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case IPv6: {
                        if (byteBuf.readableBytes() < 18) {
                            return;
                        }
                        byte[] bytes = new byte[16];
                        byteBuf.readBytes(bytes);
                        String host = SocksCommonUtils.ipv6toStr(bytes);
                        int port = byteBuf.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdRequest(cmdType, addressType, host, port));
                        break;
                    }
                    case UNKNOWN: {
                        ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
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
}
