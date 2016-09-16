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
 * Copyright 2014 Twitter, Inc.
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
package io.netty.handler.codec.http2.internal.hpack;

import static io.netty.handler.codec.http2.internal.hpack.HpackUtil.equalsConstantTime;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

class HeaderField {

    // Section 4.1. Calculating Table Size
    // The additional 32 octets account for an estimated
    // overhead associated with the structure.
    static final int HEADER_ENTRY_OVERHEAD = 32;

    static long sizeOf(CharSequence name, CharSequence value) {
        return name.length() + value.length() + HEADER_ENTRY_OVERHEAD;
    }

    final CharSequence name;
    final CharSequence value;

    // This constructor can only be used if name and value are ISO-8859-1 encoded.
    HeaderField(CharSequence name, CharSequence value) {
        this.name = checkNotNull(name, "name");
        this.value = checkNotNull(value, "value");
    }

    int size() {
        return name.length() + value.length() + HEADER_ENTRY_OVERHEAD;
    }

    @Override
    public int hashCode() {
        // TODO(nmittler): Netty's build rules require this. Probably need a better implementation.
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof HeaderField)) {
            return false;
        }
        HeaderField other = (HeaderField) obj;
        // To avoid short circuit behavior a bitwise operator is used instead of a boolean operator.
        return (equalsConstantTime(name, other.name) & equalsConstantTime(value, other.value)) != 0;
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
