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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes a single {@link Socks5PrivateAuthResponse} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later. On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 * <p>
 * The default format follows a simple structure:
 * <ul>
 *   <li>1 byte: version (must be 1)</li>
 *   <li>1 byte: status (0x00 for success, 0xFF for failure)</li>
 * </ul>
 * </p>
 * <p>
 * For custom private authentication protocols, you can:
 * <ul>
 *   <li>Create a new decoder implementing {@link ByteToMessageDecoder} or similar</li>
 *   <li>Implement the {@link Socks5PrivateAuthResponse} interface
 *   or extend {@link DefaultSocks5PrivateAuthResponse}</li>
 *   <li>Create a custom handler chain to process the authentication responses</li>
 * </ul>
 * </p>
 */
public final class Socks5PrivateAuthResponseDecoder extends ByteToMessageDecoder {

    /**
     * Decoder states for SOCKS5 private authentication responses.
     */
    private enum State {
        /**
         * Initial state.
         */
        INIT,
        /**
         * Authentication successful.
         */
        SUCCESS,
        /**
         * Authentication failed.
         */
        FAILURE
    }

    private State state = State.INIT;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
                          List<Object> out) throws Exception {
        try {
            switch (state) {
                case INIT:
                    if (in.readableBytes() < 2) {
                        return;
                    }

                    final byte version = in.readByte();
                    if (version != 1) {
                        throw new DecoderException(
                            "unsupported subnegotiation version: " + version + " (expected: 1)");
                    }

                    out.add(new DefaultSocks5PrivateAuthResponse(
                        Socks5PrivateAuthStatus.valueOf(in.readByte())));
                    state = State.SUCCESS;
                    break;
                case SUCCESS:
                    int readableBytes = in.readableBytes();
                    if (readableBytes > 0) {
                        out.add(in.readRetainedSlice(readableBytes));
                    }
                    break;
                case FAILURE:
                    in.skipBytes(in.readableBytes());
                    break;
                default:
                    throw new Error("Unexpected response decoder state: " + state);
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }

    /**
     * Handles decoder failures by setting the appropriate error state.
     *
     * @param out   the output list to add the failure message to
     * @param cause the exception that caused the failure
     */
    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        state = State.FAILURE;

        Socks5Message m = new DefaultSocks5PrivateAuthResponse(Socks5PrivateAuthStatus.FAILURE);
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }
}
