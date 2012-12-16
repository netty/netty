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
package io.netty.handler.codec.cipher;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToByteCodec;

import javax.crypto.Cipher;

public class CipherCodec extends ByteToByteCodec {
    private final Cipher encrypt;
    private final Cipher decrypt;

    private final ByteBuf heapOut = Unpooled.buffer();

    public CipherCodec(Cipher encrypt, Cipher decrypt) throws Exception {
        this.encrypt = encrypt;
        this.decrypt = decrypt;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (out.hasArray()) {
            process(encrypt, in, out);
        } else {
            process(encrypt, in, heapOut);
            out.writeBytes(heapOut);
            heapOut.discardReadBytes();
        }
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        process(decrypt, in, out);
    }

    private void process(Cipher cipher, ByteBuf in, ByteBuf out) throws Exception {
        int readable = in.readableBytes();
        out.ensureWritableBytes(cipher.getOutputSize(readable));

        int processed = cipher.update(in.array(), in.arrayOffset() + in.readerIndex(), readable, out.array(), out.arrayOffset() + out.writerIndex());

        in.readerIndex(in.readerIndex() + readable);
        out.writerIndex(out.writerIndex() + processed);
    }
}
