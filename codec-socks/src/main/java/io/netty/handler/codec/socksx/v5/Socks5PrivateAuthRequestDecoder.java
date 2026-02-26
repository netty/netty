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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.internal.EmptyArrays;

import java.util.List;

/**
 * Decodes a single {@link Socks5PrivateAuthRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.
 */
public final class Socks5PrivateAuthRequestDecoder extends ByteToMessageDecoder {

    private boolean decoded;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            if (decoded) {
                int readableBytes = in.readableBytes();
                if (readableBytes > 0) {
                    out.add(in.readRetainedSlice(readableBytes));
                }
                return;
            }

            // Check if we have enough data to decode the message
            if (in.readableBytes() < 2) {
                return;
            }

            final int startOffset = in.readerIndex();
            final byte version = in.getByte(startOffset);
            if (version != 1) {
                throw new DecoderException("unsupported subnegotiation version: " + version + " (expected: 1)");
            }

            final int tokenLength = in.getUnsignedByte(startOffset + 1);

            // Check if the full message is available
            if (in.readableBytes() < 2 + tokenLength) {
                return;
            }

            // Read the version and token length
            in.skipBytes(2);

            // Read the token
            byte[] token = new byte[tokenLength];
            in.readBytes(token);

            // Add the decoded token to the output list
            out.add(new DefaultSocks5PrivateAuthRequest(token));

            // Mark as decoded to handle remaining bytes in future calls
            decoded = true;
        } catch (Exception e) {
            fail(out, e);
        }
    }

    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        decoded = true;

        Socks5Message m = new
            DefaultSocks5PrivateAuthRequest(EmptyArrays.EMPTY_BYTES);
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }
}
