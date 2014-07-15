/*
 * Copyright 2013 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

final class HttpHeaderEntity implements CharSequence {

    private final String name;
    private final int hash;
    private final byte[] bytes;
    private final int separatorLen;

    public HttpHeaderEntity(String name) {
        this(name, null);
    }

    public HttpHeaderEntity(String name, byte[] separator) {
        this.name = name;
        hash = HttpHeaders.hash(name);
        byte[] nameBytes = name.getBytes(CharsetUtil.US_ASCII);
        if (separator == null) {
            bytes = nameBytes;
            separatorLen = 0;
        } else {
            separatorLen = separator.length;
            bytes = new byte[nameBytes.length + separator.length];
            System.arraycopy(nameBytes, 0, bytes, 0, nameBytes.length);
            System.arraycopy(separator, 0, bytes, nameBytes.length, separator.length);
        }
    }

    int hash() {
        return hash;
    }

    @Override
    public int length() {
        return bytes.length - separatorLen;
    }

    @Override
    public char charAt(int index) {
        if ((bytes.length - separatorLen) <= index) {
            throw new IndexOutOfBoundsException();
        }
        return (char) bytes[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new HttpHeaderEntity(name.substring(start, end));
    }

    @Override
    public String toString() {
        return name;
    }

    boolean encode(ByteBuf buf) {
        buf.writeBytes(bytes);
        return separatorLen > 0;
    }
}
