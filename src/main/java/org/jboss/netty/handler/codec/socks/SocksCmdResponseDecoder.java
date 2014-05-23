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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.util.CharsetUtil;

/**
 * Decodes {@link ChannelBuffer}s into {@link SocksCmdResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksCmdResponseDecoder extends ReplayingDecoder<SocksCmdResponseDecoder.State> {
    private static final String name = "SOCKS_CMD_RESPONSE_DECODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private int fieldLength;
    private SocksMessage.CmdStatus cmdStatus;
    private SocksMessage.AddressType addressType;
    private byte reserved;
    private String host;
    private int port;
    private SocksResponse msg = SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE;

    public SocksCmdResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel,
                            ChannelBuffer buffer, State state) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.ProtocolVersion.fromByte(buffer.readByte());
                if (version != SocksMessage.ProtocolVersion.SOCKS5) {
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdStatus = SocksMessage.CmdStatus.fromByte(buffer.readByte());
                reserved = buffer.readByte();
                addressType = SocksMessage.AddressType.fromByte(buffer.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        host = SocksCommonUtils.intToIp(buffer.readInt());
                        port = buffer.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case DOMAIN: {
                        fieldLength = buffer.readByte();
                        host = buffer.readBytes(fieldLength).toString(CharsetUtil.US_ASCII);
                        port = buffer.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case IPv6: {
                        host = SocksCommonUtils.ipv6toStr(buffer.readBytes(16).array());
                        port = buffer.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case UNKNOWN:
                        break;
                }
            }
        }
        ctx.getPipeline().remove(this);
        return msg;
    }

    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
}
