/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.api;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

import static io.netty.util.CharsetUtil.US_ASCII;

/**
 * Utilities for buffers.
 */
final class BufferUtils {
    private BufferUtils() {
        // no instances.
    }

    static CharSequence readCharSequence(Buffer source, int length, Charset charset) {
        byte[] data = new byte[length];
        source.copyInto(source.readerOffset(), data, 0, length);
        source.accumulateReaderOffset(length);
        if (US_ASCII.equals(charset)) {
            return new AsciiString(data);
        }
        return new String(data, 0, length, charset);
    }

    static void writeCharSequence(CharSequence source, Buffer destination, Charset charset) {
        if (US_ASCII.equals(charset) && source instanceof AsciiString) {
            AsciiString asciiString = (AsciiString) source;
            destination.writeBytes(asciiString.array(), asciiString.arrayOffset(), source.length());
            return;
        }
        // TODO: Copy optimized writes from ByteBufUtil
        byte[] bytes = source.toString().getBytes(charset);
        destination.writeBytes(bytes);
    }
}
