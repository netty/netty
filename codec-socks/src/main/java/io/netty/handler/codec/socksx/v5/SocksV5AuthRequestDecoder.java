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
import io.netty.handler.codec.socksx.v5.SocksV5AuthRequestDecoder.State;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksV5AuthRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksV5AuthRequestDecoder extends ReplayingDecoder<State> {
    private SocksV5SubnegotiationVersion version;
    private int fieldLength;
    private String username;
    private String password;
    private SocksV5Request msg = UnknownSocksV5Request.getInstance();

    public SocksV5AuthRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksV5SubnegotiationVersion.valueOf(byteBuf.readByte());
                if (version != SocksV5SubnegotiationVersion.AUTH_PASSWORD) {
                    break;
                }
                checkpoint(State.READ_USERNAME);
            }
            case READ_USERNAME: {
                fieldLength = byteBuf.readByte();
                username = byteBuf.readBytes(fieldLength).toString(CharsetUtil.US_ASCII);
                checkpoint(State.READ_PASSWORD);
            }
            case READ_PASSWORD: {
                fieldLength = byteBuf.readByte();
                password = byteBuf.readBytes(fieldLength).toString(CharsetUtil.US_ASCII);
                msg = new SocksV5AuthRequest(username, password);
            }
        }
        ctx.pipeline().remove(this);
        out.add(msg);
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_USERNAME,
        READ_PASSWORD
    }
}
