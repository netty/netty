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
package org.jboss.netty.handler.codec.http;

import java.util.Iterator;
import java.util.List;

final class HttpCodecUtil {

    static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '\t': case '\n': case 0x0b: case '\f': case '\r':
            case ' ':  case ',':  case ':':  case ';':  case '=':
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }
    }

    static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        // 0 - the previous character was neither CR nor LF
        // 1 - the previous character was CR
        // 2 - the previous character was LF
        int state = 0;

        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);

            // Check the absolutely prohibited characters.
            switch (c) {
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\v': " + value);
            case '\f':
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\f': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
            case 0:
                switch (c) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (c) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only '\\n' is allowed after '\\r': " + value);
                }
                break;
            case 2:
                switch (c) {
                case '\t': case ' ':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only ' ' and '\\t' are allowed after '\\n': " + value);
                }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "value must not end with '\\r' or '\\n':" + value);
        }
    }

    static boolean isTransferEncodingChunked(HttpMessage m) {
        List<String> chunked = m.headers().getAll(HttpHeaders.Names.TRANSFER_ENCODING);
        if (chunked.isEmpty()) {
            return false;
        }

        for (String v: chunked) {
            if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }

    static void removeTransferEncodingChunked(HttpMessage m) {
        List<String> values = m.headers().getAll(HttpHeaders.Names.TRANSFER_ENCODING);
        if (values.isEmpty()) {
            return;
        }
        Iterator<String> valuesIt = values.iterator();
        while (valuesIt.hasNext()) {
            String value = valuesIt.next();
            if (value.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                valuesIt.remove();
            }
        }
        if (values.isEmpty()) {
            m.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);
        } else {
            m.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, values);
        }
    }

    static boolean isContentLengthSet(HttpMessage m) {
        List<String> contentLength = m.headers().getAll(HttpHeaders.Names.CONTENT_LENGTH);
        return !contentLength.isEmpty();
    }

    private HttpCodecUtil() {
    }
}
