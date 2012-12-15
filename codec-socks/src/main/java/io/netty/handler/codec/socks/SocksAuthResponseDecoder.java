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

/**
 * Decodes {@link ByteBuf}s into {@link SocksAuthResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksAuthResponseDecoder extends ReplayingDecoder<SocksResponse, SocksAuthResponseDecoder.State> {
    private static final String name = "SOCKS_AUTH_RESPONSE_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.SubnegotiationVersion version;
    private SocksMessage.AuthStatus authStatus;
    private SocksResponse msg = SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE;

    public SocksAuthResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    public SocksResponse decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.SubnegotiationVersion.fromByte(byteBuf.readByte());
                if (version != SocksMessage.SubnegotiationVersion.AUTH_PASSWORD) {
                    break;
                }
                checkpoint(State.READ_AUTH_RESPONSE);
            }
            case READ_AUTH_RESPONSE: {
                authStatus = SocksMessage.AuthStatus.fromByte(byteBuf.readByte());
                msg = new SocksAuthResponse(authStatus);
            }
        }
        channelHandlerContext.pipeline().remove(this);
        return msg;
    }

    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_RESPONSE
    }
}
