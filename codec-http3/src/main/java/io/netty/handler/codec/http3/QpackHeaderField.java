/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http3;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

class QpackHeaderField {

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-dynamic-table-size">
     *     Section 3.2.1 Dynamic Table Size</a>.
     * The size of an entry is the sum of its name's length in bytes, its
     * value's length in bytes, and 32.
     */
    static final int ENTRY_OVERHEAD = 32;

    static long sizeOf(CharSequence name, CharSequence value) {
        return name.length() + value.length() + ENTRY_OVERHEAD;
    }

    final CharSequence name;
    final CharSequence value;

    // This constructor can only be used if name and value are ISO-8859-1 encoded.
    QpackHeaderField(CharSequence name, CharSequence value) {
        this.name = checkNotNull(name, "name");
        this.value = checkNotNull(value, "value");
    }

    long size() {
        return sizeOf(name, value);
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
