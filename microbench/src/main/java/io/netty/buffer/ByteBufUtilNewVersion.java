/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

public class ByteBufUtilNewVersion {
    static int reserveAndWriteUtf8Seq(ByteBuf buf, CharSequence seq, int start, int end, int reserveBytes) {
        for (; ; ) {
            if (buf instanceof WrappedCompositeByteBuf) {
                // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                buf = buf.unwrap();
            } else if (buf instanceof AbstractByteBuf) {
                AbstractByteBuf byteBuf = (AbstractByteBuf) buf;
                byteBuf.ensureWritable0(reserveBytes);
                int written = ByteBufUtil.writeUtf8(byteBuf, byteBuf.writerIndex, reserveBytes, seq, start, end);
                byteBuf.writerIndex += written;
                return written;
            } else if (buf instanceof WrappedByteBuf) {
                // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                buf = buf.unwrap();
            } else {
                byte[] bytes = seq.subSequence(start, end).toString().getBytes(CharsetUtil.UTF_8);
                buf.writeBytes(bytes);
                return bytes.length;
            }
        }
    }


    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> and write it
     * to a {@link ByteBuf}.
     * <p>
     * This method returns the actual number of bytes written.
     */
    public static int writeAscii(ByteBuf buf, CharSequence seq, int srcIndex, int len) {
        // ASCII uses 1 byte per char
        if (seq instanceof AsciiString) {
            AsciiString asciiString = (AsciiString) seq;
            buf.writeBytes(asciiString.array(), srcIndex + asciiString.arrayOffset(), len);
        } else {
            for (; ; ) {
                if (buf instanceof WrappedCompositeByteBuf) {
                    // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                    buf = buf.unwrap();
                } else if (buf instanceof AbstractByteBuf) {
                    AbstractByteBuf byteBuf = (AbstractByteBuf) buf;
                    byteBuf.ensureWritable0(len);
                    int written = writeAscii(byteBuf, byteBuf.writerIndex, seq, srcIndex, len);
                    byteBuf.writerIndex += written;
                    return written;
                } else if (buf instanceof WrappedByteBuf) {
                    // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                    buf = buf.unwrap();
                } else {
                    byte[] bytes = seq.subSequence(srcIndex, srcIndex + len).toString().getBytes(CharsetUtil.US_ASCII);
                    buf.writeBytes(bytes);
                    return bytes.length;
                }
            }
        }
        return len;
    }

    static int writeAscii(AbstractByteBuf buffer, int writerIndex, CharSequence seq, int srcIndex, int len) {

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        for (int i = srcIndex, end = srcIndex + len; i < end; i++) {
            buffer._setByte(writerIndex++, AsciiString.c2b(seq.charAt(i)));
        }
        return len;
    }

}
