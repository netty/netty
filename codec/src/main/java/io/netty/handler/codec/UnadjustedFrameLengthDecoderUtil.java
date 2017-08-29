/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;

final class UnadjustedFrameLengthDecoderUtil {

    private UnadjustedFrameLengthDecoderUtil() {
    }

    /**
     * Decodes the specified region of the buffer into an unadjusted frame length. The default implementation is
     * capable of decoding the specified region into an unsigned 8/16/24/32/64 bit integer. Override this method to
     * decode the length field encoded differently. Note that this method must not modify the state of the specified
     * buffer (e.g. {@code readerIndex}, {@code writerIndex}, and the content of the buffer.)
     *
     * @throws DecoderException if failed to decode the specified region
     */
    static long decode(ByteBuf buf, int offset, int length) {
        switch (length) {
        case 1:
            return buf.getUnsignedByte(offset);
        case 2:
            return buf.getUnsignedShort(offset);
        case 3:
            return buf.getUnsignedMedium(offset);
        case 4:
            return buf.getUnsignedInt(offset);
        case 8:
            return buf.getLong(offset);
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + length + " (expected: 1, 2, 3, 4, or 8)");
        }
    }
}
