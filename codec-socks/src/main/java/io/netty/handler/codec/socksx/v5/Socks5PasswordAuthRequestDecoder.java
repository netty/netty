/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder.State;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes a single {@link Socks5PasswordAuthRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class Socks5PasswordAuthRequestDecoder extends ReplayingDecoder<State> {

    @UnstableApi
    public enum State {
        INIT,
        SUCCESS,
        FAILURE
    }

    public Socks5PasswordAuthRequestDecoder() {
        super(State.INIT);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state()) {
            case INIT: {
                final int startOffset = in.readerIndex();
                final byte version = in.getByte(startOffset);
                if (version != 1) {
                    throw new DecoderException("unsupported subnegotiation version: " + version + " (expected: 1)");
                }

                final int usernameLength = in.getUnsignedByte(startOffset + 1);
                final int passwordLength = in.getUnsignedByte(startOffset + 2 + usernameLength);
                final int totalLength = usernameLength + passwordLength + 3;

                in.skipBytes(totalLength);
                out.add(new DefaultSocks5PasswordAuthRequest(
                        in.toString(startOffset + 2, usernameLength, CharsetUtil.US_ASCII),
                        in.toString(startOffset + 3 + usernameLength, passwordLength, CharsetUtil.US_ASCII)));

                checkpoint(State.SUCCESS);
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

        Socks5Message m = new DefaultSocks5PasswordAuthRequest("", "");
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }
}
