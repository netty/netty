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
package io.netty.handler.codec.socks.v4;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.common.SocksProtocolVersion;
import io.netty.handler.codec.socks.v4.SocksV4CmdRequestDecoder.State;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksV4CmdRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksV4CmdRequestDecoder extends ReplayingDecoder<State> {
    private static final String name = "SOCKS_CMD_REQUEST_DECODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    private SocksProtocolVersion version;
    private SocksV4CmdType cmdType;
    @SuppressWarnings("UnusedDeclaration")
    private byte reserved;
    private String host;
    private int port;
    private String userId;
    private SocksV4Request msg = SocksV4CommonUtils.UNKNOWN_SOCKS_V4_REQUEST;

    public SocksV4CmdRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksProtocolVersion.valueOf(byteBuf.readByte());
                if (version != SocksProtocolVersion.SOCKS4a) {
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = SocksV4CmdType.valueOf(byteBuf.readByte());
                port = byteBuf.readUnsignedShort();
                host = SocksV4CommonUtils.intToIp(byteBuf.readInt());
                checkpoint(State.READ_CMD_USERID);
            }
            case READ_CMD_USERID: {
                userId = readNullTerminatedString(byteBuf);
                checkpoint(State.READ_CMD_DOMAIN);
            }
            case READ_CMD_DOMAIN: {
                // Check for Socks4a protocol marker 0,0,0,x
                if (!host.equals("0.0.0.0") && host.startsWith("0.0.0.")) {
                    host = readNullTerminatedString(byteBuf);
                }
                msg = new SocksV4CmdRequest(userId, cmdType, host, port);
            }
        }
        ctx.pipeline().remove(this);
        out.add(msg);
    }
    private static String readNullTerminatedString(ByteBuf byteBuf) throws Exception {
        byte NULL_BYTE = (byte) 0x00;
        // Could be used for DoS
        String string = byteBuf.readBytes(byteBuf.bytesBefore(NULL_BYTE)).toString(CharsetUtil.US_ASCII);
        // Read NULL-byte
        byteBuf.readByte();
        return string;
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_USERID,
        READ_CMD_DOMAIN
    }
}
