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
package io.netty.handler.codec.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.v5.SocksV5AuthResponseDecoder.State;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksV5AuthResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksV5AuthResponseDecoder extends ReplayingDecoder<State> {
    private static final String name = "SOCKS_AUTH_RESPONSE_DECODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    private SocksV5SubnegotiationVersion version;
    private SocksV5AuthStatus authStatus;
    private SocksV5Response msg = SocksV5CommonUtils.UNKNOWN_SOCKS_RESPONSE;

    public SocksV5AuthResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> out)
            throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksV5SubnegotiationVersion.valueOf(byteBuf.readByte());
                if (version != SocksV5SubnegotiationVersion.AUTH_PASSWORD) {
                    break;
                }
                checkpoint(State.READ_AUTH_RESPONSE);
            }
            case READ_AUTH_RESPONSE: {
                authStatus = SocksV5AuthStatus.valueOf(byteBuf.readByte());
                msg = new SocksV5AuthResponse(authStatus);
            }
        }
        channelHandlerContext.pipeline().remove(this);
        out.add(msg);
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_RESPONSE
    }
}
