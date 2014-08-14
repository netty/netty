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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.v4.Socks4CmdResponseDecoder.State;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link Socks4CmdResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class Socks4CmdResponseDecoder extends ReplayingDecoder<State> {

    private Socks4CmdStatus cmdStatus;

    private String host;
    private int port;
    private Socks4Response msg = UnknownSocks4Response.INSTANCE;

    public Socks4CmdResponseDecoder() {
        super(State.CHECK_NULL_BYTE);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_NULL_BYTE: {
                if (byteBuf.readByte() != (byte) 0x00) {
                    break;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdStatus = Socks4CmdStatus.valueOf(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                port = byteBuf.readUnsignedShort();
                host = Socks4CommonUtils.intToIp(byteBuf.readInt());
                msg = new Socks4CmdResponse(cmdStatus, host, port);
            }
        }
        ctx.pipeline().remove(this);
        out.add(msg);
    }

    enum State {
        CHECK_NULL_BYTE,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
}
