/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes a single {@link Socks5PrivateAuthRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.
 */
public class Socks5PrivateAuthRequestDecoder extends ReplayingDecoder<Socks5PrivateAuthRequestDecoder.State> {

    @UnstableApi
    public enum State {
        INIT,
        SUCCESS,
        FAILURE
    }

    public Socks5PrivateAuthRequestDecoder() {
        super(State.INIT);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state()) {
            case INIT: {
                final byte version = in.readByte();
                if (version != 1) {
                    throw new DecoderException("unsupported subnegotiation version: " + version + " (expected: 1)");
                }

                int readableBytes = actualReadableBytes();
                byte[] token = new byte[readableBytes];
                in.readBytes(token);

                out.add(new DefaultSocks5PrivateAuthRequest(token));

                checkpoint(State.SUCCESS);
                break;
            }
            case SUCCESS: {
                int readableBytes = actualReadableBytes();
                if (readableBytes > 0) {
                    out.add(in.readRetainedSlice(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipBytes(actualReadableBytes());
                break;
            }
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }

    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        checkpoint(State.FAILURE);

        Socks5Message m = new DefaultSocks5PrivateAuthRequest(Unpooled.EMPTY_BUFFER.array());
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }
}
