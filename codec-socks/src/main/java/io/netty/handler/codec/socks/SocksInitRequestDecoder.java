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
import io.netty.handler.codec.socks.SocksInitRequestDecoder.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksInitRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksInitRequestDecoder extends ReplayingDecoder<State> {

    public SocksInitRequestDecoder() {
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
                checkpoint(State.READ_AUTH_SCHEMES);
            }
            case READ_AUTH_SCHEMES: {
                final byte authSchemeNum = byteBuf.readByte();
                final List<SocksAuthScheme> authSchemes;
                if (authSchemeNum > 0) {
                    authSchemes = new ArrayList<SocksAuthScheme>(authSchemeNum);
                    for (int i = 0; i < authSchemeNum; i++) {
                        authSchemes.add(SocksAuthScheme.valueOf(byteBuf.readByte()));
                    }
                } else {
                    authSchemes = Collections.emptyList();
                }
                out.add(new SocksInitRequest(authSchemes));
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_SCHEMES
    }
}
