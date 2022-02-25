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

package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;

import static io.netty5.handler.codec.http.HttpConstants.COLON;
import static io.netty5.handler.codec.http.HttpConstants.SP;
import static io.netty5.handler.codec.http.HttpObjectEncoder.CRLF_SHORT;
import static java.nio.charset.StandardCharsets.US_ASCII;

final class HttpHeadersEncoder {
    private static final short COLON_AND_SPACE_SHORT = (COLON << 8) | SP;

    private HttpHeadersEncoder() {
    }

    static void encoderHeader(CharSequence name, CharSequence value, Buffer buf) {
        final int nameLen = name.length();
        final int valueLen = value.length();
        final int entryLen = nameLen + valueLen + 4;
        buf.ensureWritable(entryLen);
        buf.writeCharSequence(name, US_ASCII);
        buf.writeShort(COLON_AND_SPACE_SHORT);
        buf.writeCharSequence(value, US_ASCII);
        buf.writeShort(CRLF_SHORT);
    }
}
