/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2015 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Helper class representing a single header entry. Used by the benchmarks.
 */
class HpackHeader {
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    final CharSequence name;
    final CharSequence value;

    HpackHeader(byte[] name, byte[] value) {
        this.name = new AsciiString(name, false);
        this.value = new AsciiString(value, false);
    }

    /**
     * Creates a number of random headers with the given name/value lengths.
     */
    static List<HpackHeader> createHeaders(int numHeaders, int nameLength, int valueLength,
                                           boolean limitToAscii) {
        List<HpackHeader> hpackHeaders = new ArrayList<HpackHeader>(numHeaders);
        for (int i = 0; i < numHeaders; ++i) {
            byte[] name = randomBytes(new byte[nameLength], limitToAscii);
            byte[] value = randomBytes(new byte[valueLength], limitToAscii);
            hpackHeaders.add(new HpackHeader(name, value));
        }
        return hpackHeaders;
    }

    private static byte[] randomBytes(byte[] bytes, boolean limitToAscii) {
        Random r = new Random();
        if (limitToAscii) {
            for (int index = 0; index < bytes.length; ++index) {
                int charIndex = r.nextInt(ALPHABET.length());
                bytes[index] = (byte) ALPHABET.charAt(charIndex);
            }
        } else {
            r.nextBytes(bytes);
        }
        return bytes;
    }
}
