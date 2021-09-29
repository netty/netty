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

/**
 * Decodes {@link ByteBuf}s into {@link SocksAuthRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksAuthRequestDecoder extends ByteToMessageDecoder {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_USERNAME,
        READ_PASSWORD
    }
    private State state = State.CHECK_PROTOCOL_VERSION;
    private String username;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                if (byteBuf.readByte() != SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                    break;
                }
                state = State.READ_USERNAME;
            }
            case READ_USERNAME: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                int fieldLength = byteBuf.getByte(byteBuf.readerIndex());
                if (byteBuf.readableBytes() < 1 + fieldLength) {
                    return;
                }
                byteBuf.skipBytes(1);
                username = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                state = State.READ_PASSWORD;
            }
            case READ_PASSWORD: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                int fieldLength = byteBuf.getByte(byteBuf.readerIndex());
                if (byteBuf.readableBytes() < 1 + fieldLength) {
                    return;
                }
                byteBuf.skipBytes(1);
                String password = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                ctx.fireChannelRead(new SocksAuthRequest(username, password));
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }
}
