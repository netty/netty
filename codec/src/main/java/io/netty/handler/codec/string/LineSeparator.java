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
package io.netty.handler.codec.string;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * A class to represent line separators in different environments.
 */
public final class LineSeparator {

    /**
     * The default line separator in the current system.
     */
    public static final LineSeparator DEFAULT = new LineSeparator(StringUtil.NEWLINE);

    /**
     * The Unix line separator(LF)
     */
    public static final LineSeparator UNIX = new LineSeparator("\n");

    /**
     * The Windows line separator(CRLF)
     */
    public static final LineSeparator WINDOWS = new LineSeparator("\r\n");

    private final String value;

    /**
     * Create {@link LineSeparator} with the specified {@code lineSeparator} string.
     */
    public LineSeparator(String lineSeparator) {
        this.value = ObjectUtil.checkNotNull(lineSeparator, "lineSeparator");
    }

    /**
     * Return the string value of this line separator.
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineSeparator)) {
            return false;
        }
        LineSeparator that = (LineSeparator) o;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    /**
     * Return a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a> of the line separator in UTF-8 encoding.
     */
    @Override
    public String toString() {
        return ByteBufUtil.hexDump(value.getBytes(CharsetUtil.UTF_8));
    }
}
