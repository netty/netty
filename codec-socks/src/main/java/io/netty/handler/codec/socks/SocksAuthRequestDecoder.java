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
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.SocksAuthRequestDecoder.State;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksAuthRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksAuthRequestDecoder extends ReplayingDecoder<State> {

    private String username;

    public SocksAuthRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readByte() != SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue()) {
                    out.add(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                    break;
                }
                checkpoint(State.READ_USERNAME);
            }
            case READ_USERNAME: {
                int fieldLength = byteBuf.readByte();
                username = byteBuf.readString(fieldLength, CharsetUtil.US_ASCII);
                checkpoint(State.READ_PASSWORD);
            }
            case READ_PASSWORD: {
                int fieldLength = byteBuf.readByte();
                String password = byteBuf.readString(fieldLength, CharsetUtil.US_ASCII);
                out.add(new SocksAuthRequest(username, password));
                break;
            }
            default: {
                throw new Error("Unexpected request decoder state: " + state());
            }
        }
        ctx.pipeline().remove(this);
    }

    @UnstableApi
    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_USERNAME,
        READ_PASSWORD
    }
}
