/*
* Copyright 2014 The Netty Project
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

final class HttpDecoderUtil {
    private static final String EMPTY = "";

    static int startIndex(char[] chars, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            if (chars[i] <=  ' ') {
                startIndex++;
            } else {
                break;
            }
        }
        return startIndex;
    }

    static int endIndex(char[] chars, int startIndex, int endIndex) {
        for (int i = endIndex - 1; i > startIndex; i--) {
            if (chars[i] <= ' ') {
                endIndex--;
            } else {
                break;
            }
        }
        return endIndex;
    }

    static String newString(char[] chars, int startIndex, int endIndex) {
        int size = endIndex - startIndex;
        if (size == 0) {
            return EMPTY;
        }
        return new String(chars, startIndex, size);
    }

    static int parseInt(char[] chars, int startIndex, int endIndex) {
        int result = 0;
        for (int i = startIndex; i < endIndex; i++) {
            int digit = chars[i] - '0';
            if ((digit < 0) || (digit > 9)) {
                throw new NumberFormatException();
            }
            result *= 10;
            result += digit;
        }
        return result;
    }

    private HttpDecoderUtil() { }
}
