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
package io.netty.handler.codec.http;

import java.util.List;

/**
 * A utility class mainly for use with HTTP codec classes
 */
final class HttpCodecUtil {

    /**
     * Validates the name of a header
     *
     * @param headerName The header name being validated
     */
    static void validateHeaderName(String headerName) {
        //Check to see if the name is null
        if (headerName == null) {
            throw new NullPointerException("Header names cannot be null");
        }
        //Go through each of the characters in the name
        for (int index = 0; index < headerName.length(); index ++) {
            //Actually get the character
            char character = headerName.charAt(index);

            //Check to see if the character is not an ASCII character
            if (character > 127) {
                throw new IllegalArgumentException(
                    "Header name cannot contain non-ASCII characters: " + headerName);
            }

            //Check for prohibited characters.
            switch (character) {
            case '\t': case '\n': case 0x0b: case '\f': case '\r':
            case ' ':  case ',':  case ':':  case ';':  case '=':
                throw new IllegalArgumentException(
                        "Header name cannot contain the following prohibited characters: " +
                        "=,;: \\t\\r\\n\\v\\f: " + headerName);
            }
        }
    }

    /**
     * Validates the specified header value
     *
     * @param value The value being validated
     */
    static void validateHeaderValue(String headerValue) {
        //Check to see if the value is null
        if (headerValue == null) {
            throw new NullPointerException("Header values cannot be null");
        }

        /*
         * Set up the state of the validation
         *
         * States are as follows:
         *
         * 0: Previous character was neither CR nor LF
         * 1: The previous character was CR
         * 2: The previous character was LF
         */
        int state = 0;

        //Start looping through each of the character

        for (int index = 0; index < headerValue.length(); index ++) {
            char character = headerValue.charAt(index);

            //Check the absolutely prohibited characters.
            switch (character) {
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "Header value contains a prohibited character '\\v': " + headerValue);
            case '\f':
                throw new IllegalArgumentException(
                        "Header value contains a prohibited character '\\f': " + headerValue);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
            case 0:
                switch (character) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (character) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only '\\n' is allowed after '\\r': " + headerValue);
                }
                break;
            case 2:
                switch (character) {
                case '\t': case ' ':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only ' ' and '\\t' are allowed after '\\n': " + headerValue);
                }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "Header value must not end with '\\r' or '\\n':" + headerValue);
        }
    }

    /**
     * Checks to see if the transfer encoding in a specified {@link HttpMessage} is chunked
     *
     * @param message The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    static boolean isTransferEncodingChunked(HttpMessage message) {
        List<String> transferEncodingHeaders = message.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (transferEncodingHeaders.isEmpty()) {
            return false;
        }

        for (String value: transferEncodingHeaders) {
            if (value.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }

    static void removeTransferEncodingChunked(HttpMessage m) {
        List<String> values = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        values.remove(HttpHeaders.Values.CHUNKED);
        m.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, values);
    }

    static boolean isContentLengthSet(HttpMessage m) {
        List<String> contentLength = m.getHeaders(HttpHeaders.Names.CONTENT_LENGTH);
        return !contentLength.isEmpty();
    }

    /**
     * A constructor to ensure that instances of this class are never made
     */
    private HttpCodecUtil() {
    }
}
