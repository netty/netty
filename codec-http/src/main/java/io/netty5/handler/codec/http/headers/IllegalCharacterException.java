/*
 * Copyright 2022 The Netty Project
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
/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import org.jetbrains.annotations.Nullable;

import static java.lang.Character.toChars;
import static java.lang.Integer.toHexString;

/**
 * Exception that clarifies an illegal character and expected values.
 */
public final class IllegalCharacterException extends IllegalArgumentException {
    private static final long serialVersionUID = 5109746801766842145L;

    /**
     * Creates a new instance.
     *
     * @param value value of the character
     */
    public IllegalCharacterException(final byte value) {
        super(message(value, null));
    }

    /**
     * Creates a new instance.
     *
     * @param value value of the character
     * @param expected definition of expected value(s)
     */
    public IllegalCharacterException(final byte value, final String expected) {
        super(message(value, expected));
    }

    private static String message(final byte value, @Nullable final String expected) {
        final int codePoint = value & 0xff;
        final StringBuilder sb = new StringBuilder(expected == null ? 10 : 23 + expected.length())
                .append('\'')
                .append(toChars(codePoint))
                .append("' (0x")
                .append(toHexString(0x100 | codePoint), 1, 3);  // to 2 digit hex number
        return (expected == null ? sb.append(')') : sb.append("), expected [").append(expected).append(']')).toString();
    }
}
